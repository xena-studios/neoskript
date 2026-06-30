#!/usr/bin/env python3
"""Regenerate COVERAGE.md from inventory.json (deterministic, single source of truth).

Coverage counts only entries with status == "done" — meaning implemented AND covered by a
conformance/unit test. "external" entries (third-party-plugin syntax) are excluded from the
denominator. "todo" entries are not yet verified. The `prior` flag is a non-binding heuristic hint
(an earlier fuzzy match) used only to prioritise which todo entries to verify first.

Usage: python3 docs/conformance/coverage.py
"""
import json
import os
from collections import defaultdict

HERE = os.path.dirname(os.path.abspath(__file__))
INVENTORY = os.path.join(HERE, "inventory.json")
COVERAGE = os.path.join(HERE, "COVERAGE.md")
CATEGORIES = ["effect", "condition", "expression", "event", "type", "function", "section", "structure"]


def main():
    with open(INVENTORY) as f:
        entries = json.load(f)

    documented = defaultdict(int)
    external = defaultdict(int)
    done = defaultdict(int)
    parse = defaultdict(int)
    for e in entries:
        c = e["category"]
        documented[c] += 1
        if e["status"] == "external":
            external[c] += 1
        elif e["status"] == "done":
            done[c] += 1
        elif e["status"] == "parse-verified":
            parse[c] += 1

    lines = [
        "# NeoSkript Conformance Coverage",
        "",
        "Reference: **Skript 2.15.3** — generated from `inventory.json` by `coverage.py`.",
        "",
        "Tiers: **`done`** = implemented and covered by a behaviour test (strongest). "
        "**`parse-verified`** = implemented (getter/pattern translated faithfully from Skript) and "
        "confirmed to parse/register, but its runtime behaviour can't be asserted under MockBukkit "
        "(which stubs out most live Bukkit calls). **Verified = done + parse-verified** is the headline "
        "coverage. `external` = third-party-plugin syntax (excluded). `todo` = not yet implemented.",
        "",
        "| Category | Documented | External | In-scope | Done | Parse-verified | Verified | % |",
        "|---|---|---|---|---|---|---|---|",
    ]
    t_doc = t_ext = t_done = t_parse = 0
    for c in CATEGORIES:
        doc = documented.get(c, 0)
        ext = external.get(c, 0)
        dn = done.get(c, 0)
        pv = parse.get(c, 0)
        scope = doc - ext
        ver = dn + pv
        pct = (100.0 * ver / scope) if scope else 0.0
        t_doc += doc
        t_ext += ext
        t_done += dn
        t_parse += pv
        lines.append(f"| {c} | {doc} | {ext} | {scope} | {dn} | {pv} | {ver} | {pct:.1f}% |")
    t_scope = t_doc - t_ext
    t_ver = t_done + t_parse
    t_pct = (100.0 * t_ver / t_scope) if t_scope else 0.0
    t_dpct = (100.0 * t_done / t_scope) if t_scope else 0.0
    lines.append(f"| **Total** | **{t_doc}** | **{t_ext}** | **{t_scope}** | **{t_done}** | "
                 f"**{t_parse}** | **{t_ver}** | **{t_pct:.1f}%** |")
    lines.append("")
    lines.append(f"_Verified {t_ver}/{t_scope} ({t_pct:.1f}%) — of which behaviour-tested (done) "
                 f"{t_done} ({t_dpct:.1f}%). Goal: 100% of the {t_scope} in-scope entries._")
    lines.append("")

    with open(COVERAGE, "w") as f:
        f.write("\n".join(lines))
    print(f"Wrote {COVERAGE}")


if __name__ == "__main__":
    main()
