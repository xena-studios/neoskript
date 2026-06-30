# NeoSkript Conformance Coverage

Reference: **Skript 2.15.3** — generated from `inventory.json` by `coverage.py`.

`done` = implemented **and** covered by a test (the conformance metric). `implemented` = present in NeoSkript per the coverage audit, test pending. `external` = third-party-plugin syntax (see [`EXTERNAL_PLUGIN_SYNTAX.md`](../EXTERNAL_PLUGIN_SYNTAX.md)), excluded from the denominator. `todo` = not yet implemented.

| Category | Documented | External | In-scope | Implemented | Done (tested) | Done % |
|---|---|---|---|---|---|---|
| effect | 148 | 0 | 148 | 28 | 23 | 15.5% |
| condition | 168 | 3 | 165 | 36 | 36 | 21.8% |
| expression | 515 | 8 | 507 | 43 | 43 | 8.5% |
| event | 167 | 1 | 166 | 108 | 108 | 65.1% |
| type | 135 | 2 | 133 | 37 | 37 | 27.8% |
| function | 48 | 0 | 48 | 40 | 40 | 83.3% |
| section | 9 | 0 | 9 | 3 | 3 | 33.3% |
| structure | 10 | 0 | 10 | 6 | 6 | 60.0% |
| **Total** | **1200** | **14** | **1186** | **301** | **296** | **25.0%** |

_Implemented (incl. tested): 301/1186 (25.4%). Goal: 100% of the 1186 in-scope entries, each implemented and tested._
