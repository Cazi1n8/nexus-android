#!/usr/bin/env python3
"""Prepare pinned, verified GGUF models as Android build assets.

This is an explicit developer/CI step. The generated weights stay out of Git;
when this asset directory is included in a build, the APK contains the model.
There is no model download on the phone in this preparation step.
"""
from __future__ import annotations

import argparse
import hashlib
import os
from pathlib import Path
import shutil
import tempfile
import urllib.parse
import urllib.request

import fetch_language_model as model

DEFAULT_OUTPUT = Path(__file__).resolve().parents[1] / ".build-deps/bundled-assets/models" / model.MODEL_NAME
BALANCED_NAME = "qwen2.5-1.5b-instruct-q4_k_m.gguf"
BALANCED_REVISION = "91cad51170dc346986eccefdc2dd33a9da36ead9"
BALANCED_BYTES = 1_117_320_736
BALANCED_SHA256 = "6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e"
BALANCED_URL = (
    "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/"
    f"{BALANCED_REVISION}/{BALANCED_NAME}?download=true"
)
DEFAULT_BALANCED_OUTPUT = DEFAULT_OUTPUT.parent / BALANCED_NAME


def _check_paths(output: Path, source_file: Path | None) -> None:
    if output.is_symlink():
        raise ValueError("O asset do modelo não pode ser um link simbólico.")
    for parent in output.parents:
        if parent.is_symlink():
            raise ValueError("O diretório do asset do modelo não pode ser um link simbólico.")
    if source_file is not None and source_file.is_symlink():
        raise ValueError("A origem local do modelo deve ser um arquivo regular.")


def prepare(output: Path = DEFAULT_OUTPUT, source_file: Path | None = None) -> str:
    """Use the common compact downloader and its exact revision, size and SHA checks."""
    _check_paths(output, source_file)
    return model.fetch(output, source_file)


def validate_balanced(path: Path) -> str:
    if not path.is_file() or path.stat().st_size != BALANCED_BYTES:
        raise ValueError("O GGUF equilibrado não tem o tamanho exato da revisão fixada.")
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        if handle.read(4) != b"GGUF":
            raise ValueError("O arquivo não possui cabeçalho GGUF.")
        handle.seek(0)
        while chunk := handle.read(1024 * 1024):
            digest.update(chunk)
    if digest.hexdigest() != BALANCED_SHA256:
        raise ValueError("O SHA-256 do GGUF equilibrado não corresponde à revisão fixada.")
    return digest.hexdigest()


class HttpsRedirectHandler(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, response, code, message, headers, new_url):
        if urllib.parse.urlparse(new_url).scheme != "https":
            raise ValueError("O download tentou sair de HTTPS.")
        return super().redirect_request(request, response, code, message, headers, new_url)


def prepare_balanced(output: Path = DEFAULT_BALANCED_OUTPUT, source_file: Path | None = None) -> str:
    """Add the stronger offline model without changing compact's public helper API."""
    _check_paths(output, source_file)
    if output.exists():
        return validate_balanced(output)
    if source_file is not None:
        validate_balanced(source_file)
    output.parent.mkdir(parents=True, exist_ok=True)
    if shutil.disk_usage(output.parent).free < BALANCED_BYTES + 64 * 1024 * 1024:
        raise ValueError("Não há espaço livre suficiente para incluir a IA equilibrada.")
    descriptor, name = tempfile.mkstemp(prefix=".nexus-balanced-asset-", dir=output.parent)
    temporary = Path(name)
    try:
        with os.fdopen(descriptor, "wb") as handle:
            if source_file is not None:
                with source_file.open("rb") as stream:
                    shutil.copyfileobj(stream, handle, 1024 * 1024)
            else:
                request = urllib.request.Request(BALANCED_URL, headers={"User-Agent": "NEXUS-Android-CI/1"})
                with urllib.request.build_opener(HttpsRedirectHandler()).open(request, timeout=90) as response:
                    if urllib.parse.urlparse(response.geturl()).scheme != "https":
                        raise ValueError("O download tentou sair de HTTPS.")
                    received = 0
                    while chunk := response.read(1024 * 1024):
                        received += len(chunk)
                        if received > BALANCED_BYTES:
                            raise ValueError("O download excedeu o tamanho fixado da IA equilibrada.")
                        handle.write(chunk)
            handle.flush()
            os.fsync(handle.fileno())
        result = validate_balanced(temporary)
        os.replace(temporary, output)
        return result
    finally:
        temporary.unlink(missing_ok=True)


def main() -> int:
    parser = argparse.ArgumentParser(description="Prepara as IAs verificadas para inclusão no APK, sem download no celular.")
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--source-file", type=Path, help="GGUF local com o mesmo tamanho e SHA-256 fixados.")
    parser.add_argument("--with-balanced", action="store_true", help="Inclui também a IA de 1,5B para aparelhos compatíveis.")
    parser.add_argument("--balanced-output", type=Path, default=DEFAULT_BALANCED_OUTPUT)
    parser.add_argument("--balanced-source-file", type=Path, help="GGUF equilibrado local com tamanho e SHA-256 fixados.")
    args = parser.parse_args()
    try:
        digest = prepare(args.output, args.source_file)
        if args.with_balanced:
            balanced_digest = prepare_balanced(args.balanced_output, args.balanced_source_file)
    except (OSError, ValueError) as error:
        print(f"Modelo incluído não preparado: {error}")
        return 1
    print(f"Asset de IA verificado: {args.output}")
    print(f"Bytes: {model.MODEL_BYTES}; SHA-256: {digest}")
    if args.with_balanced:
        print(f"Asset equilibrado verificado: {args.balanced_output}")
        print(f"Bytes: {BALANCED_BYTES}; SHA-256: {balanced_digest}")
    print("Este modelo será incluído no APK se o diretório de assets estiver habilitado na compilação.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
