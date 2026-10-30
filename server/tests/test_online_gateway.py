"""HTTP integration with a controlled upstream: protocol, NOT model quality."""
from dataclasses import replace
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from http.client import HTTPConnection
import json
from pathlib import Path
import tempfile
import threading
import unittest
from unittest.mock import patch
from urllib import request, error
from server.nexus_gateway import GatewayConfig, GatewayServer, ConfigurationError
from server.session_store import StoreError

KEY = "controlled-upstream-credential-not-a-real-api-key"

class ControlledProvider(BaseHTTPRequestHandler):
    def log_message(self, *args): pass
    def do_POST(self):
        data = json.loads(self.rfile.read(int(self.headers["Content-Length"])))
        self.server.seen.append((data, dict(self.headers)))
        if self.server.reject:
            self.send_response(401); self.send_header("Content-Length", str(len(KEY))); self.end_headers(); self.wfile.write(KEY.encode()); return
        self.send_response(200); self.send_header("Content-Type", "text/event-stream"); self.send_header("Connection", "close"); self.end_headers()
        events = []
        if self.server.action:
            events.append({"choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"id":"provider-private-id","type":"function","function":{"name":"navigate","arguments":'{"route":"settings"}'}}]},"finish_reason":None}]})
            events.append({"choices":[{"index":0,"delta":{},"finish_reason":"tool_calls"}]})
        else:
            events.append({"choices":[{"index":0,"delta":{"content":"controlled-"},"finish_reason":None}]})
            events.append({"choices":[{"index":0,"delta":{"content":"transport"},"finish_reason":"stop"}]})
        for event in events:
            self.wfile.write(("data: " + json.dumps(event) + "\n\n").encode()); self.wfile.flush()
        self.wfile.write(b'data: {"choices":[],"usage":{"prompt_tokens":20,"completion_tokens":4},"private_metadata":"hidden"}\n\ndata: [DONE]\n\n'); self.wfile.flush()

class OnlineGatewayTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        try: self.upstream = ThreadingHTTPServer(("127.0.0.1",0), ControlledProvider)
        except PermissionError: self.tmp.cleanup(); self.skipTest("Local sockets require execution permission")
        self.upstream.daemon_threads=True; self.upstream.seen=[]; self.upstream.reject=False; self.upstream.action=False
        self.upthread=threading.Thread(target=self.upstream.serve_forever,daemon=True); self.upthread.start()
        self.config=GatewayConfig(access_token="",session_db=str(Path(self.tmp.name)/"sessions.sqlite3"),
            upstream_base_url=f"http://127.0.0.1:{self.upstream.server_port}/v1",upstream_api_key=KEY,
            provider="anthropic",upstream_workspace_id="workspace-controlled",model="controlled-model",model_vision=True,port=0)
        self.gateway=GatewayServer(self.config); self.thread=threading.Thread(target=self.gateway.serve_forever,daemon=True); self.thread.start()
        self.base=f"http://127.0.0.1:{self.gateway.server_port}"
    def tearDown(self):
        self.gateway.shutdown(); self.gateway.server_close(); self.upstream.shutdown(); self.upstream.server_close()
        self.thread.join(2); self.upthread.join(2); self.tmp.cleanup()
    def call(self,path,payload=None,token=None):
        headers={"Content-Type":"application/json"}
        if token: headers["Authorization"]="Bearer "+token
        req=request.Request(self.base+path,data=json.dumps(payload).encode() if payload is not None else None,headers=headers)
        try:
            with request.urlopen(req,timeout=4) as r: return r.status,r.read(),r.headers
        except error.HTTPError as r:
            with r: return r.code,r.read(),r.headers
    def connect(self):
        invite=self.gateway.sessions.create_invitation()
        status,body,_=self.call("/v1/sessions/redeem",{"invitation_code":invite.code})
        self.assertEqual(200,status); value=json.loads(body); self.assertEqual("nexus-ai",value["model"])
        return value,invite.code
    def payload(self,**kw):
        return {"model":"nexus-ai","messages":[{"role":"user","content":"controlled input"}],**kw}
    def test_sessions_redeem_refresh_revoke_and_replay(self):
        initial,code=self.connect()
        self.assertEqual(401,self.call("/v1/sessions/redeem",{"invitation_code":code})[0])
        status,body,_=self.call("/v1/sessions/refresh",{"refresh_token":initial["refresh_token"]})
        self.assertEqual(200,status); fresh=json.loads(body); self.assertNotEqual(initial["refresh_token"],fresh["refresh_token"])
        self.assertEqual(401,self.call("/v1/capabilities",token=initial["access_token"])[0])
        self.assertEqual(200,self.call("/v1/capabilities",token=fresh["access_token"])[0])
        self.assertEqual(200,self.call("/v1/sessions/revoke",{"refresh_token":fresh["refresh_token"]})[0])
        self.assertEqual(401,self.call("/v1/capabilities",token=fresh["access_token"])[0])
    def test_refresh_replay_revokes_the_fresh_family(self):
        initial,_=self.connect()
        fresh=json.loads(self.call("/v1/sessions/refresh",{"refresh_token":initial["refresh_token"]})[1])
        self.assertEqual(401,self.call("/v1/sessions/refresh",{"refresh_token":initial["refresh_token"]})[0])
        self.assertEqual(401,self.call("/v1/capabilities",token=fresh["access_token"])[0])
    def test_online_alias_private_provider_key_and_no_temperature(self):
        session,_=self.connect();status,body,_=self.call("/v1/chat/completions",self.payload(temperature=0.2),session["access_token"])
        self.assertEqual(200,status); result=json.loads(body); self.assertEqual("nexus-ai",result["model"])
        data,headers=self.upstream.seen[0]; self.assertEqual("controlled-model",data["model"])
        self.assertNotIn("temperature",data); self.assertTrue(data["stream"])
        self.assertFalse(data["parallel_tool_calls"])
        self.assertEqual("Bearer "+KEY,headers["Authorization"])
        self.assertEqual("workspace-controlled",headers.get("Anthropic-Workspace-Id"))
        self.assertEqual("calculate",data["tools"][0]["function"]["name"])
        self.assertNotIn(KEY,body.decode()); self.assertNotIn("private_metadata",body.decode())
    def test_capabilities_require_auth_and_declare_not_tested(self):
        self.assertEqual(401,self.call("/v1/capabilities")[0]); session,_=self.connect()
        status,body,_=self.call("/v1/capabilities",token=session["access_token"]);value=json.loads(body)
        self.assertEqual(200,status); self.assertTrue(value["tools"]);self.assertTrue(value["sessions"])
        self.assertEqual("operator_declared_not_tested",value["vision_status"])
        self.assertEqual("not_checked",value["model_health"])
    def test_shared_token_and_client_model_switch_are_rejected(self):
        session,_=self.connect()
        self.assertEqual(401,self.call("/v1/chat/completions",self.payload(),"not-a-real-session")[0])
        self.assertEqual(400,self.call("/v1/chat/completions",{**self.payload(),"model":"expensive-model"},session["access_token"])[0])
        self.assertEqual([],self.upstream.seen)
    def test_individual_quota_is_reserved_before_provider_and_persists(self):
        session,_=self.connect(); self.gateway.sessions.account_daily_requests=1
        self.assertEqual(200,self.call("/v1/chat/completions",self.payload(),session["access_token"])[0])
        status,body,headers=self.call("/v1/chat/completions",self.payload(),session["access_token"])
        self.assertEqual(429,status); self.assertEqual("daily_quota",json.loads(body)["error"]["code"])
        self.assertIsNotNone(headers.get("Retry-After"));self.assertEqual(1,len(self.upstream.seen))
    def test_upstream_error_does_not_disclose_private_body(self):
        session,_=self.connect(); self.upstream.reject=True
        status,body,_=self.call("/v1/chat/completions",self.payload(stream=True),session["access_token"])
        self.assertEqual(502,status);self.assertNotIn(KEY,body.decode())
    def test_temporary_session_database_failure_is_503_not_account_revocation(self):
        session,_=self.connect()
        with patch.object(self.gateway.sessions,"authenticate",side_effect=StoreError("controlled database failure")):
            self.assertEqual(503,self.call("/v1/capabilities",token=session["access_token"])[0])
            status,body,_=self.call("/v1/chat/completions",self.payload(),session["access_token"])
            self.assertEqual(503,status);self.assertNotIn("controlled database failure",body.decode())
        self.assertEqual([],self.upstream.seen)
        self.assertEqual(200,self.call("/v1/capabilities",token=session["access_token"])[0])
    def test_stream_has_actual_text_usage_and_no_private_metadata(self):
        session,_=self.connect();status,body,headers=self.call("/v1/chat/completions",self.payload(stream=True),session["access_token"])
        self.assertEqual(200,status);self.assertTrue(headers["Content-Type"].startswith("text/event-stream"))
        self.assertIn(b'controlled-',body);self.assertIn(b'[DONE]',body);self.assertIn(b'prompt_tokens',body)
        self.assertNotIn(b'private_metadata',body)
    def test_actions_are_only_validated_proposals_and_schemas_are_server_owned(self):
        session,_=self.connect();self.upstream.action=True
        tools=[{"type":"function","function":{"name":"navigate","description":"arbitrary client data","parameters":{}}}]
        status,body,_=self.call("/v1/chat/completions",self.payload(tools=tools),session["access_token"])
        self.assertEqual(200,status);call=json.loads(body)["choices"][0]["message"]["tool_calls"][0]
        self.assertEqual("navigate",call["function"]["name"]);self.assertEqual("nexus-action-0",call["id"])
        self.assertNotIn("arbitrary client data",json.dumps(self.upstream.seen[0][0]))
    def test_remote_image_urls_and_unknown_tools_cannot_reach_provider(self):
        session,_=self.connect()
        bad=self.payload(messages=[{"role":"user","content":[{"type":"image_url","image_url":{"url":"https://private.invalid/image"}}]}])
        for body in (bad,self.payload(tools=[{"type":"function","function":{"name":"shell"}}])):
            self.assertEqual(400,self.call("/v1/chat/completions",body,session["access_token"])[0])
        self.assertEqual([],self.upstream.seen)
    def test_enrollment_has_strict_body_and_unauthenticated_rate_limit(self):
        self.gateway.config=replace(self.gateway.config,rate_per_minute=1)
        self.assertEqual(400,self.call("/v1/sessions/redeem",{"invitation_code":"bad","extra":"not accepted"})[0])
        self.assertEqual(429,self.call("/v1/sessions/redeem",{"invitation_code":"bad"})[0])
    def test_photo_reaches_the_configured_multimodal_transport(self):
        session,_=self.connect()
        photo="data:image/png;base64,iVBORw0KGgo="
        payload=self.payload(messages=[{"role":"user","content":[{"type":"text","text":"controlled image test"},{"type":"image_url","image_url":{"url":photo}}]}])
        self.assertEqual(200,self.call("/v1/chat/completions",payload,session["access_token"])[0])
        forwarded=self.upstream.seen[0][0]["messages"][-1]["content"]
        self.assertEqual(photo,forwarded[1]["image_url"]["url"])

class ProviderConfigurationTests(unittest.TestCase):
    def test_selected_provider_defaults_are_server_only(self):
        c=GatewayConfig.from_environment({"NEXUS_SESSION_DB":"/private/test.sqlite3","UPSTREAM_API_KEY":KEY})
        self.assertEqual("anthropic",c.provider); self.assertEqual("claude-haiku-5-5",c.model)
        self.assertEqual("https://api.anthropic.com/v1/chat/completions",c.completion_url)
        self.assertEqual("nexus-ai",c.client_model); self.assertTrue(c.model_vision)
        g=GatewayConfig.from_environment({"NEXUS_SESSION_DB":"/private/test.sqlite3","UPSTREAM_API_KEY":KEY,"NEXUS_PROVIDER":"gemini"})
        self.assertEqual("gemini-3.8-flash",g.model);self.assertIn("v1beta/openai/chat/completions",g.completion_url)
    def test_no_implicit_public_access_or_online_key(self):
        for env in ({},{"NEXUS_SESSION_DB":"/private/test.sqlite3"},{"NEXUS_PROVIDER":"unknown","NEXUS_ACCESS_TOKEN":KEY},
            {"NEXUS_SESSION_DB":"/private/test.sqlite3","UPSTREAM_API_KEY":KEY,"NEXUS_BIND":"0.0.0.0"},
            {"NEXUS_SESSION_DB":"/private/test.sqlite3","NEXUS_ACCESS_TOKEN":KEY,"UPSTREAM_API_KEY":KEY}):
            with self.assertRaises(ConfigurationError): GatewayConfig.from_environment(env)

if __name__ == "__main__": unittest.main()
