import importlib.util
import pathlib
import unittest
import hashlib
import json
import shutil
import subprocess
import sys
import tempfile
ROOT=pathlib.Path(__file__).resolve().parents[1]
class SnapshotTests(unittest.TestCase):
 def test_refresh_requires_explicit_source_root(self):
  text=(ROOT/'tools/sync_public_content.py').read_text()
  self.assertIn("'--source-root'",text)
  self.assertNotIn('parents[2]',text)
  self.assertNotIn('\nimport app',text)
 def test_bundled_snapshot_verifies_without_parent_checkout(self):
  with tempfile.TemporaryDirectory() as directory:
   isolated=pathlib.Path(directory)
   shutil.copytree(ROOT/'tools',isolated/'tools')
   shutil.copytree(ROOT/'app/src/main/assets/public',isolated/'app/src/main/assets/public')
   command=[sys.executable,str(isolated/'tools/sync_public_content.py'),'--check']
   result=subprocess.run(command,cwd=directory,capture_output=True,text=True)
   self.assertEqual(result.returncode,0,result.stderr)
   page=isolated/'app/src/main/assets/public/guide.txt'
   page.write_text(page.read_text()+'tampered')
   result=subprocess.run(command,cwd=directory,capture_output=True,text=True)
   self.assertNotEqual(result.returncode,0)
   self.assertIn('Snapshot hash mismatch',result.stderr)
 def test_extractor_only_keeps_readable_public_main_text(self):
  path=ROOT/'tools/sync_public_content.py'
  self.assertTrue(path.exists(),'Snapshot tool missing')
  spec=importlib.util.spec_from_file_location('sync_public_content',path)
  assert spec is not None and spec.loader is not None
  module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
  rendered='<header>outside</header><main><h1>Guide</h1><p>Safe <strong>words</strong></p><script>secret()</script><figure>secret image</figure><div hidden><p>secret questionnaire</p></div><input value="secret"><p dir="ltr">6219861819062158</p></main>'
  text=module.extract(rendered)
  self.assertIn('Safe words',text);self.assertIn('6219861819062158',text)
  for forbidden in ['outside','secret','<p']:
   self.assertNotIn(forbidden,text)
if __name__=='__main__':unittest.main()
