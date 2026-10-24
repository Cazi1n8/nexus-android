from __future__ import annotations

import hashlib
import importlib.util
import io
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

SCRIPT = Path(__file__).resolve().parents[1] / "fetch_ocr_models.py"
loader = importlib.util.spec_from_file_location("fetch_ocr_models", SCRIPT)
module = importlib.util.module_from_spec(loader)
sys.modules[loader.name] = module
loader.loader.exec_module(module)


class FakeResponse(io.BytesIO):
    def geturl(self):
        return "https://raw.githubusercontent.com/official/model.traineddata"


class OcrModelPreparationTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.payload = b"synthetic-build-fixture-only"
        self.spec = module.ModelSpec("por", len(self.payload), hashlib.sha256(self.payload).hexdigest())
        self.output = self.root / "assets"

    def assert_no_partial(self):
        self.assertFalse(list(self.output.glob(".ocr-model-*")))

    def test_catalog_pins_both_official_languages_and_immutable_revision(self):
        self.assertRegex(module.REVISION, r"^[0-9a-f]{40}$")
        self.assertEqual([(spec.language, spec.bytes) for spec in module.MODELS],
                         [("por", 1_982_756), ("eng", 4_113_088)])
        self.assertEqual([spec.sha256 for spec in module.MODELS], [
            "c4932b937207a9514b7514d518b931a99938c02a28a5a5a553f8599ed58b7deb",
            "7d4322bd2a7749724879683fc3912cb542f19906c83bcc1a52132556427170b2",
        ])
        for spec in module.MODELS:
            self.assertEqual(spec.url, "https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/"
                             + module.REVISION + "/" + spec.file_name)

    def test_verified_download_installs_and_reuses_without_network(self):
        with patch.object(module.urllib.request, "urlopen", return_value=FakeResponse(self.payload)) as network:
            module.prepare_model(self.output, self.spec)
            module.prepare_model(self.output, self.spec)
        self.assertEqual((self.output / self.spec.file_name).read_bytes(), self.payload)
        self.assertEqual(network.call_count, 1)
        self.assert_no_partial()

    def test_same_size_corruption_is_rejected_without_installing(self):
        bad = b"x" * len(self.payload)
        with patch.object(module.urllib.request, "urlopen", return_value=FakeResponse(bad)):
            with self.assertRaisesRegex(ValueError, "SHA-256"):
                module.prepare_model(self.output, self.spec)
        self.assertFalse((self.output / self.spec.file_name).exists())
        self.assert_no_partial()

    def test_truncated_or_oversized_download_is_rejected(self):
        for body in [self.payload[:-1], self.payload + b"x"]:
            with self.subTest(bytes=len(body)):
                with patch.object(module.urllib.request, "urlopen", return_value=FakeResponse(body)):
                    with self.assertRaises(ValueError):
                        module.prepare_model(self.output, self.spec)
                self.assertFalse((self.output / self.spec.file_name).exists())
                self.assert_no_partial()

    def test_invalid_existing_asset_is_preserved_without_network(self):
        self.output.mkdir()
        original = self.output / self.spec.file_name
        original.write_bytes(b"developer asset")
        with patch.object(module.urllib.request, "urlopen") as network:
            with self.assertRaises(ValueError):
                module.prepare_model(self.output, self.spec)
        self.assertEqual(original.read_bytes(), b"developer asset")
        network.assert_not_called()

    def test_interrupted_download_cleans_temporary_asset(self):
        class InterruptedResponse(FakeResponse):
            def read(self, count=-1):
                raise KeyboardInterrupt()
        with patch.object(module.urllib.request, "urlopen", return_value=InterruptedResponse(self.payload)):
            with self.assertRaises(KeyboardInterrupt):
                module.prepare_model(self.output, self.spec)
        self.assertFalse((self.output / self.spec.file_name).exists())
        self.assert_no_partial()

    def test_offline_source_uses_same_integrity_requirement(self):
        source = self.root / "source"
        source.mkdir()
        (source / self.spec.file_name).write_bytes(self.payload)
        with patch.object(module.urllib.request, "urlopen") as network:
            module.prepare_model(self.output, self.spec, source)
        self.assertEqual((self.output / self.spec.file_name).read_bytes(), self.payload)
        network.assert_not_called()

    def test_symlink_is_rejected_for_existing_asset_and_offline_source(self):
        source = self.root / "actual-model"
        source.write_bytes(self.payload)
        self.output.mkdir()
        asset = self.output / self.spec.file_name
        asset.symlink_to(source)
        with patch.object(module.urllib.request, "urlopen") as network:
            with self.assertRaisesRegex(ValueError, "regular"):
                module.prepare_model(self.output, self.spec)
            asset.unlink()
            source_dir = self.root / "source-dir"
            source_dir.mkdir()
            (source_dir / self.spec.file_name).symlink_to(source)
            with self.assertRaisesRegex(ValueError, "regular"):
                module.prepare_model(self.output, self.spec, source_dir)
        network.assert_not_called()
        self.assertFalse(asset.exists())
        self.assert_no_partial()

    def test_invalid_offline_source_is_rejected_and_preserved(self):
        source = self.root / "source"
        source.mkdir()
        source_file = source / self.spec.file_name
        source_file.write_bytes(b"invalid source")
        with patch.object(module.urllib.request, "urlopen") as network:
            with self.assertRaisesRegex(ValueError, "Tamanho"):
                module.prepare_model(self.output, self.spec, source)
        self.assertEqual(source_file.read_bytes(), b"invalid source")
        self.assertFalse((self.output / self.spec.file_name).exists())
        self.assert_no_partial()
        network.assert_not_called()

    def test_insecure_redirect_is_not_installed(self):
        class InsecureResponse(FakeResponse):
            def geturl(self):
                return "http://example.invalid/model"
        with patch.object(module.urllib.request, "urlopen", return_value=InsecureResponse(self.payload)):
            with self.assertRaisesRegex(ValueError, "HTTPS"):
                module.prepare_model(self.output, self.spec)
        self.assertFalse((self.output / self.spec.file_name).exists())
        self.assert_no_partial()

    def test_concurrent_unknown_asset_is_not_overwritten(self):
        real_link = module.os.link
        def competing_install(source, target):
            Path(target).write_bytes(b"other build result")
            return real_link(source, target)
        with patch.object(module.urllib.request, "urlopen", return_value=FakeResponse(self.payload)):
            with patch.object(module.os, "link", side_effect=competing_install):
                with self.assertRaises(ValueError):
                    module.prepare_model(self.output, self.spec)
        self.assertEqual((self.output / self.spec.file_name).read_bytes(), b"other build result")
        self.assert_no_partial()


if __name__ == "__main__":
    unittest.main()
