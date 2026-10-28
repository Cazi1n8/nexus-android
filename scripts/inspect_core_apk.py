#!/usr/bin/env python3
"""Measure an actual CORE APK and reject accidentally bundled optional payloads.

This is a package-content audit, not an APK signature check or a Play Protect
verdict. Use Android's apksigner/zipalign separately. No downloads or extraction
are performed; executable assets are checked by suffix and common file magic,
which cannot establish the origin or safety of every possible byte sequence.
"""
from __future__ import annotations

import argparse
from collections import Counter, defaultdict
from dataclasses import dataclass
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import struct
import sys
import tempfile
import zipfile

DEFAULT_MAX_BYTES = 150_000_000
SUPPORTED_ABIS = {
    "armeabi-v7a": (1, 40),
    "arm64-v8a": (2, 183),
    "x86": (1, 3),
    "x86_64": (2, 62),
}
EXECUTABLE_ASSET_SUFFIXES = {".dex", ".so", ".jar", ".class", ".apk", ".aab", ".aar",
                             ".dll", ".exe", ".wasm", ".o", ".a"}
OPTIONAL_WEIGHT_SUFFIXES = {".gguf", ".onnx", ".traineddata", ".tflite"}


@dataclass(frozen=True)
class HandModel:
    path: str
    bytes: int
    sha256: str


HAND_MODEL = HandModel(
    "assets/hand_landmarker.task", 7_819_105,
    "fbc2a30080c3c557093b5ddfc334698132eb341044ccee322ccf8bcf3607cde1",
)


def sha256_stream(stream) -> str:
    digest = hashlib.sha256()
    for chunk in iter(lambda: stream.read(128 * 1024), b""):
        digest.update(chunk)
    return digest.hexdigest()


def category(name: str) -> str:
    lower = name.lower()
    if lower.endswith(".gguf"):
        return "optional_generative_models"
    if lower.endswith(".traineddata"):
        return "optional_ocr_data"
    if lower.endswith((".onnx", ".tflite")) or lower.startswith("assets/vision/"):
        return "optional_vision_data"
    if lower.startswith("lib/") and lower.endswith(".so"):
        return "native_libraries"
    if lower.endswith(".dex"):
        return "dex"
    if name == HAND_MODEL.path:
        return "hand_model"
    if lower.startswith("assets/licenses/"):
        return "licenses"
    if lower.startswith("assets/"):
        return "other_assets"
    return "resources_manifest_metadata"


def unsafe_name(name: str) -> bool:
    parts = name.removesuffix("/").split("/")
    return (not name or name.startswith("/") or "\\" in name
            or any(part in ("", ".", "..") for part in parts))


def executable_magic(prefix: bytes) -> bool:
    return (prefix.startswith((b"\x7fELF", b"dex\n", b"cdex", b"MZ", b"\x00asm"))
            or prefix.startswith(b"\xca\xfe\xba\xbe"))


def inspect_apk(apk: Path, *, universal: bool = False,
                max_bytes: int = DEFAULT_MAX_BYTES) -> dict:
    if type(max_bytes) is not int or max_bytes <= 0:
        raise ValueError("O orçamento --max-bytes deve ser um inteiro positivo.")
    if not apk.is_file():
        raise ValueError("O APK informado não é um arquivo regular existente.")
    size = apk.stat().st_size
    with apk.open("rb") as source:
        apk_sha = sha256_stream(source)
    violations = []

    def reject(code: str, message: str, path: str | None = None) -> None:
        item = {"code": code, "message": message}
        if path is not None:
            item["path"] = path
        violations.append(item)

    if size > max_bytes:
        reject("apk_size_budget", f"APK com {size} bytes excede o orçamento de {max_bytes} bytes.")
    records, natives = [], []
    hand = {"path": HAND_MODEL.path, "expectedBytes": HAND_MODEL.bytes,
            "expectedSha256": HAND_MODEL.sha256, "present": False, "verified": False}
    with zipfile.ZipFile(apk) as archive:
        infos = archive.infolist()
        counts = Counter(info.filename for info in infos)
        for name, count in sorted(counts.items()):
            if count > 1:
                reject("duplicate_zip_entry", f"Nome ZIP repetido {count} vezes.", name)
        for info in infos:
            name = info.filename
            if unsafe_name(name):
                reject("unsafe_zip_path", "Caminho ZIP ambíguo ou que escaparia do diretório.", name)
            if info.is_dir():
                continue
            lower = name.lower()
            row = {"path": name, "bytes": info.file_size,
                   "compressedBytes": info.compress_size, "compressionMethod": info.compress_type,
                   "category": category(name)}
            records.append(row)
            suffix = Path(lower).suffix
            if suffix in OPTIONAL_WEIGHT_SUFFIXES or lower.startswith(("assets/vision/", "assets/ocr/")):
                reject("optional_data_in_core", "Dados opcionais não podem fazer parte do CORE.", name)
            # Read bounded headers only. Full decoding/unpacking of arbitrary
            # payloads is outside this audit; the hand asset is hashed below.
            with archive.open(info) as source:
                prefix = source.read(64)
            if prefix.startswith(b"GGUF") and suffix != ".gguf":
                reject("optional_data_in_core", "Pesos GGUF encontrados com outra extensão.", name)
            if name.startswith("assets/") and (suffix in EXECUTABLE_ASSET_SUFFIXES or executable_magic(prefix)):
                reject("executable_asset", "Payload executável extra em assets; exige revisão de empacotamento.", name)
            if name == HAND_MODEL.path:
                hand["present"] = True
                hand["bytes"] = info.file_size
                if info.file_size != HAND_MODEL.bytes:
                    reject("hand_model_size", "Modelo de mãos não tem o tamanho da revisão fixada.", name)
                else:
                    with archive.open(info) as source:
                        hand["sha256"] = sha256_stream(source)
                    if hand["sha256"] != HAND_MODEL.sha256:
                        reject("hand_model_hash", "SHA-256 do modelo de mãos diverge da revisão fixada.", name)
                    elif counts[name] == 1:
                        hand["verified"] = True
            if name.startswith("lib/") and lower.endswith(".so"):
                parts = name.split("/")
                abi = parts[1] if len(parts) == 3 else "invalid-layout"
                native = {**row, "abi": abi, "elfHeaderValid": False}
                natives.append(native)
                if abi not in SUPPORTED_ABIS:
                    reject("unsupported_native_abi", "ABI nativa desconhecida ou layout de biblioteca inválido.", name)
                    continue
                expected_class, expected_machine = SUPPORTED_ABIS[abi]
                minimum_header = 64 if expected_class == 2 else 52
                if (len(prefix) < minimum_header or prefix[:4] != b"\x7fELF"
                        or prefix[4] != expected_class or prefix[5] not in (1, 2)):
                    reject("invalid_native_elf", "Biblioteca vazia ou cabeçalho ELF incompatível com sua ABI.", name)
                    continue
                machine = struct.unpack_from("<H" if prefix[5] == 1 else ">H", prefix, 18)[0]
                if machine != expected_machine:
                    reject("invalid_native_elf", "Arquitetura ELF não corresponde ao diretório ABI.", name)
                    continue
                native.update(elfHeaderValid=True, elfClass=expected_class, elfMachine=machine)
        if not hand["present"]:
            reject("hand_model_missing", "O CORE precisa conter o modelo de mãos fixado.", HAND_MODEL.path)
        for required in ("AndroidManifest.xml", "classes.dex"):
            if not any(row["path"] == required and row["bytes"] > 0 for row in records):
                reject("required_apk_entry_missing", "Entrada Android obrigatória ausente ou vazia.", required)
    if not natives:
        reject("native_libraries_missing", "O CORE precisa incluir os motores nativos do rastreamento.")
    found_abis = {native["abi"] for native in natives}
    if universal and found_abis != set(SUPPORTED_ABIS):
        reject("universal_abis_missing", "O APK universal deve conter exatamente as quatro ABIs suportadas.")
    grouped = defaultdict(list)
    for row in records:
        grouped[row["category"]].append(row)
    categories = [{"category": name, "files": len(rows), "bytes": sum(row["bytes"] for row in rows),
                   "compressedBytes": sum(row["compressedBytes"] for row in rows)}
                  for name, rows in sorted(grouped.items())]
    abis = [{"abi": abi, "files": sum(native["abi"] == abi for native in natives),
             "bytes": sum(native["bytes"] for native in natives if native["abi"] == abi)}
            for abi in sorted(found_abis)]
    codes = {item["code"] for item in violations}
    return {
        "schemaVersion": 1, "auditTimestampUtc": datetime.now(timezone.utc).isoformat(),
        "executionStatus": "passed" if not violations else "failed", "apkPath": str(apk),
        "apkBytes": size, "apkSha256": apk_sha, "maxApkBytes": max_bytes,
        "universalRequested": universal, "entryCount": len(records),
        "uncompressedEntryBytes": sum(row["bytes"] for row in records),
        "compressedEntryBytes": sum(row["compressedBytes"] for row in records),
        "zipSignatureAlignmentDirectoryOverheadBytes": size - sum(row["compressedBytes"] for row in records),
        "categories": categories, "nativeAbis": abis, "nativeLibraries": natives,
        "handModel": hand, "top20": sorted(records, key=lambda row: (-row["bytes"], row["path"]))[:20],
        "checks": {"withinSizeBudget": size <= max_bytes, "handModelPinned": hand["verified"],
                   "optionalDataAbsent": "optional_data_in_core" not in codes,
                   "executableAssetsAbsent": "executable_asset" not in codes,
                   "zipNamesUnambiguous": not codes.intersection({"duplicate_zip_entry", "unsafe_zip_path"}),
                   "nativeElfHeadersValid": bool(natives) and all(native["elfHeaderValid"] for native in natives),
                   "universalAbisPresent": found_abis == set(SUPPORTED_ABIS) if universal else None},
        "violations": violations,
        "limitations": ["APK bytes are not installed Android storage or private user data.",
                        "This audit does not verify APK signing, ELF debug sections, full ELF semantics or Play Protect approval.",
                        "Asset suffix/header guards do not prove remote provenance or the absence of every possible executable format."],
    }


def write_report(path: Path, report: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    descriptor, temporary = tempfile.mkstemp(prefix=".nexus-apk-audit-", dir=path.parent)
    try:
        with os.fdopen(descriptor, "w", encoding="utf-8") as target:
            json.dump(report, target, ensure_ascii=False, indent=2)
            target.write("\n")
        os.replace(temporary, path)
    finally:
        Path(temporary).unlink(missing_ok=True)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--universal", action="store_true", help="Exigir as quatro ABIs suportadas.")
    parser.add_argument("--max-bytes", type=int, default=DEFAULT_MAX_BYTES,
                        help="Orçamento revisável do tamanho do APK, em bytes (padrão: 150000000).")
    args = parser.parse_args(argv)
    if args.output.resolve() == args.apk.resolve():
        parser.error("--output não pode substituir o próprio APK.")
    try:
        report = inspect_apk(args.apk, universal=args.universal, max_bytes=args.max_bytes)
    except (OSError, ValueError, zipfile.BadZipFile, RuntimeError, NotImplementedError) as error:
        report = {"schemaVersion": 1, "executionStatus": "failed", "apkPath": str(args.apk),
                  "violations": [{"code": "audit_failed", "message": str(error)}]}
    try:
        write_report(args.output, report)
    except OSError as error:
        print(f"Não foi possível gravar o relatório: {error}", file=sys.stderr)
        return 1
    print(json.dumps({"executionStatus": report["executionStatus"], "apkBytes": report.get("apkBytes"),
                      "apkSha256": report.get("apkSha256"), "violations": len(report["violations"]),
                      "report": str(args.output)}, ensure_ascii=False))
    return 0 if report["executionStatus"] == "passed" else 1


if __name__ == "__main__":
    raise SystemExit(main())
