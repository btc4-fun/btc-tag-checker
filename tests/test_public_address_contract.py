"""The customer must not invent an address-balance API."""
import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]


class PublicAddressContract(unittest.TestCase):
    def test_only_verified_tag_endpoint_is_documented_for_balance_lookup(self):
        readme = (ROOT / "README.md").read_text()
        self.assertIn("GET https://btc4.fun/api/tags/{UID}", readme)
        self.assertNotIn("/api/address/", readme)
        self.assertNotIn("/api/balance", readme)

    def test_address_lookup_contract_is_public_and_allowlisted(self):
        source = (ROOT / "core/src/main/java/com/saebm/btcnfccustomer/core/AddressClient.java").read_text()
        self.assertIn("api/addresses/", source)
        self.assertIn("lookup_url", source)
        self.assertNotIn("customer-only", source.lower())

    def test_home_groups_secondary_links_behind_one_explicit_control(self):
        source = (ROOT / "app/src/main/java/com/saebm/btcnfccustomer/MainActivity.java").read_text()
        self.assertIn('setTag("secondary-links-toggle")', source)
        self.assertIn('setVisibility(View.GONE)', source)
        self.assertIn('setContentDescription("راهنماها و ارتباطات کمکی")', source)


if __name__ == "__main__":
    unittest.main()
