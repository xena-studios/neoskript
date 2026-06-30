# Known Issues (conformance)

Bugs found by the conformance effort. Open issues are left `todo`/`implemented:false` in the
inventory until fixed, so coverage stays honest.

## Open

- **Alias completeness (compatibility):** many implemented entries support *one* valid alias of a
  Skript pattern, not *all* canonical aliases/optional forms. E.g. we accept `%entity% is in a vehicle`
  but not the canonical `is riding`; `clear the title of %player%` but not bare `clear title`;
  `x-coordinate` but not `x-coord`. For true drop-in compatibility every canonical form should parse.
  `FlipAuditTest` concretizes each flipped entry's canonical first pattern and reports which don't
  parse (see `docs/conformance/audit_concretize.py`) — that list is the alias-completeness backlog.
  These are *coverage-completeness* gaps, not mis-flips: the underlying behaviour is implemented and
  tested under the alias we registered.


## Fixed during the conformance effort

- **First-word optional suffix** (`PatternCompiler.leadingLiteral`): a pattern whose first word carried an optional suffix or alternation (`ender[ ]chest`, `fall[en]`, `x[-coordinate]`) was indexed under the truncated word, so inputs like `enderchest` missed the bucket. Fixed by treating such first words as wildcards.

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
