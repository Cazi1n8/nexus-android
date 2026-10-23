#!/usr/bin/env python3
"""Gateway OpenAI-compatible para Ollama ou um provedor configurado pelo operador.

Somente biblioteca padrão Python. Execute atrás de HTTPS fora do computador local.
Não registra conteúdo de conversas, imagens, tokens ou credenciais de provedores.
"""

from __future__ import annotations

import argparse
import base64
import binascii
from collections import OrderedDict, deque
from dataclasses import dataclass, field
import hmac
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import ipaddress
import json
import logging
import math
import os
from pathlib import Path
import socket
import threading
import time
from typing import Any
from urllib import error, parse, request


LOG = logging.getLogger("nexus.gateway")
MAX_BODY_BYTES = 8 * 1024 * 1024
MAX_IMAGE_BYTES = 3 * 1024 * 1024
MAX_RESPONSE_BYTES = 2 * 1024 * 1024
ENV_KEYS = {
    "NEXUS_BIND", "NEXUS_PORT", "NEXUS_ACCESS_TOKEN", "NEXUS_MODEL",
    "NEXUS_TOKEN_TTL_SECONDS", "NEXUS_RATE_PER_MINUTE", "NEXUS_CONCURRENCY",
    "NEXUS_UPSTREAM_TIMEOUT_SECONDS", "OLLAMA_BASE_URL", "UPSTREAM_BASE_URL",
    "UPSTREAM_API_KEY",
}


class ConfigurationError(ValueError):
    """Configuração inválida, sem incluir valores secretos na mensagem."""


class InvalidPayload(ValueError):
    """Mensagem segura para mostrar ao cliente."""


def _positive_int(env: dict[str, str], key: str, default: int, maximum: int) -> int:
    try:
        value = int(env.get(key, str(default)))
    except ValueError:
        raise ConfigurationError(f"{key} precisa ser um número inteiro.") from None
    if not 1 <= value <= maximum:
        raise ConfigurationError(f"{key} deve ficar entre 1 e {maximum}.")
    return value


def _local_host(hostname: str | None) -> bool:
    if hostname == "localhost":
        return True
    try:
        return ipaddress.ip_address(hostname or "").is_loopback
    except ValueError:
        return False


@dataclass(frozen=True)
class GatewayConfig:
    access_token: str = field(repr=False)
    upstream_base_url: str = "http://127.0.0.1:11434/v1"
    upstream_api_key: str = field(default="", repr=False)
    model: str = "gemma3:4b"
    bind: str = "127.0.0.1"
    port: int = 8787
    token_ttl_seconds: int = 28_800
    rate_per_minute: int = 30
    concurrency: int = 2
    upstream_timeout_seconds: int = 90
    started_at: float = field(default_factory=time.monotonic, repr=False)

    def __post_init__(self) -> None:
        if (len(self.access_token) < 24 or len(self.access_token) > 1024
                or any(not 33 <= ord(char) <= 126 for char in self.access_token)
                or self.access_token.upper().startswith(("SUBSTITUA", "CHANGE_ME"))):
            raise ConfigurationError("Defina NEXUS_ACCESS_TOKEN aleatório com pelo menos 24 caracteres, sem espaços.")
        if not self.model.strip() or len(self.model) > 200:
            raise ConfigurationError("Defina NEXUS_MODEL com um modelo disponível no servidor.")
        try:
            self.model.encode("utf-8")
        except UnicodeEncodeError:
            raise ConfigurationError("NEXUS_MODEL contém caracteres Unicode inválidos.") from None
        try:
            url = parse.urlsplit(self.upstream_base_url)
            url.port
        except ValueError:
            raise ConfigurationError("UPSTREAM_BASE_URL contém URL inválida.") from None
        if (url.scheme not in ("https", "http") or not url.hostname or url.username
                or url.password or url.query or url.fragment):
            raise ConfigurationError("UPSTREAM_BASE_URL deve ser uma URL HTTP(S) sem credenciais, query ou fragmento.")
        if url.scheme == "http" and not _local_host(url.hostname):
            raise ConfigurationError("HTTP para o provedor é permitido apenas em loopback. Use HTTPS para um servidor remoto.")
        if (len(self.upstream_api_key) > 16_384
                or any(not 33 <= ord(char) <= 126 for char in self.upstream_api_key)):
            raise ConfigurationError("UPSTREAM_API_KEY contém caracteres inválidos.")
        if not 0 <= self.port <= 65535:
            raise ConfigurationError("NEXUS_PORT inválida.")
        if not 1 <= self.concurrency <= 16:
            raise ConfigurationError("NEXUS_CONCURRENCY deve ficar entre 1 e 16.")
        if not 1 <= self.rate_per_minute <= 1000:
            raise ConfigurationError("NEXUS_RATE_PER_MINUTE deve ficar entre 1 e 1000.")
        if not 1 <= self.upstream_timeout_seconds <= 300:
            raise ConfigurationError("NEXUS_UPSTREAM_TIMEOUT_SECONDS deve ficar entre 1 e 300.")
        if not 1 <= self.token_ttl_seconds <= 604_800:
            raise ConfigurationError("NEXUS_TOKEN_TTL_SECONDS deve ficar entre 1 e 604800.")

    @property
    def completion_url(self) -> str:
        # Accept a server origin, an API base, or a complete endpoint, like the
        # Android settings. Copying a provider's complete URL must not duplicate
        # /chat/completions; an origin needs the standard /v1 prefix.
        url = parse.urlsplit(self.upstream_base_url)
        path = url.path.rstrip("/")
        if not path:
            path = "/v1/chat/completions"
        elif not path.endswith("/chat/completions"):
            path += "/chat/completions"
        return parse.urlunsplit((url.scheme, url.netloc, path, "", ""))

    @classmethod
    def from_environment(cls, env: dict[str, str] | None = None) -> "GatewayConfig":
        values = os.environ if env is None else env
        upstream = values.get("UPSTREAM_BASE_URL") or values.get("OLLAMA_BASE_URL", "http://127.0.0.1:11434")
        return cls(
            access_token=values.get("NEXUS_ACCESS_TOKEN", ""),
            upstream_base_url=upstream,
            upstream_api_key=values.get("UPSTREAM_API_KEY", ""),
            model=values.get("NEXUS_MODEL", "gemma3:4b"),
            bind=values.get("NEXUS_BIND", "127.0.0.1"),
            port=_positive_int(values, "NEXUS_PORT", 8787, 65535),
            token_ttl_seconds=_positive_int(values, "NEXUS_TOKEN_TTL_SECONDS", 28_800, 604_800),
            rate_per_minute=_positive_int(values, "NEXUS_RATE_PER_MINUTE", 30, 1000),
            concurrency=_positive_int(values, "NEXUS_CONCURRENCY", 2, 16),
            upstream_timeout_seconds=_positive_int(values, "NEXUS_UPSTREAM_TIMEOUT_SECONDS", 90, 300),
        )


def load_env_file(path: Path) -> None:
    """Lê apenas KEY=value conhecidos; não executa shell nem interpola valores."""
    for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        key, separator, value = line.partition("=")
        key, value = key.strip(), value.strip()
        if not separator or key not in ENV_KEYS:
            raise ConfigurationError(f"Linha {number} do arquivo de configuração é inválida.")
        if len(value) >= 2 and value[0] == value[-1] and value[0] in ("\"", "'"):
            value = value[1:-1]
        os.environ.setdefault(key, value)


def _validated_image_url(value: Any) -> dict[str, str]:
    if not isinstance(value, dict) or not isinstance(value.get("url"), str):
        raise InvalidPayload("Imagem inválida: envie image_url com uma data URI.")
    data_uri = value["url"]
    header, separator, encoded = data_uri.partition(",")
    if not separator or header not in (
        "data:image/jpeg;base64", "data:image/png;base64", "data:image/webp;base64"
    ):
        raise InvalidPayload("Imagens precisam ser JPEG, PNG ou WebP em base64; URLs remotas não são aceitas.")
    if len(encoded) > ((MAX_IMAGE_BYTES + 2) // 3) * 4:
        raise InvalidPayload("A imagem excede 3 MB. Reduza a imagem antes de enviar.")
    try:
        decoded = base64.b64decode(encoded, validate=True)
    except (binascii.Error, ValueError):
        raise InvalidPayload("A imagem contém base64 inválido.") from None
    if not decoded or len(decoded) > MAX_IMAGE_BYTES:
        raise InvalidPayload("Imagem vazia ou maior que 3 MB.")
    return {"url": data_uri}


def _validated_text(value: str) -> str:
    try:
        value.encode("utf-8")
    except UnicodeEncodeError:
        raise InvalidPayload("O texto contém caracteres Unicode inválidos.") from None
    return value


def _assistant_text(message: dict[str, Any]) -> str:
    """Normalize compatible text/refusal responses, never stringify JSON nulls."""
    content = message.get("content")
    if isinstance(content, str):
        text = content
    elif isinstance(content, list):
        text = "\n".join(
            part["text"] for part in content
            if isinstance(part, dict) and part.get("type") == "text" and isinstance(part.get("text"), str)
        )
    else:
        text = ""
    if not text.strip() and isinstance(message.get("refusal"), str):
        text = message["refusal"]
    if not text.strip():
        raise InvalidPayload("Resposta do provedor não contém texto de assistente.")
    return _validated_text(text)


def validate_payload(payload: Any, configured_model: str) -> dict[str, Any]:
    if not isinstance(payload, dict):
        raise InvalidPayload("O corpo precisa ser um objeto JSON.")
    if payload.get("stream", False) is not False:
        raise InvalidPayload("Esta versão aceita somente stream=false.")
    supplied_model = payload.get("model", configured_model)
    if supplied_model != configured_model:
        raise InvalidPayload("O modelo solicitado difere de NEXUS_MODEL. Confira a configuração do aplicativo.")
    messages = payload.get("messages")
    if not isinstance(messages, list) or not 1 <= len(messages) <= 64:
        raise InvalidPayload("Envie entre 1 e 64 mensagens por solicitação.")
    cleaned_messages: list[dict[str, Any]] = []
    total_text = 0
    image_count = 0
    for message in messages:
        if not isinstance(message, dict) or message.get("role") not in ("system", "user", "assistant"):
            raise InvalidPayload("Cada mensagem precisa de role system, user ou assistant.")
        content = message.get("content")
        if isinstance(content, str):
            total_text += len(content)
            cleaned_content: Any = _validated_text(content)
        elif isinstance(content, list) and 1 <= len(content) <= 8:
            cleaned_content = []
            for part in content:
                if not isinstance(part, dict):
                    raise InvalidPayload("Conteúdo multimodal inválido.")
                if part.get("type") == "text" and isinstance(part.get("text"), str):
                    total_text += len(part["text"])
                    cleaned_content.append({"type": "text", "text": _validated_text(part["text"])})
                elif part.get("type") == "image_url" and message["role"] == "user":
                    image_count += 1
                    if image_count > 2:
                        raise InvalidPayload("Envie no máximo duas imagens por solicitação.")
                    cleaned_content.append({"type": "image_url", "image_url": _validated_image_url(part.get("image_url"))})
                else:
                    raise InvalidPayload("Use partes text ou image_url nas mensagens do usuário.")
        else:
            raise InvalidPayload("Conteúdo de mensagem inválido.")
        if total_text > 120_000:
            raise InvalidPayload("O contexto excede 120 mil caracteres. Inicie uma nova conversa.")
        cleaned_messages.append({"role": message["role"], "content": cleaned_content})
    max_tokens = payload.get("max_tokens", 1024)
    if type(max_tokens) is not int or not 1 <= max_tokens <= 4096:
        raise InvalidPayload("max_tokens deve ficar entre 1 e 4096.")
    temperature = payload.get("temperature", 0.7)
    if (type(temperature) not in (int, float) or not 0 <= temperature <= 2
            or not math.isfinite(temperature)):
        raise InvalidPayload("temperature deve ficar entre 0 e 2.")
    # Nunca repassa URLs, credenciais, ferramentas ou outros parâmetros arbitrários do cliente.
    return {
        "model": configured_model, "messages": cleaned_messages, "stream": False,
        "max_tokens": max_tokens, "temperature": temperature,
    }


class NoRedirects(request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        # Uma credencial do provedor nunca pode seguir redirecionamentos.
        return None


class GatewayServer(ThreadingHTTPServer):
    daemon_threads = True
    allow_reuse_address = True
    request_queue_size = 16

    def __init__(self, config: GatewayConfig):
        self.config = config
        self.inference_slots = threading.BoundedSemaphore(config.concurrency)
        self.connection_slots = threading.BoundedSemaphore(16)
        self.rate_lock = threading.Lock()
        self.request_times: OrderedDict[str, deque[float]] = OrderedDict()
        self.opener = request.build_opener(NoRedirects())
        super().__init__((config.bind, config.port), GatewayHandler)

    def process_request(self, request_socket, client_address):
        if not self.connection_slots.acquire(blocking=False):
            self.shutdown_request(request_socket)
            return
        try:
            super().process_request(request_socket, client_address)
        except BaseException:
            self.connection_slots.release()
            raise

    def process_request_thread(self, request_socket, client_address):
        try:
            super().process_request_thread(request_socket, client_address)
        finally:
            self.connection_slots.release()

    def handle_error(self, request_socket, client_address):
        LOG.warning("Falha de conexão com cliente; detalhes não registrados para proteger dados.")

    def allow_rate(self, peer: str) -> bool:
        now = time.monotonic()
        with self.rate_lock:
            times = self.request_times.setdefault(peer, deque())
            self.request_times.move_to_end(peer)
            while times and times[0] <= now - 60:
                times.popleft()
            if len(self.request_times) > 4096:
                self.request_times.popitem(last=False)
            if len(times) >= self.config.rate_per_minute:
                return False
            times.append(now)
            return True


class GatewayHandler(BaseHTTPRequestHandler):
    server: GatewayServer
    protocol_version = "HTTP/1.1"
    server_version = "NEXUS"
    sys_version = ""

    def setup(self):
        super().setup()
        self.connection.settimeout(10)

    def log_message(self, format, *args):
        # BaseHTTPRequestHandler normalmente registra URL e request line completos.
        # Não registrar entradas do cliente evita vazamento de dados em logs.
        return

    def _send_json(self, status: int, payload: dict[str, Any], retry_after: int | None = None):
        data = json.dumps(payload, ensure_ascii=True, separators=(",", ":")).encode("utf-8")
        try:
            self.send_response(status)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(data)))
            self.send_header("Cache-Control", "no-store")
            self.send_header("X-Content-Type-Options", "nosniff")
            self.send_header("Connection", "close")
            if retry_after is not None:
                self.send_header("Retry-After", str(retry_after))
            self.end_headers()
            self.wfile.write(data)
        except (BrokenPipeError, ConnectionResetError, TimeoutError):
            pass
        self.close_connection = True
        LOG.info("request_completed status=%s", status)

    def _error(self, status: int, code: str, message: str, retry_after: int | None = None):
        self._send_json(status, {"error": {"code": code, "message": message}}, retry_after)

    def do_GET(self):
        if self.path == "/healthz":
            self._send_json(200, {"status": "ok", "service": "nexus-gateway", "upstream": "not_checked"})
        else:
            self._error(404, "not_found", "Rota inexistente.")

    def do_POST(self):
        if self.path != "/v1/chat/completions":
            self._error(404, "not_found", "Rota inexistente.")
            return
        config = self.server.config
        supplied_auth = self.headers.get_all("Authorization", [])
        if (len(supplied_auth) != 1
                or not hmac.compare_digest(supplied_auth[0].encode("utf-8"), ("Bearer " + config.access_token).encode("ascii"))
                or time.monotonic() - config.started_at >= config.token_ttl_seconds):
            self._error(401, "unauthorized", "Token de sessão ausente, inválido ou expirado. Atualize-o nas configurações.")
            return
        if not self.server.allow_rate(self.client_address[0]):
            self._error(429, "rate_limited", "Limite temporário de solicitações. Aguarde um minuto.", 60)
            return
        if self.headers.get("Transfer-Encoding"):
            self._error(400, "invalid_encoding", "Transfer-Encoding não é aceito. Informe Content-Length.")
            return
        if self.headers.get_content_type() != "application/json":
            self._error(415, "invalid_content_type", "Envie Content-Type: application/json.")
            return
        lengths = self.headers.get_all("Content-Length", [])
        try:
            length = int(lengths[0]) if len(lengths) == 1 else -1
        except ValueError:
            length = -1
        if length <= 0:
            self._error(400, "invalid_length", "Informe um Content-Length positivo.")
            return
        if length > MAX_BODY_BYTES:
            self._error(413, "payload_too_large", "A solicitação excede 8 MB.")
            return
        if not self.server.inference_slots.acquire(blocking=False):
            self._error(503, "busy", "O servidor está ocupado. Tente novamente em alguns segundos.", 3)
            return
        try:
            self._complete(length)
        finally:
            self.server.inference_slots.release()

    def _complete(self, length: int):
        config = self.server.config
        try:
            body = self.rfile.read(length)
            if len(body) != length:
                raise InvalidPayload("Solicitação incompleta.")
            payload = json.loads(body.decode("utf-8"))
            forward = validate_payload(payload, config.model)
        except InvalidPayload as exc:
            self._error(400, "invalid_payload", str(exc))
            return
        except (json.JSONDecodeError, UnicodeDecodeError, RecursionError, ValueError):
            self._error(400, "invalid_json", "JSON inválido.")
            return
        except (socket.timeout, TimeoutError):
            self._error(408, "request_timeout", "Tempo esgotado ao receber a solicitação.")
            return
        headers = {"Content-Type": "application/json", "Accept": "application/json"}
        if config.upstream_api_key:
            headers["Authorization"] = "Bearer " + config.upstream_api_key
        upstream_request = request.Request(
            config.completion_url,
            data=json.dumps(forward, ensure_ascii=True).encode("utf-8"),
            headers=headers,
            method="POST",
        )
        try:
            with self.server.opener.open(upstream_request, timeout=config.upstream_timeout_seconds) as response:
                response_data = response.read(MAX_RESPONSE_BYTES + 1)
            if len(response_data) > MAX_RESPONSE_BYTES:
                raise InvalidPayload("Resposta do provedor excede o limite permitido.")
            answer = json.loads(response_data.decode("utf-8"))
            choices = answer.get("choices") if isinstance(answer, dict) else None
            if (not isinstance(choices, list) or not choices
                    or not isinstance(choices[0], dict)
                    or not isinstance(choices[0].get("message"), dict)):
                raise InvalidPayload("Resposta do provedor não contém texto de assistente.")
            content = _assistant_text(choices[0]["message"])
            # Retorna apenas campos que o cliente utiliza, sem metadados privados do provedor.
            self._send_json(200, {
                "object": "chat.completion", "model": config.model,
                "choices": [{"index": 0, "message": {
                    "role": "assistant", "content": content
                }, "finish_reason": "length" if choices[0].get("finish_reason") == "length" else "stop"}],
            })
        except error.HTTPError as exc:
            # Nunca repassar o corpo do erro upstream: pode conter detalhes internos.
            status = 429 if exc.code == 429 else 502
            code = "upstream_rate_limited" if exc.code == 429 else "upstream_error"
            self._error(status, code, "O provedor de IA recusou a solicitação. Confira modelo, credenciais e limites no servidor.")
            exc.close()
        except (socket.timeout, TimeoutError):
            self._error(504, "upstream_timeout", "A IA demorou demais. Tente novamente ou use um modelo menor.")
        except error.URLError as exc:
            if isinstance(exc.reason, (socket.timeout, TimeoutError)):
                self._error(504, "upstream_timeout", "A IA demorou demais. Tente novamente ou use um modelo menor.")
            else:
                self._error(502, "upstream_unavailable", "Não foi possível conectar ao provedor de IA. Confira o servidor e sua conexão.")
        except (json.JSONDecodeError, UnicodeDecodeError, InvalidPayload, RecursionError, ValueError):
            self._error(502, "invalid_upstream_response", "O provedor retornou uma resposta incompatível ou vazia.")
        except (ConnectionError, OSError):
            self._error(502, "upstream_unavailable", "A conexão com o provedor de IA foi interrompida.")


def main() -> int:
    parser = argparse.ArgumentParser(description="Gateway de IA do NEXUS (Ollama ou API OpenAI-compatible).")
    parser.add_argument("--env-file", type=Path, help="Arquivo KEY=value local; variáveis existentes têm prioridade.")
    args = parser.parse_args()
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")
    try:
        if args.env_file:
            load_env_file(args.env_file)
        config = GatewayConfig.from_environment()
        server = GatewayServer(config)
    except (ConfigurationError, OSError) as exc:
        # OSError pode conter caminho de arquivo, mas nunca valores do arquivo.
        LOG.error("Não foi possível iniciar o gateway: %s", exc)
        return 2
    LOG.info("Gateway iniciado na porta %s. Sessão válida por %s segundos. /healthz não testa o modelo.",
             config.port, config.token_ttl_seconds)
    if not _local_host(config.bind):
        LOG.warning("Endereço não local: publique somente atrás de HTTPS, firewall e limitação de acesso.")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        LOG.info("Gateway encerrado.")
    finally:
        server.server_close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
