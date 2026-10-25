#!/usr/bin/env python3
"""Exercise production model downloads inside Android, then collect evidence.

No model is transferred by ADB. The opt-in Android test downloads the two public
pinned GGUFs through LocalModelManager; only its dedicated CI files are removed.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

APP_ID = "br.com.nexus.assistant"
TEST_CLASS = f"{APP_ID}.ProductionModelDownloadTest"
TEST_COMPONENT = f"{APP_ID}.test/androidx.test.runner.AndroidJUnitRunner"
REMOTE_REPORT = "files/ci-reports/production-download-observations.json"
REMOTE_FIXTURE_DIRECTORY = "files/ci-production-downloads"
ROOT = Path(__file__).resolve().parents[1]
EXPECTED_MODELS = {
    "compact": (491_400_032, "74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db"),
    "balanced": (1_117_320_736, "6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e"),
}


def validate_observations(output: str, report: dict) -> None:
    output = output.replace("\r\n", "\n")
    if not re.search(r"^OK \(1 test\)\s*$", output, re.MULTILINE):
        raise ValueError("O AndroidJUnitRunner não confirmou exatamente um teste aprovado.")
    if not re.search(r"^INSTRUMENTATION_CODE:\s*-1\s*$", output, re.MULTILINE):
        raise ValueError("A instrumentação Android não terminou com sucesso.")
    if any(marker in output for marker in ("FAILURES!!!", "INSTRUMENTATION_FAILED", "Process crashed")):
        raise ValueError("A instrumentação relatou uma falha.")
    if not isinstance(report, dict) or report.get("executionStatus") != "passed":
        raise ValueError("O relatório não confirma os downloads no Android.")
    models = report.get("models")
    if not isinstance(models, list) or len(models) != 2:
        raise ValueError("O relatório precisa confirmar os dois modelos públicos.")
    if any(not isinstance(model, dict) for model in models):
        raise ValueError("O relatório contém um modelo inválido.")
    if {model.get("profile") for model in models} != set(EXPECTED_MODELS):
        raise ValueError("O relatório contém perfis repetidos ou inesperados.")
    for model in models:
        size, checksum = EXPECTED_MODELS[model["profile"]]
        if model.get("bytes") != size or model.get("sha256") != checksum:
            raise ValueError("O modelo recebido não corresponde ao tamanho e SHA-256 fixados.")
        if model.get("terminalState") != "READY" or model.get("refreshState") != "READY":
            raise ValueError("O downloader não confirmou um modelo pronto após a verificação.")
        if model.get("downloadReturned") is not True:
            raise ValueError("O download não confirmou retorno ao chamador após 100%.")
    inference = report.get("inference")
    if not isinstance(inference, dict) or not isinstance(inference.get("answer"), str) or not inference["answer"].strip():
        raise ValueError("A inferência após o download não devolveu texto.")
    # A small model may answer incorrectly. Preserve that observation explicitly.
    if type(inference.get("arithmeticMatchedExpected42")) is not bool:
        raise ValueError("Falta a observação explícita sobre a resposta do modelo.")


def adb(*arguments: str, timeout: int = 180) -> bytes:
    result = subprocess.run(["adb", *arguments], stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                            timeout=timeout, check=False)
    if result.returncode:
        detail = result.stderr.decode("utf-8", errors="replace").strip()
        raise RuntimeError(f"ADB falhou ({result.returncode}): {detail}")
    return result.stdout


def run(output: Path) -> dict:
    output.mkdir(parents=True, exist_ok=True)
    for name in ("instrumentation.txt", "production-download-observations.json", "junit.xml"):
        (output / name).unlink(missing_ok=True)
    app = ROOT / "app/build/outputs/apk/debug/app-debug.apk"
    tests = ROOT / "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
    if not app.is_file() or not tests.is_file():
        raise ValueError("Compile assembleDebug e assembleDebugAndroidTest antes desta prova.")
    adb("wait-for-device")
    adb("install", "-r", str(app))
    adb("install", "-r", str(tests))
    started = time.monotonic()
    status, failure = "failed", ""
    try:
        adb("shell", "run-as", APP_ID, "rm", "-f", REMOTE_REPORT, timeout=30)
        result = adb("shell", "am", "instrument", "-w", "-r", "-e", "class", TEST_CLASS,
                     "-e", "nexusDownloadSmoke", "true", TEST_COMPONENT, timeout=1260).decode("utf-8")
        (output / "instrumentation.txt").write_text(result, encoding="utf-8")
        raw = adb("exec-out", "run-as", APP_ID, "cat", REMOTE_REPORT, timeout=30)
        (output / "production-download-observations.json").write_bytes(raw)
        report = json.loads(raw)
        validate_observations(result, report)
        status = "passed"
        return report
    except BaseException as error:
        failure = f"{type(error).__name__}: {error}"
        raise
    finally:
        if not (output / "production-download-observations.json").exists():
            try:
                raw = adb("exec-out", "run-as", APP_ID, "cat", REMOTE_REPORT, timeout=30)
                (output / "production-download-observations.json").write_bytes(raw)
            except (RuntimeError, subprocess.TimeoutExpired, OSError):
                pass
        try:
            # Hard-coded test directory only; never normal app models or history.
            adb("shell", "run-as", APP_ID, "rm", "-rf", REMOTE_FIXTURE_DIRECTORY, timeout=30)
        except (RuntimeError, subprocess.TimeoutExpired, OSError):
            pass
        suite = ET.Element("testsuite", name="AndroidProductionModelDownloads", tests="1",
                           failures="0" if status == "passed" else "1", errors="0", skipped="0")
        case = ET.SubElement(suite, "testcase", classname=TEST_CLASS,
                             name="bothProductionModelsDownloadAndCompactCanInfer",
                             time=f"{time.monotonic() - started:.3f}")
        if status != "passed":
            ET.SubElement(case, "failure", message="Android production download proof failed").text = failure
        ET.ElementTree(suite).write(output / "junit.xml", encoding="utf-8", xml_declaration=True)


def main() -> int:
    parser = argparse.ArgumentParser(description="Testa os downloads reais do próprio aplicativo no Android conectado.")
    parser.add_argument("--output", type=Path, default=ROOT / "build/model-download-smoke")
    args = parser.parse_args()
    try:
        report = run(args.output)
    except (OSError, ValueError, RuntimeError, subprocess.TimeoutExpired) as error:
        print(f"Prova de download Android não aprovada: {error}", file=sys.stderr)
        return 1
    print("Downloads reais Android concluídos e verificados; inferência executada depois do download.")
    print(json.dumps({"profiles": [model["profile"] for model in report["models"]],
                      "arithmeticMatchedExpected42": report["inference"]["arithmeticMatchedExpected42"]}))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
