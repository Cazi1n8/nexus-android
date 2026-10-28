import hashlib
import json
from pathlib import Path
import struct
import sys
import tempfile
import unittest
from unittest.mock import patch
import warnings
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import inspect_core_apk as audit


class CoreApkAuditTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.apk = self.root / "core.apk"
        self.hand_data = b"small pinned test hand payload"
        self.hand = audit.HandModel("assets/hand_landmarker.task", len(self.hand_data),
                                   hashlib.sha256(self.hand_data).hexdigest())
        patcher = patch.object(audit, "HAND_MODEL", self.hand)
        patcher.start()
        self.addCleanup(patcher.stop)

    def elf(self, abi):
        elf_class, machine = audit.SUPPORTED_ABIS[abi]
        data = bytearray(64)
        data[:7] = b"\x7fELF" + bytes((elf_class, 1, 1))
        struct.pack_into("<H", data, 18, machine)
        return bytes(data)

    def fixture(self, extra=(), *, hand=None, abis=None, omitted=()):
        entries = [("AndroidManifest.xml", b"manifest fixture"), ("classes.dex", b"dex fixture"),
                   (self.hand.path, self.hand_data if hand is None else hand)]
        for abi in (audit.SUPPORTED_ABIS if abis is None else abis):
            entries.append((f"lib/{abi}/libtracker.so", self.elf(abi)))
        entries.extend(extra)
        with warnings.catch_warnings():
            warnings.simplefilter("ignore", UserWarning)
            with zipfile.ZipFile(self.apk, "w", zipfile.ZIP_DEFLATED) as archive:
                for path, data in entries:
                    if path not in omitted:
                        archive.writestr(path, data)

    def codes(self, report):
        return {item["code"] for item in report["violations"]}

    def test_real_measurement_hash_top20_categories_and_universal_abis(self):
        self.fixture(extra=[("assets/licenses/NOTICE.txt", b"retained license")])
        report = audit.inspect_apk(self.apk, universal=True)
        self.assertEqual(report["executionStatus"], "passed")
        self.assertEqual(report["apkBytes"], self.apk.stat().st_size)
        self.assertEqual(report["apkSha256"], hashlib.sha256(self.apk.read_bytes()).hexdigest())
        self.assertEqual(report["entryCount"], 8)
        self.assertEqual(sum(row["bytes"] for row in report["categories"]), report["uncompressedEntryBytes"])
        self.assertEqual(report["top20"][0]["bytes"], 64)
        self.assertTrue(report["handModel"]["verified"])
        self.assertTrue(report["checks"]["nativeElfHeadersValid"])
        self.assertTrue(report["checks"]["universalAbisPresent"])

    def test_single_supported_abi_passes_without_universal_requirement(self):
        self.fixture(abis=["armeabi-v7a"])
        report = audit.inspect_apk(self.apk)
        self.assertEqual(report["executionStatus"], "passed")
        self.assertIsNone(report["checks"]["universalAbisPresent"])
        self.assertIn("universal_abis_missing", self.codes(audit.inspect_apk(self.apk, universal=True)))

    def test_budget_is_enforced_without_fabricating_measured_bytes(self):
        self.fixture()
        measured = self.apk.stat().st_size
        report = audit.inspect_apk(self.apk, max_bytes=measured - 1)
        self.assertEqual(report["apkBytes"], measured)
        self.assertIn("apk_size_budget", self.codes(report))
        self.assertEqual(audit.inspect_apk(self.apk, max_bytes=measured)["executionStatus"], "passed")

    def test_hand_is_required_and_size_and_hash_are_separately_checked(self):
        self.fixture(omitted=[self.hand.path])
        self.assertIn("hand_model_missing", self.codes(audit.inspect_apk(self.apk)))
        self.fixture(hand=b"wrong")
        self.assertIn("hand_model_size", self.codes(audit.inspect_apk(self.apk)))
        self.fixture(hand=b"x" * len(self.hand_data))
        report = audit.inspect_apk(self.apk)
        self.assertIn("hand_model_hash", self.codes(report))
        self.assertFalse(report["handModel"]["verified"])

    def test_generative_ocr_and_vision_data_are_rejected(self):
        for name in ("assets/models/one.gguf", "assets/models/one.onnx", "assets/ocr/por.traineddata",
                     "assets/new/classifier.tflite", "assets/vision/disguised.bin"):
            with self.subTest(name=name):
                self.fixture(extra=[(name, b"optional data")])
                report = audit.inspect_apk(self.apk)
                self.assertIn("optional_data_in_core", self.codes(report))
                self.assertFalse(report["checks"]["optionalDataAbsent"])

    def test_gguf_magic_with_disguised_name_is_rejected(self):
        self.fixture(extra=[("assets/mystery.bin", b"GGUF" + b"\x00" * 24)])
        self.assertIn("optional_data_in_core", self.codes(audit.inspect_apk(self.apk)))

    def test_executable_asset_suffix_and_magic_are_rejected(self):
        for name, content in [("assets/plugin.dex", b"payload"), ("assets/plugin.jar", b"payload"),
                              ("assets/plugin.bin", b"\x7fELF" + b"x" * 64),
                              ("assets/plugin.dat", b"dex\n035\x00"),
                              ("assets/plugin.bin", b"\x00asm\x01\x00\x00\x00")]:
            with self.subTest(name=name, content=content[:4]):
                self.fixture(extra=[(name, content)])
                report = audit.inspect_apk(self.apk)
                self.assertIn("executable_asset", self.codes(report))
                self.assertFalse(report["checks"]["executableAssetsAbsent"])

    def test_duplicate_entries_are_rejected_even_when_valid_model_is_repeated(self):
        self.fixture(extra=[(self.hand.path, self.hand_data)])
        report = audit.inspect_apk(self.apk)
        self.assertIn("duplicate_zip_entry", self.codes(report))
        self.assertFalse(report["handModel"]["verified"])

    def test_unsafe_zip_paths_are_rejected_without_extraction(self):
        for name in ("../outside", "/outside", "assets/../bad", "assets\\bad", "assets//bad"):
            with self.subTest(name=name):
                self.fixture(extra=[(name, b"bad path")])
                self.assertIn("unsafe_zip_path", self.codes(audit.inspect_apk(self.apk)))
        self.assertFalse((self.root.parent / "outside").exists())

    def test_empty_wrong_machine_and_wrong_class_native_libraries_are_rejected(self):
        invalid = [b"", b"not ELF", self.elf("arm64-v8a"), self.elf("armeabi-v7a")]
        for content in invalid:
            with self.subTest(prefix=content[:7]):
                self.fixture(extra=[("lib/x86/libother.so", content)])
                report = audit.inspect_apk(self.apk)
                self.assertIn("invalid_native_elf", self.codes(report))
                self.assertFalse(report["checks"]["nativeElfHeadersValid"])

    def test_unknown_abi_and_no_native_library_are_rejected(self):
        self.fixture(extra=[("lib/unknown/libtracker.so", self.elf("x86"))])
        self.assertIn("unsupported_native_abi", self.codes(audit.inspect_apk(self.apk)))
        self.fixture(abis=[])
        self.assertIn("native_libraries_missing", self.codes(audit.inspect_apk(self.apk)))

    def test_missing_android_manifest_or_dex_is_rejected(self):
        for entry in ("AndroidManifest.xml", "classes.dex"):
            with self.subTest(entry=entry):
                self.fixture(omitted=[entry])
                self.assertIn("required_apk_entry_missing", self.codes(audit.inspect_apk(self.apk)))

    def test_invalid_budget_and_missing_input_are_not_successes(self):
        self.fixture()
        for budget in (0, -1, True):
            with self.subTest(budget=budget), self.assertRaises(ValueError):
                audit.inspect_apk(self.apk, max_bytes=budget)
        with self.assertRaises(ValueError):
            audit.inspect_apk(self.root / "missing.apk")

    def test_cli_writes_measured_json_and_returns_failure_for_bad_apk(self):
        self.fixture()
        output = self.root / "reports" / "audit.json"
        self.assertEqual(audit.main([str(self.apk), "--universal", "--output", str(output)]), 0)
        self.assertEqual(json.loads(output.read_text())["apkBytes"], self.apk.stat().st_size)
        self.apk.write_bytes(b"not zip")
        self.assertEqual(audit.main([str(self.apk), "--output", str(output)]), 1)
        self.assertEqual(json.loads(output.read_text())["executionStatus"], "failed")

    def test_cli_never_overwrites_input_apk_with_its_report(self):
        self.fixture()
        before = self.apk.read_bytes()
        with self.assertRaises(SystemExit):
            audit.main([str(self.apk), "--output", str(self.apk)])
        self.assertEqual(self.apk.read_bytes(), before)


if __name__ == "__main__":
    unittest.main()
