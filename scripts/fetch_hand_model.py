#!/usr/bin/env python3
"""Prepare the exact official MediaPipe Hand Landmarker v1 with pinned integrity.

The default build checks both byte length and SHA-256. The URL's version alone
is not treated as proof of integrity. No code or weights are fetched by Gradle
or by the camera service at runtime.
"""
from __future__ import annotations

import argparse
import hashlib
import os
from pathlib import Path
import tempfile
import urllib.error
import urllib.parse
import urllib.request
import zipfile

MODEL_URL = (
    "https://storage.googleapis.com/mediapipe-models/hand_landmarker/"
    "hand_landmarker/float16/1/hand_landmarker.task"
)
MODEL_BYTES = 7_819_105
MODEL_SHA256 = "fbc2a30080c3c557093b5ddfc334698132eb341044ccee322ccf8bcf3607cde1"
MAX_BYTES = 16 * 1024 * 1024
MIN_BYTES = 1 * 1024 * 1024
DEFAULT_OUTPUT = Path(__file__).resolve().parents[1] / "app/src/main/assets/hand_landmarker.task"


class HttpsOnlyRedirectHandler(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, fp, code, message, headers, new_url):
        if urllib.parse.urlparse(new_url).scheme != "https":
            raise ValueError("O modelo de mãos exige HTTPS em todos os redirecionamentos.")
        return super().redirect_request(request, fp, code, message, headers, new_url)


def _open_url(request):
    return urllib.request.build_opener(HttpsOnlyRedirectHandler()).open(request, timeout=90)


def _check_path(path: Path) -> None:
    if path.is_symlink() or any(parent.is_symlink() for parent in path.parents):
        raise ValueError("O modelo de mãos deve usar arquivo e diretórios regulares, sem links simbólicos.")


def validate_model(path: Path, expected_sha256: str | None = None, expected_bytes: int | None = None) -> str:
    _check_path(path)
    expected_hash = MODEL_SHA256 if expected_sha256 is None else expected_sha256
    expected_size = MODEL_BYTES if expected_bytes is None else expected_bytes
    if not path.is_file():
        raise ValueError("O modelo de mãos não é um arquivo regular.")
    size = path.stat().st_size
    if size != expected_size or not MIN_BYTES <= size <= MAX_BYTES:
        raise ValueError("O tamanho do modelo de mãos não corresponde à versão fixada.")
    digest = hashlib.sha256(path.read_bytes()).hexdigest()
    if digest.lower() != expected_hash.lower():
        raise ValueError("O SHA-256 do modelo de mãos não corresponde à versão fixada.")
    with zipfile.ZipFile(path) as bundle:
        names = {Path(name).name for name in bundle.namelist()}
        if not {"hand_detector.tflite", "hand_landmarks_detector.tflite"}.issubset(names):
            raise ValueError("O arquivo não contém os modelos esperados do MediaPipe.")
        if sum(item.file_size for item in bundle.infolist()) > 64 * 1024 * 1024:
            raise ValueError("O conteúdo descompactado excede o limite de segurança.")
        if bundle.testzip() is not None:
            raise ValueError("O pacote do modelo está corrompido.")
    return digest


def fetch(output: Path, expected_sha256: str | None = None, expected_bytes: int | None = None) -> str:
    _check_path(output)
    if output.exists():
        return validate_model(output, expected_sha256, expected_bytes)
    output.parent.mkdir(parents=True, exist_ok=True)
    descriptor, name = tempfile.mkstemp(dir=output.parent, prefix=".hand_model_")
    temporary = Path(name)
    try:
        with os.fdopen(descriptor, "wb") as handle:
            request = urllib.request.Request(MODEL_URL, headers={"User-Agent": "NEXUS-model-setup/2"})
            with _open_url(request) as response:
                if urllib.parse.urlparse(response.geturl()).scheme != "https":
                    raise ValueError("O download final do modelo de mãos exige HTTPS.")
                total = 0
                while chunk := response.read(64 * 1024):
                    total += len(chunk)
                    if total > MAX_BYTES:
                        raise ValueError("Download excedeu o limite de segurança.")
                    handle.write(chunk)
        digest = validate_model(temporary, expected_sha256, expected_bytes)
        try:
            # Exclusive promotion never overwrites a file created by another process.
            os.link(temporary, output)
        except FileExistsError:
            return validate_model(output, expected_sha256, expected_bytes)
        return digest
    finally:
        temporary.unlink(missing_ok=True)


def main() -> int:
    parser = argparse.ArgumentParser(description="Prepara o modelo oficial de mãos v1 com tamanho e SHA-256 fixados.")
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--sha256", default=MODEL_SHA256,
                        help="Hash esperado; por padrão usa a revisão oficial fixada no código.")
    args = parser.parse_args()
    try:
        digest = fetch(args.output, args.sha256)
    except (OSError, ValueError, zipfile.BadZipFile, urllib.error.URLError) as error:
        print(f"Modelo não preparado: {error}")
        print("As funções de mãos ficam indisponíveis até preparar o arquivo íntegro.")
        return 1
    print(f"Modelo oficial verificado: {args.output}")
    print(f"Bytes: {MODEL_BYTES}; SHA-256: {digest}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
