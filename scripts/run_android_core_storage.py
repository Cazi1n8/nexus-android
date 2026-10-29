#!/usr/bin/env python3
"""Run CORE/storage evidence on a fresh, explicitly authorized emulator.

The production-like coreTest APK is non-debuggable: reports are JSON encoded in
instrumentation status bundles, never read through adb run-as or rooted access.
The CORE stage runs with network controls off; optional data packs are downloaded
only when --with-extensions explicitly selects the second stage.
"""
from __future__ import annotations

import argparse
import base64
import binascii
import json
from pathlib import Path
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

APP_ID = "br.com.nexus.assistant"
COMPONENT = f"{APP_ID}.test/androidx.test.runner.AndroidJUnitRunner"
CORE_CLASSES = ",".join(f"{APP_ID}.{name}" for name in (
    "CoreInstallationTest", "CoreOptionalModelsTest", "CoreArithmeticToolsTest",
    "N41PermissionBoundaryTest", "gestures.HandImagePipelineTest",
    "gestures.global.GlobalMousePermissionDenialTest", "gestures.SceneRenderingTest"))
CORE_EXPECTED_TESTS = 12
EXTENSION_CLASS = f"{APP_ID}.ExtensionDataSmokeTest"
ROOT = Path(__file__).resolve().parents[1]
HAND_BYTES = 7_819_105
HAND_SHA256 = "fbc2a30080c3c557093b5ddfc334698132eb341044ccee322ccf8bcf3607cde1"


def adb(*arguments: str, timeout: int = 180) -> bytes:
    result = subprocess.run(["adb", *arguments], stdout=subprocess.PIPE,
                            stderr=subprocess.PIPE, timeout=timeout, check=False)
    if result.returncode:
        detail = result.stderr.decode("utf-8", errors="replace").strip()
        raise RuntimeError(f"ADB falhou ({result.returncode}): {detail}")
    return result.stdout


def instrument(output: Path, *arguments: str, timeout: int) -> str:
    """Keep partial JUnit/status output when adb times out before normal exit."""
    try:
        raw = adb("shell", "am", "instrument", "-w", "-r", *arguments, timeout=timeout)
    except subprocess.TimeoutExpired as error:
        def as_bytes(value):
            if value is None:
                return b""
            return value.encode("utf-8", errors="replace") if isinstance(value, str) else value
        output.write_bytes(as_bytes(error.stdout))
        output.with_suffix(".stderr.txt").write_bytes(as_bytes(error.stderr))
        raise
    output.write_bytes(raw)
    return raw.decode("utf-8")


def collect_failure_diagnostics(output: Path) -> None:
    """Synthetic logs only: run() has already refused physical/existing devices."""
    errors = []
    commands = {
        "failure-logcat.txt": ("shell", "logcat", "-d", "-t", "400", "-v", "threadtime"),
        "failure-activity-top.txt": ("shell", "dumpsys", "activity", "top"),
        "failure-activity-processes.txt": ("shell", "dumpsys", "activity", "processes"),
    }
    for name, arguments in commands.items():
        try:
            (output / name).write_bytes(adb(*arguments, timeout=15))
        except (OSError, RuntimeError, subprocess.TimeoutExpired) as error:
            errors.append(f"{name}: {type(error).__name__}")
    (output / "failure-diagnostics.json").write_text(json.dumps({
        "scope": "Fresh disposable emulator only; synthetic CORE and extension fixtures, no physical/user installation.",
        "collectionErrors": errors,
    }, indent=2) + "\n", encoding="utf-8")


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError(f"Campo duplicado no relatório: {key}")
        result[key] = value
    return result


def decode_report(output: str, key: str, expected_tests: int) -> dict:
    output = output.replace("\r\n", "\n")
    noun = "test" if expected_tests == 1 else "tests"
    if not re.search(rf"^OK \({expected_tests} {noun}\)\s*$", output, re.MULTILINE):
        raise ValueError("A instrumentação não confirmou a quantidade exata de testes aprovados.")
    if not re.search(r"^INSTRUMENTATION_CODE:\s*-1\s*$", output, re.MULTILINE):
        raise ValueError("A instrumentação não terminou com sucesso.")
    if any(marker in output for marker in ("FAILURES!!!", "INSTRUMENTATION_FAILED", "Process crashed")):
        raise ValueError("A instrumentação relatou falha ou encerramento do processo.")
    values = re.findall(rf"^INSTRUMENTATION_STATUS: {re.escape(key)}=([^\n]*)$", output, re.MULTILINE)
    if len(values) != 1 or len(values[0]) > 1_048_576:
        raise ValueError("Relatório de instrumentação ausente, duplicado ou grande demais.")
    try:
        raw = base64.b64decode(values[0], validate=True).decode("utf-8")
        report = json.loads(raw, object_pairs_hook=unique_object,
                            parse_constant=lambda value: (_ for _ in ()).throw(ValueError(f"JSON inválido: {value}")))
    except (binascii.Error, UnicodeDecodeError, json.JSONDecodeError) as error:
        raise ValueError("Relatório de instrumentação não contém JSON UTF-8/base64 válido.") from error
    if not isinstance(report, dict) or report.get("schemaVersion") != 1 or report.get("executionStatus") != "passed":
        raise ValueError("O relatório não confirma execução aprovada com esquema conhecido.")
    return report


def validate_core(report: dict) -> None:
    for key in ("coreInstallation", "generativeWeightsAbsent", "optionalVisionAssetsAbsent",
                "realHandTrackerProcessedBlankFrame", "extensionsUiOpened", "storageMeasurementRendered",
                "manageModelsButtonNavigated", "sensorPermissionsUnchanged"):
        if report.get(key) is not True:
            raise ValueError(f"Falta confirmação do CORE: {key}.")
    for key in ("automaticModelDownloadStarted", "automaticExtensionDownloadStarted", "backgroundServicesActive"):
        if report.get(key) is not False:
            raise ValueError(f"O CORE iniciou um recurso sem autorização ou não informou seu estado: {key}.")
    if report.get("handModelBytes") != HAND_BYTES or report.get("handModelSha256") != HAND_SHA256:
        raise ValueError("O rastreamento não corresponde ao modelo de mãos fixado no CORE.")
    controls = report.get("mouseControlsOpenWithoutPermissionPrompt")
    activation = report.get("mouseScreenWaitsForExplicitActivation")
    unavailable = report.get("mouseScreenReportsUnsupportedHardware")
    if (any(type(value) is not bool for value in (controls, activation, unavailable)) or
            controls == unavailable or activation != controls):
        raise ValueError("A tela do mouse não confirmou controles sem permissão, ativação explícita ou limitação real da câmera.")


def validate_global_boundary(report: dict) -> None:
    # This proves a real Service refusal only. Do not promote it to proof of
    # physical hand tracking, successful global gestures or Play Protect approval.
    for key in ("cameraPermissionRemainedDenied", "realServiceRejectedDeniedCamera", "serviceStopped",
                "persistentCameraNotificationAbsent", "voiceStayedInactive"):
        if report.get(key) is not True:
            raise ValueError(f"Falta confirmação da recusa segura do mouse: {key}.")
    if type(report.get("androidApi")) is not int or report["androidApi"] < 26:
        raise ValueError("O teste de recusa não informou uma API Android compatível.")


def validate_extensions(report: dict) -> None:
    # The Android test exercises the actual data repository and native engines.
    # Keep semantic recognition quality separate from transport/file integrity.
    for key in ("realHttpsDownloadsVerified", "privateClassifierProcessedFixture", "privateOcrReadSyntheticText",
                "verifiedFilesReused", "activeLeaseBlockedRemoval", "realHttpsPartialResumeVerified",
                "optionalFilesRemoved", "handModelPreserved", "syntheticUserDataPreserved"):
        if report.get(key) is not True:
            raise ValueError(f"Falta confirmação dos pacotes opcionais: {key}.")
    if report.get("optionalDataBytes") != 11_530_361:
        raise ValueError("O relatório não corresponde ao tamanho dos três dados opcionais fixados.")


def network_control(name: str) -> str:
    value = adb("shell", "settings", "get", "global", name, timeout=30).decode().strip()
    if value not in ("0", "1"):
        raise ValueError(f"Estado original desconhecido de {name}; nenhuma rede será alterada.")
    return value


def set_network(enabled: bool) -> None:
    state = "enable" if enabled else "disable"
    for service in ("wifi", "data"):
        adb("shell", "svc", service, state, timeout=30)
    if not enabled:
        deadline = time.monotonic() + 10
        while network_control("wifi_on") != "0" or network_control("mobile_data") != "0":
            if time.monotonic() > deadline:
                raise RuntimeError("Wi-Fi e dados móveis não foram desativados no emulador.")
            time.sleep(0.25)


def wait_default_network(available: bool) -> None:
    """AOSP API 29 CI only: wait for the route, not just the Wi-Fi switch."""
    deadline = time.monotonic() + 30
    while True:
        state = adb("shell", "dumpsys", "connectivity", timeout=30).decode("utf-8", errors="replace")
        match = re.search(r"^Active default network:\s*(none|\d+)\s*$", state, re.MULTILINE)
        if match and (match.group(1) != "none") == available:
            return
        if time.monotonic() >= deadline:
            raise RuntimeError("A conectividade real do emulador não confirmou a etapa offline/online.")
        time.sleep(0.25)


def run(output: Path, *, allow_reset_emulator: bool = False, with_extensions: bool = False) -> dict:
    if not allow_reset_emulator:
        raise ValueError("Autorize somente um emulador descartável com --allow-reset-emulator.")
    app = ROOT / "app/build/outputs/apk/coreTest/app-coreTest.apk"
    tests = ROOT / "app/build/outputs/apk/androidTest/coreTest/app-coreTest-androidTest.apk"
    if not app.is_file() or not tests.is_file():
        raise ValueError("Compile assembleCoreTest assembleCoreTestAndroidTest com -PnexusTestBuildType=coreTest.")
    adb("wait-for-device")
    qemu = [adb("shell", "getprop", key, timeout=30).decode().strip()
            for key in ("ro.kernel.qemu", "ro.boot.qemu")]
    if "1" not in qemu:
        raise ValueError("A prova só aceita emulador descartável; celulares físicos são recusados.")
    installed = adb("shell", "pm", "list", "packages", APP_ID, timeout=30).decode().splitlines()
    if any(line.strip() in (f"package:{APP_ID}", f"package:{APP_ID}.test") for line in installed):
        raise ValueError("Use um emulador novo: já existe uma instalação do NEXUS que não será apagada.")
    original = {"wifi": network_control("wifi_on"), "data": network_control("mobile_data")}
    output.mkdir(parents=True, exist_ok=True)
    for name in ("core-instrumentation.txt", "core-observations.json", "extension-instrumentation.txt",
                 "core-instrumentation.stderr.txt", "extension-instrumentation.stderr.txt", "global-boundary-observations.json",
                 "extension-observations.json", "junit.xml", "network-restoration.json", "failure-logcat.txt",
                 "failure-activity-top.txt", "failure-activity-processes.txt", "failure-diagnostics.json"):
        (output / name).unlink(missing_ok=True)
    started = time.monotonic()
    status, failure = "failed", ""
    changed = False
    restoration_errors = []
    reports = {}
    try:
        # No -g, permission grants, app-data deletion, remote code or GGUF transfer.
        adb("install", str(app), timeout=300)
        adb("install", str(tests), timeout=300)
        changed = True
        set_network(False)
        wait_default_network(False)
        result = instrument(output / "core-instrumentation.txt", "-e", "class", CORE_CLASSES,
                            "-e", "nexusCoreSmoke", "true", "-e", "nexusGlobalBoundarySmoke", "true",
                            "-e", "nexusSceneSmoke", "true", COMPONENT, timeout=240)
        report = decode_report(result, "nexus_core_report", CORE_EXPECTED_TESTS)
        (output / "core-observations.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
        validate_core(report)
        reports["core"] = report
        boundary = decode_report(result, "nexus_global_boundary_report", CORE_EXPECTED_TESTS)
        validate_global_boundary(boundary)
        (output / "global-boundary-observations.json").write_text(json.dumps(boundary, indent=2) + "\n", encoding="utf-8")
        reports["globalBoundary"] = boundary
        if with_extensions:
            set_network(True)
            wait_default_network(True)
            result = instrument(output / "extension-instrumentation.txt", "-e", "class", EXTENSION_CLASS,
                                "-e", "nexusExtensionSmoke", "true", COMPONENT, timeout=900)
            report = decode_report(result, "nexus_extension_report", 1)
            (output / "extension-observations.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
            validate_extensions(report)
            reports["extensions"] = report
        status = "passed"
        return reports
    except BaseException as error:
        failure = f"{type(error).__name__}: {error}"
        collect_failure_diagnostics(output)
        raise
    finally:
        if changed:
            for service in ("wifi", "data"):
                try:
                    adb("shell", "svc", service, "enable" if original[service] == "1" else "disable", timeout=30)
                except (RuntimeError, subprocess.TimeoutExpired, OSError) as error:
                    restoration_errors.append(f"{service}: {type(error).__name__}")
            restoration = {"original": original, "restorationCommandErrors": restoration_errors}
            (output / "network-restoration.json").write_text(json.dumps(restoration, indent=2) + "\n", encoding="utf-8")
            if restoration_errors:
                print("Verifique a restauração da rede do emulador: " + ", ".join(restoration_errors), file=sys.stderr)
        suite = ET.Element("testsuite", name="AndroidCoreStorageEvidence", tests="1",
                           failures="0" if status == "passed" else "1", errors="0", skipped="0")
        case = ET.SubElement(suite, "testcase", classname="AndroidCoreStorageEvidence", name="coreAndOptionalData",
                             time=f"{time.monotonic() - started:.3f}")
        if status != "passed":
            ET.SubElement(case, "failure", message="CORE/storage evidence failed").text = failure
        ET.ElementTree(suite).write(output / "junit.xml", encoding="utf-8", xml_declaration=True)


def main() -> int:
    parser = argparse.ArgumentParser(description="Verifica CORE sem rede e downloads opcionais em emulador novo.")
    parser.add_argument("--output", type=Path, default=ROOT / "build/core-storage-smoke")
    parser.add_argument("--allow-reset-emulator", action="store_true")
    parser.add_argument("--with-extensions", action="store_true", help="Autoriza baixar e remover os 11,53 MB de dados de teste.")
    args = parser.parse_args()
    try:
        reports = run(args.output, allow_reset_emulator=args.allow_reset_emulator, with_extensions=args.with_extensions)
    except (OSError, ValueError, RuntimeError, subprocess.TimeoutExpired) as error:
        print(f"Prova CORE/armazenamento não aprovada: {error}", file=sys.stderr)
        return 1
    print("Provas Android aprovadas: " + ", ".join(reports))
    print("Este teste não valida gestos reais, voz, compatibilidade do A03 ou aprovação do Play Protect.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
