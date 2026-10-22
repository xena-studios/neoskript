![NeoSkript by Xena Studios. The .sk icon and the NeoSkript wordmark over a magenta planet horizon, with the headline "Keep your scripts. Lose the lag." and a Skript script that runs unchanged.](.github/assets/banner.png)

**Keep your scripts. Lose the lag.**

NeoSkript is a fast, modern drop-in replacement for [Skript](https://github.com/SkriptLang/Skript). Your `.sk` scripts run unchanged, only faster, on Paper and Folia.

**Status:** in development. Nothing is released yet.

By [Xena Studios](https://xenastudios.co).

## Building

Requires JDK 21, plus JDK 25 to run the test harness on 26.x servers.

```sh
./gradlew build          # compile, unit tests, license header check
./gradlew runServer      # dev server: Paper 1.21.4 with NeoSkript
./gradlew runFolia       # dev server: Folia 1.21.4 with NeoSkript
```

The plugin jar ends up in `neoskript-plugin/build/libs/`. See [`test-harness/`](test-harness/README.md) for the real-server tests, and [`docs/PLAN.md`](docs/PLAN.md) for the design and roadmap.

## License

[GPL-3.0-or-later](LICENSE).
