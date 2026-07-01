# NeoSkript Conformance Coverage

Reference: **Skript 2.15.3** — generated from `inventory.json` by `coverage.py`.

Tiers: **`done`** = implemented and covered by a behaviour test (strongest). **`parse-verified`** = implemented (getter/pattern translated faithfully from Skript) and confirmed to parse/register, but its runtime behaviour can't be asserted under MockBukkit (which stubs out most live Bukkit calls). **Verified = done + parse-verified** is the headline coverage. `external` = third-party-plugin syntax (excluded). `todo` = not yet implemented.

| Category | Documented | External | In-scope | Done | Parse-verified | Verified | % |
|---|---|---|---|---|---|---|---|
| effect | 148 | 5 | 143 | 29 | 94 | 123 | 86.0% |
| condition | 168 | 6 | 162 | 55 | 98 | 153 | 94.4% |
| expression | 515 | 21 | 494 | 73 | 338 | 411 | 83.2% |
| event | 167 | 2 | 165 | 148 | 11 | 159 | 96.4% |
| type | 135 | 10 | 125 | 68 | 38 | 106 | 84.8% |
| function | 48 | 0 | 48 | 48 | 0 | 48 | 100.0% |
| section | 9 | 2 | 7 | 5 | 1 | 6 | 85.7% |
| structure | 10 | 4 | 6 | 6 | 0 | 6 | 100.0% |
| **Total** | **1200** | **50** | **1150** | **432** | **580** | **1012** | **88.0%** |

_Verified 1012/1150 (88.0%) — of which behaviour-tested (done) 432 (37.6%). Goal: 100% of the 1150 in-scope entries._
