![NeoSkript by Xena Studios. The .sk icon and the NeoSkript wordmark over a magenta planet horizon, with the headline "Keep your scripts. Lose the lag." and a Skript script that runs unchanged.](.github/assets/banner.png)

**Keep your scripts. Lose the lag.**

NeoSkript is a fast, modern drop-in replacement for [Skript](https://github.com/SkriptLang/Skript). Your `.sk` scripts run unchanged, only faster, on Paper and Folia.

**Status:** in development, nothing released yet. Skript's whole test suite passes on NeoSkript. See [progress](docs/PROGRESS.md).

By [Xena Studios](https://xenastudios.co).

## Building

Needs JDK 21, plus JDK 25 for the test harness's 26.x servers.

```sh
./gradlew build        # compile, unit tests, licence header check
./gradlew runServer    # Paper 1.21.4 dev server with NeoSkript
./gradlew runFolia     # Folia 1.21.4 dev server with NeoSkript
```

The plugin jar ends up in `neoskript-plugin/build/libs/`.

## What's here

```
neoskript-api        the public addon API
neoskript-frontend   parser, type checker, diagnostics (no Bukkit)
neoskript-compiler   compiles scripts to JVM bytecode
neoskript-runtime    variables, storage, scheduling, waits
neoskript-paper      the standard library: Skript's syntax on Paper
neoskript-plugin     the plugin: /sk, config, reload
neoskript-cli        offline migration and diagnostics tool
test-harness         real Paper and Folia servers, run against Skript
```

| Doc | What's in it |
|---|---|
| [Plan](docs/PLAN.md) | Goals, locked decisions, architecture, roadmap |
| [Progress](docs/PROGRESS.md) | What each milestone and batch delivered |
| [Test harness](test-harness/README.md) | Running the real-server tests, scenarios, pins |

## Help

- **Bugs and ideas:** [open an issue](https://github.com/xena-studios/neoskript/issues/new/choose)
- **Security:** follow the [security policy](https://github.com/xena-studios/.github/blob/main/SECURITY.md)
- **Anything else:** [contact@xenastudios.co](mailto:contact@xenastudios.co)

## Licence

[GPL-3.0-or-later](LICENSE).
