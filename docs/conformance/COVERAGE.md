# NeoSkript Conformance Coverage

Reference: **Skript 2.15.3** — generated from `inventory.json` by `coverage.py`.

Tiers: **`done`** = implemented and covered by a behaviour test (strongest). **`parse-verified`** = implemented (getter/pattern translated faithfully from Skript) and confirmed to parse/register, but its runtime behaviour can't be asserted under MockBukkit (which stubs out most live Bukkit calls). **Verified = done + parse-verified** is the headline coverage. `external` = third-party-plugin syntax (excluded). `todo` = not yet implemented.

| Category | Documented | External | In-scope | Done | Parse-verified | Verified | % |
|---|---|---|---|---|---|---|---|
| effect | 148 | 5 | 143 | 41 | 94 | 135 | 94.4% |
| condition | 168 | 6 | 162 | 63 | 98 | 161 | 99.4% |
| expression | 515 | 21 | 494 | 129 | 340 | 469 | 94.9% |
| event | 167 | 2 | 165 | 154 | 11 | 165 | 100.0% |
| type | 135 | 16 | 119 | 73 | 38 | 111 | 93.3% |
| function | 48 | 0 | 48 | 48 | 0 | 48 | 100.0% |
| section | 9 | 2 | 7 | 5 | 1 | 6 | 85.7% |
| structure | 10 | 4 | 6 | 6 | 0 | 6 | 100.0% |
| **Total** | **1200** | **56** | **1144** | **519** | **582** | **1101** | **96.2%** |

_Verified 1101/1144 (96.2%) — of which behaviour-tested (done) 519 (45.4%). Goal: 100% of the 1144 in-scope entries._
