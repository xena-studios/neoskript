# NeoSkript Conformance Coverage

Reference: **Skript 2.15.3** — generated from `inventory.json` by `coverage.py`.

Tiers: **`done`** = implemented and covered by a behaviour test (strongest). **`parse-verified`** = implemented (getter/pattern translated faithfully from Skript) and confirmed to parse/register, but its runtime behaviour can't be asserted under MockBukkit (which stubs out most live Bukkit calls). **Verified = done + parse-verified** is the headline coverage. `external` = third-party-plugin syntax (excluded). `todo` = not yet implemented.

| Category | Documented | External | In-scope | Done | Parse-verified | Verified | % |
|---|---|---|---|---|---|---|---|
| effect | 148 | 5 | 143 | 27 | 86 | 113 | 79.0% |
| condition | 168 | 6 | 162 | 51 | 84 | 135 | 83.3% |
| expression | 515 | 17 | 498 | 65 | 296 | 361 | 72.5% |
| event | 167 | 2 | 165 | 147 | 11 | 158 | 95.8% |
| type | 135 | 7 | 128 | 64 | 32 | 96 | 75.0% |
| function | 48 | 0 | 48 | 48 | 0 | 48 | 100.0% |
| section | 9 | 1 | 8 | 4 | 0 | 4 | 50.0% |
| structure | 10 | 2 | 8 | 6 | 0 | 6 | 75.0% |
| **Total** | **1200** | **40** | **1160** | **412** | **509** | **921** | **79.4%** |

_Verified 921/1160 (79.4%) — of which behaviour-tested (done) 412 (35.5%). Goal: 100% of the 1160 in-scope entries._
