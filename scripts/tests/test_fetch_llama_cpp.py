"""Source preparation must reject tampering and unsafe extraction paths."""
import hashlib
import importlib.util
import io
import json
import random
from pathlib import Path
import tarfile
import tempfile
import unittest
from unittest.mock import patch

SCRIPT = Path(__file__).resolve().parents[1] / "fetch_llama_cpp.py"
SPEC = importlib.util.spec_from_file_location("fetch_llama_cpp", SCRIPT)
source_setup = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(source_setup)


class SourcePreparationTest(unittest.TestCase):
    def archive(self, directory, extra=None):
        path = directory / "source.tar.gz"
        prefix = f"llama.cpp-{source_setup.COMMIT}"
        with tarfile.open(path, "w:gz") as bundle:
            for name in source_setup.REQUIRED_FILES:
                content = random.Random(name).randbytes(2048)
                member = tarfile.TarInfo(f"{prefix}/{name}")
                member.size = len(content)
                bundle.addfile(member, io.BytesIO(content))
            if extra:
                bundle.addfile(extra, io.BytesIO(b"unsafe") if extra.isfile() else None)
        return path

    def test_rejects_changed_checksum_before_creating_source(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            archive = self.archive(directory)
            with patch.object(source_setup, "ARCHIVE_SHA256", "0" * 64):
                with self.assertRaisesRegex(ValueError, "SHA-256"):
                    source_setup.fetch(directory / "installed", archive)
            self.assertFalse((directory / "installed").exists())

    def test_rejects_parent_path(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            member = tarfile.TarInfo(f"llama.cpp-{source_setup.COMMIT}/../../outside")
            member.size = 6
            archive = self.archive(directory, member)
            with self.assertRaisesRegex(ValueError, "Caminho inseguro"):
                source_setup.extract_archive(archive, directory / "staged")
            self.assertFalse((directory / "outside").exists())

    def test_rejects_symlink(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            member = tarfile.TarInfo(f"llama.cpp-{source_setup.COMMIT}/escape")
            member.type = tarfile.SYMTYPE
            member.linkname = "/tmp"
            archive = self.archive(directory, member)
            with self.assertRaisesRegex(ValueError, "Links"):
                source_setup.extract_archive(archive, directory / "staged")

    def test_installs_matching_archive_once_without_replacing_source_changes(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            archive = self.archive(directory)
            checksum = hashlib.sha256(archive.read_bytes()).hexdigest()
            with patch.object(source_setup, "ARCHIVE_SHA256", checksum):
                output = directory / "installed"
                source_setup.fetch(output, archive)
                self.assertEqual(json.loads((output / source_setup.MARKER).read_text())["commit"], source_setup.COMMIT)
                (output / "LICENSE").write_text("local edit")
                source_setup.fetch(output, archive)
                self.assertEqual((output / "LICENSE").read_text(), "local edit")

    def test_existing_unknown_source_is_preserved(self):
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary)
            (output / "my-file").write_text("keep")
            with self.assertRaisesRegex(ValueError, "já existe"):
                source_setup.fetch(output)
            self.assertEqual((output / "my-file").read_text(), "keep")


if __name__ == "__main__":
    unittest.main()
