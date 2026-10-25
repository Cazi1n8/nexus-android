#!/usr/bin/env python3
"""Prepare the pinned compact GGUF as an Android build asset.

This is an explicit developer/CI step. The generated weights stay out of Git;
when this asset directory is included in a build, the APK contains the model.
There is no model download on the phone in this preparation step.
"""
from __future__ import annotations

import argparse
from pathlib import Path

import fetch_language_model as model

DEFAULT_OUTPUT = Path(__file__).resolve().parents[1] / ".build-deps/bundled-assets/models" / model.MODEL_NAME


def prepare(output: Path = DEFAULT_OUTPUT, source_file: Path | None = None) -> str:
    """Use the common downloader and its exact revision, size and SHA checks."""
    if output.is_symlink():
        raise ValueError("O asset do modelo não pode ser um link simbólico.")
    for parent in output.parents:
        if parent.is_symlink():
            raise ValueError("O diretório do asset do modelo não pode ser um link simbólico.")
    if source_file is not None and source_file.is_symlink():
        raise ValueError("A origem local do modelo deve ser um arquivo regular.")
    return model.fetch(output, source_file)


def main() -> int:
    parser = argparse.ArgumentParser(description="Prepara a IA compacta verificada para inclusão no APK.")
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--source-file", type=Path, help="GGUF local com o mesmo tamanho e SHA-256 fixados.")
    args = parser.parse_args()
    try:
        digest = prepare(args.output, args.source_file)
    except (OSError, ValueError) as error:
        print(f"Modelo incluído não preparado: {error}")
        return 1
    print(f"Asset de IA verificado: {args.output}")
    print(f"Bytes: {model.MODEL_BYTES}; SHA-256: {digest}")
    print("Este modelo será incluído no APK se o diretório de assets estiver habilitado na compilação.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
