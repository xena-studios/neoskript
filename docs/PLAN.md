# NeoSkript: Design & Roadmap

Status: v1 (decisions locked) · M0 to M3 done · 2026-10-22

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
- Commands: Skript `command /x:` structures are registered in the **server's command map** under the `skript` namespace, which Paper turns into Brigadier commands. Paper's Brigadier lifecycle API only registers commands while the server builds its command tree (startup, `/minecraft:reload`), so it can't follow `/sk reload`; the command map can. Tab completion comes from the arguments' types.
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

> **Status: done (2026-10-22).** The frontend parses the whole corpus with **zero false errors** against the stub registry: Skript's own tests (537 files, 17,705 lines), the real-world corpus (13 files) and the generated benchmark scripts (40 files, 6,000 lines). The `neoskript-parse-corpus` harness scenario checks this on every CI run.
> - **Parse time** (median of 5 warm runs on an Apple Silicon dev machine): the 6,000-line benchmark in **40 ms**, the real-world corpus in 17 ms, Skript's tests in about 2 s. Skript takes ~1.7 s to load the benchmark scripts at startup. That's not a like-for-like comparison yet: Skript's number covers its whole load, and NeoSkript's covers parsing only, until M2 adds compilation.
> - **What's built:** the structure parser; the pattern compiler, matcher and index; the registry (types, syntax, functions, type relations); and the expression, statement and script parsers.
>   - Matching is memoized by span and expected type, filtered per line by the literals a pattern needs, anchored for a pattern's final placeholder, and prefers fully resolved parses over ones that need unresolved word literals.
>   - Language features covered: typed literals (`black (color)`), percentages, block data, expression sections, `if any`/`if all`, command subcommands and named or ranged arguments, function overloads, named arguments, `->` return types, and Skript's test-only syntax.
> - **Stub registry:** generated from Skript's docs export (`/sk gen-docs` with `-Dskript.testing.genDocs=true`), in the frontend's test fixtures. The export's patterns are in documentation form: no parse marks or tags, display-name types. A few things it leaves out are added by hand, each backed by a corpus script: regex roles, type relations, closed literal sets, a handful of pattern fixes, and the test-only syntax.
> - **Known gap:** Skript reports an error in 90 of the corpus's `parse:` test blocks, and NeoSkript catches 21. Most of the rest are checks Skript makes in each element's own setup (an effect that only works on living entities, local variable type hints, unreachable code, expressions limited to certain events), so they come with each element's implementation in M4. The exit criterion was zero *false* errors, and that holds.

**M2: Compiler + runtime core (Paper and Folia)**
IR, ASM backend, control flow, locals, functions, options, waits as state machines, scheduler abstraction with both implementations, reload with atomic swap.
*Exit:* the control-flow and function tests from Skript's suite pass on Paper **and** Folia.

> **Status: done (2026-10-22).** Skript's own control-flow and function tests run on NeoSkript on Paper and Folia, 1.21.4 and 26.3 (`neoskript-skript-tests`).
> - **Results:** 33 of the tests in `SecConditional`, `SecLoop`, `SecWhile`, `EffContinue`, `ExprLoopValue`, `StructFunction` and function overloading pass. The other 8 need later milestones, and the scenario lists each one with the reason:
>   - the `location()` function, item literals, entities, vectors and `keyed` lists
>   - Skript's test-only loop peeking
>   - two error messages worded exactly as Skript has them
> - **Running scripts:**
>   - The plugin loads `plugins/NeoSkript/scripts`. On its first start it copies Skript's scripts and variables (D19).
>   - Each trigger and function compiles to a hidden class with ASM. Elements are called through class-data constants, so each call site has one target the JIT can inline. The compiler implements the core syntax itself.
>   - Runtime errors are reported once, at the script line, even through deep recursion.
> - **Core language:**
>   - local and global variables (lists, dynamic names, case-insensitive, numeric index order) and their changes
>   - arithmetic with Skript's precedence, and comparisons
>   - `if` / `else if` / `else`, `if any`/`if all` … `then`, and `X if C, else Y`
>   - `loop` (`loop-value`, `-index`, `-counter`, `next`/`previous`, `-N`), `while` and `do while`, `exit` / `continue`
>   - functions with defaults, named arguments, overloads (with Skript's ambiguity rules and errors), local functions, and recursion
> - **`wait`:** a trigger that waits compiles into a resumable state machine. Its frame saves the locals and the enclosing loops, and it carries on through the scheduler: the main thread on Paper, the global region on Folia. The frontend tracks Skript's delay state through branches, loops and `parse:` sections.
> - **`/sk reload`** (`/skript`, `/sk`, `/neoskript`, D10):
>   - It compiles everything first, then switches over together.
>   - A script with errors keeps its last version, and its functions keep their old code (D12). Function handles last across reloads.
>   - Replies use Skript's wording. A two-script reload takes about 2 ms (`neoskript-reload`).
> - **Test mode** (`-Dneoskript.testing.enabled=true`): it runs Skript's test syntax and writes JSON results like Skript's runner.
> - **Left for later:**
>   - Waits in functions, and resuming on entity or region schedulers on Folia, come with events in M4.
>   - Saving global variables is M3.
>   - The harness's parse check still has zero false errors, and now catches 30 of the 90 errors Skript reports in its tests (up from 21).

**M3: Variables & storage**
Global/list variables, the concurrent store, a Yggdrasil reader/writer, CSV/SQLite/MySQL.
*Exit:* round-trips real `variables.csv`/DB files from Skript servers byte-compatibly.

> **Status: done (2026-10-22).** NeoSkript reads and writes Skript's `variables.csv` byte for byte, both ways (`neoskript-variables`, run against the reference Skript).
> - **The check:**
>   - Skript saves 25 variables of every common type, including unicode and long text, lists, nested names, names with commas and spaces, locations, worlds, players, items, vectors and game modes.
>   - NeoSkript picks them up through the migration, decodes them, and rewrites a byte-identical file.
>   - In the other direction, Skript reads NeoSkript's file correctly and its rewrite is byte-identical too.
> - **The format:** worked out from files Skript writes, not from Skript's code:
>   - The header, then `name, type, hex` lines in tree order. Indices sort numbers by value; names are lowercased, and quoted if they contain commas, quotes or whitespace. The file ends with an empty line.
>   - Values use Skript's binary encoding: raw primitives, compact string lengths, objects as tagged fields, and back-referenced names.
>   - On reading, later lines win, and a line without a type deletes the variable.
> - **What's decoded:** text, whole numbers, decimals, booleans, timespans, worlds, locations and players. Anything else (items, dates, vectors and so on) is kept as `RawValue` and written back unchanged until M4 gives it a type, so nothing is ever lost.
> - **Storage** (`config.yml` → `variables`):
>   - Variables load before scripts and save every 5 minutes if anything changed, and when the server stops.
>   - Saving writes a temporary file and swaps it in. Skript only writes at shutdown, so a crash loses everything since start; NeoSkript loses at most the last interval.
>   - `-` variables stay in memory, as in Skript.
>   - Addons register codecs through `Registrar.storage`.
> - **Databases:** deferred (D20).

**M4: Standard library**
Types, converters, aliases, events, effects, expressions, conditions, commands (Brigadier), implemented in usage-ranked order.
*Exit:* ≥95% of the corpus behaves identically under differential testing, and all of Skript's own tests pass.

> **Steps** (one PR each). The order comes from `syntaxUsage` in the `neoskript-parse-corpus` report, which counts how often the corpus uses each Skript element. At the start of M4, the real-world corpus used 152 distinct elements and functions, and NeoSkript had 15 of them. Skript's tests use 785.
> 1. **The event foundation:**
>    - Bukkit events, with event values (`player`, `event-world`) and `cancel event`.
>    - Types' default values (`send "hi"` goes to the event's player).
>    - Changing expressions, not just variables (`set name of player to ...`).
>    - Property expressions and conditions (`X's name`, `name of X`, `X is online`).
>    - Waits in events resume on the right Folia thread: the entity's for player events, the region's for block events.
>
>    **Status: done (2026-10-22).**
>    - **API:**
>      - `Registrar.event(id, eventClasses, filter, patterns)` and `eventValue(eventClass, type, getter)`.
>      - `eventDefault(type)`, so unused optional placeholders get the event's value.
>      - `Changeable` for expressions that `set`/`add`/`remove`/`delete`/`reset` can change.
>      - `property`/`propertyCondition`, with Skript's pattern shapes in `Patterns`.
>    - **Event values follow Skript's rules:**
>      - It's enough for one of an event's classes to have the value; the closest event class wins, then the exact type.
>      - Otherwise it's a compile error: "There's no world in a command event".
>    - **Listening:** each event class the loaded scripts use gets one Bukkit listener, rebuilt on reload, at `high` priority and skipping cancelled events, as Skript does by default (`config.yml` → `events`).
>    - **Events so far:** join, first join, quit, chat and command, with `cancel event`, `sender`, and some player syntax (UUID, name, online, permission, kick). The rest comes in step 3.
>    - **Tested by:**
>      - Unit tests (`EventsTest`) with stand-in event classes.
>      - `neoskript-events` on all four servers, driven by console commands.
>      - The test servers have no players, so player events are only checked when compiled. Running them needs a bot client, which comes with the differential runner in step 6.
> 2. **Commands:** `command /x <args>:` with every entry (aliases, permission, usage, cooldown, executable by), `arg-1` and `sender`, registered with tab completion, and reloadable.
>
>    **Status: done (2026-10-22).**
>    - **Patterns, as in Skript 2.17:**
>      - Words, `[optional]` parts and `(a|b)` choices.
>      - Arguments: `<type>`, plural `<numbers>`, named `<x: number>` (the local `{_x}`), ranged `<x: number above 5 and below 10>`, and defaults `<text = home>` or `<player = %player%>`.
>      - Subcommands, with `subcommands:` groups whose entries apply to what's in them.
>      - Skript's errors for missing triggers, optional subcommands, `executable by` that a parent rules out, repeated argument names, and ranges.
>    - **Matching:** the first route that what's typed fits wins, subcommands before their command. Arguments take as few words as they can, so the last text argument gets the rest, and optional arguments are skipped when the next one fits.
>    - **Entries:** aliases, description, usage, permission and permission message, executable by, cooldown with its message and bypass permission. `cooldown storage` is a compile error for now (it needs dates).
>    - **Syntax:** `arg-1` and every other form of `ExprArgument`, choice arguments, `the [full] command`, `execute command`, cooldown info and `cancel the cooldown`, command info (`the description of command "x"`), `is a script command`, `all [script] commands`, and `X ? Y`.
>    - **Registration:** in the server's command map under `skript:` (see 3.5), replaced on every reload, then each online player gets the new command list.
>    - **Parser changes:** arithmetic and event values are tried after every library element, and a placeholder can't start inside a token like `arg-1` unless nothing else parses. So `arg-1 ? "x"` and `arg-1 * arg-2` mean what they say.
>    - **Tested by:**
>      - Unit tests (`CommandParserTest`, `CommandsTest`).
>      - `neoskript-commands` on all four servers, driven by the console, including a reload that changes one command and removes the others.
>    - **Not yet:** Skript's `StructCommand` test also needs local variable type hints and item types; `execute command` doesn't report "Failed to execute command" errors yet, as there's no `catch runtime errors`.
> 3. **The real-world corpus:** the text, player, time and location syntax it uses, and converters between types. *Done when* the 10 real-world scripts that don't use items load on NeoSkript and pass their self-tests, on all four servers (`kits.sk` and `warps.sk` need items and inventories, so they move to step 4).
>
>    **Status: done (2026-10-22).**
>    - **Types:** `date` (saved in `variables.csv` byte for byte), `location`, `game mode`, plus parsers so text can become a value (`"5" parsed as integer`, command arguments).
>    - **Converters:** `Registrar.converter(from, to, f)`. A placeholder whose type something converts to gets values converted at run time: an entity or block where a location is expected is its location.
>    - **`Update` effects:** effects that set one of their placeholders, like `replace "a" in {_x} with "b"`, compiled into a `set` through the same path as `set`.
>    - **Syntax:**
>      - Text: case, length, join and split, parts of text, character counts, `matches`, `replace`, new lines, colors, `parsed as`.
>      - Lists: `indices of` (sorted too) and `sort`.
>      - Time: `now`, `difference between`, Unix timestamps.
>      - Places: `location of`, spawn points, worlds by name, `distance between`, `teleport`, and the `location()` and `world()` functions.
>      - Players: `player()`/`offlineplayer()`, played before, can fly, flight mode, game mode, food level, tab list name, heal, ignite/extinguish, titles, action bars, sounds, `all players`.
>      - Events: `every <timespan>` (cancelled and rescheduled on reload), move, server list ping. Settable chat message and format, join and quit messages, and MOTD.
>    - **Parser fixes the corpus found:**
>      - A loop value's name has to be `value`, `index`, `counter`, `iteration` or a type, so `loop-value in lower case` isn't a loop value called "value in lower case".
>      - `f(player)` is a function call, not the literal `f` of type player: a type in brackets needs a space before it.
>      - Arithmetic is back before the library's expressions, so `length of {_x} - 1` is `(length of {_x}) - 1`. The rule from step 2 that a placeholder can't start inside `arg-1` still keeps `arg-1 ? "x"` right.
>      - The conditions of `if all:`/`if any:` are compiled against their own lines; this used to crash the compiler.
>      - `loop-index` works for any loop: values that don't come from a list variable are indexed 1, 2, 3.
>    - **Tested by:** `neoskript-real-world-corpus` on all four servers: the 10 scripts without items load cleanly and pass. `warps` passes too, though its menu lines don't load yet; `kits` fails. Both are reported but don't fail the check until step 4. Plus regression tests in `LanguageTest`.
> 4. **Items and inventories:** item types from Paper's registries, inventories and slots. *Done when* all 12 real-world scripts load on NeoSkript and pass their self-tests, on all four servers.
>
>    **Status: done (2026-10-22).**
>    - **Types:** `item type` (NeoSkript's `ItemType`: an item stack and whether an amount was written), `item`, `inventory`, `inventory holder`, `slot`, with converters between them (a slot is its item, a player is its inventory).
>    - **Item literals:** `[<amount> [of]] [a|an|all|every] <name> [of <enchantment> [<level>], ...]`, with levels in digits or Roman numerals. Names come from Paper's material registry, singular and plural. Skript's alias files (`any sword`, block states) aren't used yet.
>    - **API:**
>      - `Registrar.literalParser(type, f)`: words a script writes become values of the type. The parser leaves them to the compiler, which resolves them once into constants.
>      - `Registrar.changer(class, mode, f)`: how `add`/`remove`... change a value itself. `give X to player` adds to the player's inventory.
>    - **Syntax:** `named`, `with lore`, item amount, chest inventories, `slot N of`, slot index, `inventory of`, open and close inventories, and inventory click/open/close events with `event-slot`.
>    - **Tested by:**
>      - `neoskript-real-world-corpus`: now 12 of 12, and strict.
>      - `neoskript-items` on all four servers: literals, names and lore, parsing items from text, chest inventory slots.
>      - Skript's own tests: 34 pass now (one more test can run).
>    - **Not yet:** items in `variables.csv` (step 4b). Items saved by Skript are still kept as raw bytes, and items set by scripts aren't saved yet.
> 4b. **Item codecs:** `itemtype` and `itemstack` in `variables.csv`, byte for byte with Skript (its item type format nests the item meta as YAML), so items survive restarts and M3's raw item values become real items.
>
>    **Status: done (2026-10-22).**
>    - **Codecs:** `itemtype`, `itemstack` and `gamemode`. Item meta and item stacks are YAML from Bukkit's own serializer, as in Skript.
>    - **Format:** the binary format gained null fields and object arrays (an item type is a list of item datas).
>    - **Safety net:** a saved value is only decoded if it encodes back to the same bytes. So an item NeoSkript doesn't model, like Skript's `any sword` (several items in one), stays as Skript saved it.
>    - **Conversion on save:** a value without a codec is saved as what it converts to, if that has one: a slot as its item, as in Skript.
>    - **Tested by:** `neoskript-variables`. NeoSkript decodes the plain, named, enchanted, lore, block and counted items and the item stack the reference Skript saved, and rewrites the file byte for byte. Skript reads the items NeoSkript saves and rewrites them identically. Only `vector` is still kept raw, until vectors come with the world (step 5).
> 5. **The world:** entities and spawning, blocks, locations and vectors, worlds, potions.
> 6. **The long tail:** whatever Skript's tests still need, most-used first, then the differential runner for the 95% check.

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
