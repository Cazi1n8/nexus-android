#!/usr/bin/env python3
"""Materialize a developer-owned signing key in a private, temporary CI file.

This script never generates a key, prints secrets, or sends them to a service.
Gradle/Android subsequently validates the actual store, alias and passwords.
"""
from __future__ import annotations

import base64
import binascii
import os
from pathlib import Path
import sys
from typing import Mapping

SECRET_NAMES = (
    "NEXUS_RELEASE_KEYSTORE_BASE64", "NEXUS_RELEASE_STORE_PASSWORD",
    "NEXUS_RELEASE_KEY_ALIAS", "NEXUS_RELEASE_KEY_PASSWORD",
)


def prepare(environment: Mapping[str, str]) -> Path:
    missing = [name for name in (*SECRET_NAMES, "NEXUS_RELEASE_STORE_FILE") if not environment.get(name)]
    if missing:
        raise ValueError("Configuração de assinatura ausente: " + ", ".join(missing))
    encoded = "".join(environment["NEXUS_RELEASE_KEYSTORE_BASE64"].split())
    if len(encoded) > 2_000_000:
        raise ValueError("O arquivo de assinatura ultrapassa o limite de preparação.")
    try:
        data = base64.b64decode(encoded, validate=True)
    except (ValueError, binascii.Error) as failure:
        raise ValueError("O arquivo de assinatura não contém Base64 válido.") from failure
    if not data or len(data) > 1_048_576:
        raise ValueError("O arquivo de assinatura está vazio ou é grande demais.")
    destination = Path(environment["NEXUS_RELEASE_STORE_FILE"])
    if not destination.is_absolute() or not destination.parent.is_dir():
        raise ValueError("Use um caminho absoluto em um diretório temporário existente.")
    # Exclusive creation rejects both existing files and symbolic links. Never
    # overwrite/delete a developer's existing signing key on a failed run.
    descriptor = os.open(destination, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    try:
        with os.fdopen(descriptor, "wb") as output:
            output.write(data)
    except BaseException:
        destination.unlink(missing_ok=True)
        raise
    return destination


def main() -> int:
    try:
        prepare(os.environ)
    except (OSError, ValueError):
        # Keep values, filesystem exceptions and decoded data out of CI logs.
        print("Não foi possível preparar a assinatura. Confira os quatro Secrets e o diretório temporário.", file=sys.stderr)
        return 1
    print("Arquivo temporário de assinatura preparado. Gradle validará a chave antes de gerar o pacote.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
