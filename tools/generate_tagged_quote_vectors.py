#!/usr/bin/env python3
"""Regenerate core/src/test/resources/tagged_quote_vectors.json.

The oracle here is deliberately independent of the product code: it uses
``fractions.Fraction`` over exact ``Decimal`` inputs and its own ceiling and
budget-inversion helpers. It never imports the site's ``pricing.py`` and never
calls the Java calculator, so a shared bug cannot make the fixture agree with
the implementation.

Contract mirrored (site pricing.py, customer app QuoteCalculator):
  adjusted_btc   = btc_toman * 1.005
  adjusted_usdt  = usdt_toman * 1.005
  satoshi_toman  = adjusted_btc / 100_000_000
  network_sats   = ceil(fee_rate * 141)
  materials_sats = ceil(0.05 * adjusted_usdt / satoshi_toman)   # seal + paper + print, always
  envelope_sats  = ceil(0.06 * adjusted_usdt / satoshi_toman)   # postal only
  post_sats      = ceil(0.75 * adjusted_usdt / satoshi_toman)   # postal only
  tag_sats       = ceil(0.12 * adjusted_usdt / satoshi_toman) if include_tag else 0
  operator_sats  = ceil(0.15 * (principal + network + materials + envelope + post + tag))
  total_toman    = ceil((principal + network + materials + envelope + post + tag + operator) * satoshi_toman)
  total_rial     = total_toman * 10
  principal range: 501 .. 21_000_000 * 100_000_000 satoshis

Usage:
    python3 tools/generate_tagged_quote_vectors.py            # rewrite the fixture
    python3 tools/generate_tagged_quote_vectors.py --check    # fail if it is stale
"""

from __future__ import annotations

import argparse
import json
import sys
from decimal import Decimal
from fractions import Fraction
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
FIXTURE = ROOT / "core/src/test/resources/tagged_quote_vectors.json"

SATOSHIS_PER_BTC = 100_000_000
MAX_SATOSHIS = 21_000_000 * SATOSHIS_PER_BTC
MIN_SATOSHIS = 501

RATE_SETS = (
    {"btc_toman": "12345678901.12345678", "usdt_toman": "98765.4321", "fee_rate": "1.23456789"},
    {"btc_toman": "10000000000", "usdt_toman": "100000", "fee_rate": "1.1"},
)


def ceil_fraction(value: Fraction) -> int:
    """Smallest integer >= value, exactly, without float or Decimal rounding."""
    return -((-value.numerator) // value.denominator)


def exact(text: str) -> Fraction:
    return Fraction(Decimal(text))


def costs(btc_toman: str, usdt_toman: str, fee_rate: str, include_tag: bool):
    adjusted_btc = exact(btc_toman) * Fraction(1005, 1000)
    adjusted_usdt = exact(usdt_toman) * Fraction(1005, 1000)
    satoshi_toman = adjusted_btc / SATOSHIS_PER_BTC
    network = ceil_fraction(exact(fee_rate) * 141)
    materials = ceil_fraction(Fraction(5, 100) * adjusted_usdt / satoshi_toman)
    envelope = ceil_fraction(Fraction(6, 100) * adjusted_usdt / satoshi_toman)
    post = ceil_fraction(Fraction(75, 100) * adjusted_usdt / satoshi_toman)
    tag = ceil_fraction(Fraction(12, 100) * adjusted_usdt / satoshi_toman) if include_tag else 0
    return satoshi_toman, network, materials, envelope, post, tag


def btc_text(principal: int) -> str:
    return f"{principal // SATOSHIS_PER_BTC}.{principal % SATOSHIS_PER_BTC:08d}"


def row(principal: int, satoshi_toman: Fraction, network: int, materials: int,
        envelope: int, post: int, tag: int, budget: int | None = None) -> dict:
    subtotal = principal + network + materials + envelope + post + tag
    operator = ceil_fraction(Fraction(15, 100) * subtotal)
    total_sats = subtotal + operator
    total_toman = ceil_fraction(total_sats * satoshi_toman)
    return {
        "principal_sats": str(principal),
        "btc": btc_text(principal),
        "network_sats": str(network),
        "materials_sats": str(materials),
        "envelope_sats": str(envelope),
        "post_sats": str(post),
        "tag_sats": str(tag),
        "operator_sats": str(operator),
        # The operator's income in toman: 15% of the envelope costs, rounded up to a whole toman.
        "operator_toman": str(ceil_fraction(operator * satoshi_toman)),
        "total_sats": str(total_sats),
        "total_toman": str(total_toman),
        "total_rial": str(total_toman * 10),
        "budget_toman": None if budget is None else str(budget),
        "remainder_toman": "0" if budget is None else str(budget - total_toman),
    }


def total_toman(principal: int, satoshi_toman: Fraction, network: int, materials: int,
                envelope: int, post: int, tag: int) -> int:
    subtotal = principal + network + materials + envelope + post + tag
    operator = ceil_fraction(Fraction(15, 100) * subtotal)
    return ceil_fraction((subtotal + operator) * satoshi_toman)


def invert_budget(budget: int, satoshi_toman: Fraction, network: int, materials: int, envelope: int,
                  post: int, tag: int) -> int:
    """Largest principal in [501, MAX] whose total_toman fits the budget."""
    low, high = MIN_SATOSHIS, MAX_SATOSHIS
    while low < high:
        middle = (low + high + 1) // 2
        if total_toman(middle, satoshi_toman, network, materials, envelope, post, tag) <= budget:
            low = middle
        else:
            high = middle - 1
    return low


def principal_case(rates: dict, principal: int, include_tag: bool = True) -> dict:
    satoshi_toman, network, materials, envelope, post, tag = costs(
        rates["btc_toman"], rates["usdt_toman"], rates["fee_rate"], include_tag)
    return {
        "inputs": {**rates, "principal_sats": str(principal), "include_tag": include_tag},
        "mode": "principal",
        "expected": row(principal, satoshi_toman, network, materials, envelope, post, tag),
    }


def budget_case(rates: dict, budget: int, include_tag: bool = True) -> dict:
    satoshi_toman, network, materials, envelope, post, tag = costs(
        rates["btc_toman"], rates["usdt_toman"], rates["fee_rate"], include_tag)
    principal = invert_budget(budget, satoshi_toman, network, materials, envelope, post, tag)
    return {
        "inputs": {**rates, "budget_toman": str(budget), "include_tag": include_tag},
        "mode": "budget",
        "expected": row(principal, satoshi_toman, network, materials, envelope, post, tag, budget),
    }


def error_case(rates: dict, mode: str, value: int, reason: str, include_tag: bool = True) -> dict:
    key = "principal_sats" if mode == "principal" else "budget_toman"
    return {
        "inputs": {**rates, key: str(value), "include_tag": include_tag},
        "mode": mode,
        "expected_error": reason,
    }


def build() -> dict:
    cases: list[dict] = []
    for rates in RATE_SETS:
        satoshi_toman, network, materials, envelope, post, tag = costs(
            rates["btc_toman"], rates["usdt_toman"], rates["fee_rate"], True)
        minimum_total = total_toman(MIN_SATOSHIS, satoshi_toman, network, materials, envelope, post, tag)
        maximum_total = total_toman(MAX_SATOSHIS, satoshi_toman, network, materials, envelope, post, tag)
        cases.extend([
            principal_case(rates, MIN_SATOSHIS),
            principal_case(rates, 3000),
            principal_case(rates, MAX_SATOSHIS),
            budget_case(rates, minimum_total),
            budget_case(rates, 500001),
            budget_case(rates, maximum_total - 1),
            budget_case(rates, maximum_total),
            # The old 500-satoshi floor is now rejected, and a budget below the
            # 501-satoshi package must fail instead of inventing a principal.
            error_case(rates, "principal", 500, "principal below the 501-satoshi minimum"),
            error_case(rates, "budget", minimum_total - 1, "budget below the 501-satoshi package"),
        ])
    return {
        "source": "Synthetic Fraction oracle with algebraic budget inversion, independent of Java/Python product pricing",
        "generator": "tools/generate_tagged_quote_vectors.py",
        "contract": {
            "principal": "501 <= principal_sats <= 2100000000000000",
            "budget": "largest principal in [501, 2100000000000000] whose total_toman fits; below the 501-satoshi package it is an error",
            "costs": "materials 0.05 USD, envelope 0.06 USD, post 0.75 USD, tag 0.12 USD, network ceil(fee_rate*141), operator ceil(0.15*subtotal)",
        },
        "cases": cases,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true",
                        help="exit non-zero when the committed fixture differs from the oracle")
    args = parser.parse_args()
    text = json.dumps(build(), indent=2, ensure_ascii=False) + "\n"
    if args.check:
        current = FIXTURE.read_text(encoding="utf-8") if FIXTURE.exists() else ""
        if current != text:
            print(f"{FIXTURE} is stale; run tools/generate_tagged_quote_vectors.py", file=sys.stderr)
            return 1
        print(f"{FIXTURE} matches the independent oracle")
        return 0
    FIXTURE.write_text(text, encoding="utf-8")
    print(f"wrote {FIXTURE}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
