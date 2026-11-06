"""Exercise release admission against real disposable Git histories."""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


class ReleaseGuard(unittest.TestCase):
    def test_release_admission_event_version_and_ancestry(self):
        script = ROOT / 'tools/release_guard.py'
        self.assertTrue(script.is_file(), 'Executable release admission guard missing')
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory)
            def git(*args):
                return subprocess.check_output(['git', *args], cwd=repo, text=True, stderr=subprocess.DEVNULL).strip()
            git('init', '-b', 'main')
            git('config', 'user.name', 'Release Test')
            git('config', 'user.email', 'release@example.invalid')
            (repo / 'app').mkdir()
            shutil.copyfile(ROOT / 'app/build.gradle', repo / 'app/build.gradle')
            git('add', '.')
            git('commit', '-m', 'fixture')
            sha = git('rev-parse', 'HEAD')
            git('update-ref', 'refs/remotes/origin/main', sha)
            git('tag', '-a', 'v1.1.7', '-m', 'fixture')
            def check(event, ref, signed, expected, commit=sha):
                env = dict(os.environ, GITHUB_EVENT_NAME=event, GITHUB_REF=ref,
                           GITHUB_SHA=commit, SIGNED_RELEASE=signed)
                result = subprocess.run(['python3', str(script)], cwd=repo, env=env, capture_output=True, text=True)
                self.assertEqual(result.returncode == 0, expected, (event, ref, result.stdout, result.stderr))
            check('push', 'refs/tags/v1.1.7', '', True)
            check('workflow_dispatch', 'refs/heads/main', 'true', True)
            for event, ref, signed in [('push', 'refs/heads/main', ''),
                                       ('pull_request', 'refs/tags/v1.1.7', ''),
                                       ('workflow_dispatch', 'refs/heads/other', 'true'),
                                       ('workflow_dispatch', 'refs/heads/main', 'false'),
                                       ('workflow_dispatch', 'refs/tags/v1.1.7', 'true'),
                                       ('push', 'refs/tags/v1.0.1', ''),
                                       ('push', 'refs/tags/v1.1.7-rc1', '')]:
                check(event, ref, signed, False)
            check('push', 'refs/tags/v1.1.7', '', False, '0' * 40)
            # A tag on an older main ancestor is valid even after main advances.
            (repo / 'next').write_text('next')
            git('add', '.')
            git('commit', '-m', 'advance main')
            next_sha = git('rev-parse', 'HEAD')
            git('update-ref', 'refs/remotes/origin/main', next_sha)
            git('checkout', '--detach', sha)
            check('push', 'refs/tags/v1.1.7', '', True)
            # A matching version/tag on an unrelated branch must fail closed.
            git('checkout', '--orphan', 'untrusted')
            git('commit', '-m', 'unrelated')
            untrusted = git('rev-parse', 'HEAD')
            git('tag', '-f', 'v1.1.3')
            check('push', 'refs/tags/v1.1.3', '', False, untrusted)
            # A tag must resolve to the checked-out event commit, not just exist.
            git('checkout', '--detach', sha)
            check('push', 'refs/tags/v1.1.3', '', False)
            git('tag', '-f', 'v1.1.3', sha)
            (repo / 'app/build.gradle').write_text("versionCode 4; versionName '01.0.0'")
            check('push', 'refs/tags/v1.1.3', '', False)


if __name__ == '__main__':
    unittest.main()
