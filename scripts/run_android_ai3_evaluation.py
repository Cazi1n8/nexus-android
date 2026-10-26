#!/usr/bin/env python3
"""Evaluate the fifteen requested AI cases using the actual bundled Android model.

This is opt-in tooling for a disposable emulator. The Android test resets its
application data; this runner therefore refuses physical devices. No model is
transferred by ADB, and network controls are restored after collecting evidence.
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
TEST_CLASS = f"{APP_ID}.Ai3EvaluationTest"
TEST_COMPONENT = f"{APP_ID}.test/androidx.test.runner.AndroidJUnitRunner"
REMOTE_REPORT = "files/ci-reports/ai3-evaluation.json"
ROOT = Path(__file__).resolve().parents[1]
MODEL_BYTES = 1_117_320_736
MODEL_SHA256 = "6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e"
CRITICAL_CASE_IDS = frozenset((1, 2, 3, 12, 13))


def validate_observations(output: str, report: dict) -> None:
    output = output.replace("\r\n", "\n")
    if not re.search(r"^OK \(1 test\)\s*$", output, re.MULTILINE):
        raise ValueError("A instrumentação não confirmou exatamente uma bateria executada.")
    if not re.search(r"^INSTRUMENTATION_CODE:\s*-1\s*$", output, re.MULTILINE):
        raise ValueError("A instrumentação não terminou com sucesso.")
    if any(marker in output for marker in ("FAILURES!!!", "INSTRUMENTATION_FAILED", "Process crashed")):
        raise ValueError("A instrumentação relatou uma falha.")
    if not isinstance(report, dict) or report.get("executionStatus") != "completed":
        raise ValueError("A bateria Android não foi concluída.")
    if report.get("bundledModel") is not True or report.get("modelSource") != "BUNDLED":
        raise ValueError("A prova exige o modelo incluído no APK, sem transferência por ADB.")
    if report.get("provider") != "NEXUS_LOCAL" or report.get("modelProfile") != "BALANCED":
        raise ValueError("A avaliação não confirmou o provedor/modelo esperado.")
    if report.get("modelBytes") != MODEL_BYTES or report.get("modelSha256") != MODEL_SHA256:
        raise ValueError("O modelo não corresponde à revisão fixa avaliada.")
    if report.get("networkAvailableBefore") is not False or report.get("networkAvailableAfter") is not False:
        raise ValueError("A rede precisa permanecer desligada na prova local.")
    if report.get("technicalFailures") != 0 or report.get("roomReopenedAndVerified") is not True:
        raise ValueError("Há falhas técnicas ou falta prova de persistência após reabrir o Room.")
    if report.get("persistedAssistantMessages") != 37:
        raise ValueError("Falta a confirmação de todas as 37 respostas reais no histórico.")
    if (report.get("actualModelGenerations") != 35 or report.get("calculatorExecutions") != 1 or
            report.get("actionProposals") != 1):
        raise ValueError("Falta distinguir as 35 gerações do modelo, o cálculo e a proposta de ação reais.")
    cases = report.get("cases")
    if (not isinstance(cases, list) or len(cases) != 15 or
            any(not isinstance(case, dict) or type(case.get("id")) is not int for case in cases) or
            {case["id"] for case in cases} != set(range(1, 16))):
        raise ValueError("A bateria precisa executar exatamente os quinze casos solicitados.")
    for case in cases:
        if case.get("executionStatus") != "completed":
            raise ValueError("Um caso não concluiu geração real.")
        turns = case.get("turns")
        if not isinstance(turns, list) or not turns:
            raise ValueError("Faltam as mensagens/respostas sintéticas de um caso.")
        for turn in turns:
            if not isinstance(turn, dict) or not isinstance(turn.get("prompt"), str) or not turn["prompt"].strip():
                raise ValueError("Falta uma pergunta sintética correspondente à geração.")
            if not isinstance(turn.get("answer"), str) or not turn["answer"].strip():
                raise ValueError("Não há resposta gerada para uma etapa.")
            if type(turn.get("milliseconds")) is not int or turn["milliseconds"] < 0:
                raise ValueError("Falta uma duração de geração válida.")
            if turn.get("executionKind") not in ("local_model", "calculator", "local_action_proposal"):
                raise ValueError("Falta identificar a origem real de uma resposta.")
            expected_kind = "calculator" if case["id"] == 4 else "local_action_proposal" if case["id"] == 8 else "local_model"
            if turn["executionKind"] != expected_kind:
                raise ValueError("A origem de uma resposta diverge do caso executado.")
        checks = case.get("checks")
        if not isinstance(checks, dict) or not checks or any(type(value) is not bool for value in checks.values()):
            raise ValueError("As verificações semânticas precisam ser observações booleanas explícitas.")
        if type(case.get("automaticChecksPassed")) is not bool or case["automaticChecksPassed"] != all(checks.values()):
            raise ValueError("O resultado automático diverge das observações registradas.")
    case_twenty = next(case for case in cases if case["id"] == 11)
    if case_twenty.get("actualUserMessages") != 20 or len(case_twenty["turns"]) != 20:
        raise ValueError("O caso de contexto precisa gerar vinte turnos de verdade.")
    origins = [turn["executionKind"] for case in cases for turn in case["turns"]]
    if len(origins) != 37 or origins.count("local_model") != 35:
        raise ValueError("A contagem de etapas não comprova as 35 gerações e as duas ferramentas.")
    if report.get("semanticChecksAreHeuristics") is not True:
        raise ValueError("A avaliação não deve apresentar heurísticas como certificação de inteligência.")
    # Wrong semantic observations stay visible. Execution success is not the same
    # as all requested wording/coherence checks passing.


def critical_failures(report: dict) -> list[int]:
    return [case["id"] for case in report["cases"]
            if case["id"] in CRITICAL_CASE_IDS and not case["automaticChecksPassed"]]


def write_markdown(report: dict, path: Path) -> None:
    passed = sum(case["automaticChecksPassed"] for case in report["cases"])
    lines = ["# TESTES NEXUS AI — avaliação Android real", "",
             f"Modelo: {report['model']} · versão: {report.get('versionName', 'não informada')}.", "",
             "Bateria executada sem internet, com pesos do APK e respostas persistidas em Room: 35 gerações do modelo, uma execução da calculadora e uma proposta de ação validada.",
             f"Casos executados: 15. Casos que atenderam a todas as verificações automáticas: {passed}/15.",
             "As verificações de palavras, tópicos e formato são limitadas; revise as respostas abaixo. Elas não certificam coerência, veracidade ou inteligência geral.", "",
             "A prova usa o provedor Android com orçamento explícito de teste; não comprova a seleção automática de perfil, interface principal, voz, câmera ou desempenho no S9+.", ""]
    for case in report["cases"]:
        lines += [f"## {case['id']}. {case['title']}", "",
                  "Execução: concluída. Verificações automáticas: " + ("atendidas" if case['automaticChecksPassed'] else "há itens não atendidos"), ""]
        lines += [f"- {name}: {'observado' if value else 'não atendeu'}" for name, value in case["checks"].items()]
        for index, turn in enumerate(case["turns"], 1):
            longest = max([len(match.group()) for match in re.finditer(r"`+", turn['answer'])] or [0])
            fence = '`' * max(3, longest + 1)
            lines += ["", f"### Etapa {index}", "", f"Pergunta: {turn['prompt']}", "",
                      f"Resposta real ({turn['milliseconds']} ms; origem: {turn['executionKind']}):", "", fence + "text", turn["answer"], fence]
        lines += [""]
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def adb(*arguments: str, timeout: int = 180) -> bytes:
    result = subprocess.run(["adb", *arguments], stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                            timeout=timeout, check=False)
    if result.returncode:
        detail = result.stderr.decode("utf-8", errors="replace").strip()
        raise RuntimeError(f"ADB falhou ({result.returncode}): {detail}")
    return result.stdout


def read_network_control(name: str) -> str:
    value = adb("shell", "settings", "get", "global", name, timeout=30).decode("utf-8").strip()
    if value not in ("0", "1"):
        raise ValueError(f"Não foi possível determinar o estado original de {name}; a rede não será alterada.")
    return value


def wait_network_controls_disabled() -> None:
    deadline = time.monotonic() + 10
    while True:
        wifi = read_network_control("wifi_on")
        mobile = read_network_control("mobile_data")
        if wifi == mobile == "0":
            return
        if time.monotonic() >= deadline:
            raise RuntimeError("O emulador não confirmou Wi-Fi e dados móveis desativados.")
        time.sleep(0.25)


def run(output: Path, *, allow_reset_emulator: bool = False, require_critical_checks: bool = False) -> dict:
    if not allow_reset_emulator:
        raise ValueError("Use --allow-reset-emulator somente para autorizar a prova em um emulador descartável.")
    app = ROOT / "app/build/outputs/apk/debug/app-debug.apk"
    tests = ROOT / "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
    if not app.is_file() or not tests.is_file():
        raise ValueError("Compile assembleDebug e assembleDebugAndroidTest antes desta prova.")
    adb("wait-for-device")
    qemu = [adb("shell", "getprop", key, timeout=30).decode("utf-8").strip()
            for key in ("ro.kernel.qemu", "ro.boot.qemu")]
    if "1" not in qemu:
        raise ValueError("A prova altera dados do app e só pode ser executada em um emulador, não em um celular físico.")
    original = {"wifi": read_network_control("wifi_on"), "data": read_network_control("mobile_data")}
    output.mkdir(parents=True, exist_ok=True)
    for name in ("instrumentation.txt", "ai3-evaluation.json", "junit.xml", "network-restoration.json", "semantic-summary.json", "TESTES_NEXUS_AI.md"):
        (output / name).unlink(missing_ok=True)
    started = time.monotonic()
    status, failure = "failed", ""
    network_changed, report_reset = False, False
    restoration_errors = []
    try:
        # The universal offline APK is larger because it includes the real GGUF.
        adb("install", "-r", str(app), timeout=600)
        adb("install", "-r", str(tests), timeout=600)
        adb("shell", "run-as", APP_ID, "rm", "-f", REMOTE_REPORT, timeout=30)
        report_reset = True
        network_changed = True
        adb("shell", "svc", "wifi", "disable", timeout=30)
        adb("shell", "svc", "data", "disable", timeout=30)
        wait_network_controls_disabled()
        result = adb("shell", "am", "instrument", "-w", "-r", "-e", "class", TEST_CLASS,
                     "-e", "nexusAi3Eval", "true", TEST_COMPONENT, timeout=2700).decode("utf-8")
        (output / "instrumentation.txt").write_text(result, encoding="utf-8")
        raw = adb("exec-out", "run-as", APP_ID, "cat", REMOTE_REPORT, timeout=30)
        (output / "ai3-evaluation.json").write_bytes(raw)
        report = json.loads(raw)
        validate_observations(result, report)
        write_markdown(report, output / "TESTES_NEXUS_AI.md")
        failed_critical = critical_failures(report)
        summary = {"executionStatus": "completed", "casesExecuted": len(report["cases"]),
                   "automaticChecksSatisfied": sum(case["automaticChecksPassed"] for case in report["cases"]),
                   "criticalCaseIds": sorted(CRITICAL_CASE_IDS), "criticalFailedCaseIds": failed_critical,
                   "heuristicsAreNotQualityCertification": True}
        (output / "semantic-summary.json").write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
        if require_critical_checks and failed_critical:
            raise ValueError("A geração executou, mas falharam verificações críticas de identidade/redação nos casos: " +
                             ", ".join(map(str, failed_critical)) + ". As respostas reais foram preservadas.")
        status = "passed"
        return report
    except BaseException as error:
        failure = f"{type(error).__name__}: {error}"
        raise
    finally:
        if report_reset and not (output / "ai3-evaluation.json").exists():
            try:
                raw = adb("exec-out", "run-as", APP_ID, "cat", REMOTE_REPORT, timeout=30)
                (output / "ai3-evaluation.json").write_bytes(raw)
            except (RuntimeError, subprocess.TimeoutExpired, OSError):
                pass
        if network_changed:
            for service in ("wifi", "data"):
                try:
                    adb("shell", "svc", service, "enable" if original[service] == "1" else "disable", timeout=30)
                except (RuntimeError, subprocess.TimeoutExpired, OSError) as error:
                    restoration_errors.append(f"{service}: {type(error).__name__}")
            restoration = {"original": original, "restorationCommandErrors": restoration_errors}
            (output / "network-restoration.json").write_text(json.dumps(restoration, indent=2) + "\n", encoding="utf-8")
            if restoration_errors:
                print("Aviso: verifique a restauração da rede no emulador: " + ", ".join(restoration_errors), file=sys.stderr)
        suite = ET.Element("testsuite", name="AndroidAi3Evaluation", tests="1",
                           failures="0" if status == "passed" else "1", errors="0", skipped="0")
        case = ET.SubElement(suite, "testcase", classname=TEST_CLASS,
                             name="evaluateTheFifteenRequestedCasesWithTheBundledModel",
                             time=f"{time.monotonic() - started:.3f}")
        if status != "passed":
            ET.SubElement(case, "failure", message="Android AI evaluation did not complete").text = failure
        ET.ElementTree(suite).write(output / "junit.xml", encoding="utf-8", xml_declaration=True)


def main() -> int:
    parser = argparse.ArgumentParser(description="Executa quinze casos reais de IA no Android emulado e guarda respostas sem esconder falhas semânticas.")
    parser.add_argument("--output", type=Path, default=ROOT / "build/ai3-evaluation")
    parser.add_argument("--allow-reset-emulator", action="store_true",
                        help="Autoriza o teste a limpar dados do app e desligar a rede apenas em emulador.")
    parser.add_argument("--require-critical-checks", action="store_true",
                        help="Falha também se as verificações críticas de identidade e redação não forem atendidas; preserva todas as respostas.")
    args = parser.parse_args()
    try:
        report = run(args.output, allow_reset_emulator=args.allow_reset_emulator,
                     require_critical_checks=args.require_critical_checks)
    except (OSError, ValueError, RuntimeError, subprocess.TimeoutExpired) as error:
        print(f"Bateria Android não concluída: {error}", file=sys.stderr)
        return 1
    print("Bateria real de quinze casos executada; examine as observações e as respostas, inclusive os erros.")
    print(json.dumps({"modelProfile": report["modelProfile"], "casesExecuted": len(report["cases"]),
                      "automaticChecksSatisfied": sum(case["automaticChecksPassed"] for case in report["cases"])}))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
