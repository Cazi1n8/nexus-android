#!/usr/bin/env python3
"""Cria configuração local com token aleatório; nunca exibe o token em stdout."""

from pathlib import Path
import os
import secrets


def main() -> None:
    path = Path(__file__).with_name(".env.local")
    # O_EXCL impede substituir ou seguir um arquivo/link existente.
    descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(descriptor, "w", encoding="utf-8") as config:
        config.write(
            "# Arquivo privado. Não envie para GitHub nem compartilhe seu conteúdo.\n"
            f"NEXUS_ACCESS_TOKEN={secrets.token_urlsafe(32)}\n"
            "NEXUS_MODEL=gemma3:4b\n"
            "OLLAMA_BASE_URL=http://127.0.0.1:11434\n"
            "NEXUS_BIND=127.0.0.1\n"
            "NEXUS_PORT=8787\n"
            "NEXUS_TOKEN_TTL_SECONDS=28800\n"
        )
    print("Configuração criada em server/.env.local com acesso restrito ao seu usuário.")
    print("Abra esse arquivo apenas no seu computador para copiar o token para o NEXUS.")
    print("A sessão expira após 8 horas de execução. Gere outro token para revogar o anterior.")


if __name__ == "__main__":
    main()
