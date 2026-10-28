"""Fail-closed evidence decoding and disposable-device/network safeguards."""
import base64
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import xml.etree.ElementTree as ET

SCRIPT = Path(__file__).resolve().parents[1] / "run_android_core_storage.py"
SPEC = importlib.util.spec_from_file_location("run_android_core_storage", SCRIPT)
runner = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(runner)


def core_report():
    return {"schemaVersion": 1, "executionStatus": "passed", "coreInstallation": True,
            "generativeWeightsAbsent": True, "optionalVisionAssetsAbsent": True,
            "realHandTrackerProcessedBlankFrame": True, "extensionsUiOpened": True,
            "storageMeasurementRendered": True, "manageModelsButtonNavigated": True,
            "sensorPermissionsUnchanged": True, "automaticModelDownloadStarted": False,
            "automaticExtensionDownloadStarted": False, "backgroundServicesActive": False,
            "handModelBytes": runner.HAND_BYTES, "handModelSha256": runner.HAND_SHA256,
            "mouseScreenWaitsForCameraConsent": False, "mouseScreenReportsUnsupportedHardware": True}


def extension_report():
    return {"schemaVersion": 1, "executionStatus": "passed", "optionalDataBytes": 11_530_361,
            **{key: True for key in ("realHttpsDownloadsVerified", "privateClassifierProcessedFixture",
                "privateOcrReadSyntheticText", "verifiedFilesReused", "activeLeaseBlockedRemoval",
                "realHttpsPartialResumeVerified", "optionalFilesRemoved", "handModelPreserved",
                "syntheticUserDataPreserved")}}


def instrumentation(report, *, key="nexus_core_report", tests=2):
    encoded = base64.b64encode(json.dumps(report).encode()).decode()
    return f"INSTRUMENTATION_STATUS: {key}={encoded}\nINSTRUMENTATION_STATUS_CODE: 0\n\n" \
        f"OK ({tests} {'test' if tests == 1 else 'tests'})\nINSTRUMENTATION_CODE: -1\n"


class EvidenceDecoderTest(unittest.TestCase):
    def test_decodes_non_debuggable_status_without_private_file_access(self):
        self.assertEqual(core_report(), runner.decode_report(instrumentation(core_report()), "nexus_core_report", 2))
        runner.validate_core(core_report())
        runner.validate_extensions(extension_report())

    def test_rejects_skipped_missing_or_failed_instrumentation(self):
        success = instrumentation(core_report())
        for output in ("INSTRUMENTATION_CODE: -1\n", success.replace("OK (2 tests)", "OK (1 test)"),
                       success.replace("INSTRUMENTATION_CODE: -1", "INSTRUMENTATION_CODE: 0"),
                       success + "Process crashed", success + "FAILURES!!!", success + "INSTRUMENTATION_FAILED"):
            with self.subTest(output=output), self.assertRaises(ValueError):
                runner.decode_report(output, "nexus_core_report", 2)

    def test_rejects_duplicate_status_and_corrupted_base64_or_utf8_json(self):
        success = instrumentation(core_report())
        for output in (success + success.splitlines()[0] + "\n", success.replace("nexus_core_report=", "other_report="),
                       success.replace(success.splitlines()[0], "INSTRUMENTATION_STATUS: nexus_core_report=%%%")):
            with self.subTest(output=output), self.assertRaises(ValueError):
                runner.decode_report(output, "nexus_core_report", 2)
        for raw in (b"\xff", b"[]", b"not json", b'{"schemaVersion":1,"schemaVersion":1,"executionStatus":"passed"}',
                    b'{"schemaVersion":1,"executionStatus":"passed","invalid":NaN}'):
            value = base64.b64encode(raw).decode()
            output = f"INSTRUMENTATION_STATUS: nexus_core_report={value}\nOK (2 tests)\nINSTRUMENTATION_CODE: -1\n"
            with self.subTest(raw=raw), self.assertRaises(ValueError):
                runner.decode_report(output, "nexus_core_report", 2)

    def test_failed_reports_and_unknown_schema_never_pass(self):
        for change in ({"executionStatus": "failed"}, {"schemaVersion": 2}):
            with self.subTest(change=change), self.assertRaises(ValueError):
                runner.decode_report(instrumentation(core_report() | change), "nexus_core_report", 2)

    def test_rejects_missing_assets_no_consent_and_invented_blank_frame(self):
        for change in ({"realHandTrackerProcessedBlankFrame": False}, {"generativeWeightsAbsent": False},
                       {"handModelBytes": 3}, {"handModelSha256": "0" * 64},
                       {"sensorPermissionsUnchanged": 1}, {"automaticModelDownloadStarted": True},
                       {"backgroundServicesActive": None}, {"mouseScreenWaitsForCameraConsent": False,
                                                             "mouseScreenReportsUnsupportedHardware": False}):
            with self.subTest(change=change), self.assertRaises(ValueError):
                runner.validate_core(core_report() | change)

    def test_optional_data_requires_actual_download_native_engine_and_safe_removal(self):
        for key in extension_report():
            if key in ("schemaVersion", "executionStatus"):
                continue
            with self.subTest(key=key), self.assertRaises(ValueError):
                runner.validate_extensions(extension_report() | {key: None})


class DisposableDeviceRunnerTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.output = self.root / "observations"
        for name in ("app/build/outputs/apk/coreTest/app-coreTest.apk",
                     "app/build/outputs/apk/androidTest/coreTest/app-coreTest-androidTest.apk"):
            target = self.root / name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(b"apk")
        self.calls = []
        self.emulator = True
        self.installed = False
        self.failed = False
        self.network = {"wifi": "0", "data": "1"}
        change = patch.object(runner, "ROOT", self.root)
        change.start()
        self.addCleanup(change.stop)

    def adb(self, *args, **kwargs):
        self.calls.append(args)
        if args[:2] == ("shell", "getprop"):
            return b"1\n" if self.emulator else b"0\n"
        if args[:4] == ("shell", "pm", "list", "packages"):
            return f"package:{runner.APP_ID}\n".encode() if self.installed else b""
        if args[:4] == ("shell", "settings", "get", "global"):
            key = "wifi" if args[4] == "wifi_on" else "data"
            return self.network[key].encode()
        if args[:2] == ("shell", "svc"):
            self.network[args[2]] = "1" if args[3] == "enable" else "0"
        if args[:3] == ("shell", "dumpsys", "connectivity"):
            return b"Active default network: 101\n" if "1" in self.network.values() else b"Active default network: none\n"
        if args[:1] == ("install",):
            self.assertEqual(kwargs["timeout"], 300)
        if "instrument" in args:
            if runner.CORE_CLASSES in args:
                self.assertEqual(self.network, {"wifi": "0", "data": "0"})
                self.assertIn("nexusCoreSmoke", args)
                if self.failed:
                    return instrumentation(core_report() | {"executionStatus": "failed"}).encode()
                return instrumentation(core_report()).encode()
            self.assertIn(runner.EXTENSION_CLASS, args)
            self.assertEqual(self.network, {"wifi": "1", "data": "1"})
            self.assertIn("nexusExtensionSmoke", args)
            return instrumentation(extension_report(), key="nexus_extension_report", tests=1).encode()
        return b""

    def mutate_calls(self):
        return [args for args in self.calls if args[:1] == ("install",) or args[:2] == ("shell", "svc")]

    def test_refuses_without_opt_in_without_any_device_call(self):
        with patch.object(runner, "adb", side_effect=self.adb), self.assertRaises(ValueError):
            runner.run(self.output)
        self.assertEqual(self.calls, [])

    def test_refuses_physical_device_before_install_network_or_data_changes(self):
        self.emulator = False
        with patch.object(runner, "adb", side_effect=self.adb), self.assertRaises(ValueError):
            runner.run(self.output, allow_reset_emulator=True)
        self.assertEqual(self.mutate_calls(), [])

    def test_refuses_existing_installation_and_never_erases_it(self):
        self.installed = True
        with patch.object(runner, "adb", side_effect=self.adb), self.assertRaises(ValueError):
            runner.run(self.output, allow_reset_emulator=True)
        self.assertEqual(self.mutate_calls(), [])

    def test_undefined_network_state_aborts_before_any_mutation(self):
        self.network["data"] = "null"
        with patch.object(runner, "adb", side_effect=self.adb), self.assertRaises(ValueError):
            runner.run(self.output, allow_reset_emulator=True)
        self.assertEqual(self.mutate_calls(), [])

    def test_core_only_is_offline_and_restores_network_without_optional_download(self):
        with patch.object(runner, "adb", side_effect=self.adb):
            reports = runner.run(self.output, allow_reset_emulator=True)
        self.assertEqual(reports, {"core": core_report()})
        self.assertEqual(self.network, {"wifi": "0", "data": "1"})
        self.assertFalse(any("push" in args or "run-as" in args or "-g" in args or "clear" in args for args in self.calls))
        self.assertEqual(len([args for args in self.calls if "instrument" in args]), 1)
        self.assertEqual(ET.parse(self.output / "junit.xml").getroot().get("failures"), "0")

    def test_extensions_run_only_after_core_in_explicit_online_stage(self):
        with patch.object(runner, "adb", side_effect=self.adb):
            reports = runner.run(self.output, allow_reset_emulator=True, with_extensions=True)
        self.assertEqual(reports, {"core": core_report(), "extensions": extension_report()})
        self.assertEqual(self.network, {"wifi": "0", "data": "1"})
        self.assertEqual(json.loads((self.output / "extension-observations.json").read_text()), extension_report())

    def test_core_failure_never_runs_extensions_and_replaces_stale_evidence(self):
        self.output.mkdir()
        (self.output / "core-observations.json").write_text(json.dumps(core_report()))
        self.failed = True
        with patch.object(runner, "adb", side_effect=self.adb), self.assertRaises(ValueError):
            runner.run(self.output, allow_reset_emulator=True, with_extensions=True)
        self.assertFalse((self.output / "core-observations.json").exists())
        self.assertTrue((self.output / "core-instrumentation.txt").exists())
        self.assertEqual(len([args for args in self.calls if "instrument" in args]), 1)
        self.assertEqual(self.network, {"wifi": "0", "data": "1"})
        self.assertEqual(ET.parse(self.output / "junit.xml").getroot().get("failures"), "1")

    def test_route_must_match_offline_state_even_after_switches_are_off(self):
        def connected(*args, **kwargs):
            if args[:3] == ("shell", "dumpsys", "connectivity"):
                return b"Active default network: 101\n"
            return self.adb(*args, **kwargs)
        with patch.object(runner, "adb", side_effect=connected), \
                patch.object(runner.time, "monotonic", side_effect=range(0, 1000, 31)), \
                patch.object(runner.time, "sleep"), self.assertRaises(RuntimeError):
            runner.run(self.output, allow_reset_emulator=True)
        self.assertFalse(any("instrument" in args for args in self.calls))
        self.assertEqual(self.network, {"wifi": "0", "data": "1"})


if __name__ == "__main__":
    unittest.main()
