# NeoSkript Conformance Coverage

Reference: **Skript 2.15.3** — generated from `inventory.json` by `coverage.py`.

Tiers: **`done`** = implemented and covered by a behaviour test (strongest). **`parse-verified`** = implemented (getter/pattern translated faithfully from Skript) and confirmed to parse/register, but its runtime behaviour can't be asserted under MockBukkit (which stubs out most live Bukkit calls). **Verified = done + parse-verified** is the headline coverage. `external` = third-party-plugin syntax (excluded). `todo` = not yet implemented.

| Category | Documented | External | In-scope | Done | Parse-verified | Verified | % |
|---|---|---|---|---|---|---|---|
| effect | 148 | 5 | 143 | 32 | 94 | 126 | 88.1% |
| condition | 168 | 6 | 162 | 56 | 98 | 154 | 95.1% |
| expression | 515 | 21 | 494 | 92 | 340 | 432 | 87.4% |
| event | 167 | 2 | 165 | 148 | 11 | 159 | 96.4% |
| type | 135 | 16 | 119 | 71 | 38 | 109 | 91.6% |
| function | 48 | 0 | 48 | 48 | 0 | 48 | 100.0% |
| section | 9 | 2 | 7 | 5 | 1 | 6 | 85.7% |
| structure | 10 | 4 | 6 | 6 | 0 | 6 | 100.0% |
| **Total** | **1200** | **56** | **1144** | **458** | **582** | **1040** | **90.9%** |

_Verified 1040/1144 (90.9%) — of which behaviour-tested (done) 458 (40.0%). Goal: 100% of the 1144 in-scope entries._
