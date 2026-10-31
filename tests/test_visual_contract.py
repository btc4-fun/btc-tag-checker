"""Offline source/resource gates; device layout is checked by VisualInstrumentation."""
import pathlib
import unittest
import xml.etree.ElementTree as ET
import hashlib
import os
import re
import subprocess
import tempfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
RES = ROOT / 'app/src/main/res'
A = '{http://schemas.android.com/apk/res/android}'

class VisualContract(unittest.TestCase):
    def test_site_palette_and_real_bundled_sahel(self):
        colors = RES / 'values/colors.xml'
        self.assertTrue(colors.exists(), 'website paper-and-ink palette is missing')
        values = {item.get('name'): item.text.lower() for item in ET.parse(colors).getroot()}
        self.assertEqual({key: values[key] for key in ['paper', 'ink', 'muted', 'accent', 'surface', 'line']},
                         dict(paper='#f7f4eb', ink='#292922', muted='#68665c', accent='#b94e2d', surface='#fffcf5', line='#dcd7c9'))
        for name in ['sahel_regular', 'sahel_bold']:
            font = RES / ('font/' + name + '.ttf')
            self.assertTrue(font.exists(), 'genuine Sahel TTF missing')
            self.assertEqual(font.read_bytes()[:4], b'\x00\x01\x00\x00')
        style = (RES / 'values/styles.xml').read_text()
        self.assertIn('@font/sahel', style)
        self.assertNotIn('#006B54', style)

    def test_font_bytes_match_verified_upstream_archive(self):
        for filename, digest in {
            'sahel_regular.ttf': '5de2fe8cd1995f10fb5a570b66e3ff40183f16bc8c692519b61e8c0281679675',
            'sahel_bold.ttf': 'd714fa224c92bc51d0e477337ef69e8818c2eff8f41e6698de35639e3857ae7b',
        }.items():
            self.assertEqual(hashlib.sha256((RES / 'font' / filename).read_bytes()).hexdigest(), digest)
        self.assertIn('SIL OPEN FONT LICENSE', (ROOT / 'app/src/main/assets/licenses/Sahel-LICENSE.txt').read_text())

    def test_actual_java_paragraph_regex_not_literal_backslashes(self):
        # Coverage of unchanged production expressions: doubled Java escapes are CORRECT.
        source = (ROOT / 'app/src/main/java/com/saebm/btcnfccustomer/MainActivity.java').read_text()
        split = re.search(r'body.split\((".*?")\)', source).group(1)
        match = re.search(r'trimmed.matches\((".*?")\)', source).group(1)
        code = '''class ParagraphContract {
          public static void main(String[] args) {
            String body="راهنما\\n\\n6219861819062158\\n \\nhttps://btc4.fun/costs";
            String[] parts=body.split(SPLIT);
            if(parts.length!=3) throw new AssertionError("paragraph count");
            for(int i=0;i<parts.length;i++) {
              String trimmed=parts[i];
              if(trimmed.matches(MATCH)!=(i==0)) throw new AssertionError("direction "+i);
            }
          }
        }'''.replace('SPLIT', split).replace('MATCH', match)
        java = str(pathlib.Path(os.environ['JAVA_HOME']) / 'bin/java') if 'JAVA_HOME' in os.environ else 'java'
        with tempfile.TemporaryDirectory() as tmp:
            probe = pathlib.Path(tmp) / 'ParagraphContract.java'
            probe.write_text(code)
            result = subprocess.run([java, str(probe)], capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stderr)

    def test_website_mark_and_comfortable_section_layout(self):
        source = (ROOT / 'app/src/main/java/com/saebm/btcnfccustomer/MainActivity.java').read_text()
        for contract in ['site-content', 'brand-mark', 'scan-section', 'result-section', 'public-section', 'R.font.sahel_regular', 'R.font.sahel_bold', 'requestApplyInsets()', 'dp(32)']:
            self.assertIn(contract, source)
        icon = ET.parse(RES / 'drawable/ic_customer.xml').getroot()
        groups = icon.findall('group')
        self.assertTrue(any(g.get(A + 'rotation') == '-12' for g in groups), 'site mark must tilt counterclockwise')
        self.assertNotIn('#006B54', (RES / 'drawable/ic_customer.xml').read_text())

if __name__ == '__main__':
    unittest.main()
