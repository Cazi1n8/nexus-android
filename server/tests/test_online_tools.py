"""Controlled protocol/real arithmetic tests, never model-quality evaluations."""
import copy
from email.message import Message
import io
import json
import threading
import unittest
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib import request
from server.online_tools import (OnlineToolError, ACTION_SCHEMAS, canonical_tools,
    calculate, validate_call, stream_online, complete_online)


def event(delta=None, finish=None, **extra):
    return {"choices": [{"index": 0, "delta": delta or {}, "finish_reason": finish}], **extra}


def data_bytes(events, done=True):
    values = ["data: " + json.dumps(v) + "\n\n" for v in events]
    if done:
        values.append("data: [DONE]\n\n")
    return "".join(values).encode()


class Response(io.BytesIO):
    def __init__(self, data, content_type="text/event-stream"):
        super().__init__(data)
        self.headers = Message()
        self.headers["Content-Type"] = content_type


class Opener:
    def __init__(self, responses):
        self.responses = list(responses)
        self.seen = []

    def open(self, req, timeout):
        self.seen.append({"url": req.full_url, "payload": json.loads(req.data), "timeout": timeout})
        response = self.responses.pop(0)
        return Response(response) if isinstance(response, bytes) else response


def forward():
    return {"model": "provider-model", "messages": [{"role": "user", "content": "Synthetic test"}],
            "tools": canonical_tools(list(ACTION_SCHEMAS.values())), "max_tokens": 1024, "stream": True}


def call_events(name="calculate", arguments=None, *, signature=True):
    args = json.dumps(arguments if arguments is not None else {"expression": "50+50+50*50"})
    first = {"index": 0, "id": "provider-call-123", "type": "function", "function": {"name": name, "arguments": args[:4]}}
    if signature:
        first["extra_content"] = {"google": {"thought_signature": "private-signature-never-client"}}
        first["function"]["extra_content"] = {"provider_signature": "private-function-signature"}
    return [event({"tool_calls": [first]}, private_metadata="private-never-client"),
            event({"tool_calls": [{"index": 0, "function": {"arguments": args[4:]}}]}),
            event(finish="tool_calls"), {"choices": [], "usage": {"prompt_tokens": 12, "completion_tokens": 3, "total_tokens": 15, "private": "redact"}}]


class ArithmeticTests(unittest.TestCase):
    def test_precedence_parentheses_unary_unicode_and_right_associative_power(self):
        for expression, result in {"50+50+50*50": "2600", "10+10÷10×10": "20", "25*18": "450",
            "(2+3)*4": "20", "2^3^2": "512", "-2^2": "-4", "(-2)^2": "4", "2^-2": "0.25",
            "1--2": "3", "3−2": "1", "0.1+0.2": "0.3", ".5*2": "1", "-0": "0"}.items():
            with self.subTest(expression=expression):
                self.assertEqual(result, calculate(expression)["result"])

    def test_denies_code_complexity_non_finite_and_unsupported_syntax(self):
        for value in ("", "x", "1e1000", "__import__('os')", "sin(1)", "1/0", "0^-1", "(-1)^.5", "2^101",
                      "9"*40, "("*30+"1"+")"*30, "1+"*140+"1", "1,5", "1\n+2", "1..2", "1 2", "1**2", "2("):
            with self.subTest(expression=value), self.assertRaises(OnlineToolError):
                calculate(value)
        for value in (None, 10, True, {}):
            with self.subTest(value=value), self.assertRaises(OnlineToolError):
                calculate(value)


class ToolPolicyTests(unittest.TestCase):
    def test_replaces_untrusted_schema_and_never_invents_actions(self):
        supplied = [{"type": "function", "function": {"name": "navigate", "description": "UNTRUSTED INSTRUCTIONS", "parameters": {"url": "bad"}}}]
        tools = canonical_tools(supplied)
        self.assertEqual(["navigate", "calculate"], [t["function"]["name"] for t in tools])
        self.assertNotIn("UNTRUSTED", json.dumps(tools))
        self.assertEqual(ACTION_SCHEMAS["navigate"], tools[0])
        tools[0]["function"]["name"] = "changed"
        self.assertEqual("navigate", ACTION_SCHEMAS["navigate"]["function"]["name"])

    def test_rejects_unknown_duplicate_or_forced_tools(self):
        for tools, choice in (([{"type": "function", "function": {"name": "shell"}}], "auto"),
            (list(ACTION_SCHEMAS.values())[:1]*2, "auto"), ([], "required"), ([], {"type": "function"}),
            ({}, "auto"), ([{}], "auto"), ([{"type": "function", "function": {"name": []}}], "auto")):
            with self.subTest(tools=tools), self.assertRaises(OnlineToolError):
                canonical_tools(tools, choice)

    def test_validates_action_targets_and_exact_argument_keys(self):
        allowed = set(ACTION_SCHEMAS)
        self.assertEqual({"route": "vision"}, validate_call("navigate", {"route": "vision"}, allowed))
        self.assertEqual({}, validate_call("open_settings", {}, allowed))
        self.assertEqual({"packageName": "br.com.example"}, validate_call("open_app", {"packageName": "br.com.example"}, allowed))
        for name, args in (("navigate", {"route": "missing"}), ("navigate", {"route": "vision", "hidden": True}),
            ("open_settings", {"extra": 1}), ("open_app", {"packageName": "a; shell"}), ("open_app", {"packageName": []}),
            ("navigate", {"route": []}), ("calculate", {"expression": "2+2", "other": 1}), ("unknown", {})):
            with self.subTest(name=name,args=args), self.assertRaises(OnlineToolError):
                validate_call(name, args, allowed)
        with self.assertRaises(OnlineToolError):
            validate_call("navigate", {"route": "vision"}, set())


class OnlineStreamingTests(unittest.TestCase):
    def test_streams_real_delta_before_completion_and_strips_metadata(self):
        opener = Opener([data_bytes([event({"content": "first"}, private_metadata="redact"), event({"content": " second"}), event(finish="stop")])])
        generated = stream_online(opener, "https://provider.example/v1/chat/completions", {"Authorization": "Bearer private"}, forward(), 30)
        first = next(generated)
        self.assertIn("first", first)
        self.assertNotIn("redact", first)
        self.assertFalse(opener.responses)
        self.assertNotIn("temperature", opener.seen[0]["payload"])
        self.assertTrue(opener.seen[0]["payload"]["stream"])
        rest = list(generated)
        self.assertEqual("[DONE]", rest[-1])
        self.assertIn(" second", "".join(rest))
        self.assertNotIn("private", first+"".join(rest))

    def test_calculator_is_real_and_only_continuation_retains_private_signatures(self):
        opener = Opener([data_bytes(call_events()), data_bytes([event({"content": "2600"}), event(finish="stop"),
                         {"choices": [], "usage": {"prompt_tokens": 20, "completion_tokens": 4, "total_tokens": 24}}])])
        reservations = []
        original = forward()
        result = list(stream_online(opener, "https://provider.example/v1/chat/completions", {}, original, 30,
                                    lambda p: reservations.append(copy.deepcopy(p))))
        self.assertEqual(2, len(reservations))
        self.assertEqual(1, len(original["messages"]))
        continuation = opener.seen[1]["payload"]["messages"]
        tool = continuation[-2]["tool_calls"][0]
        self.assertIn("private-signature", json.dumps(tool))
        self.assertIn("private-function-signature", json.dumps(tool))
        self.assertEqual("provider-call-123", continuation[-1]["tool_call_id"])
        self.assertEqual("2600", json.loads(continuation[-1]["content"])["result"])
        self.assertNotIn("signature", "".join(result))
        self.assertNotIn("private_metadata", "".join(result))
        usage = [json.loads(c)["usage"] for c in result[:-1] if "usage" in json.loads(c)][0]
        self.assertEqual({"prompt_tokens": 32, "completion_tokens": 7, "total_tokens": 39}, usage)

    def test_actions_are_only_validated_proposals_never_executed_by_server(self):
        opener = Opener([data_bytes(call_events("navigate", {"route": "vision"}))])
        events = list(stream_online(opener, "https://provider.example/v1/chat/completions", {}, forward(), 30))
        self.assertEqual(1, len(opener.seen))
        self.assertNotIn("private-signature", "".join(events))
        tool = [json.loads(v)["choices"][0]["delta"]["tool_calls"][0] for v in events[:-1] if '"tool_calls":[' in v][0]
        self.assertEqual("nexus-action-0", tool["id"])
        self.assertEqual("navigate", tool["function"]["name"])
        self.assertEqual({"route": "vision"}, json.loads(tool["function"]["arguments"]))

    def test_rejects_unadvertised_or_invalid_action_and_truncated_tool(self):
        for name, args, finish in (("shell", {}, "tool_calls"), ("navigate", {"route": "missing"}, "tool_calls"),
                                   ("navigate", {"route": "vision"}, "length")):
            values = call_events(name, args)
            values[-2]["choices"][0]["finish_reason"] = finish
            with self.subTest(name=name), self.assertRaises(OnlineToolError):
                list(stream_online(Opener([data_bytes(values)]), "https://provider.example/v1/chat/completions", {}, forward(), 30))

    def test_calculator_failure_is_evidence_not_a_fake_number(self):
        opener = Opener([data_bytes(call_events(arguments={"expression": "1/0"})), data_bytes([event({"content": "Division is undefined"}), event(finish="stop")])])
        list(stream_online(opener, "https://provider.example/v1/chat/completions", {}, forward(), 30))
        observed = json.loads(opener.seen[1]["payload"]["messages"][-1]["content"])
        self.assertEqual({"error": "unsupported_or_invalid_expression"}, observed)

    def test_repeated_calculations_are_bounded_and_quota_is_reserved_for_each_attempt(self):
        opener = Opener([data_bytes(call_events()) for _ in range(3)])
        reservations = []
        with self.assertRaises(OnlineToolError):
            list(stream_online(opener, "https://provider.example/v1/chat/completions", {}, forward(), 30,
                               lambda p: reservations.append(1)))
        self.assertEqual(3, len(reservations))
        self.assertEqual(3, len(opener.seen))

    def test_quota_refusal_precedes_provider_request(self):
        opener = Opener([])
        def deny(_):
            raise OnlineToolError("Quota exhausted")
        with self.assertRaises(OnlineToolError):
            list(stream_online(opener, "https://provider.example/v1/chat/completions", {}, forward(), 30, deny))
        self.assertEqual([], opener.seen)

    def test_complete_collects_actual_sse_and_sanitizes_tool(self):
        opener = Opener([data_bytes(call_events("open_settings", {}))])
        result = complete_online(opener, "https://provider.example/v1/chat/completions", {}, forward(), 30)
        self.assertEqual("tool_calls", result["choices"][0]["finish_reason"])
        self.assertEqual("open_settings", result["choices"][0]["message"]["tool_calls"][0]["function"]["name"])
        self.assertNotIn("signature", json.dumps(result))

    def test_denies_missing_done_missing_finish_bad_utf8_and_incomplete_arguments(self):
        invalid = [data_bytes([event({"content": "text"}), event(finish="stop")], done=False),
                   data_bytes([event({"content": "text"})]), b"data: \xff\n\n", data_bytes([event({"content": "text"}), event(finish="content_filter")]),
                   data_bytes([event({"tool_calls": [{"index": 0, "id": "id", "function": {"name": "calculate", "arguments": "{"}}]}), event(finish="tool_calls")])]
        for raw in invalid:
            with self.subTest(raw=raw), self.assertRaises(OnlineToolError):
                list(stream_online(Opener([raw]), "https://provider.example/v1/chat/completions", {}, forward(), 30))

    def test_rejects_parallel_tools_choices_after_finish_and_excessive_events(self):
        invalid = [data_bytes([event({"tool_calls": [{"index": 0}, {"index": 1}]}), event(finish="tool_calls")]),
                   data_bytes([{ "choices": [{"index": 0, "delta": {}}, {"index": 1, "delta": {}}]}]),
                   data_bytes([event({"content": "text"}), event(finish="stop"), event({"content": "late"})]),
                   b"data: "+b"x"*65536+b"\n\n", data_bytes([event({"content": "x"*24001}), event(finish="stop")])]
        for raw in invalid:
            with self.subTest(raw=raw[:100]), self.assertRaises(OnlineToolError):
                list(stream_online(Opener([raw]), "https://provider.example/v1/chat/completions", {}, forward(), 30))

    def test_requires_sse_not_fabricated_json_chunks(self):
        with self.assertRaises(OnlineToolError):
            list(stream_online(Opener([Response(b"{}", "application/json")]), "https://provider.example/v1/chat/completions", {}, forward(), 30))


class OnlineSocketStreamingTests(unittest.TestCase):
    def test_first_actual_text_arrives_before_upstream_finishes(self):
        release = threading.Event()
        complete = threading.Event()
        class Handler(BaseHTTPRequestHandler):
            def log_message(self, *_):
                pass
            def do_POST(self):
                self.rfile.read(int(self.headers["Content-Length"]))
                self.send_response(200)
                self.send_header("Content-Type", "text/event-stream")
                self.send_header("Connection", "close")
                self.end_headers()
                self.wfile.write(data_bytes([event({"content": "First actual socket fragment"})], done=False))
                self.wfile.flush()
                release.wait(3)
                self.wfile.write(data_bytes([event({"content": " final fragment"}), event(finish="stop")]))
                self.wfile.flush()
                complete.set()
        try:
            server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        except PermissionError:
            self.skipTest("Local sockets unavailable; this test must run on CI")
        server.daemon_threads = True
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            generator = stream_online(request.build_opener(), f"http://127.0.0.1:{server.server_port}/v1/chat/completions", {}, forward(), 5)
            self.assertIn("First actual socket fragment", next(generator))
            self.assertFalse(complete.is_set(), "The provider must still be generating when first text is delivered")
            release.set()
            remaining = list(generator)
            self.assertEqual("[DONE]", remaining[-1])
            self.assertIn(" final fragment", "".join(remaining))
        finally:
            release.set()
            server.shutdown()
            server.server_close()
            thread.join(2)


if __name__ == "__main__":
    unittest.main()
