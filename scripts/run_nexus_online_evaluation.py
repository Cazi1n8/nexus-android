#!/usr/bin/env python3
"""Evaluate a real, explicitly configured NEXUS online deployment.

This is a Python HTTP/streaming evaluation, not an Android hardware test. It does
not start a model, supply prepared assistant answers or certify semantic quality.
No endpoint or invitation is configured by default. Credentials are read only
from private files; transcripts are written outside the repository with mode600.
The public report is built from a strict technical allowlist, never raw output.
Request counts refer to client HTTP chat attempts, not proven model executions.
The configured NEXUS gateway permits two internal calculator cycles, hence at
most three provider rounds for each chat request; the output budget includes all
three rounds. Actual provider-round counts require backend telemetry and are not
invented by this client.
"""
from __future__ import annotations

import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import re
import stat
import struct
import tempfile
import time
from typing import Any
from urllib import error, parse, request
import zlib


ROOT = Path(__file__).resolve().parents[1]
MAX_JSON_BYTES = 1_048_576
MAX_EVENT_BYTES = 65_536
MAX_ANSWER_CHARS = 24_000
DEFAULT_MODEL = "nexus-ai"
PROVIDER_ROUNDS_PER_CLIENT_REQUEST_LIMIT = 3
CASE_IDS = ("identity", "writing", "informal", "math", "context", "programming", "vision", "camera_limit", "app_action")
SYSTEM = (
    "Você é NEXUS AI, o assistente NEXUS. O nome fictício preferido do usuário nesta avaliação é Teste Azul. "
    "Responda em português brasileiro com tom amigável e claro. Use o histórico e as preferências autorizadas, "
    "sem confundir a identidade do usuário com a do assistente. Em pedidos de escrita, entregue primeiro o texto "
    "pronto para o usuário utilizar: a primeira pessoa desse texto representa seu autor humano, não a IA. "
    "Compreenda abreviações e erros informais. Não invente fatos pessoais, assinaturas ou destinatários. "
    "Você recebe apenas o texto e as imagens explicitamente anexadas; não tem acesso contínuo à câmera, "
    "tela ou microfone. Reconheça incertezas. Não afirme executar ações do Android: este avaliador não tem "
    "ferramenta para executar ações. Memória autorizada: o usuário prefere explicações simples."
)


class EvaluationError(RuntimeError):
    """Contains only a stable error code safe for a technical report."""


class NoRedirects(request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def endpoint(value: str) -> str:
    try:
        p = parse.urlsplit(value)
        p.port
        if (p.scheme not in ("https", "http") or not p.hostname or p.username or p.password
                or p.query or p.fragment or any(ord(c) < 33 for c in value)):
            raise ValueError
        if p.scheme == "http" and p.hostname not in ("localhost", "127.0.0.1", "::1"):
            raise ValueError
        if p.path.rstrip("/") not in ("", "/v1"):
            raise ValueError
    except ValueError:
        raise EvaluationError("invalid_endpoint_https_or_loopback_required") from None
    return parse.urlunsplit((p.scheme, p.netloc, "/v1", "", ""))


def read_invitation(path: Path) -> str:
    """A private operator-issued invitation, never a provider API key."""
    if path.is_symlink() or not path.is_file():
        raise EvaluationError("invalid_private_invitation_file")
    mode = path.stat().st_mode
    if stat.S_IMODE(mode) & 0o077 or path.stat().st_size > 256:
        raise EvaluationError("invitation_file_requires_mode_600_or_stricter")
    try:
        value = path.read_text(encoding="ascii").strip()
    except (UnicodeError, OSError):
        raise EvaluationError("invalid_private_invitation_file") from None
    if not 16 <= len(value) <= 128 or not re.fullmatch(r"[A-Za-z0-9_-]+", value):
        raise EvaluationError("invalid_invitation_format")
    return value


def _chunk(kind: bytes, data: bytes) -> bytes:
    return struct.pack("!I", len(data)) + kind + data + struct.pack("!I", zlib.crc32(kind + data) & 0xFFFFFFFF)


def synthetic_image() -> bytes:
    """Original deterministic RGB PNG: left red square, right blue circle."""
    rows = []
    for y in range(240):
        row = bytearray([0])
        for x in range(320):
            if 35 <= x < 115 and 75 <= y < 155:
                rgb = (230, 25, 35)
            elif (x - 235) ** 2 + (y - 115) ** 2 <= 40 ** 2:
                rgb = (20, 70, 230)
            else:
                rgb = (255, 255, 255)
            row.extend(rgb)
        rows.append(bytes(row))
    return (b"\x89PNG\r\n\x1a\n" + _chunk(b"IHDR", struct.pack("!IIBBBBB", 320, 240, 8, 2, 0, 0, 0))
            + _chunk(b"IDAT", zlib.compress(b"".join(rows), 9)) + _chunk(b"IEND", b""))


def cases() -> list[dict[str, Any]]:
    image = "data:image/png;base64," + base64.b64encode(synthetic_image()).decode("ascii")
    context = ["Guarde nesta conversa: o robô do projeto se chama Pipa e sua cor é violeta. Responda brevemente."]
    context += [f"Etapa {n}: continue falando brevemente do mesmo robô, sem mudar seu nome nem sua cor."
                for n in range(2, 20)]
    context += ["Qual era o nome e a cor do robô definidos no começo desta conversa?"]
    return [
        {"id": "identity", "turns": ["Qual é meu nome preferido?", "E qual é o seu nome?"]},
        {"id": "writing", "turns": ["Me faz um texto de desculpa que eu possa mandar para alguém, sem inventar o que aconteceu.",
                                     "Deixe esse texto mais curto e informal."]},
        {"id": "informal", "turns": ["chat me ajda faze um texo de descupa"]},
        {"id": "math", "turns": ["Quanto é 25 vezes 18? Explique o cálculo brevemente.",
                                  "Agora calcule 50 + 50 + 50 × 50, respeitando a precedência."]},
        {"id": "context", "turns": context},
        {"id": "programming", "turns": ["Corrija este Python e explique: def dobro(x)\n    return x * 2"]},
        {"id": "vision", "turns": [
            [{"type": "text", "text": "Quantas formas há nesta imagem? Descreva a forma e a cor de cada uma, da esquerda para a direita."},
             {"type": "image_url", "image_url": {"url": image}}],
            "O que está à direita na imagem anterior? Explique sem inventar outros objetos."]},
        {"id": "camera_limit", "turns": ["Você consegue ver pela minha câmera o que eu estou vendo agora?"]},
        {"id": "app_action", "turns": ["NEXUS, abra as configurações do meu celular."]},
    ]


def selected_cases(value: str) -> list[dict[str, Any]]:
    ids = value.split(",")
    if not ids or len(set(ids)) != len(ids) or any(i not in CASE_IDS for i in ids):
        raise EvaluationError("invalid_case_selection")
    source = {c["id"]: c for c in cases()}
    return [source[i] for i in ids]


def public_case(row: dict[str, Any]) -> dict[str, Any]:
    """Do not copy keys from raw data, provider metadata, prompts or outputs."""
    turns = row.get("turns", [])
    return {"id": row["id"], "status": row["status"], "requestedTurns": row["requestedTurns"],
            "completedTurns": len([t for t in turns if t.get("status") == "received"]),
            "turns": [{k: t.get(k) for k in ("status", "streamed", "firstTextMs", "totalMs", "finishReason",
                                             "truncated", "inputTokensReported", "outputTokensReported", "errorCode")}
                      for t in turns],
            "mechanicalChecks": row.get("mechanicalChecks", {}), "qualityApproved": False}


def public_report(raw: dict[str, Any]) -> dict[str, Any]:
    return {"schemaVersion": 1, "kind": "nexus_online_http_evaluation_technical_summary",
            "status": raw["status"], "reasonCode": raw.get("reasonCode"),
            "modelAlias": DEFAULT_MODEL, "modelIdentity": "operator_configured_not_independently_verified",
            "androidHardwareTest": False, "productionKotlinClientTest": False,
            "semanticQuality": "not_assessed_independent_review_required", "qualityApproved": False,
            "configuredEndpointPresent": raw.get("configuredEndpointPresent", False),
            "configuredInvitationPresent": raw.get("configuredInvitationPresent", False),
            "plannedRequests": raw.get("plannedRequests", 0), "actualInferenceRequests": raw.get("actualInferenceRequests", 0),
            "requestCountScope": "client_chat_http_requests",
            "requestCountSemantics": "attempted_client_chat_requests_not_confirmed_provider_executions",
            "providerRoundsPerClientRequestLimit": PROVIDER_ROUNDS_PER_CLIENT_REQUEST_LIMIT,
            "actualProviderRounds": None,
            "requestLimit": raw.get("requestLimit"), "maxOutputTokensPerRequest": raw.get("maxOutputTokensPerRequest"),
            "maximumRequestedOutputTokens": raw.get("maximumRequestedOutputTokens", 0),
            "maximumRequestedOutputTokensScope": "client_output_budget_before_internal_tool_cycles",
            "maximumProviderOutputTokenBudget": raw.get("maximumRequestedOutputTokens", 0)
                * PROVIDER_ROUNDS_PER_CLIENT_REQUEST_LIMIT,
            "elapsedMs": raw.get("elapsedMs", 0), "technicalFailures": raw.get("technicalFailures", 0),
            "visionOperatorDeclared": raw.get("visionOperatorDeclared"),
            "realImageFixtureSha256": hashlib.sha256(synthetic_image()).hexdigest(),
            "realImageFixtureBytes": len(synthetic_image()), "imageFixtureSyntheticNoPersonalContent": True,
            "transcriptsPublished": False, "appActionsExecuted": False,
            "calculatorExecutionTested": False,
            "cases": [public_case(c) for c in raw.get("cases", [])]}


def secure_write(path: Path, value: dict[str, Any], *, replace: bool = False, private: bool = False) -> None:
    if path.is_symlink():
        raise EvaluationError("report_symlink_refused")
    if private and path.resolve().is_relative_to(ROOT):
        raise EvaluationError("private_transcripts_must_be_outside_repository")
    if path.exists() and not replace:
        raise EvaluationError("report_already_exists")
    path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    with tempfile.NamedTemporaryFile(mode="w", encoding="utf-8", dir=path.parent, prefix=".nexus-eval-", delete=False) as f:
        temporary = Path(f.name)
        try:
            os.chmod(temporary, 0o600)
            json.dump(value, f, ensure_ascii=False, indent=2, allow_nan=False)
            f.write("\n")
            f.flush()
            os.fsync(f.fileno())
        except BaseException:
            temporary.unlink(missing_ok=True)
            raise
    try:
        if replace:
            os.replace(temporary, path)
        else:
            # Hard-link creation is atomic and refuses an existing path.
            os.link(temporary, path)
    finally:
        temporary.unlink(missing_ok=True)


class Client:
    def __init__(self, base: str, timeout: int):
        self.base = endpoint(base)
        self.timeout = timeout
        self.opener = request.build_opener(NoRedirects())
        self.access_token = ""
        self.refresh_token = ""
        self.expires_at = 0.0
        self.chat_request_attempts = 0

    def _open(self, path: str, payload: dict | None, authenticated: bool = True):
        headers = {"Accept": "application/json", "User-Agent": "NEXUS-Online-Evaluation/1"}
        if authenticated:
            headers["Authorization"] = "Bearer " + self.access_token
        data = None
        if payload is not None:
            headers["Content-Type"] = "application/json; charset=utf-8"
            data = json.dumps(payload, ensure_ascii=False, allow_nan=False).encode("utf-8")
        req = request.Request(self.base + path, data=data, headers=headers)
        try:
            return self.opener.open(req, timeout=self.timeout)
        except error.HTTPError as exc:
            exc.close()
            raise EvaluationError(f"http_{exc.code}") from None
        except (OSError, error.URLError, ValueError):
            raise EvaluationError("network_or_tls_error") from None

    def json(self, path: str, payload: dict | None = None, authenticated: bool = True) -> dict:
        with self._open(path, payload, authenticated) as response:
            if response.headers.get_content_type() != "application/json":
                raise EvaluationError("non_json_response")
            data = response.read(MAX_JSON_BYTES + 1)
        if len(data) > MAX_JSON_BYTES:
            raise EvaluationError("json_response_too_large")
        try:
            parsed = json.loads(data.decode("utf-8"))
            if not isinstance(parsed, dict):
                raise ValueError
            return parsed
        except (ValueError, UnicodeError, RecursionError):
            raise EvaluationError("invalid_json_response") from None

    def _tokens(self, response: dict) -> None:
        access, refresh, ttl = response.get("access_token"), response.get("refresh_token"), response.get("expires_in")
        if (not isinstance(access, str) or not isinstance(refresh, str) or not 16 <= len(access) <= 8192
                or not 16 <= len(refresh) <= 8192 or type(ttl) is not int or not 1 <= ttl <= 86400
                or any(ord(c) < 33 or ord(c) > 126 for c in access + refresh)):
            raise EvaluationError("invalid_session_response")
        self.access_token, self.refresh_token = access, refresh
        self.expires_at = time.monotonic() + ttl

    def redeem(self, invitation: str) -> None:
        self._tokens(self.json("/sessions/redeem", {"invitation_code": invitation}, False))

    def refresh(self) -> None:
        self._tokens(self.json("/sessions/refresh", {"refresh_token": self.refresh_token}, False))

    def capabilities(self) -> dict:
        value = self.json("/capabilities")
        if value.get("service") != "nexus-gateway" or value.get("protocol_version") != 1:
            raise EvaluationError("incompatible_nexus_capabilities")
        return value

    def complete(self, messages: list[dict], output_tokens: int, stream: bool = True) -> dict:
        # Refresh before sending a request. Never replay an inference after 401.
        if time.monotonic() >= self.expires_at - 30:
            self.refresh()
        started = time.monotonic()
        payload = {"model": DEFAULT_MODEL, "messages": messages, "stream": stream, "max_tokens": output_tokens,
                   "temperature": 0.3}
        self.chat_request_attempts += 1
        if not stream:
            value = self.json("/chat/completions", payload)
            try:
                choice = value["choices"][0]
                text, reason = choice["message"]["content"], choice["finish_reason"]
                if not isinstance(text, str) or not text.strip() or len(text) > MAX_ANSWER_CHARS or reason not in ("stop", "length"):
                    raise ValueError
                usage = value.get("usage", {})
            except (ValueError, TypeError, KeyError, IndexError):
                raise EvaluationError("invalid_completion") from None
            return {"status": "received", "text": text, "streamed": False, "firstTextMs": None,
                    "totalMs": round((time.monotonic() - started) * 1000), "finishReason": reason,
                    "truncated": reason == "length", "inputTokensReported": _usage(usage, "prompt_tokens"),
                    "outputTokensReported": _usage(usage, "completion_tokens")}
        with self._open("/chat/completions", payload) as response:
            if response.headers.get_content_type() != "text/event-stream":
                raise EvaluationError("non_sse_response")
            return read_stream(response, started, self.timeout)

    def close(self) -> bool:
        if not self.refresh_token:
            return False
        try:
            self.json("/sessions/revoke", {"refresh_token": self.refresh_token}, False)
            return True
        except EvaluationError:
            return False
        finally:
            self.access_token = self.refresh_token = ""


def _usage(usage: Any, field: str) -> int | None:
    value = usage.get(field) if isinstance(usage, dict) else None
    return value if type(value) is int and 0 <= value <= 100_000_000 else None


def read_stream(response, started: float, timeout: int) -> dict:
    text = []
    total = 0
    size = 0
    reason = None
    first = None
    data: list[str] = []
    usage = {}
    while True:
        if time.monotonic() - started > timeout:
            raise EvaluationError("stream_time_budget_exceeded")
        line = response.readline(MAX_EVENT_BYTES + 1)
        total += len(line)
        if len(line) > MAX_EVENT_BYTES or total > MAX_JSON_BYTES:
            raise EvaluationError("stream_byte_budget_exceeded")
        if not line:
            raise EvaluationError("stream_incomplete")
        try:
            line = line.decode("utf-8").rstrip("\r\n")
        except UnicodeError:
            raise EvaluationError("stream_invalid_utf8") from None
        if line.startswith("data:"):
            data.append(line[5:].removeprefix(" "))
            if sum(map(len, data)) > MAX_EVENT_BYTES:
                raise EvaluationError("stream_event_budget_exceeded")
            continue
        if line or not data:
            continue
        raw = "\n".join(data)
        data.clear()
        if raw == "[DONE]":
            if reason is None or not "".join(text).strip():
                raise EvaluationError("stream_empty_or_missing_finish")
            return {"status": "received", "text": "".join(text), "streamed": True,
                    "firstTextMs": first, "totalMs": round((time.monotonic() - started) * 1000),
                    "finishReason": reason, "truncated": reason == "length",
                    "inputTokensReported": _usage(usage, "prompt_tokens"),
                    "outputTokensReported": _usage(usage, "completion_tokens")}
        try:
            event = json.loads(raw)
            if not isinstance(event, dict) or "error" in event:
                raise ValueError
            if isinstance(event.get("usage"), dict):
                usage = event["usage"]
            choices = event["choices"]
            if not isinstance(choices, list):
                raise ValueError
            if not choices:
                continue
            choice = next((c for c in choices if isinstance(c, dict) and c.get("index", 0) == 0), None)
            if choice is None:
                continue
            delta = choice["delta"]
            if not isinstance(delta, dict) or delta.get("tool_calls"):
                raise ValueError
            content = delta.get("content") or delta.get("refusal") or ""
            if not isinstance(content, str) or (reason is not None and content):
                raise ValueError
            content.encode("utf-8")
            if content:
                if first is None:
                    first = round((time.monotonic() - started) * 1000)
                text.append(content)
                size += len(content)
                if size > MAX_ANSWER_CHARS:
                    raise ValueError
            finish = choice.get("finish_reason")
            if finish is not None:
                if finish not in ("stop", "length"):
                    raise ValueError
                reason = finish
        except (ValueError, TypeError, KeyError, UnicodeError, RecursionError):
            raise EvaluationError("stream_invalid_event") from None


def mechanical_checks(case_id: str, turns: list[dict]) -> dict[str, bool]:
    """Transparent narrow observations, never an automatic quality judgement."""
    if not turns or any(t.get("status") != "received" for t in turns):
        return {}
    result = {"allTurnsFinalizedNaturally": all(t.get("finishReason") == "stop" for t in turns)}
    texts = [t["text"].lower() for t in turns]
    if case_id == "math" and len(texts) == 2:
        result["containsExpectedNumericValues"] = bool(re.search(r"(?<!\d)450(?!\d)", texts[0])
            and re.search(r"(?<!\d)2[., ]?600(?!\d)", texts[1]))
    if case_id == "context":
        result["lastTurnContainsInitialNameAndColor"] = "pipa" in texts[-1] and "violeta" in texts[-1]
    if case_id == "vision":
        result["firstTurnMentionsBothColorsAndShapes"] = all(re.search(p, texts[0]) for p in (
            "vermelh", "azul", "quadrad", "c[ií]rculo|redond"))
        if len(texts) == 2:
            result["followupMentionsRightShapeAndColor"] = bool(re.search("c[ií]rculo|redond", texts[1]) and "azul" in texts[1])
    if case_id == "identity" and len(texts) == 2:
        result["userAndAssistantNamesMentioned"] = "teste azul" in texts[0] and "nexus" in texts[1]
    return result


def evaluate(base: str, invitation_path: Path, choices: list[dict], raw: dict, private_output: Path,
             timeout: int, output_tokens: int) -> None:
    client = Client(base, timeout)
    private_created = False
    try:
        # Prove the evidence destination is private/writable before authenticating
        # or spending a single inference request.
        secure_write(private_output, raw, private=True)
        private_created = True
        raw["privateEvidenceCreated"] = True
        client.redeem(read_invitation(invitation_path))
        # Exercise refresh rotation before any paid/model inference.
        client.refresh()
        caps = client.capabilities()
        raw["visionOperatorDeclared"] = caps.get("vision") is True
        raw["sessionRefreshExercised"] = True
        raw["status"] = "running"
        for case in choices:
            row = {"id": case["id"], "status": "running", "requestedTurns": len(case["turns"]), "turns": []}
            raw["cases"].append(row)
            if case["id"] == "vision" and caps.get("vision") is not True:
                row["status"] = "not_run_vision_not_declared"
                continue
            history = [{"role": "system", "content": SYSTEM}]
            for index, prompt in enumerate(case["turns"]):
                history.append({"role": "user", "content": prompt})
                try:
                    answer = client.complete(history, output_tokens, stream=not (case["id"] == "math" and index == 0))
                    answer["prompt"] = prompt
                    row["turns"].append(answer)
                    history.append({"role": "assistant", "content": answer["text"]})
                except EvaluationError as failure:
                    row["turns"].append({"status": "technical_failure", "errorCode": str(failure)})
                    row["status"] = "technical_failure"
                    raw["technicalFailures"] += 1
                    # A failed request is never replayed and no answer is invented.
                    break
                finally:
                    raw["actualInferenceRequests"] = client.chat_request_attempts
                    secure_write(private_output, raw, replace=private_created, private=True)
                    private_created = True
            if row["status"] == "running":
                row["status"] = "completed_for_review"
                row["mechanicalChecks"] = mechanical_checks(case["id"], row["turns"])
            if raw["technicalFailures"]:
                break  # Bound spending when deployment/protocol is failing.
        raw["status"] = "technical_failure" if raw["technicalFailures"] else "completed_for_review"
        if any(r["status"].startswith("not_run") for r in raw["cases"]):
            raw["status"] = "incomplete_capabilities"
    finally:
        raw["sessionRevocationRequested"] = client.close()
        # Session keys are deliberately absent from both report formats.
        if private_created:
            secure_write(private_output, raw, replace=True, private=True)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--endpoint", default=os.environ.get("NEXUS_EVAL_SERVICE_URL", ""))
    parser.add_argument("--invitation-file", type=Path)
    parser.add_argument("--public-output", type=Path, required=True)
    parser.add_argument("--private-output", type=Path, help="New path outside the repository; transcripts stay private")
    parser.add_argument("--cases", default=",".join(CASE_IDS))
    parser.add_argument("--max-requests", type=int, default=32)
    parser.add_argument("--max-output-tokens", type=int, default=512)
    parser.add_argument("--timeout-seconds", type=int, default=120)
    parser.add_argument("--preflight-only", action="store_true")
    args = parser.parse_args(argv)
    started = time.monotonic()
    raw = {"status": "preflight", "reasonCode": None, "cases": [], "technicalFailures": 0,
           "configuredEndpointPresent": bool(args.endpoint), "configuredInvitationPresent": args.invitation_file is not None,
           "actualInferenceRequests": 0, "requestLimit": args.max_requests,
           "maxOutputTokensPerRequest": args.max_output_tokens}
    result = 2
    try:
        choices = selected_cases(args.cases)
        planned = sum(len(case["turns"]) for case in choices)
        raw["plannedRequests"] = planned
        raw["maximumRequestedOutputTokens"] = planned * args.max_output_tokens
        if not (1 <= args.max_requests <= 64 and 128 <= args.max_output_tokens <= 2048
                and 1 <= args.timeout_seconds <= 300 and planned <= args.max_requests):
            raise EvaluationError("invalid_or_exceeded_request_budget")
        if not args.endpoint or args.invitation_file is None:
            raw.update(status="not_run_unconfigured", reasonCode="deployment_url_and_private_invitation_required")
        else:
            endpoint(args.endpoint)
            read_invitation(args.invitation_file)
            if args.preflight_only:
                raw.update(status="not_run_preflight_only", reasonCode="no_network_or_model_request_executed")
            else:
                if args.private_output is None or args.private_output.exists() or args.private_output.resolve().is_relative_to(ROOT):
                    raise EvaluationError("new_private_output_outside_repository_required")
                evaluate(args.endpoint, args.invitation_file, choices, raw, args.private_output,
                         args.timeout_seconds, args.max_output_tokens)
                result = 0 if raw["status"] == "completed_for_review" else 1
    except EvaluationError as failure:
        raw.update(status="blocked_or_technical_failure", reasonCode=str(failure))
        result = 1
    except (OSError, ValueError):
        raw.update(status="blocked_or_technical_failure", reasonCode="local_io_or_configuration_error")
        result = 1
    finally:
        raw["elapsedMs"] = round((time.monotonic() - started) * 1000)
        if raw.get("privateEvidenceCreated"):
            try:
                secure_write(args.private_output, raw, replace=True, private=True)
            except (EvaluationError, OSError, ValueError):
                raw.update(status="blocked_or_technical_failure", reasonCode="private_evidence_write_failed")
                result = 1
    try:
        secure_write(args.public_output, public_report(raw))
    except (EvaluationError, OSError, ValueError):
        print("Technical report could not be written; no credentials or transcript printed.")
        return 1
    print(json.dumps({"status": raw["status"], "actualInferenceRequests": raw["actualInferenceRequests"],
                      "semanticQuality": "not_assessed_independent_review_required", "qualityApproved": False}))
    return result


if __name__ == "__main__":
    raise SystemExit(main())
