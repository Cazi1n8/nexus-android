#!/usr/bin/env python3
"""Run one opt-in real-model Android test and retrieve its private observations.

This is developer/CI tooling. It sends only the pinned public GGUF fixture to an
explicitly connected Android device; it does not alter the app's normal models,
conversations or preferences. No account or service key is involved.
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

from fetch_language_model import MODEL_BYTES, MODEL_NAME, MODEL_SHA256, validate_model

APP_ID = "br.com.nexus.assistant"
TEST_CLASS = "br.com.nexus.assistant.LocalAiInferenceTest"
TEST_COMPONENT = f"{APP_ID}.test/androidx.test.runner.AndroidJUnitRunner"
REMOTE_MODEL = f"files/ci-models/{MODEL_NAME}"
MODEL_ARGUMENT = f"ci-models/{MODEL_NAME}"
REMOTE_REPORT = "files/ci-reports/local-ai-observations.json"
ROOT = Path(__file__).resolve().parents[1]
EXPECTED_CASES = {"arithmetic", "same_prompt_warm_cache", "after_idle_cancellation",
                  "context_acknowledgement", "context_follow_up", "unicode"}


def validate_observations(instrumentation_output: str, report: dict) -> None:
    output = instrumentation_output.replace("\r\n", "\n")
    if not re.search(r"^OK \(1 test\)\s*$", output, re.MULTILINE):
        raise ValueError("O AndroidJUnitRunner não confirmou exatamente um teste aprovado.")
    if not re.search(r"^INSTRUMENTATION_CODE:\s*-1\s*$", output, re.MULTILINE):
        raise ValueError("A instrumentação Android não terminou com sucesso.")
    if any(marker in output for marker in ("FAILURES!!!", "INSTRUMENTATION_FAILED", "Process crashed")):
        raise ValueError("A instrumentação relatou uma falha.")
    if not isinstance(report, dict) or report.get("executionStatus") != "passed":
        raise ValueError("O relatório não confirma execução da inferência.")
    if report.get("modelSha256") != MODEL_SHA256 or report.get("modelBytes") != MODEL_BYTES:
        raise ValueError("O relatório não corresponde ao GGUF fixado.")
    if report.get("contextTokens") != 512 or report.get("threads") != 1:
        raise ValueError("O relatório não confirma a configuração pequena de inferência.")
    cases = report.get("cases")
    if not isinstance(cases, list) or len(cases) != len(EXPECTED_CASES):
        raise ValueError("O relatório não contém todas as inferências reais esperadas.")
    if {case.get("name") for case in cases if isinstance(case, dict)} != EXPECTED_CASES:
        raise ValueError("O relatório contém casos inesperados ou incompletos.")
    if any(not isinstance(case.get("answer"), str) or not case["answer"].strip() for case in cases):
        raise ValueError("Uma inferência não devolveu texto.")
    for case in cases:
        prompt, cached, evaluated = (case.get(key) for key in
                                     ("promptTokens", "cachedPromptTokens", "evaluatedPromptTokens"))
        if (any(type(count) is not int for count in (prompt, cached, evaluated)) or
                not 0 <= cached < prompt <= 512 or evaluated != prompt - cached):
            raise ValueError("As contagens do cache de contexto são inconsistentes.")
    by_name = {case["name"]: case for case in cases}
    cold, warm, cancelled = (by_name[name] for name in
                             ("arithmetic", "same_prompt_warm_cache", "after_idle_cancellation"))
    if (cold["cachedPromptTokens"] != 0 or warm["cachedPromptTokens"] <= 0 or
            cancelled["cachedPromptTokens"] != 0 or
            not cold["answer"] == warm["answer"] == cancelled["answer"]):
        raise ValueError("Falta comprovar reutilização idêntica e limpeza após cancelamento.")
    # A small model's mistakes remain visible. These flags may honestly be false.
    for key in ("arithmeticMatchedExpected42", "contextMatchedExpectedCiano", "unicodeMatchedRequestedText"):
        if type(report.get(key)) is not bool:
            raise ValueError("Falta uma observação explícita sobre a resposta do modelo.")


def adb(*arguments: str, timeout: int = 180, stdin=None) -> bytes:
    command = ["adb", *arguments]
    result = subprocess.run(command, stdin=stdin, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                            timeout=timeout, check=False)
    if result.returncode != 0:
        detail = result.stderr.decode("utf-8", errors="replace").strip()
        raise RuntimeError(f"ADB falhou ({result.returncode}): {detail}")
    return result.stdout


def run(model: Path, output: Path) -> dict:
    validate_model(model)
    output.mkdir(parents=True, exist_ok=True)
    for name in ("instrumentation.txt", "local-ai-observations.json", "junit.xml"):
        (output / name).unlink(missing_ok=True)
    app = ROOT / "app/build/outputs/apk/debug/app-debug.apk"
    tests = ROOT / "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
    if not app.is_file() or not tests.is_file():
        raise ValueError("Compile assembleDebug e assembleDebugAndroidTest antes do teste de IA.")
    adb("wait-for-device")
    adb("install", "-r", str(app))
    adb("install", "-r", str(tests))
    started = time.monotonic()
    status = "failed"
    failure = ""
    try:
        # Do not accept a report left behind by an earlier instrumentation run.
        adb("shell", "run-as", APP_ID, "rm", "-f", REMOTE_REPORT, timeout=30)
        # -T disables PTY transformations; the device test verifies SHA-256 again.
        shell = f"run-as {APP_ID} sh -c 'mkdir -p files/ci-models && cat > {REMOTE_MODEL}'"
        with model.open("rb") as handle:
            adb("shell", "-T", shell, stdin=handle, timeout=300)
        result = adb("shell", "am", "instrument", "-w", "-r", "-e", "class", TEST_CLASS,
                     "-e", "nexusModelPath", MODEL_ARGUMENT, TEST_COMPONENT, timeout=660).decode("utf-8")
        (output / "instrumentation.txt").write_text(result, encoding="utf-8")
        raw = adb("exec-out", "run-as", APP_ID, "cat", REMOTE_REPORT).decode("utf-8")
        (output / "local-ai-observations.json").write_text(raw, encoding="utf-8")
        report = json.loads(raw)
        validate_observations(result, report)
        status = "passed"
        return report
    except BaseException as error:
        failure = f"{type(error).__name__}: {error}"
        raise
    finally:
        # Preserve a partial failure report as well (e.g. a failed assertion or
        # interrupted instrument process). The success path already saved it.
        if not (output / "local-ai-observations.json").exists():
            try:
                raw = adb("exec-out", "run-as", APP_ID, "cat", REMOTE_REPORT, timeout=30)
                (output / "local-ai-observations.json").write_bytes(raw)
            except (RuntimeError, subprocess.TimeoutExpired, OSError):
                pass
        # The only removed file is this helper's own CI fixture, never app models.
        try:
            adb("shell", "run-as", APP_ID, "rm", "-f", REMOTE_MODEL, timeout=30)
        except (RuntimeError, subprocess.TimeoutExpired, OSError):
            pass
        suite = ET.Element("testsuite", name="AndroidRealLocalAi", tests="1",
                           failures="0" if status == "passed" else "1", errors="0", skipped="0")
        case = ET.SubElement(suite, "testcase", classname=TEST_CLASS,
                             name="realModelProducesUtf8TextOnAndroidAndRecordsObservations",
                             time=f"{time.monotonic() - started:.3f}")
        if status != "passed":
            ET.SubElement(case, "failure", message="Real Android inference or report validation failed").text = failure
        ET.ElementTree(suite).write(output / "junit.xml", encoding="utf-8", xml_declaration=True)


def main() -> int:
    parser = argparse.ArgumentParser(description="Executa uma prova real de IA no Android conectado, com GGUF verificado.")
    parser.add_argument("--model", required=True, type=Path)
    parser.add_argument("--output", type=Path, default=ROOT / "build" / "local-ai-smoke")
    arguments = parser.parse_args()
    try:
        report = run(arguments.model, arguments.output)
    except (OSError, ValueError, RuntimeError, subprocess.TimeoutExpired) as error:
        print(f"Prova Android não aprovada: {error}", file=sys.stderr)
        return 1
    print("Inferência real Android executada; as respostas observadas estão no relatório.")
    print(json.dumps({key: report[key] for key in (
        "arithmeticMatchedExpected42", "contextMatchedExpectedCiano", "unicodeMatchedRequestedText"
    )}, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
