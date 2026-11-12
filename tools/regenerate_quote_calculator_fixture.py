#!/usr/bin/env python3
"""Refresh core/src/test/resources/quote_calculator.json from the site's pricing code.

This file is a copy of the site's own fixture (``bitcoin4funsite/tests/fixtures/
quote_calculator.json``). The site owns it; this tool only recomputes each case's
``expected`` block with the site's ``quote_for_principal`` / ``quote_for_budget`` so the
copy carries the current row fields (including ``operator_toman``) without anyone typing
numbers by hand. Case ids, inputs and ``raw_asset_toman`` are preserved.

When the site publishes its regenerated fixture, copy that file over this one and re-run
``--check``; a byte difference then means the two repos disagree.

Usage:
    python3 tools/regenerate_quote_calculator_fixture.py --site ../btc_envelope_maker
    python3 tools/regenerate_quote_calculator_fixture.py --site ../btc_envelope_maker --check
"""

from __future__ import annotations

import argparse
import importlib.util
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
FIXTURE = ROOT / "core/src/test/resources/quote_calculator.json"

# Row fields the app compares; the site's row also carries rates/assumptions/costs.
ROW_KEYS = (
    "principal_sats", "btc", "network_sats", "materials_sats", "post_sats", "tag_sats",
    "operator_sats", "operator_toman", "total_sats", "total_toman", "total_rial",
    "budget_toman", "remainder_toman",
)


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


def as_text(value) -> str | None:
    return None if value is None else str(value)


def refresh(pricing, fixture: dict) -> dict:
    cases = []
    for case in fixture["cases"]:
        inputs = case["inputs"]
        include_tag = bool(inputs.get("include_tag", False))
        if "expected_error" in case:
            cases.append(case)
            continue
        if case["mode"] == "principal":
            row = pricing.quote_for_principal(
                inputs["btc_toman"], inputs["usdt_toman"], inputs["fee_rate"],
                inputs["principal_sats"], include_tag=include_tag)
        else:
            row = pricing.quote_for_budget(
                inputs["btc_toman"], inputs["usdt_toman"], inputs["fee_rate"],
                inputs["budget_toman"], include_tag=include_tag)
        expected = dict(case["expected"])
        for key in ROW_KEYS:
            expected[key] = as_text(row[key])
        expected["rates"] = {k: as_text(v) for k, v in row["rates"].items()}
        expected["assumptions"] = {k: as_text(v) for k, v in row["assumptions"].items()}
        expected["costs"] = {k: as_text(v) for k, v in row["costs"].items()}
        cases.append({**case, "expected": expected})
    return {**fixture, "cases": cases}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--site", required=True, type=Path,
                        help="path to the btc_envelope_maker checkout")
    parser.add_argument("--check", action="store_true",
                        help="exit non-zero when the committed fixture differs")
    args = parser.parse_args()
    pricing = load_pricing(args.site)
    current = json.loads(FIXTURE.read_text(encoding="utf-8"))
    text = json.dumps(refresh(pricing, current), indent=1, ensure_ascii=False) + "\n"
    if args.check:
        if FIXTURE.read_text(encoding="utf-8") != text:
            print(f"{FIXTURE} is stale; run tools/regenerate_quote_calculator_fixture.py",
                  file=sys.stderr)
            return 1
        print("quote_calculator fixture matches the site's pricing code")
        return 0
    FIXTURE.write_text(text, encoding="utf-8")
    print(f"wrote {FIXTURE}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
