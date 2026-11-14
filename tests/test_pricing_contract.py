"""Source contracts for the pricing model shared with the site.

The behavioural proof lives in :core (QuoteCalculatorTest, MarketResponseTest). These checks
only pin the shape of the source, so a later edit cannot quietly reintroduce the double-counted
acquisition charge or drop one of the two fixed exchange fees.
"""
import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
CORE = ROOT / 'core/src/main/java/com/saebm/btcnfccustomer/core'
APP = ROOT / 'app/src/main/java/com/saebm/btcnfccustomer'


class PricingContract(unittest.TestCase):
    def test_acquisition_markup_is_applied_once_and_has_no_row_of_its_own(self):
        calculator = (CORE / 'QuoteCalculator.java').read_text()
        # The bitcoin rate carries 1.01 exactly once; the tether rate is used raw.
        self.assertIn('new BigDecimal("1.01")', calculator)
        self.assertIn('rawUsdt : rawUsdt.multiply(new BigDecimal("1.005"))', calculator)
        self.assertNotIn('1.0201', calculator)
        # The 1% lives in satoshi_toman only: a per-row acquisition charge would count it twice.
        self.assertNotIn('acquisition_sats', calculator)
        self.assertNotIn('acquisition_sats', (CORE / 'MarketResponse.java').read_text())

    def test_both_fixed_exchange_fees_are_pinned(self):
        calculator = (CORE / 'QuoteCalculator.java').read_text()
        self.assertIn('new BigDecimal("0.02")', calculator)
        self.assertIn('BigInteger.valueOf(423)', calculator)
        market = (CORE / 'MarketResponse.java').read_text()
        self.assertIn('equal("0.02",text(assumptions,"convert_usd"))', market)
        self.assertIn('BigInteger.valueOf(423)', market)
        # The network fee is the only cost the speed selector moves.
        self.assertIn('multiply(new BigDecimal("141"))', calculator)

    def test_old_and_new_models_are_told_apart_explicitly(self):
        market = (CORE / 'MarketResponse.java').read_text()
        self.assertIn('QuoteCalculator.Model.CURRENT:QuoteCalculator.Model.LEGACY', market)
        self.assertIn('equal(current?"1":"0.5",text(assumptions,"exchange_fee_percent"))', market)
        # A payload that publishes only one of the two fees is rejected, never read as zero.
        self.assertIn('if(current!=assumptions.has("transfer_sats"))', market)
        self.assertIn('else if(costs.has("convert_sats")||costs.has("transfer_sats"))', market)
        self.assertIn('CURRENT_ROW_KEYS', market)
        self.assertIn('LEGACY_ROW_KEYS', market)

    def test_quote_ui_renders_both_exchange_fees(self):
        activity = (APP / 'MainActivity.java').read_text()
        quote = activity.split('private void renderQuote')[1].split('private void openPublic')[0]
        self.assertIn('"convert_sats"', quote)
        self.assertIn('"transfer_sats"', quote)
        self.assertNotIn('acquisition_sats', quote)
        # An absent key is skipped instead of being rendered as a zero cost.
        self.assertIn('if(value==null||value.isJsonNull()) continue;', quote)

    def test_independent_oracle_mirrors_the_same_contract(self):
        oracle = (ROOT / 'tools/generate_tagged_quote_vectors.py').read_text()
        self.assertIn('Fraction(101, 100)', oracle)
        self.assertIn('transfer = 423', oracle)
        self.assertIn('Fraction(2, 100)', oracle)
        self.assertNotIn('"acquisition_sats": str(', oracle)
        self.assertIn('no_acquisition_row', oracle)


if __name__ == '__main__':
    unittest.main()
