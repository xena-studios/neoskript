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
    for e in entries:
        c = e["category"]
        documented[c] += 1
        if e["status"] == "external":
            external[c] += 1
        elif e["status"] == "done":
            done[c] += 1

    lines = [
        "# NeoSkript Conformance Coverage",
        "",
        "Reference: **Skript 2.15.3** — generated from `inventory.json` by `coverage.py`.",
        "",
        "`done` = implemented **and** covered by a test. `external` = third-party-plugin syntax "
        "(see [`EXTERNAL_PLUGIN_SYNTAX.md`](../EXTERNAL_PLUGIN_SYNTAX.md)), excluded from the denominator. "
        "`todo` = not yet verified.",
        "",
        "| Category | Documented | External | In-scope | Done (tested) | % |",
        "|---|---|---|---|---|---|",
    ]
    t_doc = t_ext = t_done = 0
    for c in CATEGORIES:
        doc = documented.get(c, 0)
        ext = external.get(c, 0)
        dn = done.get(c, 0)
        scope = doc - ext
        pct = (100.0 * dn / scope) if scope else 0.0
        t_doc += doc
        t_ext += ext
        t_done += dn
        lines.append(f"| {c} | {doc} | {ext} | {scope} | {dn} | {pct:.1f}% |")
    t_scope = t_doc - t_ext
    t_pct = (100.0 * t_done / t_scope) if t_scope else 0.0
    lines.append(f"| **Total** | **{t_doc}** | **{t_ext}** | **{t_scope}** | **{t_done}** | **{t_pct:.1f}%** |")
    lines.append("")
    lines.append(f"_Goal: 100% of the {t_scope} in-scope entries, each implemented and tested._")
    lines.append("")

    with open(COVERAGE, "w") as f:
        f.write("\n".join(lines))
    print(f"Wrote {COVERAGE}: {t_done}/{t_scope} in-scope done ({t_pct:.1f}%)")


if __name__ == "__main__":
    main()
