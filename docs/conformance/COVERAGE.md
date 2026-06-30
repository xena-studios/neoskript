# NeoSkript Conformance Coverage

Reference: **Skript 2.15.3** — generated from `inventory.json` by `coverage.py`.

`done` = implemented **and** covered by a test (the conformance metric). `implemented` = present in NeoSkript per the coverage audit, test pending. `external` = third-party-plugin syntax (see [`EXTERNAL_PLUGIN_SYNTAX.md`](../EXTERNAL_PLUGIN_SYNTAX.md)), excluded from the denominator. `todo` = not yet implemented.

| Category | Documented | External | In-scope | Implemented | Done (tested) | Done % |
|---|---|---|---|---|---|---|
| effect | 148 | 0 | 148 | 20 | 18 | 12.2% |
| condition | 168 | 3 | 165 | 30 | 30 | 18.2% |
| expression | 515 | 8 | 507 | 32 | 29 | 5.7% |
| event | 167 | 1 | 166 | 13 | 0 | 0.0% |
| type | 135 | 2 | 133 | 24 | 24 | 18.0% |
| function | 48 | 0 | 48 | 40 | 40 | 83.3% |
| section | 9 | 0 | 9 | 3 | 3 | 33.3% |
| structure | 10 | 0 | 10 | 6 | 6 | 60.0% |
| **Total** | **1200** | **14** | **1186** | **168** | **150** | **12.6%** |

_Implemented (incl. tested): 168/1186 (14.2%). Goal: 100% of the 1186 in-scope entries, each implemented and tested._
