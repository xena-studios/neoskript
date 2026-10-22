# NeoSkript: Design & Roadmap

Status: v1 (decisions locked) · M0 done · 2026-10-21

## 1. Goal

A fast, modern, **clean-room** replacement for Skript. Existing `.sk` scripts run unchanged on **Paper and Folia**, with much faster load times, tick cost, and reloads.

### Non-goals for 1.0
- Loading Skript addons (planned for 1.x through the compat shim).
- Versions before 1.21.4, Spigot/Bukkit, or Java < 21.
- New language features that break Skript syntax. Any extensions are opt-in and come later.

## 2. Locked decisions

| # | Decision | Choice | Why |
|---|----------|--------|-----|
| D1 | Compatibility scope | Core Skript syntax at 1.0. Skript-addon shim (`ch.njol.skript.*`) in 1.x. Modern NeoSkript addon API from day one. | Ships sooner. The modern API gets proven because our own stdlib is built on it. |
| D2 | Codebase | Clean-room rewrite. Skript's *behavior* is the spec. | Skript's architecture (regex-style pattern trials, `Object[]` everywhere, single-thread assumptions) is the thing we're replacing. |
| D3 | License | **GPL-3.0** | Matches the ecosystem. It also lets us legally reuse Skript's GPL data (aliases, lang files, test scripts) with attribution. The code stays ours. |
| D4 | Execution | Compile each trigger to **JVM bytecode** (ASM), loaded as hidden classes | The JIT can inline across syntax elements. Hidden classes unload cleanly on reload. |
| D5 | Platforms | Paper **and** Folia at 1.0 | Folia is the differentiator, so the threading model is designed for it from the start. |
| D6 | Versions | MC **1.21.4+**, **Java 21**, ASM. Move to the JDK ClassFile API when the floor is Java 25. | Modern Paper APIs (Brigadier lifecycle commands, data components, registries) and a large audience. |
| D7 | Language | Java | Paper ecosystem norm, full control over codegen, no Kotlin stdlib to ship. |
| D8 | Extras | Compat corpus, benchmarks vs Skript, LSP, migration/diagnostic CLI | All of them reuse the standalone frontend. |
| D9 | Reference Skript | Pin the latest **stable** Skript release at M0, then track stable minor releases | The spec has to be a fixed target. |
| D10 | Commands | Take over `/skript` and `/sk`, with `/neoskript` as an alias. **Refuse to load** if Skript is also installed, and log a clear error. | A true drop-in, and no fights between two plugins over the same scripts and commands. |
| D11 | Folia cross-region reads | Error by default. A config option allows a blocking hop. | Safe by default, with an escape hatch. |
| D12 | Failed reload | **Keep the old scripts running.** Report the errors, swap nothing. | A typo shouldn't take a live server's scripts down. |
| D13 | Team / timeline | Solo, no fixed dates | Milestones are ordered by dependency, not dated. |
| D14 | Distribution | Modrinth + GitHub Releases. Docs are a separate Starlight site, outside this plan. | |
| D15 | Telemetry & updates | bStats, with `metrics: false` in config to turn it off. A Modrinth update checker, with `update-checker: false` to turn it off. | Usage data and update notices, both opt-out. |
| D16 | License header | `GPL-3.0-or-later` (SPDX header on every source file, checked by Spotless) | Same terms as Skript. |
| D17 | Test servers | Pinned to exact Paper/Folia builds and SHA-256 hashes, bumped on purpose | Paper ships daily, and build 211 of 26.3 broke Skript 2.17.0 the day it came out. |
| D18 | Where the Skript baseline runs | Paper 26.x only. Skript doesn't support Folia, and Skript 2.17.0 needs 1.21.5 or newer. NeoSkript-only checks run on all four servers. | |

## 3. Architecture

Gradle multi-module build:

```
neoskript-api          Public addon API: syntax registration, types, converters, events, codegen hooks
neoskript-frontend     Lexer, structure parser, pattern compiler + matcher, type checker, AST, diagnostics
neoskript-compiler     AST → IR → optimizations → ASM bytecode (hidden classes)
neoskript-runtime      Values, variables, storage, scheduler abstraction, continuations (waits)
neoskript-paper        Standard library: types, events, effects, expressions, conditions, aliases, commands
neoskript-plugin       Bootstrap, /sk commands, config, reload, metrics
neoskript-cli          Offline migration/diagnostic tool (frontend + syntax registry, no server)
neoskript-lsp          Language server (1.x)
neoskript-compat       ch.njol.skript.* shim for Skript addons (1.x)
test-harness           Headless Paper/Folia servers, differential runner, corpus, JMH + server benchmarks
```

**Rule:** `frontend` and `api` must not depend on Bukkit at runtime. The CLI and LSP rely on that.

### 3.1 Frontend (load-time speed)

Skript loads slowly because it tries each line against every registered pattern. Our frontend works differently:

1. **Structure pass:** indentation into sections and lines, plus `options:`, `aliases:`, `function`, `command`, and `on <event>:` structures. Each file is parsed in parallel.
2. **Pattern compiler:** every syntax pattern (`[the] (health|hp) of %livingentities%`) is compiled once into a shared automaton. Patterns are indexed by their leading literal keywords, so a line is checked against only a handful of candidates.
3. **Packrat matching:** placeholder sub-parses (`%type%`) are memoized by `(span, expected type)`. Nested expressions don't re-parse exponentially.
4. **Ambiguity resolution:** deterministic and **matching Skript's choice** (Skript resolves by registration order and priority). Each syntax element gets an explicit priority and order so the result is reproducible. The differential corpus enforces this.
5. **Type checking:** static types wherever inference works (most cases). Otherwise we fall back to dynamic `Object` with runtime conversion, so behavior still matches Skript's loose typing. Plurality (single vs. list), `and`/`or` lists, default expressions (`event-player`), and time states (`past`/`future`) are all modeled.
6. **Diagnostics:** errors use Skript's wording and line format, so existing tutorials and forum answers still apply. They also carry precise spans, which the LSP and CLI need.

### 3.2 Compiler (runtime speed)

- **IR:** a typed, SSA-ish IR with explicit control flow (if/else, loops, return, stop, exit). Optimizations: constant folding (including `{@options}`), dead branch removal, condition short-circuiting, and caching invariant lookups.
- **Codegen:** each trigger or function becomes one hidden class. Local variables `{_x}` become JVM locals when they don't live across a `wait`, and a frame slot when they do.
- **Two ways a syntax element gets into bytecode:**
  1. *Intrinsic:* the element emits bytecode directly through a codegen hook. This is for hot core syntax (math, comparisons, locals, `send`, `set`, loops).
  2. *Call-through:* the compiler emits a direct call to the element's `get`/`run`/`check`. These call sites are monomorphic, so the JIT inlines them anyway. **All addon syntax uses this path, so it always works.**
- **Waits:** a trigger that contains `wait` compiles into a **state machine**, like Kotlin coroutines. Locals spill into a frame object and the trigger resumes through the scheduler abstraction. Triggers without waits pay none of this cost.
- **Functions:** compiled to static methods. Calls to functions whose signature is known are direct invocations. Calls across scripts link through a `MethodHandle`/`MutableCallSite` so reloading one script doesn't recompile the others.
- **Reload:** compile everything new first, then swap atomically. The old hidden classes are released for GC. If a script fails to compile, its old version keeps running and the errors are reported (D12).

### 3.3 Runtime & threading (Paper + Folia)

There is one **scheduler abstraction** with two implementations (Paper, Folia):

| Context | Paper | Folia |
|---|---|---|
| Entity/player event | main thread | entity scheduler of that entity |
| Block/location event | main thread | region scheduler at that location |
| `every X` / `on load` / console command | main thread | global region scheduler |
| `wait` resume | main thread | same scheduler the trigger is bound to (following the entity if it moves) |

Rules on Folia:
- **Thread ownership checks:** each syntax element declares what it touches (entity, location, world, global). Code generated for Folia checks `isOwnedByCurrentRegion` only where needed.
- **Reads from another region** (`health of {otherPlayer}`) fail with a clear error by default. A config option allows a blocking hop for reads (D11).
- **Effects on an entity in another region** are dispatched to that entity's scheduler, fire-and-forget. A new opt-in section, `run on %entity/location%:`, gives explicit control.
- **Global variables** live in a concurrent store. Each single operation is atomic: `add 1 to {x}` compiles to an atomic compute. Sequences of operations are not atomic, and the docs say so.

### 3.4 Variables & storage (drop-in critical)

- Load and save **Skript's `variables.csv`, SQLite, and MySQL** formats, including Yggdrasil-serialized values (items, locations, and so on). Users can switch in both directions, so rolling back to Skript stays possible.
- List variables live in a concurrent sorted map that matches Skript's index ordering exactly.
- Saving is write-behind with dirty tracking, off the main/region threads.
- An optional new NeoSkript-native backend for speed is a later decision. 1.0 is compat-first.

### 3.5 Standard library (`neoskript-paper`)

- Everything is built on the **public `neoskript-api`**, which dogfoods the modern addon API.
- Aliases: generated from Paper registries (materials, item types, block data), plus Skript's alias data for matching names. This reuse is allowed under GPL-3.0.
- Commands: Skript `command /x:` structures are registered through **Paper's Brigadier lifecycle API**, which gives proper tab-completion.
- Coverage is ranked by real usage: we count how often each syntax appears in the corpus and implement the most-used first.

### 3.6 Addon APIs

- **Modern API (1.0, `@Experimental` until 1.1):** builder-based registration, typed `Expression<T>`, optional codegen hooks for intrinsics, explicit thread-affinity declarations, versioned and published to Maven.
- **Skript compat shim (1.x):** implements `ch.njol.skript.*` (`SyntaxElement.init(Expression[], int, Kleenean, ParseResult)`, `ClassInfo`, `Converters`, `Skript.registerX`), wrapping each addon element as a call-through node. Target order: **SkBee → skript-yaml → SkQuery → skript-reflect**. skript-reflect leans heavily on Skript internals and may need special handling.

## 4. Quality: corpus, differential testing, benchmarks

- **Corpus:** Skript's own test scripts (GPL, reusable) plus our own real-world scripts in `test-harness/corpus/real-world`. They're written like typical server scripts (economy, homes, chat, punishments and so on), and each has a self-test whose results on Skript are the expected behavior. Scripts contributed by the community can join later, with permission.
- **Differential runner:** runs the same script on headless real Skript and on NeoSkript, on both Paper and Folia, then diffs messages sent, variable state, and world effects. This runs in CI on every PR.
- **Compat dashboard:** % of the corpus that parses, % that behaves identically, and the list of missing syntax. Published publicly.
- **Benchmarks:** JMH microbenchmarks (expressions, variables, loops), plus server benchmarks for script load time, `/sk reload` time, per-event cost under simulated players, and variable save time. Every run compares against Skript. Results are published so the "lose the lag" claim is backed up.

## 5. Tooling

- **Migration CLI (`neoskript-cli`):** runs against a `scripts/` folder offline. It reports what's supported, what's unsupported (with line numbers), likely Folia hazards (cross-entity access, reliance on atomicity), and performance smells.
- **LSP:** diagnostics, completion from the syntax registry, hover docs, go-to-definition for functions and options. It reuses the frontend, with a VS Code extension first.

## 6. Roadmap

Milestones are ordered by dependency. The exit criteria are what "done" means.

**M0: Foundations**
Gradle multi-module setup, CI, license and headers, pinning the latest stable Skript, headless Paper and Folia test servers, the benchmark harness with a Skript baseline, corpus collection.
*Exit:* CI boots Paper + Folia + reference Skript and runs one script on each.

> **Status: done (2026-10-21).** The harness boots NeoSkript on Paper and Folia, from 1.21.4 to 26.3. The reference Skript (2.17.0) runs on Paper 26.3, where its own suite passes 853/853 under our harness, and NeoSkript correctly refuses to run alongside it. Skript can't run on Folia (D18), so the Folia half of the exit criterion is NeoSkript booting there. Baseline on an Apple Silicon dev machine: Skript loads the 600-structure, 5,960-line benchmark in ~1.7 s and reloads it in ~1.1–1.2 s (median). The real-world corpus is 12 scripts written in-house, about 1,100 lines: Skript loads them with no errors or warnings and passes all 12 self-tests. bStats is wired up (plugin ID 34868, `metrics: false` to opt out), and test servers never report to it. JMH microbenchmarks wait until M2, when there's compiled code to measure.

**M1: Frontend**
Structure parser, pattern language + compiler + matcher, type system skeleton, AST, diagnostics, stub syntax registry imported from Skript's syntax list.
*Exit:* the full corpus parses with zero false errors against the stub registry. Parse time is measured.

> **Progress (2026-10-21).**
> - **Done:** the structure parser and the pattern compiler, matcher and index.
>   - The structure parser reads all 549 corpus files with no diagnostics in about 100 ms. That covers comments (rules taken from Skript's own `misc/comments.sk` tests), indentation, nested options and one-line `using` structures.
>   - All 2,127 of Skript 2.17.0's patterns compile.
> - **Stub registry:** generated from Skript's own docs export (`/sk gen-docs` with `-Dskript.testing.genDocs=true`) into `neoskript-frontend/src/test/resources/skript-syntax/`. Those patterns are in documentation form: no parse marks, no tags, no `-` or `@`, and display-name types. That's enough to decide whether a line parses. The real marks and tags come with each element's implementation in M4.
> - **Next:** expression parsing (literals, strings with `%…%`, variables, lists, function calls), the type system skeleton, and the corpus parse scenario.

**M2: Compiler + runtime core (Paper and Folia)**
IR, ASM backend, control flow, locals, functions, options, waits as state machines, scheduler abstraction with both implementations, reload with atomic swap.
*Exit:* the control-flow and function tests from Skript's suite pass on Paper **and** Folia.

**M3: Variables & storage**
Global/list variables, the concurrent store, a Yggdrasil reader/writer, CSV/SQLite/MySQL.
*Exit:* round-trips real `variables.csv`/DB files from Skript servers byte-compatibly.

**M4: Standard library**
Types, converters, aliases, events, effects, expressions, conditions, commands (Brigadier), implemented in usage-ranked order.
*Exit:* ≥95% of the corpus behaves identically under differential testing, and all of Skript's own tests pass.

**M5: Folia hardening + tooling**
Region-ownership enforcement, `run on` section, Folia-specific differential tests, migration CLI.
*Exit:* the corpus runs on Folia without thread violations, and the CLI is released.

**M6: Beta → 1.0**
Public beta on Modrinth + GitHub Releases, published benchmarks, a syntax-docs JSON export from the registry for the Starlight docs site, bStats and the Modrinth update checker (both can be turned off in config), Skript-installed detection, bug burn-down.
*Exit:* 1.0 release.

**1.x**
LSP + VS Code extension, Skript-addon shim (SkBee first), the modern addon API declared stable, optional native variable backend, opt-in language extensions.

## 7. Top risks

1. **Long tail of compatibility.** Skript has more than 1,000 syntax elements and many quirks (ambiguity resolution, conversions, plurality). *Mitigation:* differential testing from M1 onward, plus usage-ranked coverage.
2. **Folia semantics.** Scripts quietly depend on single-thread ordering. *Mitigation:* clear errors, CLI hazard detection, the explicit `run on` section.
3. **Yggdrasil/variable format fidelity.** Corrupting someone's variables would end adoption. *Mitigation:* round-trip tests on real files, automatic backup before the first load.
4. **Scope.** This is a compiler, a runtime, and a stdlib. *Mitigation:* strict milestone exit criteria, and the addon shim and LSP stay out of 1.0.
5. **The addon shim's depth.** Addons that reach into Skript internals (skript-reflect) may never work fully. *Mitigation:* say so up front and maintain a supported-addons list.

## 8. Open questions

None right now. Every earlier question is resolved and recorded as D9 to D15. As a solo project with no fixed dates, the work goes milestone by milestone, and each one ends at its exit criteria.
