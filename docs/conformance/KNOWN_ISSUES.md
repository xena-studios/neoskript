# Known Issues (conformance)

Bugs found by the conformance effort. Open issues are left `todo`/`implemented:false` in the
inventory until fixed, so coverage stays honest.

## Open

- **First-word optional suffix in patterns** (`PatternCompiler`/indexed dispatch): a pattern whose
  first literal carries an optional suffix (e.g. `alphabetical[ly] sorted ...`) is bucketed under the
  truncated word (`alphabetical`), so input `alphabetically ...` misses the bucket. Workaround: spell
  the first word out or start the pattern with an alternation. Low impact; fix is to index all
  first-word variants.

## Fixed during the conformance effort

- **Distance / infix `and`** (`ExpressionParser`): `distance between A and B` was mis-split on its
  ` and ` by list-literal parsing. Fixed — when a split comes only from `and`/`or` (no top-level
  comma) and the whole string matches a registered expression, the single expression wins.
- **Leading optional space** (`PatternCompiler`): a pattern starting with an optional group
  (`[the] (event-block|block)`) compiled the following space as mandatory, so event-value
  expressions were unreachable without `the`. Fixed to pull a leading optional's trailing space
  inside the group (non-leading optionals like `x[-coordinate] of` keep their separator).
- **Candidate fall-through** (`parseLeaf`/`parseCondition`/`parsePrimary`): a matching candidate whose
  arguments failed to parse aborted the line instead of trying the next overlapping pattern. Fixed —
  this had made `send actionbar`/`send title` (shadowed by plain `send`) unreachable.
- **`SimpleArguments` null elements**: `List.copyOf` rejected the `null` entries used for absent
  optional `%-slots%`, so any effect/expression with an omitted optional argument threw an NPE at
  parse time. Fixed to a null-tolerant unmodifiable copy.
