# NeoSkript: Design & Roadmap

Version 1, with the decisions locked. Where each milestone stands is in [PROGRESS.md](PROGRESS.md).

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
| D2 | Codebase | Clean-room rewrite. Skript's *behaviour* is the spec. | Skript's architecture (regex-style pattern trials, `Object[]` everywhere, single-thread assumptions) is the thing we're replacing. |
| D3 | Licence | **GPL-3.0-only** | Matches the ecosystem. It also lets us legally reuse Skript's GPL data (aliases, lang files, test scripts) with attribution. The code stays ours. |
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
| D16 | Licence header | `GPL-3.0-only` (SPDX header on every source file, checked by Spotless) | Version 3 of the GPL only. Skript is GPL-3.0-or-later, so its data we reuse (D3) can be used under version 3. |
| D17 | Test servers | Pinned to exact Paper/Folia builds and SHA-256 hashes, bumped on purpose | Paper ships daily, and build 211 of 26.3 broke Skript 2.17.0 the day it came out. |
| D18 | Where the Skript baseline runs | Paper 26.x only. Skript doesn't support Folia, and Skript 2.17.0 needs 1.21.5 or newer. NeoSkript-only checks run on all four servers. | |
| D19 | Moving over from Skript | NeoSkript uses `plugins/NeoSkript`. On its first start it copies `scripts/` and `variables.csv` from `plugins/Skript`, then leaves a marker so it never copies again. It copies rather than moves, so Skript's folder stays as it was. Skript's `config.sk` isn't copied. | Swapping the jar just works, and switching back is safe. |
| D20 | Database storage | Deferred to 1.x. Skript 2.17 itself only supports `CSV` and `disabled` out of the box (its SQLite and MySQL storage needs the abandoned SQLibrary plugin), so `variables.csv` is what servers actually use. | M3 matches Skript's CSV byte for byte instead of spreading thin. |

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
5. **Type checking:** static types wherever inference works (most cases). Otherwise we fall back to dynamic `Object` with runtime conversion, so behaviour still matches Skript's loose typing. Plurality (single vs. list), `and`/`or` lists, default expressions (`event-player`), and time states (`past`/`future`) are all modelled.
6. **Diagnostics:** errors use Skript's wording and line format, so existing tutorials and forum answers still apply. They also carry precise spans, which the LSP and CLI need.

### 3.2 Compiler (runtime speed)

- **IR:** a typed, SSA-ish IR with explicit control flow (if/else, loops, return, stop, exit). Optimisations: constant folding (including `{@options}`), dead branch removal, condition short-circuiting, and caching invariant lookups.
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
- **Thread ownership checks:** what an element touches comes from its placeholders' types: entities (players too), blocks and locations. Elements that only read what's safe from any thread (names, UUIDs, permissions, worlds, comparisons) or only send something (messages, titles, sounds) are listed as exempt. Only code compiled for Folia checks `isOwnedByCurrentRegion`: there, the compiler wraps each element call. On Paper nothing is wrapped, so the generated code is the same as before.
- **Reads from another region** (`health of {otherPlayer}`) fail with a clear error by default: a Skript-style runtime error naming the line, after which the read gives nothing (a condition fails) and the trigger carries on, as with Skript's other runtime errors. What Folia's own thread checks refuse is reported the same way, never as a stack trace. A config option for a blocking hop for reads (D11) is still to come.
- **Effects on an entity in another region** are dispatched to that entity's scheduler (a block's or location's to its region's), fire-and-forget: the trigger carries on without waiting. An effect on several entities runs here for those this thread owns and on each other one's thread for the rest. Changes (`set health of X to 3`, `give X to all players`) are effects too.
- **`run on %entity/location%:`** (NeoSkript only, opt-in) runs its code on the thread that owns the entity or location, and the trigger **waits** for it, like `wait`: the trigger stops, carries on on that thread (at once if this thread owns it already, else on the next tick), runs the code, and goes back to its own thread for the rest. A `wait` inside it stays on that thread. With nothing to run on, the code doesn't run. Its code can leave it with `exit section`, but not with `continue` or `exit loop` to a loop around it. On Paper the code just runs, in place.
- **Global variables** live in a concurrent store. Each single operation is atomic: `add 1 to {x}` compiles to an atomic compute. Sequences of operations are not atomic, and the docs say so.

### 3.4 Variables & storage (drop-in critical)

- Load and save **Skript's `variables.csv`** byte for byte, including its binary-encoded values (items, locations, and so on). Users can switch in both directions, so rolling back to Skript stays possible. SQLite and MySQL come in 1.x (D20).
- List variables live in a concurrent sorted map that matches Skript's index ordering exactly.
- Saving is write-behind with dirty tracking, off the main/region threads.
- An optional new NeoSkript-native backend for speed is a later decision. 1.0 is compat-first.

### 3.5 Standard library (`neoskript-paper`)

- Everything is built on the **public `neoskript-api`**, which dogfoods the modern addon API.
- Aliases: generated from Paper registries (materials, item types, block data), plus Skript's alias data for matching names. This reuse is allowed under GPL-3.0.
- Commands: Skript `command /x:` structures are registered in the **server's command map** under the `skript` namespace, which Paper turns into Brigadier commands. Paper's Brigadier lifecycle API only registers commands while the server builds its command tree (startup, `/minecraft:reload`), so it can't follow `/sk reload`; the command map can. Tab completion comes from the arguments' types.
- Coverage is ranked by real usage: we count how often each syntax appears in the corpus and implement the most-used first.

### 3.6 Addon APIs

- **Modern API (1.0, `@Experimental` until 1.1):** builder-based registration, typed `Expression<T>`, optional codegen hooks for intrinsics, explicit thread-affinity declarations, versioned and published to Maven.
- **Skript compat shim (1.x):** implements `ch.njol.skript.*` (`SyntaxElement.init(Expression[], int, Kleenean, ParseResult)`, `ClassInfo`, `Converters`, `Skript.registerX`), wrapping each addon element as a call-through node. Target order: **SkBee → skript-yaml → SkQuery → skript-reflect**. skript-reflect leans heavily on Skript internals and may need special handling.

## 4. Quality: corpus, differential testing, benchmarks

- **Corpus:** Skript's own test scripts (GPL, reusable) plus our own real-world scripts in [`test-harness/corpus/real-world`](../test-harness/corpus/real-world). They're written like typical server scripts (economy, homes, chat, punishments and so on), and each has a self-test whose results on Skript are the expected behaviour. Scripts contributed by the community can join later, with permission.
- **Differential runner:** runs the same script on headless real Skript and on NeoSkript, on both Paper and Folia, then diffs messages sent, variable state, and world effects. It's due by the end of M4 (its 95% check) and will run in CI on every PR. Until then, differential probes compare scripts line by line against a real Skript server ([batch 26](PROGRESS.md#batch-26-2026-10-25)).
- **Compat dashboard:** % of the corpus that parses, % that behaves identically, and the list of missing syntax, published publicly.
- **Benchmarks:** JMH microbenchmarks (expressions, variables, loops), plus server benchmarks for script load time, `/sk reload` time, per-event cost under simulated players, and variable save time. Every run compares against Skript. Results are published so the "lose the lag" claim is backed up. So far the harness times Skript's load and reload (`skript-load-benchmark`); the rest comes by M6.

## 5. Tooling

- **Migration CLI (`neoskript-cli`):** runs against a `scripts/` folder offline. It reports what's supported, what's unsupported (with line numbers), likely Folia hazards (cross-entity access, reliance on atomicity), and performance smells.
- **LSP:** diagnostics, completion from the syntax registry, hover docs, go-to-definition for functions and options. It reuses the frontend, with a VS Code extension first.

## 6. Roadmap

Milestones are ordered by dependency, not dated. A milestone is done when its exit criterion holds. Where each one stands, and what it delivered step by step, is in [PROGRESS.md](PROGRESS.md).

| | Milestone | Scope | Exit criterion |
|---|---|---|---|
| **M0** | Foundations | Gradle modules, CI, licence headers, the pinned reference Skript, headless Paper and Folia test servers, the benchmark harness with a Skript baseline, the corpus | CI boots Paper, Folia and the reference Skript, and runs one script on each |
| **M1** | Frontend | Structure parser, pattern compiler and matcher, type system, AST, diagnostics, a stub registry from Skript's syntax list | The whole corpus parses with zero false errors against the stub registry, and parse time is measured |
| **M2** | Compiler and runtime core | IR, ASM backend, control flow, locals, functions, options, waits as state machines, the scheduler on Paper and Folia, reload with an atomic swap | Skript's control-flow and function tests pass on Paper **and** Folia |
| **M3** | Variables and storage | Global and list variables, the concurrent store, Skript's binary value format, `variables.csv` (databases deferred, D20) | Real `variables.csv` files from Skript round-trip byte for byte |
| **M4** | Standard library | Types, converters, aliases, events, effects, expressions, conditions and commands, most-used first | ≥95% of the corpus behaves identically under differential testing, and all of Skript's own tests pass |
| **M5** | Folia hardening and tooling | Region-ownership enforcement, the `run on` section, Folia differential tests, the migration CLI | The corpus runs on Folia without thread violations, and the CLI is released |
| **M6** | Beta → 1.0 | Public beta on Modrinth and GitHub Releases, published benchmarks, a syntax-docs export for the docs site, bStats and the update checker (both can be turned off), bug burn-down | 1.0 is released |
| **1.x** | After 1.0 | LSP and VS Code extension, the Skript-addon shim (SkBee first), the addon API declared stable, an optional native variable backend, opt-in language extensions | |

## 7. Top risks

1. **Long tail of compatibility.** Skript has more than 1,000 syntax elements and many quirks (ambiguity resolution, conversions, plurality). *Mitigation:* differential testing from M1 onward, plus usage-ranked coverage.
2. **Folia semantics.** Scripts quietly depend on single-thread ordering. *Mitigation:* clear errors, CLI hazard detection, the explicit `run on` section.
3. **Yggdrasil/variable format fidelity.** Corrupting someone's variables would end adoption. *Mitigation:* round-trip tests on real files, automatic backup before the first load.
4. **Scope.** This is a compiler, a runtime, and a stdlib. *Mitigation:* strict milestone exit criteria, and the addon shim and LSP stay out of 1.0.
5. **The addon shim's depth.** Addons that reach into Skript internals (skript-reflect) may never work fully. *Mitigation:* say so up front and maintain a supported-addons list.

## 8. Open questions

None right now. The earlier ones are resolved and recorded as D9 to D15.
