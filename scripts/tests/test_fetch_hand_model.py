from pathlib import Path
import hashlib
import io
import sys
import tempfile
import unittest
from unittest.mock import patch
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import fetch_hand_model as model


class FakeResponse(io.BytesIO):
    def geturl(self): return model.MODEL_URL


class HandModelIntegrityTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.output = self.root / 'assets/hand.task'
        buffer = io.BytesIO()
        with zipfile.ZipFile(buffer, 'w') as archive:
            archive.writestr('hand_detector.tflite', b'fixture detector')
            archive.writestr('hand_landmarks_detector.tflite', b'fixture landmarks')
        self.payload = buffer.getvalue()
        self.digest = hashlib.sha256(self.payload).hexdigest()
        for name, value in [('MIN_BYTES', 1), ('MODEL_BYTES', len(self.payload)), ('MODEL_SHA256', self.digest)]:
            mocked = patch.object(model, name, value); mocked.start(); self.addCleanup(mocked.stop)

    def test_default_integrity_is_pinned_and_existing_valid_asset_is_reused(self):
        with patch.object(model, '_open_url', return_value=FakeResponse(self.payload)) as network:
            self.assertEqual(self.digest, model.fetch(self.output))
            self.assertEqual(self.digest, model.fetch(self.output))
        self.assertEqual(network.call_count, 1)
        self.assertEqual(self.output.read_bytes(), self.payload)
        self.assertEqual([], list(self.output.parent.glob('.hand_model_*')))

    def test_same_length_corruption_is_rejected_before_promotion(self):
        corrupted = self.payload[:-1] + bytes([self.payload[-1] ^ 1])
        with patch.object(model, '_open_url', return_value=FakeResponse(corrupted)):
            with self.assertRaisesRegex(ValueError, 'SHA-256'): model.fetch(self.output)
        self.assertFalse(self.output.exists())
        self.assertEqual([], list(self.output.parent.glob('.hand_model_*')))

    def test_wrong_size_existing_file_is_preserved_without_network(self):
        self.output.parent.mkdir(); self.output.write_bytes(b'developer file')
        with patch.object(model, '_open_url', side_effect=AssertionError('network forbidden')):
            with self.assertRaisesRegex(ValueError, 'tamanho'): model.fetch(self.output)
        self.assertEqual(self.output.read_bytes(), b'developer file')

    def test_destination_and_parent_symlinks_are_rejected(self):
        self.output.parent.mkdir(); actual = self.root / 'actual'; actual.write_bytes(self.payload)
        self.output.symlink_to(actual)
        with self.assertRaisesRegex(ValueError, 'links'): model.fetch(self.output)
        self.output.unlink()
        linked = self.root / 'linked'; linked.symlink_to(self.output.parent, target_is_directory=True)
        with self.assertRaisesRegex(ValueError, 'links'): model.fetch(linked / 'other.task')
        self.assertEqual(actual.read_bytes(), self.payload)

    def test_https_downgrade_is_rejected(self):
        import urllib.request
        handler = model.HttpsOnlyRedirectHandler()
        with self.assertRaisesRegex(ValueError, 'HTTPS'):
            handler.redirect_request(urllib.request.Request(model.MODEL_URL), None, 302, 'found', {}, 'http://example.invalid/model')
        response = FakeResponse(self.payload); response.geturl = lambda: 'http://example.invalid/model'
        with patch.object(model, '_open_url', return_value=response):
            with self.assertRaisesRegex(ValueError, 'HTTPS'): model.fetch(self.output)
        self.assertFalse(self.output.exists())

    def test_valid_hash_does_not_replace_expected_zip_structure(self):
        buffer = io.BytesIO()
        with zipfile.ZipFile(buffer, 'w') as archive: archive.writestr('unrelated', b'data')
        payload = buffer.getvalue()
        self.output.parent.mkdir(); self.output.write_bytes(payload)
        with self.assertRaisesRegex(ValueError, 'modelos esperados'):
            model.validate_model(self.output, hashlib.sha256(payload).hexdigest(), len(payload))

    def test_competing_file_is_never_overwritten(self):
        original_link = model.os.link
        def competing(source, destination):
            Path(destination).write_bytes(b'preserve concurrent data')
            return original_link(source, destination)
        with patch.object(model, '_open_url', return_value=FakeResponse(self.payload)), patch.object(model.os, 'link', side_effect=competing):
            with self.assertRaises(ValueError): model.fetch(self.output)
        self.assertEqual(self.output.read_bytes(), b'preserve concurrent data')
        self.assertEqual([], list(self.output.parent.glob('.hand_model_*')))

    def test_explicit_fixture_integrity_does_not_disable_validation(self):
        self.output.parent.mkdir(); self.output.write_bytes(self.payload)
        self.assertEqual(self.digest, model.validate_model(self.output, self.digest, len(self.payload)))
        with self.assertRaisesRegex(ValueError, 'SHA-256'):
            model.validate_model(self.output, '0' * 64, len(self.payload))


class OfficialHandModelCatalogTest(unittest.TestCase):
    def test_official_version_has_fixed_size_and_sha256(self):
        self.assertEqual(model.MODEL_BYTES, 7_819_105)
        self.assertEqual(model.MODEL_SHA256, 'fbc2a30080c3c557093b5ddfc334698132eb341044ccee322ccf8bcf3607cde1')
        self.assertTrue(model.MODEL_URL.startswith('https://storage.googleapis.com/mediapipe-models/'))


if __name__ == '__main__': unittest.main()
