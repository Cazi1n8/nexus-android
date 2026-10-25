"""Evidence validation and protection of devices/network controls in the CI runner."""
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import xml.etree.ElementTree as ET

SCRIPT = Path(__file__).resolve().parents[1] / "run_android_bundled_chat.py"
SPEC = importlib.util.spec_from_file_location("run_android_bundled_chat", SCRIPT)
runner = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(runner)
PASSED = "INSTRUMENTATION_RESULT: stream=\nTime: 3.4\n\nOK (1 test)\n\nINSTRUMENTATION_CODE: -1\n"


def report():
    return {"schemaVersion": 1, "executionStatus": "passed", "bundledModel": True,
            "modelPreparedByViewModel": True, "provider": "NEXUS_LOCAL", "modelProfile": "COMPACT",
            "modelSource": "BUNDLED", "profileStableAfterRecreation": True,
            "modelBytes": runner.MODEL_BYTES, "modelSha256": runner.MODEL_SHA256,
            "networkAvailableBefore": False, "networkAvailableAfter": False,
            "activeNetworkPresentBefore": False, "activeNetworkPresentAfter": False,
            "mathCorrect": True, "imageTextReadLocally": True,
            "persistedAssistantMessage": True, "preparationMilliseconds": 2500,
            "reply": {"answer": "azul", "milliseconds": 4500, "matchedExpectedWord": True}}


class ObservationValidationTest(unittest.TestCase):
    def test_accepts_real_ui_local_chat_and_preserves_quality_mistakes(self):
        data = report()
        data["reply"]["answer"] = "verde"
        data["reply"]["matchedExpectedWord"] = False
        runner.validate_observations(PASSED, data)

    def test_rejects_missing_skipped_failed_or_crashed_instrumentation(self):
        for output in ("INSTRUMENTATION_CODE: -1", PASSED.replace("1 test", "0 tests"),
                       PASSED.replace("-1", "0"), PASSED + "Process crashed"):
            with self.subTest(output=output), self.assertRaises(ValueError):
                runner.validate_observations(output, report())

    def test_rejects_network_or_bypassed_viewmodel_and_history(self):
        for change in ({"networkAvailableBefore": True}, {"networkAvailableAfter": 0},
                       {"modelPreparedByViewModel": False}, {"persistedAssistantMessage": False},
                       {"bundledModel": False}, {"provider": "CUSTOM"}, {"modelSource": "NETWORK"},
                       {"profileStableAfterRecreation": False}, {"mathCorrect": False},
                       {"imageTextReadLocally": False}):
            with self.subTest(change=change), self.assertRaises(ValueError):
                runner.validate_observations(PASSED, report() | change)

    def test_rejects_another_model_or_missing_generated_reply(self):
        for change in ({"modelProfile": "BALANCED"}, {"modelBytes": 1}, {"modelSha256": "0" * 64},
                       {"preparationMilliseconds": -1}, {"reply": {"answer": "", "milliseconds": 2,
                                                                   "matchedExpectedWord": True}},
                       {"reply": {"answer": "azul", "milliseconds": 2}}):
            with self.subTest(change=change), self.assertRaises(ValueError):
                runner.validate_observations(PASSED, report() | change)


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
        self.emulator = True
        self.restore_failure = False
        self.network = {"wifi": "0", "data": "1"}
        self.network_disabled = False
        change = patch.object(runner, "ROOT", self.root)
        change.start()
        self.addCleanup(change.stop)

    def adb(self, *args, **kwargs):
        self.calls.append(args)
        if args[:2] == ("shell", "getprop"):
            return b"1\n" if self.emulator else b"0\n"
        if args[:4] == ("shell", "settings", "get", "global"):
            key = "wifi" if args[4] == "wifi_on" else "data"
            return self.network[key].encode()
        if args[:2] == ("shell", "svc"):
            if self.restore_failure and args[2:] == ("data", "enable"):
                raise RuntimeError("emulator disconnected during restore")
            self.network[args[2]] = "1" if args[3] == "enable" else "0"
            return b""
        if args[:1] == ("install",):
            self.assertEqual(kwargs["timeout"], 300)
        if "instrument" in args:
            self.assertEqual(self.network, {"wifi": "0", "data": "0"})
            self.assertEqual(kwargs["timeout"], 960)
            self.assertIn(runner.TEST_CLASS, args)
            self.assertIn("nexusBundledChatSmoke", args)
            self.assertIn("true", args)
            if self.failed:
                raise RuntimeError("instrument disconnected")
            return PASSED.encode()
        if args[:1] == ("exec-out",):
            return json.dumps(report() | ({"executionStatus": "failed"} if self.failed else {})).encode()
        return b""

    def test_requires_explicit_opt_in_and_refuses_physical_devices_before_mutation(self):
        with patch.object(runner, "adb", side_effect=self.adb):
            with self.assertRaises(ValueError):
                runner.run(self.output)
            self.assertEqual(self.calls, [])
            self.emulator = False
            with self.assertRaises(ValueError):
                runner.run(self.output, allow_reset_emulator=True)
        self.assertFalse(any(args[:1] == ("install",) or args[:2] == ("shell", "svc") for args in self.calls))

    def test_runs_offline_without_model_push_and_restores_original_network(self):
        with patch.object(runner, "adb", side_effect=self.adb):
            self.assertEqual(report(), runner.run(self.output, allow_reset_emulator=True))
        self.assertFalse(any("push" in args or "pm" in args or "-T" in args for args in self.calls))
        self.assertIn(("shell", "run-as", runner.APP_ID, "rm", "-f", runner.REMOTE_REPORT), self.calls)
        self.assertFalse(any("-rf" in args for args in self.calls))
        self.assertEqual(self.network, {"wifi": "0", "data": "1"})
        self.assertEqual(ET.parse(self.output / "junit.xml").getroot().get("failures"), "0")

    def test_failure_replaces_stale_report_collects_partial_and_restores_network(self):
        self.output.mkdir()
        (self.output / "bundled-chat-observations.json").write_text(json.dumps(report()))
        self.failed = True
        with patch.object(runner, "adb", side_effect=self.adb), self.assertRaises(RuntimeError):
            runner.run(self.output, allow_reset_emulator=True)
        saved = json.loads((self.output / "bundled-chat-observations.json").read_text())
        self.assertEqual(saved["executionStatus"], "failed")
        self.assertEqual(self.network, {"wifi": "0", "data": "1"})
        self.assertEqual(ET.parse(self.output / "junit.xml").getroot().get("failures"), "1")

    def test_restoration_failure_is_recorded_without_replacing_success(self):
        self.restore_failure = True
        with patch.object(runner, "adb", side_effect=self.adb):
            self.assertEqual(report(), runner.run(self.output, allow_reset_emulator=True))
        saved = json.loads((self.output / "network-restoration.json").read_text())
        self.assertEqual(saved["restorationCommandErrors"], ["data: RuntimeError"])
        self.assertEqual(ET.parse(self.output / "junit.xml").getroot().get("failures"), "0")

    def test_undefined_network_state_is_rejected_before_install_or_disable(self):
        self.network["data"] = "null"
        with patch.object(runner, "adb", side_effect=self.adb), self.assertRaises(ValueError):
            runner.run(self.output, allow_reset_emulator=True)
        self.assertFalse(any(args[:1] == ("install",) or args[:2] == ("shell", "svc") for args in self.calls))


if __name__ == "__main__":
    unittest.main()
