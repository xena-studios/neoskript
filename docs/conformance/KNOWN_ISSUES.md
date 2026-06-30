# Known Issues (conformance)

Bugs found by the conformance effort that are implemented but not yet correct. Each is left
`todo`/`implemented:false` in the inventory until fixed, so coverage stays honest.

| Entry | Issue | Notes |
|---|---|---|
| `expression:distance` | `distance between A and B` fails to parse — the ` and ` is consumed by list-literal parsing before the distance pattern is tried. | Parser-precedence: registered patterns containing ` and ` need to be matched before top-level `and`/`,` list splitting. Affects any expression whose pattern contains ` and `. |
| `expression:block`, `expression:creature-entity-…` (event-values) | A bare `event-block`/`block`/`event-entity` does not resolve standalone — the indexed pattern dispatch doesn't bucket a pattern that begins with an optional group + alternation (`[the] (event-block\|block)`) under the right leading word. | Event-value expressions are effectively unreachable; fix the first-literal computation to index all alternation branches (and the post-optional word). |

## Fixed during the conformance effort

- **Candidate fall-through** (`parseLeaf`/`parseCondition`/`parsePrimary`): a matching candidate whose
  arguments failed to parse aborted the line instead of trying the next overlapping pattern. Fixed —
  this had made `send actionbar`/`send title` (shadowed by plain `send`) unreachable.
- **`SimpleArguments` null elements**: `List.copyOf` rejected the `null` entries used for absent
  optional `%-slots%`, so any effect/expression with an omitted optional argument threw an NPE at
  parse time. Fixed to a null-tolerant unmodifiable copy.
