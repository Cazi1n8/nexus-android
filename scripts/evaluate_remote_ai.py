#!/usr/bin/env python3
"""Compile production Kotlin HTTP transport and evaluate an actual loopback model.

No Gradle, server startup, model download or prepared assistant answers. Session
credentials come only from NEXUS_EVAL_SESSION_TOKEN in the private environment.
This host test is not an Android hardware test or automatic semantic approval.
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
from urllib.parse import urlsplit


def digest(path: Path) -> str:
    with path.open("rb") as source:
        return hashlib.file_digest(source, "sha256").hexdigest()


def validate_endpoint(value: str) -> None:
    parsed = urlsplit(value)
    if (parsed.scheme not in ("http", "https") or parsed.hostname not in ("localhost", "127.0.0.1", "::1")
            or parsed.username or parsed.password or parsed.query or parsed.fragment):
        raise ValueError("Only an explicit loopback developer endpoint is allowed")
    parsed.port


def validate_fixture(value: dict) -> None:
    if not isinstance(value, dict) or set(value) - {"cases", "userPreferredName", "personality", "detail", "memories"}:
        raise ValueError("Fixture contains prompt settings only")
    cases = value.get("cases")
    if not isinstance(cases, list) or not 1 <= len(cases) <= 32:
        raise ValueError("Use 1..32 prompt-only cases")
    seen = set()
    for case in cases:
        if not isinstance(case, dict) or set(case) != {"id", "turns"} or not isinstance(case["id"], str) or not case["id"] or case["id"] in seen:
            raise ValueError("Cases need unique IDs and user turns")
        seen.add(case["id"])
        if not isinstance(case["turns"], list) or not 1 <= len(case["turns"]) <= 24:
            raise ValueError("Use 1..24 user turns")
        for turn in case["turns"]:
            if not isinstance(turn, dict) or set(turn) - {"text", "imagePath", "searchQuery"}:
                raise ValueError("Turns may not supply prepared assistant answers")
            if not isinstance(turn.get("text"), str) or not 1 <= len(turn["text"]) <= 16_000 or "\0" in turn["text"]:
                raise ValueError("Bounded user text is required")
            for key in ("imagePath", "searchQuery"):
                if key in turn and not isinstance(turn[key], str):
                    raise ValueError("Image paths and search queries must be text")


def jar(cache: Path, group: str, artifact: str, version: str) -> Path:
    matches = sorted((cache / group / artifact / version).glob("*/*.jar"))
    if len(matches) != 1:
        raise ValueError(f"Run normal dependency resolution first; expected cached {group}:{artifact}:{version}")
    return matches[0]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--endpoint", required=True)
    parser.add_argument("--model", required=True)
    parser.add_argument("--fixture", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--gradle-cache", type=Path)
    parser.add_argument("--java", default=shutil.which("java"))
    parser.add_argument("--timeout", type=int, default=1800)
    parser.add_argument("--compile-only", action="store_true", help="Compile actual transport without contacting a model")
    args = parser.parse_args()
    validate_endpoint(args.endpoint)
    if not args.java or not 1 <= args.timeout <= 7200 or not 1 <= len(args.model) <= 200:
        raise ValueError("A JDK, bounded model name and timeout are required")
    if not args.compile_only and not os.environ.get("NEXUS_EVAL_SESSION_TOKEN"):
        raise ValueError("Set NEXUS_EVAL_SESSION_TOKEN privately; no token command-line argument is accepted")
    if args.output.exists() and not args.compile_only:
        raise ValueError("Choose a new private report path; existing evaluation evidence is never overwritten")
    if args.fixture.stat().st_size > 128_000:
        raise ValueError("Fixture too large")
    validate_fixture(json.loads(args.fixture.read_text(encoding="utf-8")))
    root = Path(__file__).resolve().parents[1]
    cache_root = args.gradle_cache or Path(os.environ.get("GRADLE_USER_HOME", root / ".gradle-dist/cache"))
    cache = cache_root / "caches/modules-2/files-2.1"
    kotlin = jar(cache, "org.jetbrains.kotlin", "kotlin-stdlib", "2.1.20")
    coroutines = jar(cache, "org.jetbrains.kotlinx", "kotlinx-coroutines-core-jvm", "1.10.2")
    annotation = jar(cache, "org.jetbrains", "annotations", "13.0")
    runtime = [kotlin, coroutines, annotation,
        jar(cache, "com.squareup.okhttp3", "okhttp", "4.12.0"),
        jar(cache, "com.squareup.okio", "okio-jvm", "3.6.0"),
        jar(cache, "org.json", "json", "20240303")]
    compiler = [jar(cache, "org.jetbrains.kotlin", "kotlin-compiler-embeddable", "2.1.20"), kotlin,
        jar(cache, "org.jetbrains.kotlin", "kotlin-script-runtime", "2.1.20"),
        jar(cache, "org.jetbrains.kotlin", "kotlin-reflect", "1.6.10"),
        jar(cache, "org.jetbrains.intellij.deps", "trove4j", "1.0.20200330"), coroutines, annotation]
    main_source = root / "app/src/main/java/br/com/nexus/assistant"
    sources = [main_source / p for p in (
        "data/AiProvider.kt", "data/RemoteStreaming.kt", "data/GatewayCapabilities.kt", "data/AssistantIdentity.kt",
        "data/NexusSettings.kt", "data/AiCompletion.kt", "commands/ActionProposal.kt", "data/local/LocalModelCatalog.kt")]
    sources += [Path(__file__).with_name("RemoteAiEvaluation.kt")]
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="nexus-real-remote-eval-") as temporary:
        temp = Path(temporary)
        stub = temp / "BuildConfig.kt"
        # Compile-time host adapter only. Never added to the Android app source,
        # APK, network security config or production default endpoint.
        stub.write_text("package br.com.nexus.assistant\nobject BuildConfig { const val DEBUG = true }\n")
        classes = temp / "classes"
        subprocess.run([args.java, "-Xmx768m", "-cp", os.pathsep.join(map(str, compiler)),
            "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler", "-no-stdlib", "-no-reflect", "-jvm-target", "17",
            "-classpath", os.pathsep.join(map(str, runtime)), "-d", str(classes), *map(str, sources), str(stub)],
            check=True, timeout=120)
        if args.compile_only:
            print("Production Kotlin remote transport compiled for host evaluation; no model request executed")
            return 0
        result = subprocess.run([args.java, "-Xmx768m", "-cp", os.pathsep.join(map(str, [classes] + runtime)),
            "RemoteAiEvaluationKt", args.endpoint, args.model, str(args.fixture.resolve()), str(args.output.resolve())],
            check=False, timeout=args.timeout)
    if args.output.is_file():
        report = json.loads(args.output.read_text())
        report["provenance"] = {"fixtureSha256": digest(args.fixture),
            "sourcesSha256": {str(source.relative_to(root)): digest(source) for source in sources + [Path(__file__).resolve()]}}
        args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n")
        args.output.chmod(0o600)
    return result.returncode


if __name__ == "__main__":
    raise SystemExit(main())
