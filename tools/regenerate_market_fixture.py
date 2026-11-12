#!/usr/bin/env python3
"""Regenerate core/src/test/resources/market.json from the site's own pricing code.

The fixture must be the payload the site's ``/api/market`` route would return, so it is
built by importing the site's ``pricing.quote_table`` rather than by hand-editing numbers.
Nothing is fetched from the network and no database is touched: the raw rates, fee rates
and timestamp below are the fixed synthetic inputs the fixture has always used.

Usage:
    python3 tools/regenerate_market_fixture.py --site ../btc_envelope_maker
    python3 tools/regenerate_market_fixture.py --site ../btc_envelope_maker --check
"""

from __future__ import annotations

import argparse
import importlib.util
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
FIXTURE = ROOT / "core/src/test/resources/market.json"
ANDROID_TEST_FIXTURE = ROOT / "app/src/androidTest/assets/market.json"

RAW_RATES = {"btc_toman": "10000000000", "usdt_toman": "100000"}
FEE_RATES = {"economy": "1.1", "medium": "2", "fast": "5"}
FETCHED_AT = "2026-11-01T00:00:00+00:00"
SOURCE = {
    "prices": "https://swapwallet.app/api/v1/market/prices",
    "fees": "https://mempool.space/api/v1/fees/recommended",
}


def load_pricing(site: Path):
    path = site / "bitcoin4funsite/pricing.py"
    if not path.is_file():
        raise SystemExit(f"site pricing module not found: {path}")
    spec = importlib.util.spec_from_file_location("site_pricing", path)
    if spec is None or spec.loader is None:
        raise SystemExit(f"cannot load site pricing module: {path}")
    module = importlib.util.module_from_spec(spec)
    sys.modules["site_pricing"] = module
    spec.loader.exec_module(module)
    return module


def build(pricing) -> dict:
    quote_options = {}
    for speed, rate in FEE_RATES.items():
        quote_options[speed] = {
            variant: pricing.quote_table(**RAW_RATES, fee_rate=rate, include_tag=enabled)
            for variant, enabled in (("no_tag", False), ("with_tag", True))
        }
    # Same key order the route produces: the table first, then the options and metadata.
    table = pricing.quote_table(**RAW_RATES, fee_rate=FEE_RATES["economy"])
    return {
        **table,
        "quote_options": quote_options,
        "raw_rates": dict(RAW_RATES),
        "status": "fresh",
        "fetched_at": FETCHED_AT,
        "source": dict(SOURCE),
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--site", required=True, type=Path,
                        help="path to the btc_envelope_maker checkout")
    parser.add_argument("--check", action="store_true",
                        help="exit non-zero when the committed fixtures differ")
    args = parser.parse_args()
    text = json.dumps(build(load_pricing(args.site)), indent=2, ensure_ascii=False) + "\n"
    if args.check:
        stale = [p for p in (FIXTURE, ANDROID_TEST_FIXTURE)
                 if not p.exists() or p.read_text(encoding="utf-8") != text]
        if stale:
            for path in stale:
                print(f"{path} is stale; run tools/regenerate_market_fixture.py", file=sys.stderr)
            return 1
        print("market fixtures match the site's pricing code")
        return 0
    for path in (FIXTURE, ANDROID_TEST_FIXTURE):
        path.write_text(text, encoding="utf-8")
        print(f"wrote {path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
