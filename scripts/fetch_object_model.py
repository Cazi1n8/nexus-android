#!/usr/bin/env python3
"""Prepare the pinned official MediaPipe EfficientNet-Lite0 INT8 model.

Model download occurs during development/CI. The app processes imported photos
and periodic camera frames locally, without sending them to a server.
"""
from __future__ import annotations

import argparse
from dataclasses import dataclass
import hashlib
import os
from pathlib import Path
import tempfile
import urllib.request

ROOT = Path(__file__).resolve().parents[1]

@dataclass(frozen=True)
class Asset:
    url: str
    output: Path
    bytes: int
    sha256: str
    tflite: bool = False

MODEL = Asset(
    'https://storage.googleapis.com/mediapipe-models/image_classifier/efficientnet_lite0/int8/1/efficientnet_lite0.tflite',
    ROOT / 'app/src/main/assets/vision/efficientnet_lite0_int8.tflite',
    5_434_517, 'bc2ffe19c1118de0c0c2a9088992da5589722656e0fba81421385300a4a34b16', True,
)
TEST_IMAGE = Asset(
    'https://storage.googleapis.com/mediapipe-tasks/image_classifier/burger.jpg',
    ROOT / 'app/src/androidTest/assets/vision/burger.jpg',
    92_220, '08151ebb48f30a6cfbea02168ec0f3c0f1694d64c8d0f75ca08a63a89302853f',
)

class HttpsOnlyRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, fp, code, message, headers, target):
        if not target.startswith('https://'):
            raise ValueError('O download exige HTTPS em todos os redirecionamentos.')
        return super().redirect_request(request, fp, code, message, headers, target)


def validate(path: Path, spec: Asset) -> str:
    if path.is_symlink() or not path.is_file() or path.stat().st_size != spec.bytes:
        raise ValueError('Arquivo ausente, simbólico ou com tamanho diferente da revisão fixada.')
    data = path.read_bytes()
    if spec.tflite and data[4:8] != b'TFL3':
        raise ValueError('O modelo não possui cabeçalho TensorFlow Lite.')
    digest = hashlib.sha256(data).hexdigest()
    if digest != spec.sha256:
        raise ValueError('SHA-256 diferente da revisão oficial fixada.')
    return digest


def prepare(spec: Asset, source_file: Path | None = None) -> str:
    output = spec.output
    if output.is_symlink() or any(p.is_symlink() for p in output.parents):
        raise ValueError('O destino não pode usar links simbólicos.')
    if output.exists():
        return validate(output, spec)
    if source_file is not None:
        validate(source_file, spec)
    output.parent.mkdir(parents=True, exist_ok=True)
    descriptor, temporary_name = tempfile.mkstemp(prefix='.nexus-object-', dir=output.parent)
    temporary = Path(temporary_name)
    try:
        with os.fdopen(descriptor, 'wb') as destination:
            source = source_file.open('rb') if source_file is not None else urllib.request.build_opener(HttpsOnlyRedirect()).open(spec.url, timeout=90)
            with source:
                if source_file is None and not source.geturl().startswith('https://'):
                    raise ValueError('O download exige HTTPS.')
                count = 0
                while chunk := source.read(64 * 1024):
                    count += len(chunk)
                    if count > spec.bytes:
                        raise ValueError('O download ultrapassou o tamanho fixado.')
                    destination.write(chunk)
                destination.flush()
                os.fsync(destination.fileno())
        digest = validate(temporary, spec)
        # Do not replace another process/developer's file without validation.
        if output.exists():
            return validate(output, spec)
        os.link(temporary, output)
        return digest
    finally:
        temporary.unlink(missing_ok=True)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source-file', type=Path)
    parser.add_argument('--with-test-fixture', action='store_true')
    args = parser.parse_args()
    try:
        print('Modelo de classificação verificado:', prepare(MODEL, args.source_file))
        if args.with_test_fixture:
            print('Imagem oficial de teste verificada:', prepare(TEST_IMAGE))
    except (OSError, ValueError) as failure:
        print('Visão local não preparada:', failure)
        return 1
    return 0

if __name__ == '__main__':
    raise SystemExit(main())
