import base64
import os
from pathlib import Path
import stat
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import prepare_release_keystore as signing


class ReleaseKeyPreparationTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.target = self.root / "release.jks"
        self.payload = b"synthetic signing fixture, not a real private key"
        self.env = {name: "test-only-value" for name in signing.SECRET_NAMES}
        self.env.update(NEXUS_RELEASE_KEYSTORE_BASE64=base64.b64encode(self.payload).decode(),
                        NEXUS_RELEASE_STORE_FILE=str(self.target))

    def test_private_exclusive_copy_accepts_wrapped_base64(self):
        self.env['NEXUS_RELEASE_KEYSTORE_BASE64'] += '\n'
        with patch('builtins.print') as output:
            self.assertEqual(signing.prepare(self.env), self.target)
        output.assert_not_called()
        self.assertEqual(self.target.read_bytes(), self.payload)
        self.assertEqual(stat.S_IMODE(self.target.stat().st_mode), 0o600)

    def test_every_missing_value_stops_before_creating_any_file(self):
        for name in (*signing.SECRET_NAMES, 'NEXUS_RELEASE_STORE_FILE'):
            with self.subTest(name=name):
                env = dict(self.env); env.pop(name)
                with self.assertRaises(ValueError): signing.prepare(env)
                self.assertFalse(self.target.exists())

    def test_invalid_empty_or_oversized_encoding_is_rejected(self):
        for value in ('%%%invalid%%%', '  \n', 'A' * 2_000_001):
            with self.subTest(value_length=len(value)):
                env = dict(self.env, NEXUS_RELEASE_KEYSTORE_BASE64=value)
                with self.assertRaises(ValueError): signing.prepare(env)
                self.assertFalse(self.target.exists())

    def test_existing_file_is_preserved(self):
        self.target.write_bytes(b'existing developer key')
        with self.assertRaises(FileExistsError): signing.prepare(self.env)
        self.assertEqual(self.target.read_bytes(), b'existing developer key')

    def test_symbolic_link_is_not_followed_or_deleted(self):
        real = self.root / 'actual-key'; real.write_bytes(b'preserve')
        self.target.symlink_to(real)
        with self.assertRaises(FileExistsError): signing.prepare(self.env)
        self.assertTrue(self.target.is_symlink())
        self.assertEqual(real.read_bytes(), b'preserve')

    def test_relative_and_missing_parent_paths_are_rejected(self):
        for target in ('release.jks', str(self.root / 'absent' / 'release.jks')):
            with self.assertRaises(ValueError):
                signing.prepare(dict(self.env, NEXUS_RELEASE_STORE_FILE=target))


if __name__ == '__main__': unittest.main()
