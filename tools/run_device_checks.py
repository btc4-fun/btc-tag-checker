#!/usr/bin/env python3
"""Run framework instrumentation on an explicitly selected emulator; fail on textual FAIL."""
import argparse
import subprocess

parser = argparse.ArgumentParser()
parser.add_argument('--serial', required=True)
args = parser.parse_args()
if not args.serial.startswith('emulator-'):
    parser.error('This runner only permits a disposable emulator, never a physical phone')
adb = ['adb', '-s', args.serial]
for apk in ['app/build/outputs/apk/debug/btc4fun-debug.apk', 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk']:
    subprocess.run(adb + ['install', '-r', apk], check=True, timeout=60)
result = subprocess.run(adb + ['shell', 'am', 'instrument', '-w', 'com.saebm.btcnfccustomer.debug.test/com.saebm.btcnfccustomer.VisualInstrumentation'], text=True, capture_output=True, timeout=180)
print(result.stdout)
print(result.stderr)
if result.returncode or 'PASS:' not in result.stdout or 'FAIL' in result.stdout:
    raise SystemExit('Instrumentation failed or did not report completion')
