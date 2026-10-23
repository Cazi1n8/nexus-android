"""Testes de integração HTTP locais; não usam modelo real, internet ou credenciais."""

from __future__ import annotations

import base64
from dataclasses import replace
from collections import OrderedDict
from email.message import Message
from http.client import HTTPConnection
from http.client import HTTPResponse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from http import HTTPStatus
import io
import json
import socket
import threading
import time
from types import SimpleNamespace
import unittest
from urllib import error, parse, request
from urllib.response import addinfourl

from server.nexus_gateway import (
    ConfigurationError, GatewayConfig, GatewayHandler, GatewayServer, InvalidPayload, NoRedirects,
    MAX_BODY_BYTES, validate_payload,
)


TOKEN = "test-session-token-not-a-real-secret-123456789"
UPSTREAM_KEY = "test-upstream-key-never-shipped-to-android"


class UpstreamHandler(BaseHTTPRequestHandler):
    def log_message(self, format, *args):
        pass

    def do_POST(self):
        length = int(self.headers.get("Content-Length", "0"))
        payload = json.loads(self.rfile.read(length))
        self.server.seen.append({
            "path": self.path,
            "auth": self.headers.get("Authorization"),
            "payload": payload,
        })
        mode = self.server.mode
        if mode == "redirect":
            self.send_response(302)
            self.send_header("Location", f"http://127.0.0.1:{self.server.server_port}/stolen")
            self.send_header("Content-Length", "0")
            self.end_headers()
            return
        if mode == "timeout":
            time.sleep(1.3)
        if mode == "refuse":
            status, value = 401, {"error": {"message": UPSTREAM_KEY}}
        elif mode == "rate":
            status, value = 429, {"error": {"message": UPSTREAM_KEY}}
        elif mode == "malformed":
            status, value = 200, {"choices": []}
        elif mode == "invalid_unicode":
            status, value = 200, {"choices": [{"message": {"content": "\ud800"}}]}
        elif mode == "content_parts":
            status, value = 200, {"choices": [{"message": {"content": [
                {"type": "text", "text": "Primeira parte."}, {"type": "text", "text": "Segunda parte."}
            ]}}]}
        elif mode == "refusal":
            status, value = 200, {"choices": [{"message": {"content": None, "refusal": "Não posso ajudar com isso."}}]}
        elif mode == "null_content":
            status, value = 200, {"choices": [{"message": {"content": None, "refusal": None}}]}
        elif mode == "non_text_content":
            status, value = 200, {"choices": [{"message": {"content": [{"type": "text", "text": 123}]}}]}
        else:
            status, value = 200, {
                "choices": [{"message": {"role": "assistant", "content": "Resposta real do upstream de teste."}}],
                "private_metadata": UPSTREAM_KEY,
            }
        body = json.dumps(value).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        try:
            self.wfile.write(body)
        except (BrokenPipeError, ConnectionResetError):
            pass


class GatewayTests(unittest.TestCase):
    def setUp(self):
        try:
            self.upstream = ThreadingHTTPServer(("127.0.0.1", 0), UpstreamHandler)
        except PermissionError:
            self.skipTest("Este sandbox não permite sockets locais; execute também InMemoryGatewayTests.")
        self.upstream.daemon_threads = True
        self.upstream.seen = []
        self.upstream.mode = "success"
        self.upstream_thread = threading.Thread(target=self.upstream.serve_forever, daemon=True)
        self.upstream_thread.start()
        self.config = GatewayConfig(
            access_token=TOKEN,
            upstream_api_key=UPSTREAM_KEY,
            upstream_base_url=f"http://127.0.0.1:{self.upstream.server_port}/v1",
            model="test-model",
            port=0,
            upstream_timeout_seconds=1,
        )
        self.gateway = GatewayServer(self.config)
        self.gateway_thread = threading.Thread(target=self.gateway.serve_forever, daemon=True)
        self.gateway_thread.start()
        self.base_url = f"http://127.0.0.1:{self.gateway.server_port}"

    def tearDown(self):
        self.gateway.shutdown()
        self.gateway.server_close()
        self.upstream.shutdown()
        self.upstream.server_close()
        self.gateway_thread.join(2)
        self.upstream_thread.join(2)

    def call(self, body=None, token=TOKEN, path="/v1/chat/completions", raw=None, headers=None):
        if body is None:
            body = {"model": "test-model", "messages": [{"role": "user", "content": "Olá"}]}
        data = raw if raw is not None else json.dumps(body).encode()
        actual_headers = {"Content-Type": "application/json"}
        if token is not None:
            actual_headers["Authorization"] = "Bearer " + token
        actual_headers.update(headers or {})
        req = request.Request(self.base_url + path, data=data, headers=actual_headers, method="POST")
        try:
            with request.urlopen(req, timeout=4) as response:
                return response.status, json.loads(response.read()), response.headers
        except error.HTTPError as response:
            with response:
                return response.code, json.loads(response.read()), response.headers

    def test_health_is_not_a_claim_of_model_health(self):
        with request.urlopen(self.base_url + "/healthz", timeout=2) as response:
            value = json.loads(response.read())
        self.assertEqual("ok", value["status"])
        self.assertEqual("not_checked", value["upstream"])
        self.assertEqual([], self.upstream.seen)

    def test_forward_uses_server_key_and_fixed_model_and_drops_unknown_fields(self):
        status, answer, headers = self.call({
            "model": "test-model",
            "messages": [{"role": "user", "content": "Olá"}],
            "api_key": "malicious-client-key", "base_url": "http://127.0.0.1:1",
            "tools": [{"name": "should_not_be_forwarded"}],
        })
        self.assertEqual(200, status)
        self.assertEqual("Resposta real do upstream de teste.", answer["choices"][0]["message"]["content"])
        self.assertNotIn("private_metadata", answer)
        self.assertEqual("no-store", headers["Cache-Control"])
        seen = self.upstream.seen[0]
        self.assertEqual("/v1/chat/completions", seen["path"])
        self.assertEqual("Bearer " + UPSTREAM_KEY, seen["auth"])
        self.assertNotEqual("Bearer " + TOKEN, seen["auth"])
        self.assertNotIn("tools", seen["payload"])
        self.assertNotIn("api_key", seen["payload"])
        self.assertNotIn("base_url", seen["payload"])

    def test_missing_and_invalid_auth_never_reach_upstream(self):
        for token in (None, "incorrect-session"):
            with self.subTest(token=token):
                status, body, _ = self.call(token=token)
                self.assertEqual(401, status)
                self.assertEqual("unauthorized", body["error"]["code"])
        self.assertEqual([], self.upstream.seen)

    def test_non_ascii_auth_returns_401_without_crash(self):
        conn = HTTPConnection("127.0.0.1", self.gateway.server_port, timeout=2)
        conn.request("POST", "/v1/chat/completions", body=b"{}", headers={
            "Authorization": "Bearer sess\u00e3o", "Content-Type": "application/json"
        })
        response = conn.getresponse()
        self.assertEqual(401, response.status)
        response.read()
        conn.close()

    def test_expired_session_is_refused(self):
        self.gateway.config = replace(self.config, started_at=time.monotonic() - 28_801)
        self.assertEqual(401, self.call()[0])
        self.assertEqual([], self.upstream.seen)

    def test_bad_json_and_invalid_unicode_return_400(self):
        status, body, _ = self.call(raw=b'{"messages":')
        self.assertEqual(400, status)
        self.assertEqual("invalid_json", body["error"]["code"])
        status, body, _ = self.call(body={"messages": [{"role": "user", "content": "\ud800"}]})
        self.assertEqual(400, status)
        self.assertEqual("invalid_payload", body["error"]["code"])

    def test_different_model_is_rejected(self):
        self.assertEqual(400, self.call({"model": "expensive-model", "messages": [{"role": "user", "content": "Hi"}]})[0])
        self.assertEqual([], self.upstream.seen)

    def test_remote_image_url_is_rejected_before_upstream(self):
        status, _, _ = self.call({"messages": [{"role": "user", "content": [
            {"type": "image_url", "image_url": {"url": "http://169.254.169.254/latest/meta-data/"}}
        ]}]})
        self.assertEqual(400, status)
        self.assertEqual([], self.upstream.seen)

    def test_multimodal_data_uri_reaches_upstream(self):
        # O gateway valida envelope/tamanho; decodificação visual pertence ao modelo.
        uri = "data:image/png;base64," + base64.b64encode(b"tiny-image-envelope").decode()
        status, _, _ = self.call({"messages": [{"role": "user", "content": [
            {"type": "text", "text": "Descreva"},
            {"type": "image_url", "image_url": {"url": uri}},
        ]}]})
        self.assertEqual(200, status)
        self.assertEqual(uri, self.upstream.seen[0]["payload"]["messages"][0]["content"][1]["image_url"]["url"])

    def test_too_large_request_rejected_without_reading_body(self):
        conn = HTTPConnection("127.0.0.1", self.gateway.server_port, timeout=2)
        conn.request("POST", "/v1/chat/completions", body=None, headers={
            "Authorization": "Bearer " + TOKEN,
            "Content-Type": "application/json",
            "Content-Length": str(MAX_BODY_BYTES + 1),
        })
        response = conn.getresponse()
        self.assertEqual(413, response.status)
        self.assertEqual("payload_too_large", json.loads(response.read())["error"]["code"])
        conn.close()

    def test_other_path_and_queries_are_not_proxied(self):
        for path in ("/v1/models", "/proxy", "/v1/chat/completions?url=https://other.example"):
            with self.subTest(path=path):
                self.assertEqual(404, self.call(path=path)[0])
        self.assertEqual([], self.upstream.seen)

    def test_upstream_secrets_are_not_returned_in_errors(self):
        self.upstream.mode = "refuse"
        status, body, _ = self.call()
        self.assertEqual(502, status)
        self.assertNotIn(UPSTREAM_KEY, json.dumps(body))
        self.assertNotIn(TOKEN, json.dumps(body))

    def test_upstream_429_is_visible_as_rate_limit(self):
        self.upstream.mode = "rate"
        status, body, _ = self.call()
        self.assertEqual(429, status)
        self.assertEqual("upstream_rate_limited", body["error"]["code"])

    def test_malformed_upstream_is_not_presented_as_ai_answer(self):
        for mode in ("malformed", "invalid_unicode", "null_content", "non_text_content"):
            with self.subTest(mode=mode):
                self.upstream.mode = mode
                status, body, _ = self.call()
                self.assertEqual(502, status)
                self.assertEqual("invalid_upstream_response", body["error"]["code"])

    def test_text_parts_and_refusals_are_normalized_for_the_app(self):
        for mode, expected in (
            ("content_parts", "Primeira parte.\nSegunda parte."),
            ("refusal", "Não posso ajudar com isso."),
        ):
            with self.subTest(mode=mode):
                self.upstream.mode = mode
                status, body, _ = self.call()
                self.assertEqual(200, status)
                self.assertEqual(expected, body["choices"][0]["message"]["content"])
                self.assertNotIn(UPSTREAM_KEY, json.dumps(body))

    def test_upstream_timeout_is_reported(self):
        self.upstream.mode = "timeout"
        status, body, _ = self.call()
        self.assertEqual(504, status)
        self.assertEqual("upstream_timeout", body["error"]["code"])

    def test_redirect_is_not_followed_with_upstream_key(self):
        self.upstream.mode = "redirect"
        status, _, _ = self.call()
        self.assertEqual(502, status)
        self.assertEqual(1, len(self.upstream.seen))
        self.assertEqual("/v1/chat/completions", self.upstream.seen[0]["path"])

    def test_busy_server_returns_503(self):
        self.assertTrue(self.gateway.inference_slots.acquire(blocking=False))
        self.assertTrue(self.gateway.inference_slots.acquire(blocking=False))
        try:
            status, body, _ = self.call()
        finally:
            self.gateway.inference_slots.release()
            self.gateway.inference_slots.release()
        self.assertEqual(503, status)
        self.assertEqual("busy", body["error"]["code"])
        self.assertEqual([], self.upstream.seen)

    def test_rate_limiter_rejects_repeated_requests(self):
        self.gateway.config = replace(self.config, rate_per_minute=1)
        self.assertEqual(200, self.call()[0])
        status, body, headers = self.call()
        self.assertEqual(429, status)
        self.assertEqual("rate_limited", body["error"]["code"])
        self.assertEqual("60", headers["Retry-After"])


class MemorySocket:
    """Transporte HTTP em memória para testar o handler sem exigir sockets."""

    def __init__(self, incoming: bytes):
        self.incoming = io.BytesIO(incoming)
        self.outgoing = io.BytesIO()

    def makefile(self, mode, *args):
        return self.incoming

    def sendall(self, data):
        self.outgoing.write(data)

    def settimeout(self, timeout):
        pass


class MemoryUpstreamHandler(request.BaseHandler):
    handler_order = 100

    def __init__(self, state):
        self.state = state

    def http_open(self, req):
        self.state.seen.append({
            "path": parse.urlsplit(req.full_url).path,
            "auth": req.get_header("Authorization"),
            "payload": json.loads(req.data),
        })
        headers = Message()
        headers["Content-Type"] = "application/json"
        mode = self.state.mode
        if mode == "timeout":
            raise socket.timeout("test timeout")
        if mode == "redirect":
            status, value = 302, {}
            headers["Location"] = "http://127.0.0.1:11434/stolen"
        elif mode == "refuse":
            status, value = 401, {"error": {"message": UPSTREAM_KEY}}
        elif mode == "rate":
            status, value = 429, {"error": {"message": UPSTREAM_KEY}}
        elif mode == "malformed":
            status, value = 200, {"choices": []}
        elif mode == "invalid_unicode":
            status, value = 200, {"choices": [{"message": {"content": "\ud800"}}]}
        elif mode == "content_parts":
            status, value = 200, {"choices": [{"message": {"content": [
                {"type": "text", "text": "Primeira parte."}, {"type": "text", "text": "Segunda parte."}
            ]}}]}
        elif mode == "refusal":
            status, value = 200, {"choices": [{"message": {"content": None, "refusal": "Não posso ajudar com isso."}}]}
        elif mode == "null_content":
            status, value = 200, {"choices": [{"message": {"content": None, "refusal": None}}]}
        elif mode == "non_text_content":
            status, value = 200, {"choices": [{"message": {"content": [{"type": "text", "text": 123}]}}]}
        else:
            status, value = 200, {
                "choices": [{"message": {"role": "assistant", "content": "Resposta real do upstream de teste."}}],
                "private_metadata": UPSTREAM_KEY,
            }
        response = addinfourl(io.BytesIO(json.dumps(value).encode()), headers, req.full_url, status)
        response.msg = HTTPStatus(status).phrase
        return response


class InMemoryGatewayTests(GatewayTests):
    """Mesmos casos de protocolo, executados sem rede ou modelo real.

    O transporte HTTP do cliente e do upstream é substituído; o handler,
    validação, autenticação, limites e política de redirects são os reais.
    """

    def setUp(self):
        self.upstream = SimpleNamespace(mode="success", seen=[])
        self.config = GatewayConfig(
            access_token=TOKEN, upstream_api_key=UPSTREAM_KEY,
            upstream_base_url="http://127.0.0.1:11434/v1", model="test-model", port=0,
            upstream_timeout_seconds=1,
        )
        self.gateway = GatewayServer.__new__(GatewayServer)
        self.gateway.config = self.config
        self.gateway.inference_slots = threading.BoundedSemaphore(self.config.concurrency)
        self.gateway.rate_lock = threading.Lock()
        self.gateway.request_times = OrderedDict()
        self.gateway.opener = request.build_opener(NoRedirects(), MemoryUpstreamHandler(self.upstream))

    def tearDown(self):
        pass

    def memory_request(self, method, path, data=b"", headers=None):
        actual_headers = {"Host": "localhost", "Content-Length": str(len(data)), "Connection": "close"}
        actual_headers.update(headers or {})
        header_text = "".join(f"{key}: {value}\r\n" for key, value in actual_headers.items())
        socket_stub = MemorySocket(f"{method} {path} HTTP/1.1\r\n{header_text}\r\n".encode("latin-1") + data)
        GatewayHandler(socket_stub, ("127.0.0.1", 1234), self.gateway)
        response = HTTPResponse(MemorySocket(socket_stub.outgoing.getvalue()))
        response.begin()
        return response.status, json.loads(response.read()), response.headers

    def call(self, body=None, token=TOKEN, path="/v1/chat/completions", raw=None, headers=None):
        if body is None:
            body = {"model": "test-model", "messages": [{"role": "user", "content": "Olá"}]}
        actual_headers = {"Content-Type": "application/json"}
        if token is not None:
            actual_headers["Authorization"] = "Bearer " + token
        actual_headers.update(headers or {})
        return self.memory_request("POST", path, raw if raw is not None else json.dumps(body).encode(), actual_headers)

    def test_health_is_not_a_claim_of_model_health(self):
        status, body, _ = self.memory_request("GET", "/healthz")
        self.assertEqual(200, status)
        self.assertEqual("not_checked", body["upstream"])
        self.assertEqual([], self.upstream.seen)

    def test_non_ascii_auth_returns_401_without_crash(self):
        status, _, _ = self.call(headers={"Authorization": "Bearer sess\u00e3o"})
        self.assertEqual(401, status)

    def test_too_large_request_rejected_without_reading_body(self):
        status, body, _ = self.call(raw=b"", headers={"Content-Length": str(MAX_BODY_BYTES + 1)})
        self.assertEqual(413, status)
        self.assertEqual("payload_too_large", body["error"]["code"])


class ConfigurationAndValidationTests(unittest.TestCase):
    def test_provider_origin_base_and_complete_urls_reach_the_same_endpoint(self):
        for url in ("https://provider.example", "https://provider.example/", "https://provider.example/v1/", "https://provider.example/v1/chat/completions/"):
            with self.subTest(url=url):
                config = GatewayConfig(TOKEN, upstream_base_url=url)
                self.assertEqual("https://provider.example/v1/chat/completions", config.completion_url)
        self.assertEqual("https://provider.example/custom/v1/chat/completions", GatewayConfig(
            TOKEN, upstream_base_url="https://provider.example/custom/v1/"
        ).completion_url)

    def test_ollama_environment_does_not_duplicate_v1_or_completions(self):
        for url in ("http://127.0.0.1:11434", "http://127.0.0.1:11434/v1", "http://127.0.0.1:11434/v1/chat/completions"):
            with self.subTest(url=url):
                config = GatewayConfig.from_environment({"NEXUS_ACCESS_TOKEN": TOKEN, "OLLAMA_BASE_URL": url})
                self.assertEqual("http://127.0.0.1:11434/v1/chat/completions", config.completion_url)

    def test_credentials_never_appear_in_repr(self):
        config = GatewayConfig(TOKEN, upstream_api_key=UPSTREAM_KEY)
        self.assertNotIn(TOKEN, repr(config))
        self.assertNotIn(UPSTREAM_KEY, repr(config))

    def test_missing_token_and_public_http_upstream_are_rejected(self):
        for config in ({"access_token": ""}, {"access_token": TOKEN, "upstream_base_url": "http://example.com/v1"}):
            with self.subTest(config=config), self.assertRaises(ConfigurationError):
                GatewayConfig(**config)

    def test_invalid_configuration_does_not_echo_secret(self):
        secret_url = "https://private-user:private-password@example.com/v1"
        with self.assertRaises(ConfigurationError) as raised:
            GatewayConfig(TOKEN, upstream_base_url=secret_url)
        self.assertNotIn("private-password", str(raised.exception))

    def test_request_parameters_are_bounded(self):
        base = {"messages": [{"role": "user", "content": "Olá"}]}
        for extra in ({"temperature": float("nan")}, {"temperature": 10**400}, {"max_tokens": 999_999}, {"stream": True}):
            with self.subTest(extra=extra), self.assertRaises(InvalidPayload):
                validate_payload({**base, **extra}, "test-model")


if __name__ == "__main__":
    unittest.main()
