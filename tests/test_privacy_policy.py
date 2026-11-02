"""Release privacy policy; debug-only visual evidence is an explicit exception."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]


class PrivacyPolicy(unittest.TestCase):
    # Window flags and Activity startup are exercised by WindowPolicyInstrumentation.
    def test_declared_domains_are_excluded_from_backup_and_transfer(self):
        root = ET.parse(ROOT / 'app/src/main/res/xml/data_extraction_rules.xml').getroot()
        for section in ('cloud-backup', 'device-transfer'):
            element = root.find(section)
            assert element is not None
            exclusions = {(node.get('domain'), node.get('path')) for node in element}
            self.assertEqual(exclusions, {(domain, '.') for domain in ('root', 'database', 'sharedpref', 'file', 'external')})
