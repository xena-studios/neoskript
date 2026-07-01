# NeoSkript Conformance Coverage

Reference: **Skript 2.15.3** — generated from `inventory.json` by `coverage.py`.

Tiers: **`done`** = implemented and covered by a behaviour test (strongest). **`parse-verified`** = implemented (getter/pattern translated faithfully from Skript) and confirmed to parse/register, but its runtime behaviour can't be asserted under MockBukkit (which stubs out most live Bukkit calls). **Verified = done + parse-verified** is the headline coverage. `external` = third-party-plugin syntax (excluded). `todo` = not yet implemented.

| Category | Documented | External | In-scope | Done | Parse-verified | Verified | % |
|---|---|---|---|---|---|---|---|
| effect | 148 | 5 | 143 | 29 | 94 | 123 | 86.0% |
| condition | 168 | 6 | 162 | 54 | 98 | 152 | 93.8% |
| expression | 515 | 17 | 498 | 66 | 332 | 398 | 79.9% |
| event | 167 | 2 | 165 | 147 | 11 | 158 | 95.8% |
| type | 135 | 7 | 128 | 66 | 37 | 103 | 80.5% |
| function | 48 | 0 | 48 | 48 | 0 | 48 | 100.0% |
| section | 9 | 1 | 8 | 5 | 0 | 5 | 62.5% |
| structure | 10 | 2 | 8 | 6 | 0 | 6 | 75.0% |
| **Total** | **1200** | **40** | **1160** | **421** | **572** | **993** | **85.6%** |

_Verified 993/1160 (85.6%) — of which behaviour-tested (done) 421 (36.3%). Goal: 100% of the 1160 in-scope entries._
