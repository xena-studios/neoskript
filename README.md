# NeoSkript

A fast, modern, drop-in replacement for [Skript](https://github.com/SkriptLang/Skript) — built for
Paper + Folia on Java 25, with a clean, extensible API.

NeoSkript runs existing `.sk` scripts unchanged while being dramatically faster to load and execute,
Folia-safe by construction, and designed so addon authors are first-class users.

> **Status:** Phases 0–5 of the roadmap are implemented. Scripts parse and run end-to-end with
> control flow, variables (including lists + persistence), functions, scheduled and load triggers, a
> profiler, an indexed/optimizing parser, a `/neoskript` command, addon discovery, and generated
> docs. See [`docs/PLAN.md`](docs/PLAN.md) for the roadmap and [`docs/SYNTAX.md`](docs/SYNTAX.md) for
> the syntax guide.

## Quick example

```sk
on join:
    add 1 to {joins}
    broadcast "%player% joined! (%{joins}% total)"
    send "Welcome to the server!" to player

every 5 minutes:
    broadcast "Still going strong."
```

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

## Documentation

- [`docs/SYNTAX.md`](docs/SYNTAX.md) — the language guide (events, effects, conditions, control flow,
  variables, functions, commands).
- [`docs/PLAN.md`](docs/PLAN.md) — architecture and roadmap.
- [`CONTRIBUTING.md`](CONTRIBUTING.md) — building, project layout, and writing addons.

## Addons

NeoSkript exposes a typed SPI (`neoskript-api`). Implement `NeoSkriptAddon` and either ship it via
`ServiceLoader` or register it from a dependent plugin's `onEnable`. See
[`CONTRIBUTING.md`](CONTRIBUTING.md#writing-an-addon).

## Releases

- **Nightly builds:** every push to `main` refreshes a rolling prerelease. Grab the latest jar at
  `…/releases/download/nightly/NeoSkript-nightly.jar`.
- **Stable / beta releases:** push a tag. `v1.2.3` cuts a release; a tag with a `-` suffix
  (`v1.2.3-beta.1`) is marked a prerelease. The version flows into the jar via
  `-PneoskriptVersion=…`.
- **Modrinth:** tag releases also publish to Modrinth when these repository settings are present —
  variable `MODRINTH_PROJECT_ID`, secret `MODRINTH_TOKEN` (optional variable `MINECRAFT_VERSIONS`,
  default `1.21`). Prerelease tags upload as Modrinth `beta` channel versions.

## License

NeoSkript is licensed under the [GNU General Public License v3.0](LICENSE).
