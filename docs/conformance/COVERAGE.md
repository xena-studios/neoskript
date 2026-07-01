# NeoSkript Conformance Coverage

Reference: **Skript 2.15.3** — generated from `inventory.json` by `coverage.py`.

Tiers: **`done`** = implemented and covered by a behaviour test (strongest). **`parse-verified`** = implemented (getter/pattern translated faithfully from Skript) and confirmed to parse/register, but its runtime behaviour can't be asserted under MockBukkit (which stubs out most live Bukkit calls). **Verified = done + parse-verified** is the headline coverage. `external` = third-party-plugin syntax (excluded). `todo` = not yet implemented.

| Category | Documented | External | In-scope | Done | Parse-verified | Verified | % |
|---|---|---|---|---|---|---|---|
| effect | 148 | 4 | 144 | 27 | 77 | 104 | 72.2% |
| condition | 168 | 5 | 163 | 51 | 77 | 128 | 78.5% |
| expression | 515 | 14 | 501 | 65 | 280 | 345 | 68.9% |
| event | 167 | 2 | 165 | 147 | 11 | 158 | 95.8% |
| type | 135 | 6 | 129 | 63 | 32 | 95 | 73.6% |
| function | 48 | 0 | 48 | 48 | 0 | 48 | 100.0% |
| section | 9 | 1 | 8 | 3 | 0 | 3 | 37.5% |
| structure | 10 | 1 | 9 | 6 | 0 | 6 | 66.7% |
| **Total** | **1200** | **33** | **1167** | **410** | **477** | **887** | **76.0%** |

_Verified 887/1167 (76.0%) — of which behaviour-tested (done) 410 (35.1%). Goal: 100% of the 1167 in-scope entries._
