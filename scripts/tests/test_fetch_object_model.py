import dataclasses
import hashlib
import io
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch
import urllib.request

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import fetch_object_model as objects


class ObjectAssetTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.data = b'1234TFL3' + b'verified test payload'
        self.asset = objects.Asset('https://example.invalid/model', self.root / 'assets/model.tflite',
                                   len(self.data), hashlib.sha256(self.data).hexdigest(), True)
        self.source = self.root / 'source.tflite'
        self.source.write_bytes(self.data)

    def test_official_model_and_fixture_are_pinned(self):
        self.assertEqual(objects.MODEL.bytes, 5_434_517)
        self.assertEqual(objects.MODEL.sha256, 'bc2ffe19c1118de0c0c2a9088992da5589722656e0fba81421385300a4a34b16')
        self.assertEqual(objects.TEST_IMAGE.bytes, 92_220)
        self.assertTrue(objects.MODEL.url.startswith('https://storage.googleapis.com/mediapipe-models/'))

    def test_offline_copy_and_reuse_never_access_network(self):
        with patch.object(objects.urllib.request, 'build_opener', side_effect=AssertionError('network forbidden')):
            self.assertEqual(objects.prepare(self.asset, self.source), self.asset.sha256)
            self.assertEqual(objects.prepare(self.asset), self.asset.sha256)
        self.assertEqual(self.asset.output.read_bytes(), self.data)
        self.assertFalse(list(self.asset.output.parent.glob('.nexus-object-*')))

    def test_same_length_corruption_is_not_accepted(self):
        self.source.write_bytes(self.data[:-1] + b'x')
        with self.assertRaises(ValueError): objects.prepare(self.asset, self.source)
        self.assertFalse(self.asset.output.exists())

    def test_existing_corrupt_developer_file_is_preserved(self):
        self.asset.output.parent.mkdir()
        self.asset.output.write_bytes(b'developer data')
        with self.assertRaises(ValueError): objects.prepare(self.asset, self.source)
        self.assertEqual(self.asset.output.read_bytes(), b'developer data')

    def test_symbolic_source_and_destination_are_refused(self):
        link = self.root / 'source-link'; link.symlink_to(self.source)
        with self.assertRaises(ValueError): objects.prepare(self.asset, link)
        self.asset.output.parent.mkdir()
        self.asset.output.symlink_to(self.source)
        with self.assertRaises(ValueError): objects.prepare(self.asset)
        self.assertTrue(self.asset.output.is_symlink())

    def test_tflite_signature_is_checked_even_with_matching_sha(self):
        self.source.write_bytes(b'notflatbuffer')
        asset = dataclasses.replace(self.asset, bytes=self.source.stat().st_size,
                                    sha256=hashlib.sha256(self.source.read_bytes()).hexdigest())
        with self.assertRaisesRegex(ValueError, 'TensorFlow'): objects.prepare(asset, self.source)

    def test_redirect_cannot_downgrade_to_http(self):
        handler = objects.HttpsOnlyRedirect()
        with self.assertRaises(ValueError):
            handler.redirect_request(urllib.request.Request(self.asset.url), None, 302, 'found', {}, 'http://example.invalid/model')


if __name__ == '__main__': unittest.main()
