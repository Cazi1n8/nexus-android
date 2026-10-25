"""A completed process or a 100% byte count alone cannot certify a usable model."""
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import xml.etree.ElementTree as ET

SCRIPT = Path(__file__).resolve().parents[1] / "run_android_model_download.py"
SPEC = importlib.util.spec_from_file_location("run_android_model_download", SCRIPT)
runner = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(runner)
PASSED = "INSTRUMENTATION_RESULT: stream=\nTime: 3.4\n\nOK (1 test)\n\nINSTRUMENTATION_CODE: -1\n"


def report():
    return {"executionStatus": "passed", "models": [
        {"profile": profile, "bytes": size, "sha256": checksum, "terminalState": "READY",
         "downloadReturned": True, "refreshState": "READY"}
        for profile, (size, checksum) in runner.EXPECTED_MODELS.items()],
        "inference": {"answer": "42", "arithmeticMatchedExpected42": True}}


class ObservationValidationTest(unittest.TestCase):
    def test_accepts_both_verified_models_and_preserves_incorrect_model_answer(self):
        data = report()
        data["inference"] = {"answer": "41", "arithmeticMatchedExpected42": False}
        runner.validate_observations(PASSED, data)

    def test_rejects_successful_exit_without_the_opt_in_test(self):
        for output in ("INSTRUMENTATION_CODE: -1", PASSED.replace("1 test", "0 tests"),
                       PASSED.replace("-1", "0"), PASSED + "Process crashed"):
            with self.subTest(output=output), self.assertRaises(ValueError):
                runner.validate_observations(output, report())

    def test_rejects_missing_duplicate_or_corrupted_model(self):
        for modify in (lambda data: data["models"].pop(),
                       lambda data: data["models"][0].update(sha256="0" * 64),
                       lambda data: data["models"][0].update(bytes=1),
                       lambda data: data["models"][0].update(profile="balanced")):
            data = report()
            modify(data)
            with self.assertRaises(ValueError):
                runner.validate_observations(PASSED, data)

    def test_rejects_100_percent_without_return_or_verified_ready(self):
        for change in ({"terminalState": "DOWNLOADING"}, {"refreshState": "VERIFYING"},
                       {"downloadReturned": False}, {"downloadReturned": "true"}):
            data = report()
            data["models"][0].update(change)
            with self.subTest(change=change), self.assertRaises(ValueError):
                runner.validate_observations(PASSED, data)

    def test_rejects_missing_generation_or_quality_observation(self):
        for inference in ({"answer": "", "arithmeticMatchedExpected42": True},
                          {"answer": "42"}, None):
            with self.subTest(inference=inference), self.assertRaises(ValueError):
                runner.validate_observations(PASSED, report() | {"inference": inference})


class DeviceRunnerTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        for name in ("app/build/outputs/apk/debug/app-debug.apk",
                     "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"):
            file = self.root / name
            file.parent.mkdir(parents=True, exist_ok=True)
            file.write_bytes(b"apk")
        self.output = self.root / "observations"
        self.calls = []
        self.failed = False
        change = patch.object(runner, "ROOT", self.root)
        change.start()
        self.addCleanup(change.stop)

    def adb(self, *args, **kwargs):
        self.calls.append(args)
        if "instrument" in args:
            self.assertEqual(kwargs["timeout"], 1260)
            self.assertIn(runner.TEST_CLASS, args)
            self.assertIn("nexusDownloadSmoke", args)
            self.assertIn("true", args)
            if self.failed:
                raise RuntimeError("instrument disconnected")
            return PASSED.encode()
        if args[:1] == ("exec-out",):
            return json.dumps(report() | ({"executionStatus": "failed"} if self.failed else {})).encode()
        return b""

    def test_downloads_in_android_without_model_push_and_cleans_only_fixture(self):
        with patch.object(runner, "adb", side_effect=self.adb):
            self.assertEqual(report(), runner.run(self.output))
        self.assertFalse(any("push" in args or "-T" in args for args in self.calls))
        self.assertIn(("shell", "run-as", runner.APP_ID, "rm", "-f", runner.REMOTE_REPORT), self.calls)
        self.assertEqual(self.calls[-1], ("shell", "run-as", runner.APP_ID, "rm", "-rf", runner.REMOTE_FIXTURE_DIRECTORY))
        self.assertEqual(ET.parse(self.output / "junit.xml").getroot().get("failures"), "0")

    def test_failure_replaces_stale_success_collects_partial_report_and_cleans(self):
        self.output.mkdir()
        (self.output / "production-download-observations.json").write_text(json.dumps(report()))
        self.failed = True
        with patch.object(runner, "adb", side_effect=self.adb), self.assertRaises(RuntimeError):
            runner.run(self.output)
        saved = json.loads((self.output / "production-download-observations.json").read_text())
        self.assertEqual(saved["executionStatus"], "failed")
        self.assertEqual(ET.parse(self.output / "junit.xml").getroot().get("failures"), "1")
        self.assertEqual(self.calls[-1], ("shell", "run-as", runner.APP_ID, "rm", "-rf", runner.REMOTE_FIXTURE_DIRECTORY))


if __name__ == "__main__":
    unittest.main()
