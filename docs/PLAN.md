# NeoSkript — Implementation Plan

A from-scratch reimplementation of [Skript](https://github.com/SkriptLang/Skript) that runs
existing `.sk` scripts unchanged, but is dramatically faster to load and execute, Folia-safe,
and built on a clean, documented, extensible API.

## Locked decisions

| Decision | Choice |
|---|---|
| Drop-in scope | **Script language only** — run existing `.sk` files unchanged. No binary compatibility with Skript's Java addon API (addons port to the new API). |
| Platform | **Paper + Folia**. Folia-safe scheduling from day one. |
| Core language | **Java 25** — current Paper (`26.1.x`) requires a Java 25 runtime, so we target it (it's also the modern choice for the core engine). |
| Execution model | **Optimized AST now, JIT later** — a fast tree-walking interpreter first; optional ASM bytecode backend for hot scripts in a later phase. |

## Guiding principles

1. **Script compatibility is sacred.** A valid Skript file behaving differently is a bug.
2. **Allocation-free hot paths.** The per-event execution path should produce near-zero garbage.
3. **Pay parsing cost once.** Parse/analyze/optimize at load; runtime walks a flat, pre-resolved structure.
4. **Thread-safety by construction.** No "main thread" assumption — concurrency rules are part of the contract.
5. **The API is a product.** Addon authors are first-class users.

## Why Skript is slow (what we're fixing)

| Skript today | Consequence | NeoSkript fix |
|---|---|---|
| Parsing brute-forces every line against *all* registered patterns | Reload time grows with `scripts × syntaxes` | Token-indexed pattern dispatch (trie / first-keyword bucketing) |
| Tree-walking interpreter returning `Object[]`, heavy boxing & reflection | GC pressure + slow per-event cost | Pre-resolved executable nodes, primitive-specialized slots, cached method handles |
| Variable names re-parsed & rebuilt as strings on every access | String allocs per variable touch | Compiled variable "paths" resolved once at parse time |
| Single-thread assumptions | Incompatible with Folia | Region/thread-aware scheduler + concurrency contracts |
| Synchronous, monolithic reload | Server freeze on `/sk reload` | Parallel + incremental script loading off-thread |

## Pipeline architecture

Parsing and execution are separated hard. Everything left of Runtime happens at load time, off-thread.

```
.sk source
  → Lexer       (source text → token stream, with positions)
  → Parser      (tokens → syntax tree via indexed pattern matcher)
  → Resolver    (bind variables, types, events; report errors w/ spans)
  → Optimizer   (constant fold, dead-code elim, pattern specialization)
  → Linker      (produce flat ExecutableNode[] + variable layout)
  → Runtime     (executes ExecutableNodes against a TriggerContext)
        ↘ (Phase 4) JIT backend: hot Trigger → JVM bytecode via ASM
```

## Core subsystems

- **Pattern/parse engine** — patterns compiled once, indexed by leading literal tokens; rich diagnostics with source spans.
- **Type system** — `Type<T>` registry (parsers, serializers, converter graph, comparators, arithmetic), built for primitive specialization.
- **Variables** — parse-time compiled paths; efficient ordered list-variable structure; pluggable async persistence backends.
- **Events** — one listener per event type dispatching to a pre-built trigger list; event-values resolved at parse time.
- **Scheduler abstraction** — `NeoScheduler` mapping to Folia region/global/async schedulers or Bukkit; effects declare thread requirements.
- **Runtime** — continuation-style execution so `wait`/`delay` suspend without blocking a thread.

## Performance strategy

- Pre-resolve reflection to cached `MethodHandle`/`VarHandle` at link time.
- Primitive-specialized value slots; avoid `Object[]` on the hot path.
- Constant folding & dead-branch elimination.
- Parallel script compilation; incremental reload (re-parse only changed files + dependents).
- Phase 4 JIT: hot triggers compiled to bytecode via ASM, interpreter fallback for anything unsupported.
- Built-in profiler + JMH benchmarks in CI to catch regressions.

## Extensibility & API

- Typed builder/annotation registration for `Expression`/`Effect`/`Condition`/`Section`/`Event`/`Type`/`Converter`.
- Stable, semver'd `neoskript-api` artifact, published separately from the implementation.
- ServiceLoader discovery + a `NeoSkriptAddon` lifecycle (register/enable/reload/disable).
- Docs generated from syntax metadata.

## Drop-in compatibility (scripts only)

- `.sk` parser targets Skript's grammar; a **conformance suite** of real-world scripts gates every release.
- Discover existing `plugins/Skript/scripts/` layout; importable variable persistence formats.
- Compatibility report on load — unsupported syntax is listed explicitly, never silently misbehaves.
- **Non-goal:** binary compatibility with Skript's Java addon API.

## Candidate new features (prioritized with stakeholder before Phase 4)

- True async sections (Folia-safe), script module/import system, typed user functions with better errors,
  per-script hot-reload, built-in profiler & structured logging, optional Kotlin/JVM scripting bridge.

## Project structure

```
neoskript/
├─ neoskript-api/        public SPI — stable, semver'd
├─ neoskript-core/       lexer, parser, resolver, optimizer, runtime
├─ neoskript-lang/       built-in expressions/effects/conditions/events/types
├─ neoskript-platform/   Paper + Folia adapters, scheduler, NMS bridge
├─ neoskript-plugin/     the shaded, distributable plugin jar
└─ neoskript-testkit/    script conformance harness + JMH benchmarks
```

## Testing & tooling

- Unit tests per pipeline stage.
- Script conformance suite on a headless Paper test server.
- JMH benchmarks (parse time, event dispatch, variable access); CI fails on regression.
- Parser fuzzing against a corpus of public scripts.

## Roadmap

- **Phase 0 — Foundations** *(done)*: multi-module Gradle build, Paper plugin bootstrap, Folia-aware scheduler, CI, testkit scaffolding, API interface drafts. *Deliverable: empty plugin loads on Paper + Folia.*
- **Phase 1 — Pipeline core** *(done)*: lexer → pattern-based parser → linker → tree-walking interpreter; argument-aware syntax API; scalar variables (local + global); string interpolation; `on join`/`on quit` events; `broadcast`, `send`, `set` effects; the `player` expression; script discovery + Bukkit event wiring. *Deliverable: real `.sk` scripts run end-to-end.*
- **Phase 2 — Language breadth** *(done)*: control-flow sections (`if`/`else if`/`else`, `while`, `loop … times`, `loop <values>`); conditions (equality, numeric comparisons, `is set`); arithmetic with precedence + parentheses; list variables with dynamic names; user functions with `return`; more effects (`add`/`remove`/`delete`, `message`, `stop`); more events (chat/death/break/place); flatfile variable persistence. *Deliverable: substantial scripts run; representative syntax coverage, growing against the corpus.*
- **Phase 3 — Performance pass** *(done)*: constant folding of literal arithmetic; indexed pattern dispatch (candidates bucketed by leading literal word); parallel script parsing; a full `/neoskript reload` (unregisters prior listeners/functions); a parser benchmark harness. *Per-file incremental reload and a JMH suite are tracked as refinements.*
- **Phase 4 — New features + JIT**: prioritized features, optional ASM bytecode backend, profiler.
- **Phase 5 — Polish**: docs site, addon API stabilization, public beta.

## Risks

- **Syntax fidelity** — Skript's grammar has years of edge cases; mitigated by the conformance suite from Phase 1.
- **Folia concurrency** — subtle; the threading contract is built in early, not retrofitted.
- **Scope creep** — gate each phase on conformance + benchmarks before adding more.
