import json
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]

class ContactDestinationContract(unittest.TestCase):
    def test_actual_java_allowlist_accepts_bundle_not_arbitrary_hosts(self):
        source = (ROOT / 'app/src/main/java/com/saebm/btcnfccustomer/MainActivity.java').read_text()
        guard = source.split('private void openContact(String url)', 1)[1].split('consent =', 1)[0]
        encoded = re.search(r'url\.matches\("([^"\n]+)"\)', guard).group(1)
        pattern = encoded.replace('\\\\', '\\')
        links = json.loads((ROOT / 'app/src/main/assets/public/contact-links.json').read_text())
        for url in links.values():
            self.assertIsNotNone(re.fullmatch(pattern, url), url)
        for url in ['https://evil.example/contact', 'https://btc.saeb.id.ir.evil.example/', 'https://btc.saeb.id.ir/?uid=123', 'https://btc4.fun/contact']:
            self.assertIsNone(re.fullmatch(pattern, url), url)
        instrumentation = (ROOT / 'app/src/androidTest/java/com/saebm/btcnfccustomer/MarketInteractionChecks.java').read_text()
        self.assertIn(links['form'], instrumentation)
        self.assertIn('contact-form', instrumentation)

if __name__ == '__main__':
    unittest.main()
