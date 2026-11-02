#!/usr/bin/env python3
"""Run the framework window policy proof and reject silent instrumentation failures."""
import argparse
import subprocess

parser = argparse.ArgumentParser()
parser.add_argument('--adb', default='adb')
parser.add_argument('--serial', required=True)
args = parser.parse_args()
result = subprocess.run([args.adb, '-s', args.serial, 'shell', 'am', 'instrument', '-w',
    'com.saebm.btcnfccustomer.debug.test/com.saebm.btcnfccustomer.WindowPolicyInstrumentation'],
    text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=120)
print(result.stdout, end='')
if result.returncode or 'FAIL:' in result.stdout or 'PASS: window policy and actual debug Activity startup' not in result.stdout:
    raise SystemExit(1)
