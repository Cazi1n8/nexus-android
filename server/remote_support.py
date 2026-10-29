"""Bounded protocol helpers. Search destinations are operator configuration only."""
from __future__ import annotations

import json
import time
from urllib import parse, request

MAX_EVENT_BYTES = 64 * 1024
MAX_STREAM_BYTES = 1024 * 1024
MAX_SEARCH_BYTES = 512 * 1024


class RemoteProtocolError(ValueError):
    pass


def sse_chunks(response, timeout_seconds: int):
    """Yield sanitized OpenAI text chunks, never forwarding arbitrary upstream data."""
    total = 0
    text_size = 0
    reason = None
    started = time.monotonic()
    data = []
    while True:
        if time.monotonic() - started > timeout_seconds:
            raise RemoteProtocolError("Stream exceeded time budget")
        line = response.readline(MAX_EVENT_BYTES + 1)
        total += len(line)
        if len(line) > MAX_EVENT_BYTES or total > MAX_STREAM_BYTES:
            raise RemoteProtocolError("Stream exceeded byte budget")
        if not line:
            raise RemoteProtocolError("Stream closed without completion marker")
        try:
            line = line.decode("utf-8").rstrip("\r\n")
        except UnicodeError:
            raise RemoteProtocolError("Invalid stream encoding") from None
        if line.startswith("data:"):
            data.append(line[5:].removeprefix(" "))
            if sum(map(len, data)) > MAX_EVENT_BYTES:
                raise RemoteProtocolError("Event exceeded budget")
            continue
        if line or not data:
            continue
        value = "\n".join(data)
        data.clear()
        if value == "[DONE]":
            if reason is None:
                raise RemoteProtocolError("Missing finish reason")
            yield "[DONE]"
            return
        try:
            event = json.loads(value)
            if not isinstance(event, dict) or "error" in event:
                raise RemoteProtocolError("Upstream error")
            choices = event.get("choices")
            if not isinstance(choices, list):
                raise RemoteProtocolError("Invalid choices")
            if not choices:
                # Usage events contain no answer text and may include private metadata.
                continue
            choice = next((c for c in choices if isinstance(c, dict) and c.get("index", 0) == 0), None)
            if choice is None:
                continue
            delta = choice.get("delta")
            if not isinstance(delta, dict) or delta.get("tool_calls"):
                raise RemoteProtocolError("Unsupported stream action")
            content = delta.get("content")
            if content is None:
                content = delta.get("refusal", "") or ""
            if not isinstance(content, str):
                raise RemoteProtocolError("Invalid text delta")
            content.encode("utf-8")
            if content and reason is not None:
                raise RemoteProtocolError("Content after stop")
            text_size += len(content)
            if text_size > 24_000:
                raise RemoteProtocolError("Answer exceeded budget")
            finish = choice.get("finish_reason")
            if finish is not None:
                if finish not in ("stop", "length"):
                    raise RemoteProtocolError("Unsupported finish reason")
                reason = finish
            yield json.dumps({"choices": [{"index": 0, "delta": {"content": content},
                                          "finish_reason": finish}]}, ensure_ascii=True, separators=(",", ":"))
        except (ValueError, TypeError, UnicodeError, RecursionError) as exc:
            if isinstance(exc, RemoteProtocolError):
                raise
            raise RemoteProtocolError("Malformed stream") from None


def search_searxng(opener, base_url: str, query: str, timeout: int):
    """SearXNG JSON API: at most five text snippets; never fetch result URLs."""
    url = base_url.rstrip("/") + "/search?" + parse.urlencode({
        "q": query, "format": "json", "language": "pt-BR", "safesearch": "1",
    })
    req = request.Request(url, headers={"Accept": "application/json", "User-Agent": "NEXUS-Gateway/4.1"})
    with opener.open(req, timeout=min(timeout, 15)) as response:
        raw = response.read(MAX_SEARCH_BYTES + 1)
    if len(raw) > MAX_SEARCH_BYTES:
        raise RemoteProtocolError("Search response too large")
    try:
        value = json.loads(raw.decode("utf-8"))
    except (ValueError, UnicodeError, RecursionError):
        raise RemoteProtocolError("Invalid search response") from None
    if not isinstance(value, dict) or not isinstance(value.get("results"), list):
        raise RemoteProtocolError("Invalid search results")
    results = []
    seen = set()
    for item in value["results"]:
        if not isinstance(item, dict):
            continue
        url = item.get("url")
        title, content = item.get("title"), item.get("content", "")
        if not all(isinstance(v, str) for v in (url, title, content)):
            continue
        try:
            parsed = parse.urlsplit(url)
            parsed.port
            # Sources are displayed, never requested by this service.
            if (parsed.scheme not in ("https", "http") or not parsed.hostname or parsed.username
                    or parsed.password or len(url) > 2_000 or any(ord(c) < 32 for c in url)):
                continue
            (url + title + content).encode("utf-8")
        except (ValueError, UnicodeError):
            continue
        if url in seen:
            continue
        seen.add(url)
        results.append({"title": title[:250], "url": url, "snippet": content[:1_500]})
        if len(results) == 5:
            break
    return results
