# NeoSkript Conformance Coverage

Reference: **Skript 2.15.3** — generated from `inventory.json` by `coverage.py`.

Tiers: **`done`** = implemented and covered by a behaviour test (strongest). **`parse-verified`** = implemented (getter/pattern translated faithfully from Skript) and confirmed to parse/register, but its runtime behaviour can't be asserted under MockBukkit (which stubs out most live Bukkit calls). **Verified = done + parse-verified** is the headline coverage. `external` = third-party-plugin syntax (excluded). `todo` = not yet implemented.

| Category | Documented | External | In-scope | Done | Parse-verified | Verified | % |
|---|---|---|---|---|---|---|---|
| effect | 148 | 0 | 148 | 23 | 0 | 23 | 15.5% |
| condition | 168 | 3 | 165 | 36 | 15 | 51 | 30.9% |
| expression | 515 | 8 | 507 | 43 | 24 | 67 | 13.2% |
| event | 167 | 1 | 166 | 108 | 0 | 108 | 65.1% |
| type | 135 | 2 | 133 | 55 | 0 | 55 | 41.4% |
| function | 48 | 0 | 48 | 40 | 0 | 40 | 83.3% |
| section | 9 | 0 | 9 | 3 | 0 | 3 | 33.3% |
| structure | 10 | 0 | 10 | 6 | 0 | 6 | 60.0% |
| **Total** | **1200** | **14** | **1186** | **314** | **39** | **353** | **29.8%** |

_Verified 353/1186 (29.8%) — of which behaviour-tested (done) 314 (26.5%). Goal: 100% of the 1186 in-scope entries._
