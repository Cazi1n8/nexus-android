"""Controlled transport/guard tests; none of these executes or approves an LLM."""
import contextlib
import importlib.util
import io
import json
import os
from pathlib import Path
import struct
import tempfile
import threading
import time
import unittest
from unittest.mock import patch
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import zlib


spec = importlib.util.spec_from_file_location("online_evaluator", Path(__file__).parents[1] / "run_nexus_online_evaluation.py")
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


def sse(*events):
    return io.BytesIO(b"".join(b"data: " + (e.encode() if isinstance(e, str) else json.dumps(e, ensure_ascii=False).encode())
                             + b"\n\n" for e in events))


def event(text="", reason=None):
    return {"choices": [{"index": 0, "delta": {"content": text}, "finish_reason": reason}]}


class EvaluationGuards(unittest.TestCase):
    def test_https_or_explicit_loopback_and_no_embedded_secrets(self):
        for value in ("https://nexus.example", "https://nexus.example/v1/", "http://127.0.0.1:8888/v1", "http://[::1]:8888"):
            self.assertTrue(module.endpoint(value).endswith("/v1"))
        for value in ("http://nexus.example", "http://localhost.evil", "http://10.0.2.2:8787", "file:///tmp/x",
                      "https://user:secret@nexus.example", "https://nexus.example?key=secret", "https://nexus.example/#key",
                      "https://nexus.example/v1/chat/completions", "https://nexus.example/a\nb", "https://nexus.example:bad"):
            with self.subTest(value=value), self.assertRaises(module.EvaluationError):
                module.endpoint(value)

    def test_invitation_requires_private_regular_file_and_never_api_key_argument(self):
        with tempfile.TemporaryDirectory() as d:
            p = Path(d) / "invitation"
            p.write_text("x" * 32)
            p.chmod(0o600)
            self.assertEqual("x" * 32, module.read_invitation(p))
            p.chmod(0o644)
            with self.assertRaises(module.EvaluationError):
                module.read_invitation(p)
            p.chmod(0o600)
            alias = Path(d) / "alias"
            alias.symlink_to(p)
            with self.assertRaises(module.EvaluationError):
                module.read_invitation(alias)

    def test_synthetic_png_has_correct_crc_dimensions_and_pixels(self):
        png = module.synthetic_image()
        self.assertEqual(png, module.synthetic_image())
        self.assertTrue(png.startswith(b"\x89PNG\r\n\x1a\n"))
        cursor = 8
        pixels = b""
        while cursor < len(png):
            size = struct.unpack("!I", png[cursor:cursor + 4])[0]
            kind = png[cursor + 4:cursor + 8]
            data = png[cursor + 8:cursor + 8 + size]
            crc = struct.unpack("!I", png[cursor + 8 + size:cursor + 12 + size])[0]
            self.assertEqual(crc, zlib.crc32(kind + data) & 0xFFFFFFFF)
            if kind == b"IHDR":
                self.assertEqual((320, 240), struct.unpack("!II", data[:8]))
            if kind == b"IDAT":
                pixels += data
            cursor += 12 + size
        raw = zlib.decompress(pixels)
        self.assertEqual(240 * (1 + 320 * 3), len(raw))
        def color(x, y):
            i = y * 961 + 1 + 3 * x
            return tuple(raw[i:i + 3])
        self.assertEqual((230, 25, 35), color(70, 100))
        self.assertEqual((20, 70, 230), color(235, 115))
        self.assertEqual((255, 255, 255), color(10, 10))

    def test_planned_cases_have_only_user_turns_and_known_request_budget(self):
        cases = module.cases()
        self.assertEqual(list(module.CASE_IDS), [c["id"] for c in cases])
        self.assertEqual(32, sum(len(c["turns"]) for c in cases))
        context = next(c for c in cases if c["id"] == "context")
        self.assertEqual(20, len(context["turns"]))
        for c in cases:
            self.assertEqual({"id", "turns"}, set(c))
        for value in ("", "unknown", "math,math", "vision,unknown"):
            with self.assertRaises(module.EvaluationError):
                module.selected_cases(value)

    def test_public_report_never_copies_personal_transcripts_provider_metadata_or_tokens(self):
        secret = "PRIVATE_SENTINEL"
        report = module.public_report({"status": "completed_for_review", "access_token": secret, "endpoint": secret,
            "provider": {"private": secret}, "cases": [{"id": "writing", "status": "completed_for_review", "requestedTurns": 1,
            "turns": [{"status": "received", "text": secret, "prompt": secret, "token": secret, "streamed": True}]}]})
        encoded = json.dumps(report)
        self.assertNotIn(secret, encoded)
        self.assertNotIn('"prompt"', encoded)
        self.assertFalse(report["qualityApproved"])
        self.assertEqual("not_assessed_independent_review_required", report["semanticQuality"])
        self.assertFalse(report["productionKotlinClientTest"])

    def test_private_report_requires_outside_repo_and_never_overwrites_old_evidence(self):
        with tempfile.TemporaryDirectory() as d:
            p = Path(d) / "private" / "record.json"
            module.secure_write(p, {"x": 1}, private=True)
            self.assertEqual(0o600, p.stat().st_mode & 0o777)
            self.assertEqual(0o700, p.parent.stat().st_mode & 0o777)
            with self.assertRaises(module.EvaluationError):
                module.secure_write(p, {"x": 2}, private=True)
            self.assertEqual({"x": 1}, json.loads(p.read_text()))
            with self.assertRaises(module.EvaluationError):
                module.secure_write(module.ROOT / "private-model-transcript.json", {}, private=True)

    def test_no_configuration_means_no_network_and_exit_two_not_fake_model_success(self):
        with tempfile.TemporaryDirectory() as d, patch.dict(os.environ, {}, clear=True), patch.object(module.Client, "redeem") as redeem:
            output = Path(d) / "technical.json"
            with contextlib.redirect_stdout(io.StringIO()) as stdout:
                code = module.main(["--public-output", str(output)])
            self.assertEqual(2, code)
            redeem.assert_not_called()
            report = json.loads(output.read_text())
            self.assertEqual("not_run_unconfigured", report["status"])
            self.assertEqual(0, report["actualInferenceRequests"])
            self.assertEqual("client_chat_http_requests", report["requestCountScope"])
            self.assertEqual(3, report["providerRoundsPerClientRequestLimit"])
            self.assertIsNone(report["actualProviderRounds"])
            self.assertEqual(32 * 512, report["maximumRequestedOutputTokens"])
            self.assertEqual(32 * 512 * 3, report["maximumProviderOutputTokenBudget"])
            self.assertFalse(report["qualityApproved"])
            self.assertNotIn("PRIVATE", stdout.getvalue())

    def test_request_budget_blocks_before_any_model_and_does_not_rewrite_old_report(self):
        with tempfile.TemporaryDirectory() as d, patch.object(module.Client, "redeem") as redeem:
            output = Path(d) / "technical.json"
            with contextlib.redirect_stdout(io.StringIO()):
                code = module.main(["--public-output", str(output), "--max-requests", "1"])
            self.assertEqual(1, code)
            self.assertEqual("invalid_or_exceeded_request_budget", json.loads(output.read_text())["reasonCode"])
            first = output.read_bytes()
            with contextlib.redirect_stdout(io.StringIO()):
                module.main(["--public-output", str(output)])
            self.assertEqual(first, output.read_bytes())
            redeem.assert_not_called()

    def test_mechanical_number_check_does_not_approve_quality_or_accept_wrong_substring(self):
        rows = [{"status": "received", "text": "450", "finishReason": "stop"},
                {"status": "received", "text": "2600", "finishReason": "stop"}]
        self.assertTrue(module.mechanical_checks("math", rows)["containsExpectedNumericValues"])
        rows[0]["text"] = "14500"
        self.assertFalse(module.mechanical_checks("math", rows)["containsExpectedNumericValues"])
        self.assertEqual({}, module.mechanical_checks("math", [{"status": "technical_failure"}]))

    def test_provider_output_upper_budget_includes_internal_tool_rounds_without_inventing_actual_count(self):
        report = module.public_report({"status": "completed_for_review", "plannedRequests": 2,
            "actualInferenceRequests": 2, "maximumRequestedOutputTokens": 2 * 128,
            "actualProviderRounds": 999, "providerRoundsPerClientRequestLimit": 999})
        self.assertEqual(2 * 128 * 3, report["maximumProviderOutputTokenBudget"])
        self.assertEqual(3, report["providerRoundsPerClientRequestLimit"])
        self.assertIsNone(report["actualProviderRounds"])
        self.assertEqual("client_output_budget_before_internal_tool_cycles", report["maximumRequestedOutputTokensScope"])
        self.assertIn("not_confirmed_provider_executions", report["requestCountSemantics"])

    def test_refresh_failure_does_not_count_chat_and_failed_chat_is_never_replayed(self):
        client = module.Client("http://127.0.0.1:8787", 5)
        with patch.object(client, "refresh", side_effect=module.EvaluationError("http_401")) as refresh:
            with self.assertRaises(module.EvaluationError):
                client.complete([], 128, stream=False)
            self.assertEqual(0, client.chat_request_attempts)
            refresh.assert_called_once()
        client.expires_at = time.monotonic() + 900
        with patch.object(client, "json", side_effect=module.EvaluationError("http_401")) as chat:
            with self.assertRaises(module.EvaluationError):
                client.complete([], 128, stream=False)
            self.assertEqual(1, client.chat_request_attempts)
            chat.assert_called_once()


class StreamingProtocolTests(unittest.TestCase):
    def test_real_chunks_are_concatenated_once_utf8_and_usage_are_bounded(self):
        stream = sse(event("Olá "), event("ação"), event(reason="stop"),
                     {"choices": [], "usage": {"prompt_tokens": 20, "completion_tokens": 5}}, "[DONE]")
        out = module.read_stream(stream, time.monotonic(), 5)
        self.assertEqual("Olá ação", out["text"])
        self.assertTrue(out["streamed"])
        self.assertEqual(20, out["inputTokensReported"])
        self.assertIsInstance(out["firstTextMs"], int)
        self.assertFalse(out["truncated"])

    def test_missing_done_finish_or_text_never_succeeds(self):
        for stream in (sse(event("Text"), event(reason="stop")), sse(event("Text"), "[DONE]"),
                       sse(event(reason="stop"), "[DONE]")):
            with self.subTest(stream=stream), self.assertRaises(module.EvaluationError):
                module.read_stream(stream, time.monotonic(), 5)

    def test_bad_encoding_error_bodies_and_text_after_stop_are_not_exposed(self):
        streams = [sse({"error": {"message": "PRIVATE_SENTINEL"}}), sse(event("Text", "stop"), event("extra"), "[DONE]"),
                   io.BytesIO(b"data: \xff\n\n")]
        for stream in streams:
            with self.subTest(stream=stream), self.assertRaises(module.EvaluationError) as found:
                module.read_stream(stream, time.monotonic(), 5)
            self.assertNotIn("PRIVATE_SENTINEL", str(found.exception))

    def test_length_is_explicit_truncation_not_unqualified_success(self):
        out = module.read_stream(sse(event("Text", "length"), "[DONE]"), time.monotonic(), 5)
        self.assertTrue(out["truncated"])

    def test_time_and_byte_budgets_are_enforced(self):
        with self.assertRaises(module.EvaluationError):
            module.read_stream(sse(event("Text", "stop"), "[DONE]"), time.monotonic() - 10, 1)
        with self.assertRaises(module.EvaluationError):
            module.read_stream(io.BytesIO(b"data: " + b"x" * module.MAX_EVENT_BYTES + b"\n\n"), time.monotonic(), 5)


class ControlledSocketTests(unittest.TestCase):
    """Actual loopback HTTP bytes; synthetic transport data, not model outputs."""
    def setUp(self):
        self.paths = []
        self.bodies = []
        paths, bodies = self.paths, self.bodies
        class Handler(BaseHTTPRequestHandler):
            def log_message(self, *args):
                pass

            def do_GET(self):
                paths.append(self.path)
                data = json.dumps({"service": "nexus-gateway", "protocol_version": 1, "vision": True}).encode()
                self.send_response(200)
                self.send_header("Content-Type", "application/json")
                self.send_header("Content-Length", str(len(data)))
                self.end_headers()
                self.wfile.write(data)

            def do_POST(self):
                paths.append(self.path)
                body = json.loads(self.rfile.read(int(self.headers["Content-Length"])))
                bodies.append(body)
                if self.path.endswith(("redeem", "refresh")):
                    data = json.dumps({"access_token": "controlled-access-" + "a" * 32,
                                       "refresh_token": "controlled-refresh-" + "b" * 32, "expires_in": 900}).encode()
                    ctype = "application/json"
                elif self.path.endswith("revoke"):
                    data, ctype = b'{"revoked":true}', "application/json"
                else:
                    data = sse(event("controlled transport fragment"), event(reason="stop"), "[DONE]").getvalue()
                    ctype = "text/event-stream"
                self.send_response(200)
                self.send_header("Content-Type", ctype)
                self.send_header("Content-Length", str(len(data)))
                self.end_headers()
                self.wfile.write(data)
        self.server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()

    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()
        self.thread.join(2)

    def test_redeem_rotate_infer_revoke_and_private_evidence_roundtrip(self):
        with tempfile.TemporaryDirectory() as d:
            base = f"http://127.0.0.1:{self.server.server_port}/v1"
            invite = Path(d) / "invitation"
            invite.write_text("a" * 32)
            invite.chmod(0o600)
            raw = {"cases": [], "actualInferenceRequests": 0, "technicalFailures": 0, "status": "preflight"}
            private = Path(d) / "private" / "report.json"
            module.evaluate(base, invite, module.selected_cases("writing"), raw, private, 5, 128)
            self.assertEqual("completed_for_review", raw["status"])
            self.assertEqual(2, raw["actualInferenceRequests"])
            self.assertEqual(["/v1/sessions/redeem", "/v1/sessions/refresh", "/v1/capabilities",
                              "/v1/chat/completions", "/v1/chat/completions", "/v1/sessions/revoke"], self.paths)
            second = self.bodies[-2]
            self.assertEqual("assistant", second["messages"][-2]["role"])
            self.assertEqual("nexus-ai", second["model"])
            self.assertTrue(raw["sessionRevocationRequested"])
            self.assertEqual(0o600, private.stat().st_mode & 0o777)
            persisted = private.read_text()
            self.assertIn("controlled transport fragment", persisted)
            self.assertNotIn("controlled-access-", persisted)
            self.assertNotIn("controlled-refresh-", persisted)
            self.assertNotIn("controlled transport fragment", json.dumps(module.public_report(raw)))

    def test_preflight_only_never_redeems_or_executes_model(self):
        with tempfile.TemporaryDirectory() as d:
            invite = Path(d) / "invitation"
            invite.write_text("a" * 32)
            invite.chmod(0o600)
            with contextlib.redirect_stdout(io.StringIO()):
                code = module.main(["--endpoint", f"http://127.0.0.1:{self.server.server_port}",
                                    "--invitation-file", str(invite), "--preflight-only", "--public-output", str(Path(d) / "summary")])
            self.assertEqual(2, code)
            self.assertEqual([], self.paths)


if __name__ == "__main__":
    unittest.main()
