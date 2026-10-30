"""Bounded online inference and real tools; no provider credentials or canned AI.

The provider must offer OpenAI-compatible SSE, including finish_reason and [DONE].
Only calculator calls execute here. Android actions remain validated proposals that
must be confirmed and executed on the device. Provider thought signatures needed
for calculator continuations remain in request-local memory, never in client SSE.
"""
from __future__ import annotations

import copy
import json
import math
import re
import time
from dataclasses import dataclass, field
from typing import Any, Callable, Iterator
from urllib import request

MAX_EVENT_BYTES = 64 * 1024
MAX_STREAM_BYTES = 1024 * 1024
MAX_ANSWER_CHARACTERS = 24_000
MAX_ARGUMENT_CHARACTERS = 2_048
MAX_CALCULATIONS = 2
ROUTES = ("home", "chat", "voice", "vision", "mouse", "hands", "cube", "libras",
          "commands", "control", "personality", "memory", "settings", "diagnostics")
PACKAGE = re.compile(r"[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z][A-Za-z0-9_]*)+", re.ASCII)
NUMBER = re.compile(r"(?:[0-9]+(?:\.[0-9]*)?|\.[0-9]+)", re.ASCII)


class OnlineToolError(ValueError):
    """Safe technical error; never contains input, generated text or credentials."""


def _schema(name: str, description: str, properties: dict, required: list[str]) -> dict:
    return {"type": "function", "function": {"name": name, "description": description,
        "parameters": {"type": "object", "properties": properties,
                       "required": required, "additionalProperties": False}}}


ACTION_SCHEMAS = {
    "open_settings": _schema("open_settings", "Propor abrir configurações do Android; o usuário confirma antes de executar.", {}, []),
    "open_app": _schema("open_app", "Propor abrir um aplicativo instalado; use somente um identificador conhecido, nunca inventado. O usuário confirma.",
                        {"packageName": {"type": "string"}}, ["packageName"]),
    "navigate": _schema("navigate", "Propor abrir uma tela real do NEXUS; o usuário confirma antes de executar.",
                        {"route": {"type": "string", "enum": list(ROUTES)}}, ["route"]),
}
CALCULATOR_SCHEMA = _schema("calculate", "Calcular expressão numérica com precedência real. Use para cálculos; operadores +, -, *, /, ^ e parênteses. Não invente o resultado.",
                            {"expression": {"type": "string", "maxLength": 256}}, ["expression"])


def canonical_tools(client_tools: Any = None, tool_choice: Any = "auto") -> list[dict]:
    """Accept only app allowlisted tool names; discard all client-written schemas."""
    if tool_choice != "auto":
        raise OnlineToolError("Only automatic tool choice is supported")
    if client_tools is None:
        client_tools = []
    if not isinstance(client_tools, list) or len(client_tools) > len(ACTION_SCHEMAS):
        raise OnlineToolError("Invalid app tool list")
    names = []
    for item in client_tools:
        if not isinstance(item, dict) or item.get("type") != "function":
            raise OnlineToolError("Invalid app tool")
        function = item.get("function")
        name = function.get("name") if isinstance(function, dict) else None
        if not isinstance(name, str) or name not in ACTION_SCHEMAS or name in names:
            raise OnlineToolError("Unsupported or duplicate app tool")
        names.append(name)
    return copy.deepcopy([ACTION_SCHEMAS[name] for name in names] + [CALCULATOR_SCHEMA])


class _Expression:
    """Recursive-descent arithmetic, bounded depth/operations/numbers; never eval."""
    def __init__(self, value: str):
        if not isinstance(value, str) or not 1 <= len(value) <= 256:
            raise OnlineToolError("Invalid arithmetic expression length")
        self.value = value.translate(str.maketrans({"×": "*", "÷": "/", "−": "-"}))
        if any(c not in "0123456789.+-*/^() \t" for c in self.value):
            raise OnlineToolError("Unsupported arithmetic syntax")
        self.at = 0
        self.depth = 0
        self.operations = 0

    def _skip(self):
        while self.at < len(self.value) and self.value[self.at] in " \t":
            self.at += 1

    def take(self, character: str) -> bool:
        self._skip()
        if self.value.startswith(character, self.at):
            self.at += len(character)
            return True
        return False

    def bounded(self, value: float) -> float:
        self.operations += 1
        if self.operations > 128 or not math.isfinite(value) or abs(value) > 1e100:
            raise OnlineToolError("Arithmetic budget exceeded")
        return value

    def expression(self) -> float:
        value = self.term()
        while True:
            if self.take("+"):
                value = self.bounded(value + self.term())
            elif self.take("-"):
                value = self.bounded(value - self.term())
            else:
                return value

    def term(self) -> float:
        value = self.unary()
        while True:
            if self.take("*"):
                value = self.bounded(value * self.unary())
            elif self.take("/"):
                divisor = self.unary()
                if divisor == 0:
                    raise OnlineToolError("Division by zero")
                value = self.bounded(value / divisor)
            else:
                return value

    def unary(self) -> float:
        self.depth += 1
        if self.depth > 24:
            raise OnlineToolError("Arithmetic nesting exceeded")
        try:
            if self.take("+"):
                return self.unary()
            if self.take("-"):
                return self.bounded(-self.unary())
            return self.power()
        finally:
            self.depth -= 1

    def power(self) -> float:
        base = self.atom()
        if self.take("^"):
            exponent = self.unary()  # Right-associative, mathematical unary minus.
            if abs(exponent) > 100 or (base == 0 and exponent < 0):
                raise OnlineToolError("Power outside arithmetic budget")
            if base < 0 and not exponent.is_integer():
                raise OnlineToolError("Complex arithmetic is unsupported")
            try:
                base = self.bounded(math.pow(base, exponent))
            except (OverflowError, ValueError):
                raise OnlineToolError("Power outside arithmetic budget") from None
        return base

    def atom(self) -> float:
        if self.take("("):
            self.depth += 1
            if self.depth > 24:
                raise OnlineToolError("Arithmetic nesting exceeded")
            try:
                value = self.expression()
                if not self.take(")"):
                    raise OnlineToolError("Unclosed arithmetic parentheses")
                return value
            finally:
                self.depth -= 1
        self._skip()
        number = NUMBER.match(self.value, self.at)
        if number is None or len(number.group()) > 32:
            raise OnlineToolError("Invalid arithmetic number")
        self.at = number.end()
        return self.bounded(float(number.group()))

    def result(self) -> str:
        value = self.expression()
        self._skip()
        if self.at != len(self.value):
            raise OnlineToolError("Unexpected arithmetic syntax")
        # IEEE754 precision is explicit in calculator tool metadata and docs.
        if value == 0:
            return "0"
        return str(int(value)) if value.is_integer() else format(value, ".15g")


def calculate(expression: str) -> dict:
    """Actual arithmetic result, with finite double precision (not symbolic math)."""
    return {"result": _Expression(expression).result(), "precision": "IEEE754 binary64, formatted to at most 15 significant digits"}


def validate_call(name: str, arguments: Any, allowed_actions: set[str]) -> dict:
    if not isinstance(arguments, dict):
        raise OnlineToolError("Invalid tool arguments")
    if name == "calculate":
        if set(arguments) != {"expression"} or not isinstance(arguments["expression"], str):
            raise OnlineToolError("Invalid calculator arguments")
        if not 1 <= len(arguments["expression"]) <= 256:
            raise OnlineToolError("Invalid calculator expression length")
    elif name not in allowed_actions:
        raise OnlineToolError("Unadvertised action tool")
    elif name == "open_settings":
        if arguments:
            raise OnlineToolError("Invalid settings arguments")
    elif name == "open_app":
        package = arguments.get("packageName")
        if set(arguments) != {"packageName"} or not isinstance(package, str) or len(package) > 200 or not PACKAGE.fullmatch(package):
            raise OnlineToolError("Invalid app arguments")
    elif name == "navigate":
        route = arguments.get("route")
        if set(arguments) != {"route"} or not isinstance(route, str) or route not in ROUTES:
            raise OnlineToolError("Invalid navigation arguments")
    else:
        raise OnlineToolError("Unsupported tool")
    return arguments


def _json(value: Any) -> str:
    return json.dumps(value, ensure_ascii=True, separators=(",", ":"), allow_nan=False)


def _usage(raw: Any) -> dict:
    if not isinstance(raw, dict):
        return {}
    return {name: raw[name] for name in ("prompt_tokens", "completion_tokens", "total_tokens")
            if type(raw.get(name)) is int and 0 <= raw[name] <= 10_000_000}


def _events(response, timeout_seconds: int) -> Iterator[dict | None]:
    """Read bounded SSE; None means the provider's real completion marker."""
    total = 0
    data: list[str] = []
    started = time.monotonic()
    while True:
        if time.monotonic() - started > timeout_seconds:
            raise OnlineToolError("Online stream time budget exceeded")
        line = response.readline(MAX_EVENT_BYTES + 1)
        total += len(line)
        if len(line) > MAX_EVENT_BYTES or total > MAX_STREAM_BYTES:
            raise OnlineToolError("Online stream byte budget exceeded")
        if not line:
            raise OnlineToolError("Online stream closed before completion")
        try:
            line = line.decode("utf-8").rstrip("\r\n")
        except UnicodeError:
            raise OnlineToolError("Invalid online stream encoding") from None
        if line.startswith("data:"):
            data.append(line[5:].removeprefix(" "))
            if sum(len(p.encode("utf-8")) for p in data) > MAX_EVENT_BYTES:
                raise OnlineToolError("Online event byte budget exceeded")
            continue
        if line or not data:
            continue
        raw = "\n".join(data)
        data.clear()
        if raw == "[DONE]":
            yield None
            return
        try:
            value = json.loads(raw)
            if not isinstance(value, dict) or "error" in value:
                raise OnlineToolError("Online provider error")
            yield value
        except (ValueError, TypeError, RecursionError) as error:
            if isinstance(error, OnlineToolError):
                raise
            raise OnlineToolError("Malformed online stream event") from None


@dataclass
class _ToolBuffer:
    name: str = ""
    arguments: str = ""
    identifier: str = ""
    extra_content: dict = field(default_factory=dict)
    function_extra_content: dict = field(default_factory=dict)

    def append(self, value: Any):
        if not isinstance(value, dict) or value.get("index", 0) != 0:
            raise OnlineToolError("Parallel tool calls are unsupported")
        if "type" in value and value["type"] != "function":
            raise OnlineToolError("Invalid online tool type")
        identifier = value.get("id")
        if identifier is not None:
            if not isinstance(identifier, str) or (self.identifier and self.identifier != identifier):
                raise OnlineToolError("Invalid online tool identifier")
            self.identifier = identifier
        function = value.get("function", {})
        if not isinstance(function, dict):
            raise OnlineToolError("Invalid online tool function")
        for key in ("name", "arguments"):
            piece = function.get(key)
            if piece is not None:
                if not isinstance(piece, str):
                    raise OnlineToolError("Invalid online tool fragment")
                setattr(self, key, getattr(self, key) + piece)
        if len(self.name) > 100 or len(self.arguments) > MAX_ARGUMENT_CHARACTERS or len(self.identifier) > 200:
            raise OnlineToolError("Online tool argument budget exceeded")
        # Gemini's OpenAI compatibility layer attaches thought signatures in
        # extra_content. Retain these solely for its immediate tool continuation.
        for source, attribute in ((value, "extra_content"), (function, "function_extra_content")):
            extra = source.get("extra_content")
            if extra is not None:
                if not isinstance(extra, dict) or len(_json(extra)) > 16_384:
                    raise OnlineToolError("Invalid private tool continuation metadata")
                existing = getattr(self, attribute)
                if existing and existing != extra:
                    raise OnlineToolError("Conflicting private tool continuation metadata")
                setattr(self, attribute, copy.deepcopy(extra))

    def call(self, allowed_actions: set[str]) -> dict:
        try:
            arguments = json.loads(self.arguments)
        except (ValueError, TypeError, RecursionError):
            raise OnlineToolError("Incomplete online tool arguments") from None
        validate_call(self.name, arguments, allowed_actions)
        if not self.identifier or any(ord(c) < 33 or ord(c) > 126 for c in self.identifier):
            raise OnlineToolError("Missing or invalid online tool identifier")
        return {"id": self.identifier, "type": "function", "function": {"name": self.name, "arguments": _json(arguments)}}

    def internal_call(self, allowed_actions: set[str]) -> dict:
        value = self.call(allowed_actions)
        if self.extra_content:
            value["extra_content"] = self.extra_content
        if self.function_extra_content:
            value["function"]["extra_content"] = self.function_extra_content
        return value


def stream_online(opener, url: str, headers: dict[str, str], forward: dict,
                  timeout_seconds: int, on_request: Callable[[dict], None] | None = None) -> Iterator[str]:
    """Stream actual provider deltas, with at most two real calculator continuations.

    ``forward`` is the already validated request, with canonical_tools installed.
    ``on_request`` reserves quota before each upstream round, including calculator
    continuations. No tool call can access Android, shell, URLs or private files.
    """
    payload = copy.deepcopy(forward)
    payload["stream"] = True
    payload["stream_options"] = {"include_usage": True}
    tools = payload.get("tools", [])
    if not isinstance(tools, list):
        raise OnlineToolError("Invalid canonical tool list")
    names = {t.get("function", {}).get("name") for t in tools if isinstance(t, dict)}
    if not names <= set(ACTION_SCHEMAS) | {"calculate"}:
        raise OnlineToolError("Unsupported canonical tool")
    allowed = names & set(ACTION_SCHEMAS)
    used_calculations = 0
    answer_size = 0
    usage_total: dict[str, int] = {}
    budget_started = time.monotonic()
    while True:
        if time.monotonic() - budget_started > timeout_seconds:
            raise OnlineToolError("Online inference total time budget exceeded")
        if on_request is not None:
            on_request(payload)
        req_headers = dict(headers)
        req_headers.update({"Content-Type": "application/json", "Accept": "text/event-stream"})
        req = request.Request(url, data=_json(payload).encode("utf-8"), headers=req_headers, method="POST")
        reason = None
        tool: _ToolBuffer | None = None
        round_content: list[str] = []
        round_usage: dict = {}
        remaining = max(0.001, timeout_seconds - (time.monotonic() - budget_started))
        with opener.open(req, timeout=remaining) as response:
            if response.headers.get_content_type() != "text/event-stream":
                raise OnlineToolError("Online provider does not support actual SSE")
            for event in _events(response, timeout_seconds):
                if time.monotonic() - budget_started > timeout_seconds:
                    raise OnlineToolError("Online inference total time budget exceeded")
                if event is None:
                    if reason is None:
                        raise OnlineToolError("Online stream lacks finish reason")
                    break
                if "usage" in event:
                    round_usage = _usage(event["usage"])
                choices = event.get("choices")
                if not isinstance(choices, list):
                    raise OnlineToolError("Invalid online choices")
                if not choices:
                    continue
                if len(choices) != 1 or not isinstance(choices[0], dict) or choices[0].get("index", 0) != 0:
                    raise OnlineToolError("Parallel online choices are unsupported")
                choice = choices[0]
                delta = choice.get("delta")
                if not isinstance(delta, dict):
                    raise OnlineToolError("Invalid online delta")
                content = delta.get("content")
                if content is None:
                    content = delta.get("refusal") or ""
                if not isinstance(content, str):
                    raise OnlineToolError("Invalid online text delta")
                try:
                    content.encode("utf-8")
                except UnicodeError:
                    raise OnlineToolError("Invalid online text encoding") from None
                calls = delta.get("tool_calls")
                if reason is not None and (content or calls):
                    raise OnlineToolError("Online content after finish")
                if content:
                    answer_size += len(content)
                    if answer_size > MAX_ANSWER_CHARACTERS:
                        raise OnlineToolError("Online answer budget exceeded")
                    round_content.append(content)
                    yield _json({"choices": [{"index": 0, "delta": {"content": content}, "finish_reason": None}]})
                if calls is not None:
                    if not isinstance(calls, list) or len(calls) > 1:
                        raise OnlineToolError("Parallel online tool calls are unsupported")
                    if calls:
                        if tool is None:
                            tool = _ToolBuffer()
                        tool.append(calls[0])
                finish = choice.get("finish_reason")
                if finish is not None:
                    if reason is not None or finish not in ("stop", "length", "tool_calls"):
                        raise OnlineToolError("Invalid online finish reason")
                    reason = finish
        for key, value in round_usage.items():
            usage_total[key] = usage_total.get(key, 0) + value
            if usage_total[key] > 10_000_000:
                raise OnlineToolError("Online usage budget exceeded")
        if tool is None:
            if reason not in ("stop", "length"):
                raise OnlineToolError("Missing online tool call")
            if not "".join(round_content).strip():
                raise OnlineToolError("Online provider returned empty text")
            yield _json({"choices": [{"index": 0, "delta": {}, "finish_reason": reason}]})
            if usage_total:
                yield _json({"choices": [], "usage": usage_total})
            yield "[DONE]"
            return
        if reason != "tool_calls":
            raise OnlineToolError("Incomplete online tool call")
        call = tool.call(allowed)
        if tool.name != "calculate":
            # Keep provider signatures, arbitrary metadata and IDs out of the APK.
            call["id"] = "nexus-action-0"
            call["index"] = 0
            yield _json({"choices": [{"index": 0, "delta": {"tool_calls": [call]}, "finish_reason": None}]})
            yield _json({"choices": [{"index": 0, "delta": {}, "finish_reason": "tool_calls"}]})
            if usage_total:
                yield _json({"choices": [], "usage": usage_total})
            yield "[DONE]"
            return
        used_calculations += 1
        if used_calculations > MAX_CALCULATIONS:
            raise OnlineToolError("Online calculator round budget exceeded")
        arguments = json.loads(tool.arguments)
        try:
            result = calculate(arguments["expression"])
        except OnlineToolError:
            # A real tool failure is supplied as evidence, never a fabricated
            # numerical answer. The model may explain it in the bounded next round.
            result = {"error": "unsupported_or_invalid_expression"}
        payload["messages"].append({"role": "assistant", "content": "".join(round_content) or None,
                                    "tool_calls": [tool.internal_call(allowed)]})
        payload["messages"].append({"role": "tool", "tool_call_id": call["id"], "content": _json(result)})


def complete_online(opener, url: str, headers: dict[str, str], forward: dict,
                    timeout_seconds: int, on_request: Callable[[dict], None] | None = None) -> dict:
    """Collect the same real SSE engine for clients requesting final JSON."""
    fragments = []
    call = None
    reason = None
    usage = {}
    for raw in stream_online(opener, url, headers, forward, timeout_seconds, on_request):
        if raw == "[DONE]":
            break
        event = json.loads(raw)
        usage = event.get("usage", usage)
        if not event["choices"]:
            continue
        choice = event["choices"][0]
        delta = choice["delta"]
        if delta.get("content"):
            fragments.append(delta["content"])
        if delta.get("tool_calls"):
            call = {k: v for k, v in delta["tool_calls"][0].items() if k != "index"}
        reason = choice.get("finish_reason") or reason
    message: dict[str, Any] = {"role": "assistant", "content": "".join(fragments)}
    if call:
        message["tool_calls"] = [call]
    return {"object": "chat.completion", "choices": [{"index": 0, "message": message, "finish_reason": reason}], "usage": usage}
