# NeoSkript

A fast, modern, drop-in replacement for [Skript](https://github.com/SkriptLang/Skript) — built for
Paper + Folia on Java 25, with a clean, extensible API.

NeoSkript runs existing `.sk` scripts unchanged while being dramatically faster to load and execute,
Folia-safe by construction, and designed so addon authors are first-class users.

> **Status:** Phase 3 (Performance pass). Constant folding, indexed pattern dispatch, parallel script
> loading, and a `/neoskript reload` command, on top of the Phase 2 language. See
> [`docs/PLAN.md`](docs/PLAN.md) for the full plan and roadmap.

## Modules

| Module | Purpose |
|---|---|
| `neoskript-api` | Public SPI — stable, semver'd (`Expression`, `Effect`, `Condition`, `Type`, `SyntaxRegistry`, `NeoSkriptAddon`). |
| `neoskript-core` | Pipeline internals: lexer, parser, resolver, optimizer, runtime. |
| `neoskript-lang` | Built-in expressions/effects/conditions/events/types. |
| `neoskript-platform` | Paper + Folia adapters and the `NeoScheduler` abstraction. |
| `neoskript-plugin` | The shaded, distributable plugin jar. |
| `neoskript-testkit` | Script conformance harness and benchmarks. |

## Building

Requires JDK 25 (the build uses a Java 25 toolchain).

```bash
./gradlew build
```

The distributable plugin jar is produced at
`neoskript-plugin/build/libs/neoskript-plugin-<version>.jar`.

## License

NeoSkript is licensed under the [GNU General Public License v3.0](LICENSE).
