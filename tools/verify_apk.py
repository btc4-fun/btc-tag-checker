#!/usr/bin/env python3
"""Validate Gradle output identity, bundled content, ZIP and Android signature."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def validate_identity(metadata, badging, variant):
    element, = metadata['elements']
    expected_file = f'BTC-Tag-Checker-{variant}.apk'
    if element['outputFile'] != expected_file:
        raise ValueError('Unexpected APK filename')
    match = re.search(r"package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'", badging)
    expected = (metadata['applicationId'], str(element['versionCode']), element['versionName'])
    if match is None or match.groups() != expected:
        raise ValueError('APK identity differs from Gradle output metadata')
    label = 'BTC Tag Checker' + (' (Debug)' if variant == 'debug' else '')
    if f"application-label:'{label}'" not in badging:
        raise ValueError('APK display label mismatch')


def validate_signer(output, expected):
    fingerprints = re.findall(r'^Signer #\d+ certificate SHA-256 digest: ([0-9a-f]{64})$', output, re.M)
    if not re.fullmatch(r'[0-9a-f]{64}', expected) or fingerprints != [expected]:
        raise ValueError('Release signing certificate differs from approved existing user key')


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('variant', choices=('debug', 'release'))
    args = parser.parse_args()
    folder = ROOT/f'app/build/outputs/apk/{args.variant}'
    metadata = json.loads((folder/'output-metadata.json').read_text())
    apk = folder/f'BTC-Tag-Checker-{args.variant}.apk'
    sdk = Path(os.environ.get('ANDROID_HOME') or os.environ['ANDROID_SDK_ROOT'])
    gradle = (ROOT/'app/build.gradle').read_text()
    tools_version = re.search(r"buildToolsVersion '([^']+)'", gradle).group(1)
    tools = sdk/'build-tools'/tools_version
    with zipfile.ZipFile(apk) as archive:
        if archive.testzip() is not None:
            raise ValueError('APK ZIP corruption')
        manifest = json.loads(archive.read('assets/public/manifest.json'))
        for page, digest in manifest['pages'].items():
            if hashlib.sha256(archive.read(f'assets/public/{page}.txt')).hexdigest() != digest:
                raise ValueError('Packaged public snapshot hash mismatch')
        for local in (ROOT/'app/src/main/assets/public').iterdir():
            if local.is_file() and archive.read('assets/public/'+local.name) != local.read_bytes():
                raise ValueError('APK contains stale public snapshot')
    badging = subprocess.check_output([str(tools/'aapt'), 'dump', 'badging', str(apk)], text=True)
    validate_identity(metadata, badging, args.variant)
    signature = subprocess.check_output([str(tools/'apksigner'), 'verify', '--verbose', '--print-certs', str(apk)], text=True)
    if args.variant == 'release':
        validate_signer(signature, os.environ.get('EXPECTED_RELEASE_CERT_SHA256', ''))
    digest = hashlib.sha256(apk.read_bytes()).hexdigest()
    (folder/'SHA256SUMS').write_text(f'{digest}  {apk.name}\n')
    print(badging)
    print(signature)
    print(f'{apk}: {apk.stat().st_size} bytes; SHA-256 {digest}')


if __name__ == '__main__':
    main()
