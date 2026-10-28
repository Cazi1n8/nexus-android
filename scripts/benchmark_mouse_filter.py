#!/usr/bin/env python3
"""Compile the actual pure Kotlin filter and run a deterministic synthetic benchmark.

Does not measure Galaxy A03 camera/MediaPipe/Android latency. Uses cached Kotlin
compiler jars, requires a Java17+ SDK; does not invoke Gradle or download anything.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--cache", type=Path)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    cache = args.cache or root / ".gradle-dist/cache/caches/modules-2/files-2.1"
    def jar(group, module, version):
        return next((cache / group / module / version).rglob("*.jar"))
    stdlib = jar("org.jetbrains.kotlin", "kotlin-stdlib", "2.1.20")
    compiler = [
        jar("org.jetbrains.kotlin", "kotlin-compiler-embeddable", "2.1.20"),
        stdlib,
        jar("org.jetbrains.kotlin", "kotlin-script-runtime", "2.1.20"),
        jar("org.jetbrains.intellij.deps", "trove4j", "1.0.20200330"),
        jar("org.jetbrains.kotlinx", "kotlinx-coroutines-core-jvm", "1.8.0"),
        next((cache / "org.jetbrains" / "annotations").rglob("*.jar")),
    ]
    java = Path(os.environ.get("JAVA_HOME", "")) / "bin/java"
    if not java.is_file():
        java = shutil.which("java")
    if not java:
        raise SystemExit("Java17+ SDK required (set JAVA_HOME).")
    sources = [
        root / "app/src/main/java/br/com/nexus/assistant/gestures/HandGeometry.kt",
        root / "app/src/main/java/br/com/nexus/assistant/gestures/LatestFrameDelivery.kt",
        root / "scripts/MouseFilterBenchmark.kt",
    ]
    with tempfile.TemporaryDirectory(prefix="nexus-filter-benchmark-") as workspace:
        subprocess.run([str(java), "-cp", os.pathsep.join(map(str, compiler)),
            "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler", "-no-stdlib", "-no-reflect",
            "-classpath", str(stdlib), "-jvm-target", "17", "-d", workspace,
            *map(str, sources)], check=True)
        result = subprocess.run([str(java), "-cp", os.pathsep.join([workspace, str(stdlib)]),
            "MouseFilterBenchmarkKt"], check=True, capture_output=True, text=True)
    payload = json.loads(result.stdout)
    payload["sources_sha256"] = {
        str(path.relative_to(root)): hashlib.sha256(path.read_bytes()).hexdigest() for path in sources
    }
    content = json.dumps(payload, indent=2, ensure_ascii=False) + "\n"
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(content)
    print(content, end="")


if __name__ == "__main__":
    main()
