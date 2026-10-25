#!/usr/bin/env python3
"""Verify first-run bundled chat through the real Android UI and ViewModel.

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
TEST_CLASS = f"{APP_ID}.BundledChatFirstRunTest"
TEST_COMPONENT = f"{APP_ID}.test/androidx.test.runner.AndroidJUnitRunner"
REMOTE_REPORT = "files/ci-reports/bundled-chat-observations.json"
ROOT = Path(__file__).resolve().parents[1]
MODEL_BYTES = 491_400_032
MODEL_SHA256 = "74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db"


def validate_observations(output: str, report: dict) -> None:
    output = output.replace("\r\n", "\n")
    if not re.search(r"^OK \(1 test\)\s*$", output, re.MULTILINE):
        raise ValueError("O AndroidJUnitRunner não confirmou exatamente um teste aprovado.")
    if not re.search(r"^INSTRUMENTATION_CODE:\s*-1\s*$", output, re.MULTILINE):
        raise ValueError("A instrumentação Android não terminou com sucesso.")
    if any(marker in output for marker in ("FAILURES!!!", "INSTRUMENTATION_FAILED", "Process crashed")):
        raise ValueError("A instrumentação relatou uma falha.")
    if not isinstance(report, dict) or report.get("executionStatus") != "passed":
        raise ValueError("O relatório não confirma o chat com IA incluída.")
    if report.get("bundledModel") is not True or report.get("modelPreparedByViewModel") is not True:
        raise ValueError("A prova não confirma a preparação da IA incluída pelo ViewModel.")
    if report.get("provider") != "NEXUS_LOCAL" or report.get("modelProfile") != "COMPACT":
        raise ValueError("O chat não confirmou o provedor local e o modelo base compacto.")
    if report.get("modelBytes") != MODEL_BYTES or report.get("modelSha256") != MODEL_SHA256:
        raise ValueError("O arquivo preparado não corresponde ao modelo incluído fixado.")
    if report.get("networkAvailableBefore") is not False or report.get("networkAvailableAfter") is not False:
        raise ValueError("A prova não confirma ausência de internet antes e depois do chat.")
    if report.get("modelSource") != "BUNDLED" or report.get("profileStableAfterRecreation") is not True:
        raise ValueError("Falta a confirmação do asset incluído e do perfil estável após recriação.")
    if report.get("mathCorrect") is not True or report.get("imageTextReadLocally") is not True:
        raise ValueError("Falta a prova do cálculo correto e da leitura local da imagem pelo Chat.")
    if report.get("persistedAssistantMessage") is not True:
        raise ValueError("A resposta não foi confirmada no histórico real do aplicativo.")
    if type(report.get("preparationMilliseconds")) is not int or report["preparationMilliseconds"] < 0:
        raise ValueError("Falta um tempo de preparação válido.")
    reply = report.get("reply")
    if not isinstance(reply, dict) or not isinstance(reply.get("answer"), str) or not reply["answer"].strip():
        raise ValueError("O chat não gerou uma resposta real.")
    if type(reply.get("milliseconds")) is not int or reply["milliseconds"] < 0:
        raise ValueError("Falta um tempo válido para a resposta.")
    # Keep factual mistakes visible; transport and UI success do not certify the model's quality.
    if type(reply.get("matchedExpectedWord")) is not bool:
        raise ValueError("Falta a observação explícita sobre a resposta do modelo.")


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


def run(output: Path, *, allow_reset_emulator: bool = False) -> dict:
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
    for name in ("instrumentation.txt", "bundled-chat-observations.json", "junit.xml", "network-restoration.json"):
        (output / name).unlink(missing_ok=True)
    started = time.monotonic()
    status, failure = "failed", ""
    network_changed, report_reset = False, False
    restoration_errors = []
    try:
        # The universal offline APK is larger because it includes the real GGUF.
        adb("install", "-r", str(app), timeout=300)
        adb("install", "-r", str(tests), timeout=300)
        adb("shell", "run-as", APP_ID, "rm", "-f", REMOTE_REPORT, timeout=30)
        report_reset = True
        network_changed = True
        adb("shell", "svc", "wifi", "disable", timeout=30)
        adb("shell", "svc", "data", "disable", timeout=30)
        wait_network_controls_disabled()
        result = adb("shell", "am", "instrument", "-w", "-r", "-e", "class", TEST_CLASS,
                     "-e", "nexusBundledChatSmoke", "true", TEST_COMPONENT, timeout=960).decode("utf-8")
        (output / "instrumentation.txt").write_text(result, encoding="utf-8")
        raw = adb("exec-out", "run-as", APP_ID, "cat", REMOTE_REPORT, timeout=30)
        (output / "bundled-chat-observations.json").write_bytes(raw)
        report = json.loads(raw)
        validate_observations(result, report)
        status = "passed"
        return report
    except BaseException as error:
        failure = f"{type(error).__name__}: {error}"
        raise
    finally:
        if report_reset and not (output / "bundled-chat-observations.json").exists():
            try:
                raw = adb("exec-out", "run-as", APP_ID, "cat", REMOTE_REPORT, timeout=30)
                (output / "bundled-chat-observations.json").write_bytes(raw)
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
        suite = ET.Element("testsuite", name="AndroidBundledFirstChat", tests="1",
                           failures="0" if status == "passed" else "1", errors="0", skipped="0")
        case = ET.SubElement(suite, "testcase", classname=TEST_CLASS,
                             name="firstChatPreparesBundledModelAndRespondsOffline",
                             time=f"{time.monotonic() - started:.3f}")
        if status != "passed":
            ET.SubElement(case, "failure", message="Bundled Android chat proof failed").text = failure
        ET.ElementTree(suite).write(output / "junit.xml", encoding="utf-8", xml_declaration=True)


def main() -> int:
    parser = argparse.ArgumentParser(description="Testa o primeiro chat offline com IA incluída em emulador descartável.")
    parser.add_argument("--output", type=Path, default=ROOT / "build/bundled-chat-smoke")
    parser.add_argument("--allow-reset-emulator", action="store_true",
                        help="Autoriza o teste a limpar dados do app e desligar a rede apenas em emulador.")
    args = parser.parse_args()
    try:
        report = run(args.output, allow_reset_emulator=args.allow_reset_emulator)
    except (OSError, ValueError, RuntimeError, subprocess.TimeoutExpired) as error:
        print(f"Prova de chat incluído não aprovada: {error}", file=sys.stderr)
        return 1
    print("Primeiro chat executado pela interface e pelo ViewModel, com modelo incluído e sem internet.")
    print(json.dumps({"provider": report["provider"], "modelProfile": report["modelProfile"],
                      "matchedExpectedWord": report["reply"]["matchedExpectedWord"]}))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
