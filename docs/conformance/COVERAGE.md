# NeoSkript Conformance Coverage

Reference: **Skript 2.15.3** — generated from `inventory.json` by `coverage.py`.

Tiers: **`done`** = implemented and covered by a behaviour test (strongest). **`parse-verified`** = implemented (getter/pattern translated faithfully from Skript) and confirmed to parse/register, but its runtime behaviour can't be asserted under MockBukkit (which stubs out most live Bukkit calls). **Verified = done + parse-verified** is the headline coverage. `external` = third-party-plugin syntax (excluded). `todo` = not yet implemented.

| Category | Documented | External | In-scope | Done | Parse-verified | Verified | % |
|---|---|---|---|---|---|---|---|
| effect | 148 | 4 | 144 | 27 | 72 | 99 | 68.8% |
| condition | 168 | 5 | 163 | 51 | 71 | 122 | 74.8% |
| expression | 515 | 14 | 501 | 65 | 212 | 277 | 55.3% |
| event | 167 | 2 | 165 | 144 | 0 | 144 | 87.3% |
| type | 135 | 6 | 129 | 55 | 2 | 57 | 44.2% |
| function | 48 | 0 | 48 | 46 | 0 | 46 | 95.8% |
| section | 9 | 1 | 8 | 3 | 0 | 3 | 37.5% |
| structure | 10 | 1 | 9 | 6 | 0 | 6 | 66.7% |
| **Total** | **1200** | **33** | **1167** | **397** | **357** | **754** | **64.6%** |

_Verified 754/1167 (64.6%) — of which behaviour-tested (done) 397 (34.0%). Goal: 100% of the 1167 in-scope entries._
