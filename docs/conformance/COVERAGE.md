# NeoSkript Conformance Coverage

Reference: **Skript 2.15.3** — generated from `inventory.json` by `coverage.py`.

Tiers: **`done`** = implemented and covered by a behaviour test (strongest). **`parse-verified`** = implemented (getter/pattern translated faithfully from Skript) and confirmed to parse/register, but its runtime behaviour can't be asserted under MockBukkit (which stubs out most live Bukkit calls). **Verified = done + parse-verified** is the headline coverage. `external` = third-party-plugin syntax (excluded). `todo` = not yet implemented.

| Category | Documented | External | In-scope | Done | Parse-verified | Verified | % |
|---|---|---|---|---|---|---|---|
| effect | 148 | 0 | 148 | 26 | 10 | 36 | 24.3% |
| condition | 168 | 3 | 165 | 36 | 30 | 66 | 40.0% |
| expression | 515 | 8 | 507 | 49 | 92 | 141 | 27.8% |
| event | 167 | 1 | 166 | 144 | 0 | 144 | 86.7% |
| type | 135 | 2 | 133 | 55 | 2 | 57 | 42.9% |
| function | 48 | 0 | 48 | 46 | 0 | 46 | 95.8% |
| section | 9 | 0 | 9 | 3 | 0 | 3 | 33.3% |
| structure | 10 | 0 | 10 | 6 | 0 | 6 | 60.0% |
| **Total** | **1200** | **14** | **1186** | **365** | **134** | **499** | **42.1%** |

_Verified 499/1186 (42.1%) — of which behaviour-tested (done) 365 (30.8%). Goal: 100% of the 1186 in-scope entries._
