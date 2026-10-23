#!/usr/bin/env python3
"""Download the versioned official MediaPipe hand model, without third-party packages.

Run explicitly before Gradle when the camera/gesture modules are wanted. A missing
model is handled visibly by the Android app; this script does not create a fake one.
"""

from __future__ import annotations

import argparse
import hashlib
import os
from pathlib import Path
import tempfile
import urllib.error
import urllib.request
import zipfile

MODEL_URL = (
    "https://storage.googleapis.com/mediapipe-models/hand_landmarker/"
    "hand_landmarker/float16/1/hand_landmarker.task"
)
MAX_BYTES = 16 * 1024 * 1024
MIN_BYTES = 1 * 1024 * 1024
DEFAULT_OUTPUT = Path(__file__).resolve().parents[1] / "app/src/main/assets/hand_landmarker.task"


def validate_model(path: Path, expected_sha256: str | None = None) -> str:
    size = path.stat().st_size
    if not MIN_BYTES <= size <= MAX_BYTES:
        raise ValueError(f"Tamanho inesperado do modelo: {size} bytes")
    with zipfile.ZipFile(path) as bundle:
        names = {Path(name).name for name in bundle.namelist()}
        if not {"hand_detector.tflite", "hand_landmarks_detector.tflite"}.issubset(names):
            raise ValueError("O arquivo não contém os modelos esperados do MediaPipe")
        if sum(item.file_size for item in bundle.infolist()) > 64 * 1024 * 1024:
            raise ValueError("O conteúdo descompactado excede o limite de segurança")
        if bundle.testzip() is not None:
            raise ValueError("O pacote do modelo está corrompido")
    digest = hashlib.sha256(path.read_bytes()).hexdigest()
    if expected_sha256 is not None and digest.lower() != expected_sha256.lower():
        raise ValueError("O SHA-256 não corresponde ao valor fornecido")
    return digest


def fetch(output: Path, expected_sha256: str | None = None) -> str:
    if output.exists():
        return validate_model(output, expected_sha256)
    output.parent.mkdir(parents=True, exist_ok=True)
    descriptor, name = tempfile.mkstemp(dir=output.parent, prefix=".hand_model_")
    temporary = Path(name)
    try:
        with os.fdopen(descriptor, "wb") as handle:
            request = urllib.request.Request(MODEL_URL, headers={"User-Agent": "NEXUS-model-setup/1"})
            with urllib.request.urlopen(request, timeout=90) as response:
                total = 0
                while chunk := response.read(64 * 1024):
                    total += len(chunk)
                    if total > MAX_BYTES:
                        raise ValueError("Download excedeu o limite de segurança")
                    handle.write(chunk)
        digest = validate_model(temporary, expected_sha256)
        os.replace(temporary, output)
        return digest
    finally:
        temporary.unlink(missing_ok=True)


def main() -> int:
    parser = argparse.ArgumentParser(description="Baixa o modelo oficial de mãos do MediaPipe (versão 1)")
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--sha256", help="SHA-256 esperado, obtido por um canal confiável; opcional")
    args = parser.parse_args()
    try:
        digest = fetch(args.output, args.sha256)
    except (OSError, ValueError, zipfile.BadZipFile, urllib.error.URLError) as error:
        print(f"Modelo não preparado: {error}")
        print("O build pode ser feito sem o modelo, mas as funções de mãos exibirão indisponibilidade.")
        return 1
    print(f"Modelo oficial disponível: {args.output}")
    print(f"SHA-256: {digest}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
