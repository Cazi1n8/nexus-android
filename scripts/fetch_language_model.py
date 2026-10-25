#!/usr/bin/env python3
"""Explicitly prepare a pinned and verified public GGUF for Android development/CI."""

from __future__ import annotations

import argparse
import hashlib
import os
from pathlib import Path
import shutil
import tempfile
import urllib.parse
import urllib.request

MODEL_NAME = "qwen2.5-0.5b-instruct-q4_k_m.gguf"
MODEL_REVISION = "9217f5db79a29953eb74d5343926648285ec7e67"
MODEL_URL = (
    "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/"
    f"{MODEL_REVISION}/{MODEL_NAME}?download=true"
)
MODEL_BYTES = 491_400_032
MODEL_SHA256 = "74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db"
DEFAULT_OUTPUT = Path(__file__).resolve().parents[1] / "build" / "ci-models" / MODEL_NAME


def validate_model(path: Path) -> str:
    if not path.is_file() or path.stat().st_size != MODEL_BYTES:
        raise ValueError("O GGUF não tem o tamanho exato da revisão fixada.")
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        if handle.read(4) != b"GGUF":
            raise ValueError("O arquivo não possui cabeçalho GGUF.")
        handle.seek(0)
        while chunk := handle.read(1024 * 1024):
            digest.update(chunk)
    actual = digest.hexdigest()
    if actual != MODEL_SHA256:
        raise ValueError("O SHA-256 do GGUF não corresponde à revisão fixada.")
    return actual


def fetch(output: Path, source: Path | None = None) -> str:
    if output.exists():
        # An unrecognized existing file is not silently replaced.
        return validate_model(output)
    if source is not None:
        validate_model(source)
    output.parent.mkdir(parents=True, exist_ok=True)
    if shutil.disk_usage(output.parent).free < MODEL_BYTES + 64 * 1024 * 1024:
        raise ValueError("Não há espaço livre suficiente para o modelo de teste.")
    descriptor, name = tempfile.mkstemp(prefix=".nexus-llm-fixture-", dir=output.parent)
    temporary = Path(name)
    try:
        with os.fdopen(descriptor, "wb") as handle:
            if source is not None:
                with source.open("rb") as stream:
                    shutil.copyfileobj(stream, handle, 1024 * 1024)
            else:
                request = urllib.request.Request(MODEL_URL, headers={"User-Agent": "NEXUS-Android-CI/1"})
                with urllib.request.urlopen(request, timeout=90) as response:
                    if urllib.parse.urlparse(response.geturl()).scheme != "https":
                        raise ValueError("O download tentou sair de HTTPS.")
                    total = 0
                    while chunk := response.read(1024 * 1024):
                        total += len(chunk)
                        if total > MODEL_BYTES:
                            raise ValueError("O download excedeu o tamanho fixado do modelo.")
                        handle.write(chunk)
            handle.flush()
            os.fsync(handle.fileno())
        result = validate_model(temporary)
        os.replace(temporary, output)
        return result
    finally:
        temporary.unlink(missing_ok=True)


def main() -> int:
    parser = argparse.ArgumentParser(description="Prepara GGUF real para um teste Android explícito, fora do APK.")
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--from-file", type=Path, help="Reaproveita um GGUF local, após verificar tamanho e SHA-256.")
    arguments = parser.parse_args()
    try:
        digest = fetch(arguments.output, arguments.from_file)
    except (OSError, ValueError) as error:
        print(f"Modelo de teste não preparado: {error}")
        return 1
    print(f"GGUF de teste verificado: {arguments.output}")
    print(f"Bytes: {MODEL_BYTES}; SHA-256: {digest}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
