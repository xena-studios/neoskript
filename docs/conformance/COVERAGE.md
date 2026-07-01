# NeoSkript Conformance Coverage

Reference: **Skript 2.15.3** — generated from `inventory.json` by `coverage.py`.

Tiers: **`done`** = implemented and covered by a behaviour test (strongest). **`parse-verified`** = implemented (getter/pattern translated faithfully from Skript) and confirmed to parse/register, but its runtime behaviour can't be asserted under MockBukkit (which stubs out most live Bukkit calls). **Verified = done + parse-verified** is the headline coverage. `external` = third-party-plugin syntax (excluded). `todo` = not yet implemented.

| Category | Documented | External | In-scope | Done | Parse-verified | Verified | % |
|---|---|---|---|---|---|---|---|
| effect | 148 | 5 | 143 | 27 | 77 | 104 | 72.7% |
| condition | 168 | 6 | 162 | 51 | 77 | 128 | 79.0% |
| expression | 515 | 17 | 498 | 65 | 283 | 348 | 69.9% |
| event | 167 | 2 | 165 | 147 | 11 | 158 | 95.8% |
| type | 135 | 7 | 128 | 64 | 32 | 96 | 75.0% |
| function | 48 | 0 | 48 | 48 | 0 | 48 | 100.0% |
| section | 9 | 1 | 8 | 4 | 0 | 4 | 50.0% |
| structure | 10 | 2 | 8 | 6 | 0 | 6 | 75.0% |
| **Total** | **1200** | **40** | **1160** | **412** | **480** | **892** | **76.9%** |

_Verified 892/1160 (76.9%) — of which behaviour-tested (done) 412 (35.5%). Goal: 100% of the 1160 in-scope entries._
