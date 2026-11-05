"""The customer must not invent an address-balance API."""
import pathlib
import re
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
ACTIVITY = ROOT / "app/src/main/java/com/saebm/btcnfccustomer/MainActivity.java"


def method_body(source, name):
    """Extract one method's braces-balanced body, so order/absence checks stay local."""
    start = re.search(r"private (?:static )?\w[\w<>\[\]]* " + re.escape(name) + r"\(", source)
    if start is None:
        start = re.search(r"@Override public \w[\w<>\[\]]* " + re.escape(name) + r"\(", source)
    opening = source.index("{", start.end() - 1)
    depth = 0
    for index in range(opening, len(source)):
        if source[index] == "{":
            depth += 1
        elif source[index] == "}":
            depth -= 1
            if depth == 0:
                return source[opening + 1:index]
    raise AssertionError("unbalanced method: " + name)


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
        source = ACTIVITY.read_text()
        home = method_body(source, "showInquiry")
        # New contract: exactly two group entry points replace the collapsed link list.
        self.assertIn('"group-learn-button"', home)
        self.assertIn('"group-contact-button"', home)
        self.assertIn("امنیت، آموزش و شرایط", home)
        self.assertIn("اطلاعات پرداخت و تماس", home)
        # The collapsible control and its expandable list are gone, not merely hidden.
        self.assertNotIn("secondary-links-toggle", source)
        self.assertNotIn('"public-section"', source)
        self.assertNotIn("نمایش راهنماها و ارتباطات کمکی", source)
        # No home control jumps straight into terms/payment/security/guide.
        for page in ('"terms"', '"payment"', '"security"', '"guide"', '"costs"'):
            with self.subTest(page=page):
                self.assertNotIn(page, home)

    def test_group_pages_keep_owner_order_and_required_tags(self):
        source = ACTIVITY.read_text()
        learn = method_body(source, "showLearnGroup")
        for tag in ("learn-terms", "learn-security", "learn-guide", "learn-back", "learn-site"):
            with self.subTest(tag=tag):
                self.assertIn(tag, learn)
        # Farsi requirement: «اول هم شرابط باشه» — terms before security before guide.
        self.assertLess(learn.index("learn-terms"), learn.index("learn-security"))
        self.assertLess(learn.index("learn-security"), learn.index("learn-guide"))
        self.assertIn('pageButton("learn-terms", "terms")', learn)
        self.assertIn('pageButton("learn-security", "security")', learn)
        self.assertIn('pageButton("learn-guide", "guide"', learn)
        self.assertIn("آموزش؛ پاکت و بیت‌کوین چیست", learn)
        contact = method_body(source, "showContactGroup")
        for tag in ("contact-payment", "contact-costs", "contact-support", "contact-back"):
            with self.subTest(tag=tag):
                self.assertIn(tag, contact)
        self.assertLess(contact.index("contact-payment"), contact.index("contact-costs"))
        self.assertLess(contact.index("contact-costs"), contact.index("contact-support"))
        self.assertIn('pageButton("contact-payment", "payment")', contact)
        self.assertIn("this::showCosts", contact)
        self.assertIn("this::showContact", contact)

    def test_home_screen_has_exactly_one_entry_per_group(self):
        source = ACTIVITY.read_text()
        home = method_body(source, "showInquiry")
        self.assertEqual(home.count("groupButton("), 2)
        self.assertIn('"group-section"', home)
        # Group buttons use the shared 48dp helper and a fixed spoken suffix.
        helper = method_body(source, "groupButton")
        self.assertIn("button(parent, label, action, false)", helper)
        self.assertIn('"؛ باز کردن فهرست"', helper)
        self.assertIn("setContentDescription", helper)

    def test_back_navigation_returns_every_inner_screen_home(self):
        source = ACTIVITY.read_text()
        back = method_body(source, "onBackPressed")
        self.assertIn("super.onBackPressed()", back)
        self.assertIn("backToHome()", back)
        self.assertIn("if (inquiry) return false;", method_body(source, "backToHome"))
        # The shared inner-screen prologue is the one place that leaves inquiry mode.
        self.assertIn("inquiry = false", method_body(source, "innerScreen"))
        for method in ("showPublic", "showLearnGroup", "showContactGroup", "showContact"):
            with self.subTest(method=method):
                self.assertIn("innerScreen(", method_body(source, method))
        # Costs keeps its own prologue but must also leave inquiry mode.
        self.assertIn("inquiry=false", method_body(source, "showCosts"))

    def test_public_page_list_is_one_source_for_all_entry_points(self):
        source = ACTIVITY.read_text()
        declaration = re.search(r"PUBLIC_PAGES = \{([^}]*)\}", source)
        self.assertIsNotNone(declaration, "PUBLIC_PAGES allowlist declaration missing")
        assert declaration is not None
        self.assertEqual(
            declaration.group(1).replace('"', "").replace(" ", ""),
            "guide,terms,payment,security,costs",
        )
        # No screen re-hardcodes a list; each caller asks the one predicate.
        self.assertEqual(source.count("isPublicPage("), 4)  # declaration + pageButton + showPublic + openPublic
        self.assertNotIn('contains("guide"', source)
        self.assertNotIn("Arrays.asList(\"guide\"", source)


if __name__ == "__main__":
    unittest.main()
