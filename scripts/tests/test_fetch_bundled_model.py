import hashlib
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import fetch_bundled_model as bundled
import fetch_language_model as model


class BundledModelPreparationTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)
        self.payload = b"GGUF" + bytes(range(256)) * 4
        self.digest = hashlib.sha256(self.payload).hexdigest()
        self.size_patch = patch.object(model, "MODEL_BYTES", len(self.payload))
        self.hash_patch = patch.object(model, "MODEL_SHA256", self.digest)
        self.size_patch.start()
        self.hash_patch.start()
        self.addCleanup(self.hash_patch.stop)
        self.addCleanup(self.size_patch.stop)
        self.addCleanup(self.temporary.cleanup)
        self.output = self.root / "assets/models" / model.MODEL_NAME

    def test_default_output_is_the_compact_model_android_asset(self):
        repository = Path(__file__).resolve().parents[2]
        self.assertEqual(repository / ".build-deps/bundled-assets/models" / model.MODEL_NAME,
                         bundled.DEFAULT_OUTPUT)

    def test_offline_copy_uses_common_integrity_checks_and_never_opens_network(self):
        source = self.root / "downloaded.gguf"
        source.write_bytes(self.payload)
        with patch.object(model.urllib.request, "urlopen", side_effect=AssertionError("network forbidden")):
            self.assertEqual(self.digest, bundled.prepare(self.output, source))
        self.assertEqual(self.payload, self.output.read_bytes())
        self.assertEqual([], list(self.output.parent.glob(".nexus-llm-fixture-*")))

    def test_existing_valid_asset_is_reused_without_redownloading(self):
        self.output.parent.mkdir(parents=True)
        self.output.write_bytes(self.payload)
        with patch.object(model.urllib.request, "urlopen", side_effect=AssertionError("network forbidden")):
            self.assertEqual(self.digest, bundled.prepare(self.output))

    def test_same_size_corruption_cannot_be_packaged_or_replace_existing_data(self):
        source = self.root / "corrupted.gguf"
        source.write_bytes(self.payload[:-1] + b"X")
        with self.assertRaises(ValueError):
            bundled.prepare(self.output, source)
        self.assertFalse(self.output.exists())
        self.output.parent.mkdir(parents=True)
        self.output.write_bytes(source.read_bytes())
        original = self.output.read_bytes()
        with self.assertRaises(ValueError):
            bundled.prepare(self.output)
        self.assertEqual(original, self.output.read_bytes())

    def test_asset_and_source_symlinks_are_rejected_even_if_target_has_valid_hash(self):
        valid = self.root / "valid.gguf"
        valid.write_bytes(self.payload)
        self.output.parent.mkdir(parents=True)
        self.output.symlink_to(valid)
        with self.assertRaises(ValueError):
            bundled.prepare(self.output)
        self.output.unlink()
        source_link = self.root / "source-link.gguf"
        source_link.symlink_to(valid)
        with self.assertRaises(ValueError):
            bundled.prepare(self.output, source_link)
        self.assertFalse(self.output.exists())

    def test_a_symlinked_asset_directory_is_rejected_before_any_download(self):
        destination = self.root / "actual-assets"
        destination.mkdir()
        link = self.root / "linked-assets"
        link.symlink_to(destination, target_is_directory=True)
        with patch.object(model.urllib.request, "urlopen", side_effect=AssertionError("network forbidden")):
            with self.assertRaises(ValueError):
                bundled.prepare(link / model.MODEL_NAME)
        self.assertEqual([], list(destination.iterdir()))


if __name__ == "__main__":
    unittest.main()
