"""Offline compatibility and real refresh regressions; never read sibling data."""
import hashlib
import importlib.util
import pathlib
import shutil
import subprocess
import sys
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('public_sync', ROOT/'tools/sync_public_content.py')
assert spec is not None and spec.loader is not None
sync = importlib.util.module_from_spec(spec)
spec.loader.exec_module(sync)
CONTACT = '<a href="mailto:help@btc4.fun">mail</a><a href="https://t.me/example">tg</a><a href="https://www.instagram.com/example/">ig</a><a href="https://x.com/example">x</a>'

class ReviewRegressions(unittest.TestCase):
    def test_android26_core_uses_available_integer_operations(self):
        root = ROOT/'core/src/main/java/com/saebm/btcnfccustomer/core'
        self.assertNotIn('BigInteger.TWO', (root/'QuoteCalculator.java').read_text())
        self.assertNotIn('.intValueExact()', (root/'MarketResponse.java').read_text())

    def test_contact_extraction_executes_and_rejects_invalid_sources(self):
        links = sync.contact_links(CONTACT)
        self.assertEqual(links['email'], 'mailto:help@btc4.fun')
        self.assertEqual(links['telegram'], 'https://t.me/example')
        self.assertEqual(len(links), 6)
        for bad in ['', CONTACT + '<a href="https://t.me/other">duplicate</a>', CONTACT.replace('https://x.com/example', 'http://x.com/example')]:
            with self.assertRaises(ValueError):
                sync.contact_links(bad)

    def test_refresh_validates_before_modifying_bundle(self):
        with tempfile.TemporaryDirectory() as directory:
            isolated = pathlib.Path(directory)
            (isolated/'tools').mkdir()
            shutil.copy(ROOT/'tools/sync_public_content.py', isolated/'tools/sync_public_content.py')
            target = isolated/'app/src/main/assets/public'
            shutil.copytree(ROOT/'app/src/main/assets/public', target)
            templates = isolated/'source/bitcoin4funsite/templates'
            templates.mkdir(parents=True)
            for name in ['base', 'guide', 'terms', 'security', '_payment_details']:
                (templates/(name+'.html')).write_text('<main><p>synthetic public text</p></main>')
            command = [sys.executable, str(isolated/'tools/sync_public_content.py'), '--source-root', str(isolated/'source')]
            def inventory():
                return {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in target.iterdir()}
            for invalid in [None, CONTACT + '<a href="https://x.com/duplicate">x</a>']:
                contact = templates/'contact.html'
                if invalid is None:
                    contact.unlink(missing_ok=True)
                else:
                    contact.write_text(invalid)
                before = inventory()
                result = subprocess.run(command, capture_output=True, text=True)
                self.assertNotEqual(result.returncode, 0)
                self.assertEqual(inventory(), before)
            (templates/'contact.html').write_text(CONTACT)
            result = subprocess.run(command, capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stderr)
            sync.check_snapshot(target)
            import json
            self.assertEqual(json.loads((target/'contact-links.json').read_text()), sync.contact_links(CONTACT))
