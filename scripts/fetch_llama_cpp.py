#!/usr/bin/env python3
"""Prepare the pinned upstream llama.cpp source used by the Android JNI build.

No model weights, executables or Python dependencies are downloaded. The archive
is verified before extraction and the source stays outside version control.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import shutil
import tarfile
import tempfile
import urllib.error
import urllib.request

COMMIT = "4231b23dc2c46661d01157aac5245bdcf03ce4b1"
TAG = "b11838"
ARCHIVE_URL = f"https://codeload.github.com/ggml-org/llama.cpp/tar.gz/{COMMIT}"
ARCHIVE_SHA256 = "b065f8ba1524df021d35078117b050d436c6a88f30c0ceede8614d9aac22713f"
MAX_ARCHIVE_BYTES = 256 * 1024 * 1024
MAX_EXTRACTED_BYTES = 1024 * 1024 * 1024
DEFAULT_OUTPUT = Path(__file__).resolve().parents[1] / "app/src/main/cpp/vendor/llama.cpp"
MARKER = ".nexus-source.json"
REQUIRED_FILES = ("CMakeLists.txt", "include/llama.h", "ggml/CMakeLists.txt", "LICENSE")


def verify_archive(path: Path) -> str:
    if not 1024 <= path.stat().st_size <= MAX_ARCHIVE_BYTES:
        raise ValueError("Tamanho inesperado do arquivo-fonte")
    checksum = hashlib.sha256()
    with path.open("rb") as handle:
        while chunk := handle.read(128 * 1024):
            checksum.update(chunk)
    digest = checksum.hexdigest()
    if digest != ARCHIVE_SHA256:
        raise ValueError("SHA-256 do código-fonte divergente; nenhum arquivo foi instalado")
    return digest


def check_source(path: Path) -> None:
    if not all((path / name).is_file() for name in REQUIRED_FILES):
        raise ValueError("Código-fonte incompleto: faltam arquivos necessários ao build")


def extract_archive(archive: Path, destination: Path) -> None:
    """Extract regular files only, rejecting links and paths outside the source."""
    prefix = f"llama.cpp-{COMMIT}"
    with tarfile.open(archive, "r:gz") as bundle:
        members = bundle.getmembers()
        if sum(member.size for member in members) > MAX_EXTRACTED_BYTES:
            raise ValueError("Código-fonte descompactado excede o limite")
        for member in members:
            name = PurePosixPath(member.name)
            if name.is_absolute() or ".." in name.parts or not name.parts or name.parts[0] != prefix:
                raise ValueError("Caminho inseguro no arquivo-fonte")
            relative = Path(*name.parts[1:])
            target = destination / relative
            if member.isdir():
                target.mkdir(parents=True, exist_ok=True)
            elif member.isfile():
                target.parent.mkdir(parents=True, exist_ok=True)
                source = bundle.extractfile(member)
                if source is None:
                    raise ValueError("Arquivo-fonte ilegível")
                with source, target.open("wb") as output:
                    shutil.copyfileobj(source, output)
                target.chmod(member.mode & 0o777)
            else:
                raise ValueError("Links ou entradas especiais não são aceitos no arquivo-fonte")
    check_source(destination)


def fetch(output: Path, local_archive: Path | None = None) -> None:
    if output.exists():
        marker = output / MARKER
        if not marker.is_file():
            raise ValueError(f"A pasta {output} já existe sem identificação; preserve suas alterações e use outra pasta")
        metadata = json.loads(marker.read_text(encoding="utf-8"))
        if metadata.get("commit") != COMMIT or metadata.get("archive_sha256") != ARCHIVE_SHA256:
            raise ValueError("A pasta existente corresponde a outra versão; remova-a explicitamente para atualizar")
        check_source(output)
        return
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix=".nexus-llama-", dir=output.parent) as work:
        temporary = Path(work)
        archive = local_archive
        if archive is None:
            archive = temporary / "source.tar.gz"
            request = urllib.request.Request(ARCHIVE_URL, headers={"User-Agent": "NEXUS-source-setup/2"})
            with urllib.request.urlopen(request, timeout=120) as response, archive.open("wb") as handle:
                total = 0
                while chunk := response.read(128 * 1024):
                    total += len(chunk)
                    if total > MAX_ARCHIVE_BYTES:
                        raise ValueError("Download do código-fonte excedeu o limite")
                    handle.write(chunk)
        verify_archive(archive)
        staged = temporary / "source"
        staged.mkdir()
        extract_archive(archive, staged)
        (staged / MARKER).write_text(
            json.dumps({"commit": COMMIT, "tag": TAG, "archive_sha256": ARCHIVE_SHA256, "url": ARCHIVE_URL}, indent=2) + "\n",
            encoding="utf-8",
        )
        os.replace(staged, output)


def main() -> int:
    parser = argparse.ArgumentParser(description="Prepara fonte oficial pinada do llama.cpp para compilar a IA local")
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--archive", type=Path, help="Arquivo .tar.gz já baixado; o mesmo SHA-256 será exigido")
    args = parser.parse_args()
    try:
        fetch(args.output, args.archive)
    except (OSError, ValueError, tarfile.TarError, urllib.error.URLError) as error:
        print(f"Runtime não preparado: {error}")
        return 1
    print(f"llama.cpp {TAG} ({COMMIT}) disponível em {args.output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
