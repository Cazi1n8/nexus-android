#!/usr/bin/env python3
"""Run synthetic prompt A/B through the real NEXUS C++ engine, not Android.

The developer supplies pinned model data and prompt-only fixtures. No network,
Gradle, app installation, response replacement or semantic pass claim occurs.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile


def sha256(path: Path) -> str:
    with path.open("rb") as source:
        return hashlib.file_digest(source, "sha256").hexdigest()


def validate_fixture(fixture: dict) -> None:
    if not isinstance(fixture, dict):
        raise ValueError("A fixture must be an object")
    if fixture.get("contextTokens", 2048) not in (512, 1024, 2048, 4096):
        raise ValueError("Use a context supported by the app")
    if type(fixture.get("threads", 2)) is not int or fixture.get("threads", 2) not in range(1, 5):
        raise ValueError("Use 1..4 CPU threads")
    output = fixture.get("maxOutputTokens", 384)
    if type(output) is not int or not 1 <= output <= 1024 or output >= fixture.get("contextTokens", 2048) - 64:
        raise ValueError("Output must fit the app's supported token budget")
    variants, cases = fixture.get("variants"), fixture.get("cases")
    if not isinstance(variants, list) or not 1 <= len(variants) <= 8:
        raise ValueError("Supply 1..8 prompt variants")
    if not isinstance(cases, list) or not 1 <= len(cases) <= 64:
        raise ValueError("Supply 1..64 synthetic cases")
    ids = set()
    for variant in variants:
        if not isinstance(variant, dict) or not isinstance(variant.get("id"), str) or not variant["id"] or variant["id"] in ids:
            raise ValueError("Variant IDs must be unique nonempty strings")
        ids.add(variant["id"])
        if not isinstance(variant.get("system"), str) or not 1 <= len(variant["system"]) <= 16000:
            raise ValueError("Variant must have a bounded system prompt")
    ids.clear()
    for case in cases:
        if not isinstance(case, dict) or not isinstance(case.get("id"), str) or not case["id"] or case["id"] in ids:
            raise ValueError("Case IDs must be unique nonempty strings")
        ids.add(case["id"])
        turns = case.get("turns")
        if not isinstance(turns, list) or not 1 <= len(turns) <= 24:
            raise ValueError("Supply 1..24 user turns per case")
        for turn in turns:
            if isinstance(turn, str):
                query = turn
            elif isinstance(turn, dict) and set(turn) <= {"query", "system"}:
                query = turn.get("query")
                if "system" in turn and (not isinstance(turn["system"], str) or not 1 <= len(turn["system"]) <= 16000):
                    raise ValueError("Turn system override must be bounded text")
            else:
                raise ValueError("Turns contain user prompts only, not assistant answers")
            if not isinstance(query, str) or not 1 <= len(query) <= 16000 or "\0" in query:
                raise ValueError("User query must be bounded nonempty text")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--model", required=True, type=Path)
    parser.add_argument("--model-sha256", required=True)
    parser.add_argument("--fixture", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--upstream", type=Path, default=Path("/workspace/tooling/llama.cpp"))
    parser.add_argument("--runtime", type=Path, default=Path("/workspace/tooling/ai-smoke/runtime/llama-b11838"))
    parser.add_argument("--timeout", type=int, default=1800)
    args = parser.parse_args()
    fixture = json.loads(args.fixture.read_text(encoding="utf-8"))
    validate_fixture(fixture)
    digest = sha256(args.model)
    if digest != args.model_sha256.lower():
        raise ValueError("Model SHA-256 does not match the explicitly pinned weight")
    root = Path(__file__).resolve().parents[1]
    cpp = root / "app/src/main/cpp"
    harness = Path(__file__).with_name("LocalAiVariantEvaluation.cpp")
    compiler = shutil.which("g++")
    if not compiler or args.timeout not in range(1, 7201):
        raise ValueError("A C++17 compiler and a timeout within 1..7200s are required")
    args.output.parent.mkdir(parents=True, exist_ok=True)
    output = args.output.resolve()
    runtime = args.runtime.resolve()
    environment = os.environ.copy()
    environment["LD_LIBRARY_PATH"] = str(runtime) + os.pathsep + environment.get("LD_LIBRARY_PATH", "")
    with tempfile.TemporaryDirectory(prefix="nexus-real-variant-eval-") as temporary:
        executable = Path(temporary) / "evaluate"
        subprocess.run([compiler, "-std=c++17", "-O3", "-pthread", "-I" + str(cpp),
            "-I" + str(args.upstream / "include"), "-I" + str(args.upstream / "ggml/include"),
            "-I" + str(args.upstream / "vendor"), str(harness), str(cpp / "nexus_runtime.cpp"),
            "-L" + str(runtime), "-Wl,-rpath," + str(runtime), "-lllama", "-lggml", "-lggml-base",
            "-o", str(executable)], check=True)
        completed = subprocess.run([str(executable), str(args.model.resolve()), str(args.fixture.resolve()),
            str(output), str(runtime)], env=environment, cwd=runtime, check=False, timeout=args.timeout)
    result = json.loads(output.read_text(encoding="utf-8"))
    result["provenance"] = {
        "modelSha256": digest, "modelBytes": args.model.stat().st_size,
        "fixtureSha256": sha256(args.fixture),
        "sourcesSha256": {str(path.relative_to(root)): sha256(path) for path in
            (cpp / "nexus_runtime.cpp", cpp / "nexus_runtime.hpp", cpp / "nexus_chat_template.hpp", harness, Path(__file__).resolve())},
        "runtimeLlamaSha256": sha256(runtime / "libllama.so.0.7.0"),
    }
    result["semanticApproval"] = "not_assessed_by_this_runner; inspect actual text and dedicated rubric"
    output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return completed.returncode


if __name__ == "__main__":
    raise SystemExit(main())
