#!/usr/bin/env python3
"""Prepare fixed Tesseract language assets before building the Android APK.

Build-side preparation for the optional bundled variant. CORE obtains these same
pinned data through its explicitly authorized optional downloads and reuses valid
private copies. Only the full bundled variant copies these assets from the APK.
"""
from __future__ import annotations

import argparse
from dataclasses import dataclass
import hashlib
import os
from pathlib import Path
import shutil
import tempfile
import urllib.error
import urllib.parse
import urllib.request

# Immutable official tessdata_fast source; never follow a moving main branch.
REVISION = "87416418657359cb625c412a48b6e1d6d41c29bd"


@dataclass(frozen=True)
class ModelSpec:
    language: str
    bytes: int
    sha256: str

    @property
    def file_name(self) -> str:
        return f"{self.language}.traineddata"

    @property
    def url(self) -> str:
        return f"https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/{REVISION}/{self.file_name}"


MODELS: tuple[ModelSpec, ...] = (
    ModelSpec("por", 1_982_756, "c4932b937207a9514b7514d518b931a99938c02a28a5a5a553f8599ed58b7deb"),
    ModelSpec("eng", 4_113_088, "7d4322bd2a7749724879683fc3912cb542f19906c83bcc1a52132556427170b2"),
)
DEFAULT_OUTPUT = Path(__file__).resolve().parents[1] / "app/src/main/assets/ocr"
MAX_MODEL_BYTES = 32 * 1024 * 1024


def validate(path: Path, spec: ModelSpec) -> str:
    if path.is_symlink() or not path.is_file():
        raise ValueError(f"Modelo {spec.language} não é um arquivo regular")
    if not 1 <= spec.bytes <= MAX_MODEL_BYTES or path.stat().st_size != spec.bytes:
        raise ValueError(f"Tamanho inesperado do modelo {spec.language}")
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        while chunk := handle.read(128 * 1024):
            digest.update(chunk)
    checksum = digest.hexdigest()
    if checksum != spec.sha256:
        raise ValueError(f"SHA-256 divergente do modelo {spec.language}; nenhum modelo foi substituído")
    return checksum


def prepare_model(output_directory: Path, spec: ModelSpec, source_directory: Path | None = None) -> str:
    """Install only a verified complete file, without replacing an existing path."""
    output = output_directory / spec.file_name
    if output.exists() or output.is_symlink():
        # Never destroy a developer's file or silently replace an unknown asset.
        return validate(output, spec)
    output_directory.mkdir(parents=True, exist_ok=True)
    descriptor, temporary_name = tempfile.mkstemp(prefix=".ocr-model-", dir=output_directory)
    temporary = Path(temporary_name)
    try:
        with os.fdopen(descriptor, "wb") as target:
            if source_directory is not None:
                source = source_directory / spec.file_name
                validate(source, spec)
                with source.open("rb") as handle:
                    shutil.copyfileobj(handle, target)
            else:
                request = urllib.request.Request(spec.url, headers={"User-Agent": "NEXUS-OCR-build/1"})
                with urllib.request.urlopen(request, timeout=120) as response:
                    if urllib.parse.urlparse(response.geturl()).scheme != "https":
                        raise ValueError("O download do modelo OCR exige HTTPS")
                    total = 0
                    while chunk := response.read(128 * 1024):
                        total += len(chunk)
                        if total > spec.bytes:
                            raise ValueError(f"Download de {spec.language} excede o tamanho fixado")
                        target.write(chunk)
            target.flush()
            os.fsync(target.fileno())
        checksum = validate(temporary, spec)
        try:
            # Atomic creation without overwrite. Temp and destination share a volume.
            os.link(temporary, output)
        except FileExistsError:
            # A concurrent setup may have installed it; accept only the exact model.
            return validate(output, spec)
        return checksum
    finally:
        temporary.unlink(missing_ok=True)


def prepare(output: Path = DEFAULT_OUTPUT, source_directory: Path | None = None) -> None:
    if len(REVISION) != 40 or not MODELS:
        raise ValueError("O catálogo OCR não possui uma revisão e modelos fixados")
    for spec in MODELS:
        checksum = prepare_model(output, spec, source_directory)
        print(f"OCR {spec.language}: {spec.bytes} bytes · SHA-256 {checksum}")


def main() -> int:
    parser = argparse.ArgumentParser(description="Prepara modelos OCR oficiais e verificados antes de compilar")
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--source-dir", type=Path, help="Diretório local com modelos já baixados; exige os mesmos hashes")
    args = parser.parse_args()
    try:
        prepare(args.output, args.source_dir)
    except (OSError, ValueError, urllib.error.URLError) as error:
        print(f"Modelos OCR não preparados: {error}")
        return 1
    print(f"Modelos oficiais disponíveis para o APK em {args.output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
