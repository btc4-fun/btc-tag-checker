"""Release privacy policy; debug-only visual evidence is an explicit exception."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]


class PrivacyPolicy(unittest.TestCase):
    def test_release_window_is_secure_and_debug_exception_is_explicit(self):
        source = (ROOT / 'app/src/main/java/com/saebm/btcnfccustomer/MainActivity.java').read_text()
        self.assertIn('if (!BuildConfig.DEBUG)', source)
        self.assertIn('WindowManager.LayoutParams.FLAG_SECURE', source)
        self.assertLess(source.index('WindowManager.LayoutParams.FLAG_SECURE'), source.index('nfc = NfcAdapter'))

    def test_every_data_domain_is_excluded_from_backup_and_transfer(self):
        root = ET.parse(ROOT / 'app/src/main/res/xml/data_extraction_rules.xml').getroot()
        for section in ('cloud-backup', 'device-transfer'):
            element = root.find(section)
            assert element is not None
            exclusions = {(node.get('domain'), node.get('path')) for node in element}
            self.assertEqual(exclusions, {(domain, '.') for domain in ('root', 'database', 'sharedpref', 'file', 'external')})
