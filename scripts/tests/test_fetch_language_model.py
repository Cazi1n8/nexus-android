"""Pinned CI fixtures must reject altered bytes without replacing local files."""
import hashlib
import importlib.util
import io
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

SCRIPT = Path(__file__).resolve().parents[1] / "fetch_language_model.py"
SPEC = importlib.util.spec_from_file_location("fetch_language_model", SCRIPT)
fixture = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(fixture)
CONTENT = b"GGUF" + bytes(range(256)) * 4


class Response(io.BytesIO):
    def geturl(self):
        return "https://cdn.example.invalid/model.gguf"


class LanguageFixtureTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.output = self.root / "output" / "model.gguf"
        for name, value in (("MODEL_BYTES", len(CONTENT)),
                            ("MODEL_SHA256", hashlib.sha256(CONTENT).hexdigest())):
            change = patch.object(fixture, name, value)
            change.start()
            self.addCleanup(change.stop)

    def test_local_fixture_is_verified_and_atomically_installed(self):
        source = self.root / "source.gguf"
        source.write_bytes(CONTENT)
        with patch.object(fixture.urllib.request, "urlopen", side_effect=AssertionError("No network")):
            digest = fixture.fetch(self.output, source)
        self.assertEqual(digest, hashlib.sha256(CONTENT).hexdigest())
        self.assertEqual(self.output.read_bytes(), CONTENT)
        self.assertEqual(list(self.output.parent.glob(".nexus-llm-fixture-*")), [])

    def test_existing_unknown_file_is_never_replaced(self):
        self.output.parent.mkdir()
        self.output.write_bytes(b"my existing file")
        with self.assertRaisesRegex(ValueError, "tamanho"):
            fixture.fetch(self.output)
        self.assertEqual(self.output.read_bytes(), b"my existing file")

    def test_altered_download_fails_checksum_and_removes_staging(self):
        changed = CONTENT[:-1] + b"x"
        with patch.object(fixture.urllib.request, "urlopen", return_value=Response(changed)):
            with self.assertRaisesRegex(ValueError, "SHA-256"):
                fixture.fetch(self.output)
        self.assertFalse(self.output.exists())
        self.assertEqual(list(self.output.parent.iterdir()), [])

    def test_oversized_stream_stops_before_install(self):
        with patch.object(fixture.urllib.request, "urlopen", return_value=Response(CONTENT + b"extra")):
            with self.assertRaisesRegex(ValueError, "excedeu"):
                fixture.fetch(self.output)
        self.assertFalse(self.output.exists())
        self.assertEqual(list(self.output.parent.iterdir()), [])

    def test_truncated_stream_is_rejected(self):
        with patch.object(fixture.urllib.request, "urlopen", return_value=Response(CONTENT[:-1])):
            with self.assertRaisesRegex(ValueError, "tamanho"):
                fixture.fetch(self.output)
        self.assertFalse(self.output.exists())

    def test_non_gguf_is_rejected_even_with_matching_hash(self):
        source = self.root / "source.gguf"
        changed = b"HTML" + CONTENT[4:]
        source.write_bytes(changed)
        with patch.object(fixture, "MODEL_SHA256", hashlib.sha256(changed).hexdigest()):
            with self.assertRaisesRegex(ValueError, "cabeçalho GGUF"):
                fixture.fetch(self.output, source)
        self.assertFalse(self.output.exists())

    def test_existing_verified_fixture_needs_no_network(self):
        self.output.parent.mkdir()
        self.output.write_bytes(CONTENT)
        with patch.object(fixture.urllib.request, "urlopen", side_effect=AssertionError("No network")):
            fixture.fetch(self.output)


if __name__ == "__main__":
    unittest.main()
