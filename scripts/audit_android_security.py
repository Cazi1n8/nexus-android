#!/usr/bin/env python3
"""Inspect NEXUS source XML or the actual APK's decoded manifest/resources.

This allowlist detects changes to permissions/component boundaries. It does not
certify absence of malware, audit every executable byte or predict Play Protect.
--production additionally refuses a test/debug certificate. Never reads a key.
"""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

ANDROID = "{http://schemas.android.com/apk/res/android}"
PACKAGE = "br.com.nexus.assistant"
PERMISSIONS = {"android.permission." + name for name in (
    "INTERNET", "ACCESS_NETWORK_STATE", "CAMERA", "RECORD_AUDIO",
    "FOREGROUND_SERVICE", "FOREGROUND_SERVICE_CAMERA",
    "FOREGROUND_SERVICE_MICROPHONE", "POST_NOTIFICATIONS")}
INTERNAL_PERMISSION = PACKAGE + ".DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
CAMERA_SERVICE = PACKAGE + ".gestures.global.GlobalMouseCameraService"
VOICE_SERVICE = PACKAGE + ".device.voice.BackgroundVoiceService"
ACCESSIBILITY_SERVICE = PACKAGE + ".gestures.global.NexusAccessibilityService"
BIND_ACCESSIBILITY = "android.permission.BIND_ACCESSIBILITY_SERVICE"


def attr(element: ET.Element | None, name: str, default=None):
    return default if element is None else element.get(ANDROID + name, default)


def qualified(name: str) -> str:
    return PACKAGE + name if name.startswith(".") else name


def zero(value: str | None) -> bool:
    # An omitted event/flag field defaults to zero in AccessibilityServiceInfo.
    if value is None:
        return True
    try:
        return int(value, 0) == 0
    except ValueError:
        return False


def inspect_xml(manifest: str, accessibility: str, network: str, *,
                final: bool = False) -> dict:
    root, config, network_root = map(ET.fromstring, (manifest, accessibility, network))
    if root.tag != "manifest" or config.tag != "accessibility-service" or network_root.tag != "network-security-config":
        raise ValueError("XMLs não correspondem a manifesto, acessibilidade e política de rede.")
    violations: list[dict] = []

    def reject(code: str, message: str):
        violations.append({"code": code, "message": message})

    permissions = sorted({attr(node, "name", "") for tag in (
        "uses-permission", "uses-permission-sdk-23", "uses-permission-sdk-m") for node in root.findall(tag)})
    for permission in set(permissions) - PERMISSIONS - {INTERNAL_PERMISSION}:
        reject("unexpected_permission", "Permissão exige revisão funcional: " + permission)
    for permission in PERMISSIONS - set(permissions):
        reject("required_permission_missing", "Permissão funcional ausente: " + permission)
    if root.get("package", PACKAGE) != PACKAGE:
        reject("package_changed", "O identificador do aplicativo existente foi alterado.")
    declared_internal = root.find(f"permission[@{ANDROID}name='{INTERNAL_PERMISSION}']")
    if INTERNAL_PERMISSION in permissions and (declared_internal is None or attr(declared_internal, "protectionLevel") not in ("signature", "0x2", "2")):
        reject("internal_permission_not_signature", "A permissão AndroidX deve ser protegida por assinatura.")
    sdk = root.find("uses-sdk")
    if final and (sdk is None or attr(sdk, "minSdkVersion") != "26" or attr(sdk, "targetSdkVersion") != "35"):
        reject("unexpected_sdk", "O manifesto final deve manter Android mínimo 26 e alvo 35 nesta versão.")
    application = root.find("application")
    if application is None:
        raise ValueError("Manifesto não possui application.")
    if attr(application, "debuggable", "false") != "false":
        reject("debuggable_build", "Artefato de distribuição não pode permitir depuração.")
    if attr(application, "testOnly", "false") != "false":
        reject("test_only_build", "Artefato testOnly não é distribuição instalável comum.")
    if attr(application, "allowBackup") != "false" or attr(application, "fullBackupContent") != "false":
        reject("personal_backup_enabled", "Histórico e preferências não devem ser exportados por backup automático.")
    if attr(application, "usesCleartextTraffic", "false") != "false":
        reject("cleartext_manifest", "O manifesto permite tráfego sem TLS.")
    if attr(application, "networkSecurityConfig") is None:
        reject("network_policy_missing", "A configuração de segurança de rede deve estar vinculada ao aplicativo.")
    base = network_root.find("base-config")
    if base is None or base.get("cleartextTrafficPermitted") != "false":
        reject("cleartext_base_config", "A política base de rede precisa proibir HTTP sem TLS.")
    for node in network_root.iter():
        if node.get("cleartextTrafficPermitted") == "true":
            reject("cleartext_exception", "A distribuição contém exceção de rede sem TLS.")
        if node.tag == "certificates" and node.get("src") == "user":
            reject("user_ca_trust", "A distribuição confia em certificados de usuário sem revisão.")
    components = []
    allowed_exported = {
        PACKAGE + ".MainActivity": None,
        ACCESSIBILITY_SERVICE: BIND_ACCESSIBILITY,
        "androidx.profileinstaller.ProfileInstallReceiver": "android.permission.DUMP",
    }
    for tag in ("activity", "activity-alias", "service", "receiver", "provider"):
        for node in application.findall(tag):
            name = qualified(attr(node, "name", ""))
            # Pre-31 components with an intent-filter could be implicitly exported.
            exported = attr(node, "exported", "true" if node.find("intent-filter") is not None else "false") == "true"
            permission = attr(node, "permission")
            components.append({"type": tag, "name": name, "exported": exported,
                               "permission": permission, "foregroundServiceType": attr(node, "foregroundServiceType")})
            if exported and (name not in allowed_exported or permission != allowed_exported[name]):
                reject("unexpected_exported_component", "Componente exportado exige revisão: " + name)
    by_name = {row["name"]: row for row in components}
    for name, expected_type, raw_type in ((CAMERA_SERVICE, "camera", "0x40"), (VOICE_SERVICE, "microphone", "0x80")):
        item = by_name.get(name)
        if item is None or item["exported"] or item["foregroundServiceType"] not in (expected_type, raw_type):
            reject("foreground_service_boundary", "Serviço privado/tipo foreground incorreto: " + name)
    accessible = by_name.get(ACCESSIBILITY_SERVICE)
    if accessible is None or not accessible["exported"] or accessible["permission"] != BIND_ACCESSIBILITY:
        reject("accessibility_binding", "A acessibilidade deve ser vinculada somente pelo sistema Android.")
    for field, expected in (("canRetrieveWindowContent", "false"), ("canPerformGestures", "true"), ("isAccessibilityTool", "false")):
        if attr(config, field) != expected:
            reject("accessibility_capability", f"Acessibilidade {field} precisa ser {expected}.")
    for field in ("canTakeScreenshot", "canRequestFilterKeyEvents", "canRequestTouchExplorationMode", "canRequestEnhancedWebAccessibility"):
        if attr(config, field, "false") != "false":
            reject("accessibility_extra_capability", "Capacidade não necessária ao mouse: " + field)
    for field in ("accessibilityEventTypes", "accessibilityFlags"):
        if not zero(attr(config, field)):
            reject("accessibility_events_or_flags", "O mouse não deve receber eventos ou flags adicionais: " + field)
    return {"schemaVersion": 1, "auditTimestampUtc": datetime.now(timezone.utc).isoformat(),
            "status": "passed" if not violations else "failed", "finalManifest": final,
            "package": root.get("package", PACKAGE), "versionName": attr(root, "versionName"),
            "versionCode": attr(root, "versionCode"), "permissions": permissions,
            "components": components, "accessibility": {key.removeprefix(ANDROID): value for key, value in config.attrib.items()},
            "violations": violations,
            "limitations": ["This is a manifest/resource boundary audit, not a Play Protect verdict.",
                            "Permission prompts, consent, camera/audio lifecycle and OEM behavior require runtime tests.",
                            "Dependencies, private data handling and native code require additional analysis."]}


def run(command: list[str]) -> str:
    result = subprocess.run(command, capture_output=True, text=True, timeout=120, check=False)
    if result.returncode:
        # Do not serialize arbitrary tool diagnostics (which might contain local paths/environment).
        raise ValueError("Ferramenta oficial falhou: " + Path(command[0]).name + f" (código {result.returncode}).")
    return result.stdout


def apk_resource(apk: Path, analyzer: str, name: str) -> str:
    path = run([analyzer, "resources", "value", "--config", "default", "--type", "xml", "--name", name, str(apk)]).strip()
    if not re.fullmatch(r"res/[\w/.-]+\.xml", path) or ".." in path.split("/"):
        raise ValueError("Nome do XML compilado inesperado: " + name)
    return run([analyzer, "resources", "xml", "--file", path, str(apk)])


def signing_summary(output: str) -> dict:
    dnames = re.findall(r"^Signer #\d+ certificate DN: (.+)$", output, re.MULTILINE)
    digests = re.findall(r"^Signer #\d+ certificate SHA-256 digest: ([a-fA-F0-9]{64})$", output, re.MULTILINE)
    if not dnames or len(dnames) != len(digests):
        raise ValueError("Não foi possível identificar os certificados do APK verificado.")
    debug = any(re.search(r"\bCN\s*=\s*Android Debug\b", name, re.I) for name in dnames)
    return {"signatureVerified": True, "certificateSha256": [digest.lower() for digest in digests],
            "testDebugCertificate": debug, "productionIdentityVerified": False}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    source = parser.add_mutually_exclusive_group(required=True)
    source.add_argument("--apk", type=Path)
    source.add_argument("--manifest", type=Path)
    parser.add_argument("--accessibility-xml", type=Path)
    parser.add_argument("--network-xml", type=Path)
    parser.add_argument("--apkanalyzer", default="apkanalyzer")
    parser.add_argument("--apksigner", default="apksigner")
    parser.add_argument("--production", action="store_true", help="Recusa certificado Android Debug; não aprova Play Protect.")
    parser.add_argument("--final", action="store_true", help="Exige metadados de SDK no manifesto mesclado.")
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    try:
        if args.apk:
            manifest = run([args.apkanalyzer, "manifest", "print", str(args.apk)])
            accessibility = apk_resource(args.apk, args.apkanalyzer, "nexus_accessibility_service")
            network = apk_resource(args.apk, args.apkanalyzer, "network_security_config")
            report = inspect_xml(manifest, accessibility, network, final=True)
            with args.apk.open("rb") as stream:
                digest = hashlib.file_digest(stream, "sha256").hexdigest()
            report["artifact"] = {"bytes": args.apk.stat().st_size, "sha256": digest}
            report["signing"] = signing_summary(run([args.apksigner, "verify", "--verbose", "--print-certs", str(args.apk)]))
            if args.production and report["signing"]["testDebugCertificate"]:
                report["violations"].append({"code": "debug_signing_identity", "message": "Certificado Android Debug não constitui identidade de distribuição de produção."})
                report["status"] = "failed"
        else:
            if not args.accessibility_xml or not args.network_xml or args.production:
                raise ValueError("Inspeção de fonte requer os dois XMLs; --production requer um APK assinado.")
            report = inspect_xml(args.manifest.read_text(), args.accessibility_xml.read_text(), args.network_xml.read_text(), final=args.final)
            report["input"] = "source_or_merged_xml_not_apk"
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n")
        print(json.dumps({"status": report["status"], "permissions": len(report["permissions"]),
                          "violations": report["violations"], "report": str(args.output)}, ensure_ascii=False))
        return 0 if report["status"] == "passed" else 1
    except (ValueError, OSError, ET.ParseError, subprocess.TimeoutExpired) as error:
        print(str(error), file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
