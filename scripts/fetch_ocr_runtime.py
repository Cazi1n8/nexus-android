#!/usr/bin/env python3
"""Fetch the exact upstream OCR AAR before Gradle, independent of Maven metadata.

Only a complete size/SHA-256-verified artifact can become a build dependency.
No code or library is downloaded while the Android application is running.
"""
from __future__ import annotations

import argparse
import hashlib
import os
from pathlib import Path
import shutil
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request

VERSION = "4.9.0"
URL = "https://jitpack.io/cz/adaptech/tesseract4android/tesseract4android/4.9.0/tesseract4android-4.9.0.aar"
EXPECTED_BYTES = 12_848_454
EXPECTED_SHA256 = "bce5d6413a1a5ae3d7240033fbbc851ba3217d0a08d9769400e17a077f42cb2a"
DEFAULT_OUTPUT = Path(__file__).resolve().parents[1] / ".build-deps/ocr/tesseract4android-4.9.0.aar"
MAX_ATTEMPTS = 3


class HttpsOnlyRedirectHandler(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, response, code, message, headers, new_url):
        if urllib.parse.urlparse(new_url).scheme != "https":
            raise ValueError("O runtime OCR exige HTTPS em todos os redirecionamentos")
        return super().redirect_request(request, response, code, message, headers, new_url)


def _open_url(request):
    return urllib.request.build_opener(HttpsOnlyRedirectHandler()).open(request, timeout=120)


def validate(path: Path) -> str:
    if path.is_symlink() or not path.is_file():
        raise ValueError("O runtime OCR não é um arquivo regular")
    if path.stat().st_size != EXPECTED_BYTES:
        raise ValueError("Tamanho inesperado do runtime OCR")
    checksum = hashlib.sha256()
    with path.open("rb") as handle:
        while chunk := handle.read(128 * 1024):
            checksum.update(chunk)
    digest = checksum.hexdigest()
    if digest != EXPECTED_SHA256:
        raise ValueError("SHA-256 divergente do runtime OCR; nenhum arquivo foi substituído")
    return digest


def _download(temporary: Path) -> None:
    for attempt in range(MAX_ATTEMPTS):
        request = urllib.request.Request(URL, headers={"User-Agent": "NEXUS-OCR-runtime-build/1"})
        try:
            with _open_url(request) as response, temporary.open("wb") as target:
                if urllib.parse.urlparse(response.geturl()).scheme != "https":
                    raise ValueError("O download do runtime OCR exige HTTPS")
                total = 0
                while chunk := response.read(128 * 1024):
                    total += len(chunk)
                    if total > EXPECTED_BYTES:
                        raise ValueError("Download do runtime OCR excede o tamanho fixado")
                    target.write(chunk)
                target.flush()
                os.fsync(target.fileno())
            return
        except urllib.error.HTTPError as error:
            retriable = error.code in (404, 429) or 500 <= error.code <= 599
            error.close()
            if not retriable or attempt + 1 == MAX_ATTEMPTS:
                raise
            # JitPack can briefly return missing metadata/artifacts. Never retry
            # indefinitely or accept a different artifact. Each attempt truncates.
            time.sleep(min(attempt + 1, 2))


def prepare(output: Path = DEFAULT_OUTPUT, source_file: Path | None = None) -> str:
    if output.exists() or output.is_symlink():
        return validate(output)
    if output.parent.is_symlink():
        raise ValueError("O diretório do runtime OCR não pode ser um link simbólico")
    output.parent.mkdir(parents=True, exist_ok=True)
    descriptor, name = tempfile.mkstemp(prefix=".ocr-runtime-", dir=output.parent)
    os.close(descriptor)
    temporary = Path(name)
    try:
        if source_file is not None:
            validate(source_file)
            with source_file.open("rb") as source, temporary.open("wb") as target:
                shutil.copyfileobj(source, target)
                target.flush()
                os.fsync(target.fileno())
        else:
            _download(temporary)
        checksum = validate(temporary)
        try:
            # Same-volume atomic installation, without replacing unknown files.
            os.link(temporary, output)
        except FileExistsError:
            return validate(output)
        return checksum
    finally:
        temporary.unlink(missing_ok=True)


def main() -> int:
    parser = argparse.ArgumentParser(description="Prepara o AAR OCR oficial verificado antes do Gradle")
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--source-file", type=Path, help="AAR local; exige o mesmo tamanho e SHA-256")
    args = parser.parse_args()
    try:
        checksum = prepare(args.output, args.source_file)
    except (OSError, ValueError, urllib.error.URLError) as error:
        print(f"Runtime OCR não preparado: {error}")
        return 1
    print(f"Tesseract4Android {VERSION}: {EXPECTED_BYTES} bytes · SHA-256 {checksum}")
    print(f"AAR verificado disponível para o Gradle em {args.output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
