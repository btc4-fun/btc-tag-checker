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
  self.assertEqual({p.get(A+'name') for p in root.findall('uses-permission')}, {'android.permission.INTERNET','android.permission.NFC','android.permission.CAMERA'})
  app=root.find('application')
  assert app is not None
  self.assertEqual(app.get(A+'allowBackup'),'false')
  self.assertEqual(app.get(A+'usesCleartextTraffic'),'false')
  self.assertEqual(app.get(A+'supportsRtl'),'true')
  self.assertEqual(len(app.findall('activity')),2)
  self.assertEqual(app.findall('activity')[1].get(A+'exported'),'true')
  self.assertEqual(app.findall('activity')[0].get(A+'exported'),'false')
 def test_read_only_scan_opt_in_and_background_lifecycle(self):
  source = ROOT / 'app/src/main/java/com/saebm/btcnfccustomer/MainActivity.java'
  self.assertTrue(source.exists(), 'Native UI missing')
  text=source.read_text()
  for required in ['tag.getId()', 'enableReaderMode', 'FLAG_READER_SKIP_NDEF_CHECK','onPause()', 'gate.invalidate()', 'gate.accepts(', 'new Client()', 'setPositiveButton', 'setTextDirection', 'ACTION_VIEW', 'BROWSABLE', 'scanGate.accepts(token, active, inquiry, busy)']:
   self.assertIn(required,text)
  for forbidden in ['WebView','Ndef.write','writeNdefMessage','Log.', 'SharedPreferences', 'getSharedPreferences']:
   self.assertNotIn(forbidden,text)
 def test_reader_mode_survives_a_read_so_the_tag_link_never_opens_a_browser(self):
  # A tag that also stores a link must not hand that link to Android while the user is scanning.
  text = (ROOT / 'app/src/main/java/com/saebm/btcnfccustomer/MainActivity.java').read_text()
  read = text.split('private void onTagRead')[1].split('private void startQrScanner')[0]
  self.assertNotIn('stopReading()', read, 'releasing reader mode inside the read callback re-dispatches the tag link')
  self.assertNotIn('disableReaderMode', read)
  # The callback must be gated by the per-scan token, not by a shared mutable field that the next
  # scan overwrites. ScanGateTest in :core proves the accept/reject behaviour itself.
  self.assertIn('scanGate.accepts(token, active, inquiry, busy)', read)
  self.assertIn('scanGate.consumed()', read)
  self.assertNotIn('scanToken', text, 'a shared token field lets a late callback compare equal to a newer scan')
  begin = text.split('private void beginScan')[1].split('private void onTagRead')[0]
  self.assertIn('final long token = scanGate.begin()', begin)
  self.assertIn('onTagRead(tag, token)', begin)
  # The field is released only by leaving the screen, and the flag tracks the real adapter state.
  stop = text.split('private void stopReading')[1].split('private void confirmRequest')[0]
  self.assertIn('scanGate.stop()', stop)
  self.assertIn('readerOn', stop)
  self.assertIn('disableReaderMode', stop)
  self.assertIn('readerOn = true', text)
  self.assertIn('readerOn = false', text)
 def test_offline_pages_and_exact_payment_snapshot(self):
  assets=ROOT/'app/src/main/assets/public'
  for page in ['guide','terms','payment','security','costs']:
   self.assertTrue((assets/(page+'.txt')).is_file(), page+' offline content missing')
  payment=(assets/'payment.txt').read_text()
  for value in ['6219861819062158','930560611828006181828601','صائب ملایی']:
   self.assertIn(value,payment)
  self.assertIn('https://btc.saeb.id.ir/costs',(assets/'costs.txt').read_text())
  self.assertTrue((assets/'manifest.json').is_file())
if __name__=='__main__':unittest.main()
