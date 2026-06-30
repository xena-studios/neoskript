# NeoSkript — Completion Plan

A concrete, phased plan to finish every item in [`GAPS.md`](GAPS.md): the remaining deeper-engine
subsystems and the content long tail. This complements [`PLAN.md`](PLAN.md) (the original roadmap)
and supersedes nothing — it's the execution plan for "100% of GAPS.md".

_Authored: 2026-06-30. Baseline: 93 tests green; all engine-tractable features and broad content done._

## Status (2026-06-30)

- **Phase A — Type system depth:** ✅ done. Converter graph, value serializers, and **type-aware
  comparisons** (numeric / `Comparable` / string-vs-display) wired into conditions.
- **Phase B — Async runtime:** ✅ done. Continuation interpreter gives `wait` anywhere in a trigger.
  Like Skript, there is no separate `async:` section type (off-thread work is an add-on concern).
- **Phase C — Persistence:** ✅ done. Flatfile + JDBC/SQLite backends sharing a `VariableCodec`,
  selected by config; rich-type serialization round-trips.
- **Phase D — Structures:** ✅ done. `aliases:` custom items; `import`/`using` recognised and skipped.
- **Phase E — Pattern niceties:** ✅ mostly done. Leading `the`/`a`/`an` articles tolerated; plural
  type names normalised in signatures. Remaining: matcher-level parse marks/tags from alternations.
- **Phase F — Content tail:** 🔄 continuous. Every named gap closed (`rgb()`, `date()`, colour & date
  types, `play sound`, apply/clear potion effects, thunder/weather-duration effects, `is in
  water/lava`, `can build`, `is thundering`, vector coordinates/length, item amount, capitalized
  text, local/typed functions). The remaining work is the long tail of additional same-shape content
  (more types, events, effects, expressions), each individually testable. **117 tests green.**

## Working principles

- **Never break green.** Every change keeps `./gradlew build` green; CI/Nightly must stay passing.
- **Test-first, per item.** Pure logic → JUnit; Bukkit-touching → MockBukkit (test classpath on the
  MockBukkit-supported Paper API). Each new syntax ships with a test.
- **One subsystem per phase, committed in small batches.** Conventional Commits; update `GAPS.md` in
  the same commit that implements an item so the doc never drifts.
- **Honesty over coverage.** If a thing can't be honored, the parser rejects it with a clear error
  rather than silently misbehaving (as `wait`-in-sections does today).
- **Definition of done:** `GAPS.md` "Remaining" sections are empty, a conformance corpus of real
  `.sk` scripts parses+runs, and a manual smoke test passes on a real Paper **and** Folia server.

## Dependency order

```
Phase A (Type system)  ──► Phase C (Persistence)
        │
        └──► Phase F (Content) can start anytime, runs in parallel
Phase B (Async runtime)  — independent, highest risk
Phase D (Structures), Phase E (Pattern niceties) — independent, low risk
```

Recommended sequence: **A → B → C → F (continuous) → D → E.** Content (F) is parallelizable and can
fill gaps between the harder phases.

---

## Phase A — Type system depth

**Goal:** a real converter/comparator/serializer graph so cross-type comparison, conversion, and
rich-type persistence work, and types can be parsed as literals.

**Design**
- `Converter<F, T>` SPI + `ConverterRegistry` with a BFS over registered edges to find a conversion
  path (e.g. `player → location`, `entity → location`, `string → number`). Cache resolved chains.
- `Comparator<A, B>` SPI + `ComparatorRegistry`; `Comparisons` becomes a thin facade that consults it,
  converting operands via the converter graph before comparing. Keeps current numeric/equality
  behaviour as the default comparator.
- `Serializer<T>` SPI on `Type<T>` (or a parallel registry): `serialize(T) → String` /
  `deserialize(String) → T`. Implement for location, vector, item (Bukkit's `ConfigurationSerializable`
  / base64 `ItemStack`), gamemode, world (by name), plus existing primitives.
- Wire `Renderer`, conditions, arithmetic, and `FlatFileVariableStore` through the registries.

**Files:** `neoskript-core` `type/` (Converter, ConverterRegistry, ComparatorRegistry, Serializer);
update `Comparisons` (move to core or keep in lang as a facade), `FlatFileVariableStore`,
`BuiltinModule.registerTypes`.

**Tests:** converter BFS pathfinding; comparator dispatch with conversion; serializer round-trips for
each rich type; persistence round-trip of an item/location variable.

**Acceptance:** `{home}` holding a location persists and reloads; `if player is location` style
conversions compare correctly; `GAPS.md` engine item #1 removed.

**Risk:** medium. Mitigate by keeping the current `Comparisons` behaviour as the fallback path.

## Phase B — Continuation-based async runtime (hardest)

**Goal:** `wait` anywhere (inside `if`/`loop`/`while`/functions) and `async:` / `run … async`
sections, on a Folia-safe scheduler.

**Design (chosen approach: explicit continuation, not thread-blocking)**
- Replace the recursive `IfSection.runAll` walk with a **resumable interpreter**: model execution as a
  stack of frames (statement-list + index, loop state, function scope). Running yields either
  `COMPLETED` or a `Suspension{ delayTicks | async }` carrying the captured continuation (the frame
  stack).
- On `wait`, the interpreter captures the stack and hands it to the `DelayScheduler` (extend it with
  `runOnRegion`/`runAsync`); on resume it pops back into the exact frame.
- `async:` runs its sub-frames via the async scheduler; the interpreter re-marshals to the region
  thread when the section ends (Folia-correct).
- **Rules:** functions used as expressions must be delay-free (Skript's rule) — the parser flags a
  `wait` in an expression-context function. Per-event state (`event-values`, `cancel`) is only valid
  before the first delay; document and enforce.
- Keep the synchronous fast path for triggers with no delays (no allocation/overhead regression).

**Files:** rework `Trigger`, `IfSection`/`WhileSection`/`LoopSection`, `FunctionDefinition` into the
frame model; new `Interpreter`/`Continuation`/`Frame` in `neoskript-core/runtime`; extend
`DelayScheduler` and the platform schedulers; remove the parser's "wait only at top level" guard.

**Tests (MockBukkit `performTicks`):** wait inside loop resumes correctly N times; wait inside
`if`/`else`; async section runs off-thread then resumes; loop-control + wait interaction; no
regression in the delay-free path (existing 93 tests stay green).

**Acceptance:** the deferred GAPS engine item #2 removed; a script with `loop … : wait 1 tick`
behaves correctly under MockBukkit.

**Risk:** high — it's an interpreter rewrite. Mitigate: land behind the existing tests, build the
frame model alongside the current one and switch over once parity is proven, extensive MockBukkit
coverage, smoke test on real Folia.

## Phase C — Persistence backends + rich serialization

**Goal:** SQL backends and serialization of non-simple values (depends on Phase A serializers).

**Design**
- `VariableStorage` SPI (load/save/`setAsync`) with implementations: `FlatFileVariableStore`
  (existing), `SqlVariableStore` (JDBC; SQLite default, MySQL/H2/Postgres optional) with an async,
  batched write-behind queue and a debounce, plus the existing periodic+shutdown flush.
- Config selects the backend (`config.yml`/`config.sk` keys). Values serialized via Phase A
  `Serializer`s; schema = `(name TEXT PRIMARY KEY, type TEXT, value BLOB/TEXT)`.

**Files:** `neoskript-core/variable/` (VariableStorage SPI, SqlVariableStore), shaded JDBC drivers in
`neoskript-plugin` (relocate to avoid clashes), plugin wiring + config.

**Tests:** in-memory SQLite/H2 round-trip incl. rich types; async flush ordering; backend selection.

**Acceptance:** GAPS engine item #5 removed.

**Risk:** medium. Mitigate: SQLite-in-memory for tests; keep flatfile the default.

## Phase D — Remaining structures

- **`aliases:`** — custom item-name aliases feeding the `item` type's parser (depends on the item
  type from done work). Parse the block into an alias map; the item parser consults it.
- **`import`/`using`** — `import` is a Java-class import used by reflection-style addons; implement as
  a no-op-with-warning unless/until a reflection bridge is added, and `using <experimental feature>`
  as a feature-flag toggle. Document scope.

**Tests:** alias resolves to the right material; import parses without error.
**Acceptance:** GAPS engine item #4 removed (with documented scope for `import`).

## Phase E — Pattern engine niceties

- **Articles:** make standalone `the`/`a`/`an` optional in matching (compile them into optional groups
  in `PatternCompiler`, or tolerate them in the matcher) without changing capture semantics.
- **Plurals:** treat a trailing `s` on type names / simple plural forms as optional.
- **Parse marks/tags:** let alternations expose which branch matched (a parse tag) to factories, so
  one pattern can drive multiple behaviours (e.g. `(ban|unban)`).

**Tests:** patterns match with/without articles; alternation tag selects the right branch; no
regression in existing `SyntaxPatternTest`.
**Acceptance:** GAPS engine item #6 removed.

**Risk:** medium — matcher changes are global; cover with the existing pattern tests plus new ones.

## Phase F — Content completion (parallel, continuous)

Mechanical, family-by-family. Each family: register syntax + tests, update `GAPS.md`.

- **Types:** entity, block, inventory, slot, biome, colour, enchantment, material, chunk, offline
  player, date, direction.
- **Effects:** play sound/particles, apply/clear potion effects, open/modify inventories, set weather
  amount/duration, scoreboard tags, ban/unban.
- **Expressions:** direction/vector math, item properties (amount/name/lore/enchants), weather state,
  target/looked-at block, nearby entities, time differences/formatting, more string/number formatting.
- **Events:** the remaining (leash, advancement, server ping, tab complete, …).
- **Conditions:** can build, generic `world is …`, is in water/lava, …
- **Functions:** `date()`, `rgb()`/colour.

**Approach:** drive coverage with a **conformance corpus** — collect real public `.sk` scripts into
`neoskript-testkit`, parse them all, and treat every "don't understand" as a work item. This both
prioritises by real usage and provides regression coverage.

**Acceptance:** GAPS "Remaining — content long tail" emptied; conformance corpus parses clean.

---

## Cross-cutting: testing & release

- **Unit + MockBukkit** as today; add the **conformance corpus** harness in `neoskript-testkit`.
- **Real-server smoke:** before declaring done, run on a real Paper and a real Folia server (the one
  thing CI/MockBukkit can't fully prove), exercising delays, commands, persistence, and events.
- **JMH benchmarks** (parse + event dispatch + variable access) to guard against regressions from the
  async rewrite and the type graph.
- Keep cutting releases via release-please as phases land; the API stays semver'd.

## Rough effort & sequencing

| Phase | Scope | Risk | Rel. effort |
|---|---|---|---|
| A — Type system | converter/comparator/serializer graph | med | L |
| B — Async runtime | continuation interpreter | high | XL |
| C — Persistence | SQL + rich serialization | med | M |
| D — Structures | aliases/import/using | low | S |
| E — Pattern niceties | articles/plurals/marks | med | M |
| F — Content | the long tail | low | L (continuous) |

Suggested order: **A, then F in parallel, then B, then C, then E, then D.** B is isolated last among
the hard items so the type system + persistence + most content are already solid and well-tested
before the interpreter rewrite.

## Definition of done

1. `GAPS.md` "Remaining" sections (content + deeper engine) are empty.
2. Conformance corpus parses and runs clean in `neoskript-testkit`.
3. Manual smoke test passes on real Paper **and** Folia.
4. Benchmarks show no regression vs. the pre-async baseline.
