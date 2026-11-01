import pathlib
import unittest
ROOT=pathlib.Path(__file__).resolve().parents[1]
class CustomerTools(unittest.TestCase):
 def test_native_tools_have_explicit_fetch_and_lifecycle_fence(self):
  source=(ROOT/'app/src/main/java/com/saebm/btcnfccustomer/MainActivity.java').read_text()
  for token in ['this::showContact', 'this::showCosts', 'this::fetchMarket', 'marketClient.cancel()', 'marketGate.invalidate()', 'marketGate.accepts(generation)', 'market.usable(', 'market.fresh(', 'contact-links.json', 'سی‌ودو', 'کلید خصوصی']:
   self.assertIn(token,source)
  self.assertNotIn('WebView',source)
