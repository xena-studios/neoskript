# NeoSkript Conformance Coverage

Reference: **Skript 2.15.3** — generated from `inventory.json` by `coverage.py`.

Tiers: **`done`** = implemented and covered by a behaviour test (strongest). **`parse-verified`** = implemented (getter/pattern translated faithfully from Skript) and confirmed to parse/register, but its runtime behaviour can't be asserted under MockBukkit (which stubs out most live Bukkit calls). **Verified = done + parse-verified** is the headline coverage. `external` = third-party-plugin syntax (excluded). `todo` = not yet implemented.

| Category | Documented | External | In-scope | Done | Parse-verified | Verified | % |
|---|---|---|---|---|---|---|---|
| effect | 148 | 5 | 143 | 40 | 94 | 134 | 93.7% |
| condition | 168 | 6 | 162 | 59 | 98 | 157 | 96.9% |
| expression | 515 | 21 | 494 | 113 | 340 | 453 | 91.7% |
| event | 167 | 2 | 165 | 148 | 11 | 159 | 96.4% |
| type | 135 | 16 | 119 | 72 | 38 | 110 | 92.4% |
| function | 48 | 0 | 48 | 48 | 0 | 48 | 100.0% |
| section | 9 | 2 | 7 | 5 | 1 | 6 | 85.7% |
| structure | 10 | 4 | 6 | 6 | 0 | 6 | 100.0% |
| **Total** | **1200** | **56** | **1144** | **491** | **582** | **1073** | **93.8%** |

_Verified 1073/1144 (93.8%) — of which behaviour-tested (done) 491 (42.9%). Goal: 100% of the 1144 in-scope entries._
