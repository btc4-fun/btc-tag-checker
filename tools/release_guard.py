#!/usr/bin/env python3
"""Fail closed before signing; execute from the checked-out repository root."""
import os
from pathlib import Path
import re
import subprocess
import sys


def git(*args):
    return subprocess.check_output(['git', *args], text=True).strip()


def main():
    event = os.environ.get('GITHUB_EVENT_NAME', '')
    ref = os.environ.get('GITHUB_REF', '')
    manual = (event == 'workflow_dispatch' and ref == 'refs/heads/main'
              and os.environ.get('SIGNED_RELEASE') == 'true')
    tagged = event == 'push' and ref.startswith('refs/tags/v')
    if not (manual or tagged):
        raise ValueError('Signing allowed only for opted-in main dispatch or version tag push')
    version, = re.findall(r"\bversionName '([^']+)'", Path('app/build.gradle').read_text())
    if not re.fullmatch(r'(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)', version):
        raise ValueError('Release requires a canonical stable version')
    head = git('rev-parse', 'HEAD')
    if head != os.environ.get('GITHUB_SHA'):
        raise ValueError('Checkout differs from event commit')
    subprocess.run(['git', 'merge-base', '--is-ancestor', head, 'refs/remotes/origin/main'], check=True)
    if tagged:
        if ref != f'refs/tags/v{version}':
            raise ValueError('Release tag differs from Gradle version')
        if git('rev-parse', '--verify', f'{ref}^{{commit}}') != head:
            raise ValueError('Release tag differs from event commit')
    print(f'Release admission passed: {version}')


if __name__ == '__main__':
    try:
        main()
    except (ValueError, subprocess.CalledProcessError, OSError) as error:
        sys.exit(f'Release admission refused: {error}')
