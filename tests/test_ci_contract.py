import pathlib
import unittest
import re
import os
import subprocess
import tempfile
import textwrap
ROOT=pathlib.Path(__file__).resolve().parents[1]
class CiContract(unittest.TestCase):
 def test_window_policy_runs_in_hosted_emulator_after_install(self):
  text=(ROOT/'.github/workflows/android.yml').read_text()
  block=text.split('      - name: Exercise native callbacks, expiry and contact consent\n',1)[1].split('      - name:',1)[0]
  command='python3 tools/verify_window_policy.py --serial emulator-5554'
  self.assertIn(command,block)
  self.assertLess(block.index('python3 tools/run_device_checks.py'),block.index(command))
  self.assertIn('tee build/reports/window-policy.txt',block)
 def test_every_gradle_gate_is_explicitly_strict(self):
  text=(ROOT/'.github/workflows/android.yml').read_text()
  for line in text.splitlines():
   if './gradlew ' in line:
    self.assertIn('--dependency-verification strict',line)

 def test_avd_creation_and_emulator_share_explicit_home(self):
  text=(ROOT/'.github/workflows/android.yml').read_text()
  block=text.split('      - name: Exercise native callbacks, expiry and contact consent\n',1)[1].split('      - name:',1)[0]
  self.assertIn('export ANDROID_AVD_HOME="$RUNNER_TEMP/customer-avd"',block)
  self.assertLess(block.index('export ANDROID_AVD_HOME='),block.index('avdmanager create avd'))
  self.assertIn('test -s "$ANDROID_AVD_HOME/customer-checks.ini"',block)
 def test_market_lifecycle_instrumentation_is_executed(self):
  text=(ROOT/'.github/workflows/android.yml').read_text()
  self.assertIn('python3 tools/run_device_checks.py --serial emulator-5554',text)
  self.assertIn(':app:assembleDebugAndroidTest',text)
  self.assertIn('jinja2==3.1.6',text)
 def test_signing_step_rejects_each_missing_secret_and_cleans_up(self):
  text=(ROOT/'.github/workflows/android.yml').read_text()
  block=text.split('      - name: Sign with the existing user key\n',1)[1]
  script=textwrap.dedent(block.split('        run: |\n',1)[1].split('      - name:',1)[0])
  names=('ANDROID_RELEASE_KEYSTORE_BASE64','ANDROID_RELEASE_STORE_PASSWORD','ANDROID_RELEASE_KEY_ALIAS','ANDROID_RELEASE_KEY_PASSWORD')
  for missing in names:
   with self.subTest(missing=missing), tempfile.TemporaryDirectory() as directory:
    env={key:value for key,value in os.environ.items() if not key.startswith('ANDROID_RELEASE_')}
    env.update({name:'synthetic-test-value' for name in names if name!=missing})
    env['RUNNER_TEMP']=directory
    result=subprocess.run(['bash','-c',script],cwd=directory,env=env,capture_output=True,text=True)
    self.assertNotEqual(result.returncode,0)
    self.assertIn('signing secrets are missing',result.stderr)
    self.assertNotIn('synthetic-test-value',result.stdout+result.stderr)
    self.assertFalse((pathlib.Path(directory)/'btc-tag-checker-release.jks').exists())
 def test_workflow_is_independent_read_only_and_signing_is_opt_in(self):
  path=ROOT/'.github/workflows/android.yml'
  self.assertTrue(path.is_file(),'Independent workflow missing')
  text=path.read_text()
  self.assertIn('workflow_dispatch:',text)
  self.assertIn('pull_request:',text)
  self.assertIn('push:',text)
  self.assertIn('branches: [main]',text)
  self.assertIn('contents: read',text)
  self.assertIn("github.event_name == 'workflow_dispatch' && github.ref == 'refs/heads/main' && inputs.signed_release",text)
  self.assertIn('needs: tests-lint-debug',text)
  for variant in ['debug', 'release']:
   self.assertIn(f'app/build/outputs/apk/{variant}/btc4fun-{variant}.apk',text)
   self.assertIn(f'name: btc4fun-{variant}-${{{{ github.sha }}}}',text)
  self.assertIn('default: false',text)
  self.assertIn('validate-wrappers: true',text)
  self.assertIn(':core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug',text)
  self.assertIn('ANDROID_RELEASE_KEYSTORE_BASE64',text)
  for line in text.splitlines():
   if 'secrets.' in line:
    self.assertTrue(line.startswith('          ANDROID_RELEASE_'),line)
  self.assertEqual(len(re.findall(r'secrets\.',text)),4)
  for action in re.findall(r'uses: (\S+)',text):
   self.assertRegex(action,r'^[\w/-]+@[0-9a-f]{40}$')
  for forbidden in ['pull_request_target:', 'working-directory: android-customer', '--offline', 'CUSTOMER_']:
   self.assertNotIn(forbidden,text)
  self.assertIn("trap cleanup EXIT",text)
  self.assertIn('if: always()',text)
  self.assertIn('0o600',text)
  self.assertIn('if-no-files-found: error',text)
  self.assertIn('retention-days: 14',text)
  self.assertIn('7c4ccefc998b2407b22c2a7a3bc97e9b5f9baca3089886a5e71dcfef85b0fa3b',text)
  self.assertNotIn('keytool',text)
  self.assertIn("tags: ['v*']",text)
  release=text.split('  signed-release:',1)[1]
  self.assertIn("github.event_name == 'push' && startsWith(github.ref, 'refs/tags/v')",release)
  self.assertIn('fetch-depth: 0',release)
  self.assertLess(release.index('python3 tools/release_guard.py'),release.index('secrets.ANDROID_RELEASE_'))
  self.assertIn('git fetch --no-tags origin +refs/heads/main:refs/remotes/origin/main',release)
  self.assertEqual(text.count('contents: write'),1)
  self.assertNotIn('contents: write',text.split('  signed-release:',1)[0])
  self.assertIn('permissions:\n      contents: write',release)
  publish=release.split('      - name: Publish verified tag release\n',1)[1].split('      - uses:',1)[0]
  self.assertIn("if: ${{ github.event_name == 'push' && startsWith(github.ref, 'refs/tags/v') }}",publish)
  self.assertLess(release.index('python3 tools/verify_apk.py release'),release.index('Publish verified tag release'))
  for required in ['gh release create "$RELEASE_TAG"', '--verify-tag', '--draft=false', '--prerelease=false', '--latest', '--notes-file .github/release-notes.md', 'app/build/outputs/apk/release/btc4fun-release.apk', 'app/build/outputs/apk/release/SHA256SUMS']:
   self.assertIn(required,publish)
  self.assertNotIn('debug',publish)
  self.assertNotIn('--generate-notes',publish)
  self.assertTrue((ROOT/'.github/release-notes.md').is_file())
 def test_publication_shell_uses_exact_assets_and_noninterpolated_tag(self):
  import json
  text=(ROOT/'.github/workflows/android.yml').read_text()
  block=text.split('      - name: Publish verified tag release\n',1)[1].split('      - uses:',1)[0]
  script=textwrap.dedent(block.split('        run: |\n',1)[1])
  with tempfile.TemporaryDirectory() as directory:
   folder=pathlib.Path(directory)
   gh=folder/'gh'
   gh.write_text('#!/usr/bin/env python3\nimport json, os, sys\nwith open(os.environ["CALLS"], "a") as f: f.write(json.dumps(sys.argv[1:])+"\\n")\n')
   gh.chmod(0o755)
   calls=folder/'calls.jsonl'
   tag='v1.0.0'
   result=subprocess.run(['bash','-euo','pipefail','-c',script],cwd=ROOT,
     env=dict(os.environ,PATH=str(folder)+os.pathsep+os.environ['PATH'],CALLS=str(calls),RELEASE_TAG=tag),capture_output=True,text=True)
   self.assertEqual(result.returncode,0,result.stderr)
   commands=[json.loads(line) for line in calls.read_text().splitlines()]
   self.assertEqual(commands[0],['release','create',tag,
     'app/build/outputs/apk/release/btc4fun-release.apk','app/build/outputs/apk/release/SHA256SUMS',
     '--verify-tag','--title','btc4fun '+tag,'--notes-file','.github/release-notes.md',
     '--draft=false','--prerelease=false','--latest'])
   self.assertEqual(commands[1][:3],['release','view',tag])
 def test_release_cannot_fall_back_to_debug_identity(self):
  text=(ROOT/'app/build.gradle').read_text()
  self.assertIn("applicationId 'com.saebm.btcnfccustomer'",text)
  self.assertIn("applicationIdSuffix '.debug'",text)
  self.assertIn('throw new GradleException',text)
  self.assertNotIn('signingConfig signingConfigs.debug',text)
  self.assertIn("resValue 'string', 'app_name', 'btc4fun'",text)
  self.assertIn("resValue 'string', 'app_name', 'btc4fun (Debug)'",text)
  self.assertIn('btc4fun-${variant.buildType.name}.apk',text)
  self.assertIn('ANDROID_RELEASE_STORE_PASSWORD',text)
  self.assertIn("versionCode 11; versionName '1.1.5'",text)
if __name__=='__main__':unittest.main()
