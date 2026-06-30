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

Releases are automated with [release-please](https://github.com/googleapis/release-please) and your
Conventional Commits.

- **Cutting a release:** every push to `main` updates a release PR that bumps `version.txt` and
  `CHANGELOG.md` based on the commits. Merging that PR creates the tag + GitHub Release, then the
  publish job builds the plugin jar, attaches it, publishes `neoskript-api`/`neoskript-core` to
  **GitHub Packages**, and (if configured) uploads to **Modrinth**.
- **Nightly builds:** every push to `main` also refreshes a rolling prerelease (updated in place).
  Grab the latest jar at `…/releases/download/nightly/NeoSkript-nightly.jar`.
- **Minecraft version** is single-sourced in [`gradle.properties`](gradle.properties)
  (`paperApiVersion` for `paper-plugin.yml`, `minecraftVersions` for Modrinth) — adjust both there.
- **Modrinth** requires repository settings: variable `MODRINTH_PROJECT_ID` and secret
  `MODRINTH_TOKEN`. The Modrinth step is skipped until `MODRINTH_PROJECT_ID` is set.
- **release-please PRs:** the workflow uses `GITHUB_TOKEN`, which requires Settings → Actions →
  "Allow GitHub Actions to create and approve pull requests". If that's locked by org policy, add a
  PAT secret `RELEASE_PLEASE_TOKEN` (Contents + Pull requests write) — the workflow prefers it.

### Consuming the API

Once published, addons can depend on `neoskript-api` from GitHub Packages:

```kotlin
repositories { maven("https://maven.pkg.github.com/xena-studios/neoskript") }
dependencies { compileOnly("co.xenastudios:neoskript-api:<version>") }
```

## License

NeoSkript is licensed under the [GNU General Public License v3.0](LICENSE).
