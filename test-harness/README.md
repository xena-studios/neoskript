# Test harness

Boots real Paper and Folia servers, installs NeoSkript or the reference Skript, and runs scenarios against them. CI runs it on every push. You can also run it locally.

Needs JDK 21 and JDK 25 installed. Gradle finds them automatically.

```sh
./gradlew :test-harness:harness                                   # smoke scenarios, every environment
./gradlew :test-harness:harness -Pharness.env=folia-26.3          # one environment
./gradlew :test-harness:harness -Pharness.group=baseline          # Skript test suite + load benchmark
./gradlew :test-harness:harness -Pharness.scenario=skript-smoke   # one scenario
./gradlew :test-harness:harness -Pharness.echo=true               # stream server consoles
```

Reports go to `build/harness/reports/`, and each run's server directory and `harness-console.log` go to `build/harness/runs/<env>/<scenario>/`. Downloaded jars and unpacked servers are cached in `.harness-cache/` at the repo root.

## Scenarios

| Scenario | Group | Runs on | Checks |
|---|---|---|---|
| `neoskript-boot` | smoke | all | NeoSkript enables and detects the right platform and version |
| `neoskript-skript-conflict` | smoke | Skript envs | With Skript installed, NeoSkript refuses to run and Skript still works (D10) |
| `skript-smoke` | smoke | Skript envs | The reference Skript runs `scripts/smoke.sk` |
| `skript-test-suite` | baseline | Skript envs | Runs Skript's own test suite in Skript's test mode and records the results |
| `skript-load-benchmark` | baseline | Skript envs | Times Skript loading and reloading 40 generated scripts (600 structures) |

## Pins

- `environments/*.json`: one per server, pinned to an exact build and SHA-256. Paper ships builds daily and sometimes breaks plugins (build 211 of 26.3 broke Skript 2.17.0), so bump builds on purpose, not automatically. `skriptReference` marks the servers the reference Skript supports. That excludes Folia, which Skript doesn't support, and 1.21.4, which is older than Skript 2.17.0's minimum (1.21.5).
- `reference-skript.properties`: the Skript release that serves as the spec (D9).

## Corpus

`./gradlew :test-harness:fetchSkriptTests` extracts Skript's own test scripts (GPL-3.0) for the pinned version into `build/corpus/skript/`, in the same layout as Skript's repo.
