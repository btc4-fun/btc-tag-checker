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
        source = (ROOT / "core/src/main/java/com/saebm/btcnfccustomer/core/LookupClient.java").read_text()
        self.assertIn("api/addresses/", (ROOT / "core/src/main/java/com/saebm/btcnfccustomer/core/RequestPolicy.java").read_text() + source)
        self.assertIn("lookup_url", source)
        self.assertNotIn("customer-only", source.lower())

    def test_balance_lookup_never_uses_a_looser_serial_shape(self):
        policy = (ROOT / "core/src/main/java/com/saebm/btcnfccustomer/core/RequestPolicy.java").read_text()
        self.assertIn("SERIAL_PATTERN", policy)
        self.assertIn("validSerial", policy)
        # Both URLs are pinned to the one fixed origin; no host is ever taken from input.
        self.assertIn("https://btc4.fun/api/wallets/", policy)
        self.assertIn("https://btc4.fun/api/addresses/", policy)

    def test_image_host_is_allowlisted_and_never_loaded_in_app(self):
        source = (ROOT / "core/src/main/java/com/saebm/btcnfccustomer/core/LookupClient.java").read_text()
        self.assertIn('IMAGE_HOST = "btc4.fun"', source)
        activity = (ROOT / "app/src/main/java/com/saebm/btcnfccustomer/MainActivity.java").read_text()
        # Decision 2: the image is offered as a link after consent, never fetched into the app.
        self.assertNotIn("ImageView(this)", activity.split("renderLookup")[-1])
        self.assertIn('setTag("image-open")', activity)

    def test_unified_lookup_path_exists_for_serial_and_address(self):
        activity = (ROOT / "app/src/main/java/com/saebm/btcnfccustomer/MainActivity.java").read_text()
        for tag in ('"serial-input"', '"serial-submit"', '"address-input"', '"address-submit"'):
            self.assertIn(tag, activity)
        self.assertIn("fetchLookup(", activity)
        self.assertIn("lookupGate.invalidate()", activity)
        self.assertIn("result.removeAllViews()", activity)

    def test_home_groups_secondary_links_behind_one_explicit_control(self):
        source = (ROOT / "app/src/main/java/com/saebm/btcnfccustomer/MainActivity.java").read_text()
        self.assertIn('setTag("secondary-links-toggle")', source)
        self.assertIn('setVisibility(View.GONE)', source)
        self.assertIn('setContentDescription("راهنماها و ارتباطات کمکی")', source)


if __name__ == "__main__":
    unittest.main()
