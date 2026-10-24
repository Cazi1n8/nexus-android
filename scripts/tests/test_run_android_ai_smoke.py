"""A process exit code alone must never masquerade as real Android inference."""
import copy
import importlib.util
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch
import xml.etree.ElementTree as ET

SCRIPT_ROOT = Path(__file__).resolve().parents[1]
# The standalone runner imports its sibling downloader when executed by Python.
with patch.object(sys, "path", [str(SCRIPT_ROOT), *sys.path]):
    SPEC = importlib.util.spec_from_file_location("run_android_ai_smoke", SCRIPT_ROOT / "run_android_ai_smoke.py")
    runner = importlib.util.module_from_spec(SPEC)
    SPEC.loader.exec_module(runner)

PASSED = "INSTRUMENTATION_RESULT: stream=\nTime: 3.4\n\nOK (1 test)\n\nINSTRUMENTATION_CODE: -1\n"


def report():
    return {"executionStatus": "passed", "modelSha256": runner.MODEL_SHA256,
            "modelBytes": runner.MODEL_BYTES, "contextTokens": 512, "threads": 1,
            "cases": [{"name": name, "answer": "Observação real em português"}
                      for name in sorted(runner.EXPECTED_CASES)],
            "arithmeticMatchedExpected42": False,
            "contextMatchedExpectedCiano": True,
            "unicodeMatchedRequestedText": False}


class ObservationValidationTest(unittest.TestCase):
    def test_preserves_honest_quality_failures_when_inference_really_ran(self):
        runner.validate_observations(PASSED, report())

    def test_does_not_accept_a_successful_process_without_test_confirmation(self):
        with self.assertRaisesRegex(ValueError, "exatamente um teste"):
            runner.validate_observations("INSTRUMENTATION_CODE: -1", report())

    def test_rejects_skipped_missing_or_crashed_test(self):
        for output in (PASSED.replace("OK (1 test)", "OK (0 tests)"),
                       PASSED.replace("-1", "0"), PASSED + "Process crashed"):
            with self.subTest(output=output):
                with self.assertRaises(ValueError):
                    runner.validate_observations(output, report())

    def test_rejects_report_for_another_model_or_missing_inference(self):
        changes = [{"modelSha256": "0" * 64}, {"modelBytes": 1},
                   {"executionStatus": "failed"}, {"cases": []}, {"threads": 4}]
        for change in changes:
            with self.subTest(change=change):
                invalid = report() | change
                with self.assertRaises(ValueError):
                    runner.validate_observations(PASSED, invalid)

    def test_rejects_blank_generation_and_missing_quality_observation(self):
        invalid = report()
        invalid["cases"][0]["answer"] = "  "
        with self.assertRaisesRegex(ValueError, "não devolveu texto"):
            runner.validate_observations(PASSED, invalid)
        invalid = report()
        del invalid["unicodeMatchedRequestedText"]
        with self.assertRaisesRegex(ValueError, "observação explícita"):
            runner.validate_observations(PASSED, invalid)


class DeviceRunnerTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        for name in ("app/build/outputs/apk/debug/app-debug.apk",
                     "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"):
            apk = self.root / name
            apk.parent.mkdir(parents=True, exist_ok=True)
            apk.write_bytes(b"apk")
        self.model = self.root / "fixture.gguf"
        self.model.write_bytes(b"GGUFfixture")
        self.output = self.root / "observations"
        self.calls = []
        self.instrumentation_error = False
        for target, value in (("ROOT", self.root), ("validate_model", lambda path: None)):
            change = patch.object(runner, target, value)
            change.start()
            self.addCleanup(change.stop)

    def adb(self, *arguments, **kwargs):
        self.calls.append(arguments)
        if arguments[:2] == ("shell", "-T"):
            self.assertEqual(kwargs["stdin"].read(), self.model.read_bytes())
        if "instrument" in arguments:
            if self.instrumentation_error:
                raise RuntimeError("instrument process disconnected")
            return PASSED.encode()
        if arguments[:1] == ("exec-out",):
            saved = report()
            if self.instrumentation_error:
                saved["executionStatus"] = "failed"
            return json.dumps(saved, ensure_ascii=False).encode()
        return b""

    def test_streams_fixture_saves_verified_report_and_cleans_only_fixture(self):
        with patch.object(runner, "adb", side_effect=self.adb):
            actual = runner.run(self.model, self.output)
        self.assertEqual(actual, report())
        self.assertEqual(json.loads((self.output / "local-ai-observations.json").read_text()), report())
        self.assertEqual(ET.parse(self.output / "junit.xml").getroot().get("failures"), "0")
        self.assertIn(("shell", "run-as", runner.APP_ID, "rm", "-f", runner.REMOTE_REPORT), self.calls)
        self.assertEqual(self.calls[-1], ("shell", "run-as", runner.APP_ID, "rm", "-f", runner.REMOTE_MODEL))

    def test_failure_retrieves_partial_report_then_cleans_fixture_and_records_failure(self):
        self.instrumentation_error = True
        # This old successful report must not survive the failed new run.
        self.output.mkdir()
        (self.output / "local-ai-observations.json").write_text(json.dumps(report()))
        with patch.object(runner, "adb", side_effect=self.adb):
            with self.assertRaisesRegex(RuntimeError, "disconnected"):
                runner.run(self.model, self.output)
        saved = json.loads((self.output / "local-ai-observations.json").read_text())
        self.assertEqual(saved["executionStatus"], "failed")
        self.assertEqual(ET.parse(self.output / "junit.xml").getroot().get("failures"), "1")
        self.assertEqual(self.calls[-1], ("shell", "run-as", runner.APP_ID, "rm", "-f", runner.REMOTE_MODEL))


if __name__ == "__main__":
    unittest.main()
