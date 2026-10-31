import pathlib
import unittest
import re
import os
import subprocess
import tempfile
import textwrap
ROOT=pathlib.Path(__file__).resolve().parents[1]
class CiContract(unittest.TestCase):
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
  self.assertIn('cdee481eac2d81904d0bed6281b956fe56e4f67ca37c5479592f4c89a18912f4',text)
  self.assertNotIn('keytool',text)
  self.assertNotIn('gh release',text)
 def test_release_cannot_fall_back_to_debug_identity(self):
  text=(ROOT/'app/build.gradle').read_text()
  self.assertIn("applicationId 'com.saebm.btcnfccustomer'",text)
  self.assertIn("applicationIdSuffix '.debug'",text)
  self.assertIn('throw new GradleException',text)
  self.assertNotIn('signingConfig signingConfigs.debug',text)
  self.assertIn("resValue 'string', 'app_name', 'BTC Tag Checker'",text)
  self.assertIn("resValue 'string', 'app_name', 'BTC Tag Checker (Debug)'",text)
  self.assertIn('BTC-Tag-Checker-${variant.buildType.name}.apk',text)
  self.assertIn('ANDROID_RELEASE_STORE_PASSWORD',text)
  self.assertIn("versionCode 1; versionName '0.1.0'",text)
if __name__=='__main__':unittest.main()
