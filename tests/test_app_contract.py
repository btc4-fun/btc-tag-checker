import pathlib
import unittest
import xml.etree.ElementTree as ET
ROOT = pathlib.Path(__file__).resolve().parents[1]
A = '{http://schemas.android.com/apk/res/android}'
class AppContract(unittest.TestCase):
 def test_manifest_privacy_and_only_launcher_exported(self):
  manifest = ROOT / 'app/src/main/AndroidManifest.xml'
  self.assertTrue(manifest.exists(), 'Native application manifest missing')
  root=ET.parse(manifest).getroot()
  self.assertEqual({p.get(A+'name') for p in root.findall('uses-permission')}, {'android.permission.INTERNET','android.permission.NFC'})
  app=root.find('application')
  assert app is not None
  self.assertEqual(app.get(A+'allowBackup'),'false')
  self.assertEqual(app.get(A+'usesCleartextTraffic'),'false')
  self.assertEqual(app.get(A+'supportsRtl'),'true')
  self.assertEqual(len(app.findall('activity')),1)
 def test_read_only_scan_opt_in_and_background_lifecycle(self):
  source = ROOT / 'app/src/main/java/com/saebm/btcnfccustomer/MainActivity.java'
  self.assertTrue(source.exists(), 'Native UI missing')
  text=source.read_text()
  for required in ['tag.getId()', 'enableReaderMode', 'FLAG_READER_SKIP_NDEF_CHECK','onPause()', 'gate.invalidate()', 'gate.accepts(', 'new Client()', 'setPositiveButton', 'setTextDirection', 'ACTION_VIEW', 'BROWSABLE', 'scanGeneration != scanToken']:
   self.assertIn(required,text)
  for forbidden in ['WebView','Ndef.write','writeNdefMessage','Log.', 'SharedPreferences', 'getSharedPreferences']:
   self.assertNotIn(forbidden,text)
 def test_offline_pages_and_exact_payment_snapshot(self):
  assets=ROOT/'app/src/main/assets/public'
  for page in ['guide','terms','payment','security','costs']:
   self.assertTrue((assets/(page+'.txt')).is_file(), page+' offline content missing')
  payment=(assets/'payment.txt').read_text()
  for value in ['6219861819062158','930560611828006181828601','صائب ملایی']:
   self.assertIn(value,payment)
  self.assertIn('https://btc4.fun/costs',(assets/'costs.txt').read_text())
  self.assertTrue((assets/'manifest.json').is_file())
if __name__=='__main__':unittest.main()
