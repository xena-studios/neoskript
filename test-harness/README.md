# Test harness

Boots real Paper and Folia servers, installs NeoSkript or the reference Skript, and runs scenarios against them. CI runs the smoke group on every pull request and push to `main`, and the baseline group on Paper 26.3 weekly and when the harness changes. You can also run it locally. References like D12 are the plan's [locked decisions](../docs/PLAN.md#2-locked-decisions).

Needs JDK 21 and JDK 25 installed. Gradle finds them automatically.

```sh
./gradlew :test-harness:harness                                   # smoke scenarios, every environment
./gradlew :test-harness:harness -Pharness.env=folia-26.3          # one environment
./gradlew :test-harness:harness -Pharness.group=baseline          # Skript's suite, the differential, the benchmarks
./gradlew :test-harness:harness -Pharness.scenario=skript-smoke   # one scenario
./gradlew :test-harness:harness -Pharness.echo=true               # stream server consoles
```

Reports go to `test-harness/build/harness/reports/`, and each run's server directory and `harness-console.log` go to `test-harness/build/harness/runs/<env>/<scenario>/`. Downloaded jars and unpacked servers are cached in `.harness-cache/` at the repo root.

## Scenarios

| Scenario | Group | Runs on | Checks |
|---|---|---|---|
| `neoskript-boot` | smoke | all | NeoSkript enables and detects the right platform and version |
| `neoskript-hello` | smoke | all | NeoSkript compiles and runs `scripts/neoskript-hello.sk` |
| `neoskript-language` | smoke | all | NeoSkript runs `scripts/neoskript-language.sk`: variables, arithmetic, loops, conditionals and functions |
| `neoskript-skript-tests` | smoke | all | M2's exit check: NeoSkript runs Skript's own control-flow and function tests in test mode, and all of them must pass |
| `neoskript-reload` | smoke | all | `/sk reload`: changed scripts switch over, a script with errors keeps its last version (D12), and one script can be reloaded on its own |
| `neoskript-auto-reload` | smoke | all | A script with `auto reload` reloads itself when its file changes, with Skript's messages. A version with errors keeps the old one running |
| `neoskript-runtime-errors` | smoke | all | Runtime errors print as Skript prints them, with its per-frame limits and timeouts |
| `neoskript-events` | smoke | all | Server events, driven by console commands: triggers and their filters run for the right events, `cancel event` stops the command, a message without `to` goes to the event's sender, and a trigger that waits carries on |
| `neoskript-commands` | smoke | all | Script commands, run from the console: arguments, aliases, subcommands, choices, default values, named arguments, `executable by`, usage messages, `execute command`, and a reload that changes one command and removes the others |
| `neoskript-real-world-corpus` | smoke | all | NeoSkript loads the real-world corpus without problems and every script's self-test passes, with no thread violations or cross-region errors in the log (M5's Folia check) |
| `neoskript-regions` | smoke | all | Region ownership, with two pigs in different regions: on Folia, reading one from another region's thread is a runtime error naming the line, changes reach each pig's own thread, and `run on` runs code on its thread in order with the rest of the trigger. On Paper the same script runs with no errors |
| `skill-examples` | smoke | all | Every example script in the AI skill (`plugins/skript`) loads without errors or warnings on NeoSkript, and on the reference Skript in Skript envs |
| `neoskript-items` | smoke | all | Items and inventories, with the server's registries: item literals with amounts, plurals and enchantments, names and lore, items parsed from text, and chest inventory slots |
| `neoskript-hooks` | baseline | Paper 26.x | Skript's plugin hooks against the real plugins, pinned by SHA-256: WorldGuard 7.0.20 (with WorldEdit 7.4.6) regions written into its `regions.yml`, Vault 1.7.3 with EssentialsX 2.22.0 money and balances, and LuckPerms 5.5.71 groups. Baseline, as it downloads about 16 MB of plugins |
| `neoskript-skript-suite` | baseline | all | Every one of Skript's own tests, run by NeoSkript: reports how many pass, fail and can't run yet (M4's progress measure). Only fails if no results come back |
| `neoskript-variables` | smoke | Skript envs | M3's exit check: Skript's `variables.csv` round-trips through NeoSkript byte for byte, and Skript reads and rewrites NeoSkript's file byte for byte. A file with its last line cut short is backed up, the rest loads, and it isn't written over with less |
| `neoskript-skript-migration` | smoke | all | With only `plugins/Skript/scripts`, NeoSkript copies the scripts over on first start and runs them |
| `neoskript-skript-conflict` | smoke | Skript envs | With Skript installed, NeoSkript refuses to run and Skript still works (D10) |
| `skript-smoke` | smoke | Skript envs | The reference Skript runs `scripts/smoke.sk` |
| `skript-real-world-corpus` | smoke | Skript envs | The reference Skript loads the real-world corpus with no errors or warnings, and every self-test passes |
| `neoskript-parse-corpus` | smoke | Skript envs | NeoSkript's frontend parses Skript's tests, the real-world corpus and the benchmark scripts with zero false errors against the stub registry, and times it. No server needed |
| `skript-test-suite` | baseline | Skript envs | Runs Skript's own test suite in Skript's test mode and records the results |
| `skript-load-benchmark` | baseline | Skript envs | Times Skript loading and reloading 40 generated scripts (600 structures) |
| `neoskript-player-events` | smoke | 26.3 | Real players from the harness bot (`src/bot`): join, chat, commands and quit run their triggers, messages reach the players, and on Paper the same script behaves the same on the reference Skript |
| `differential` | baseline | Skript envs, Folia 26.3 | M4's exit check: Skript's documentation examples, the checks and probes in `corpus/differential` and the real-world corpus, on the reference Skript and on NeoSkript. Each script's load messages, the probes' output and the saved variables are compared; at least 95% must match (every unit does, since 2026-11-11), and any difference `corpus/differential/known-differences.txt` doesn't list fails. Folia is compared with Skript on Paper |
| `neoskript-cli` | smoke | Paper 26.3 | `neoskript-cli check` loads the real-world corpus cleanly, and gives the Folia and performance hints for a script with known hazards |
| `syntax-export` | manual | Skript envs | Not a check: writes the data for neoskript.dev's syntax reference. NeoSkript's registry (`-Dneoskript.syntaxExport`) as `syntax-export.json` and the reference Skript's `sk gen-docs` output as `skript-docs.json`. Run it by name; `pnpm syntax:sync` in `website/` turns them into the site's data |
| `neoskript-load-benchmark` | baseline | Skript envs | The same measurement on NeoSkript, for the comparison on neoskript.dev |
| `event-benchmark` | baseline | Skript envs | What a trigger costs per event, on Skript and NeoSkript with `scripts/event-benchmark.sk`: the harness bot fires each toggle event 20,000 times in a row, 12 rounds, and the median of the last 8 is the result |
| `tick-benchmark` | baseline | Skript envs | Milliseconds per tick with 20 bots online and `scripts/tick-benchmark.sk` running, on Skript, on NeoSkript and with no scripts: the median of 8 readings of the server's mean over 100 ticks |
| `variable-benchmark` | baseline | Skript envs | Loading 140,000 saved variables when the server starts: Skript saves them with `scripts/variables-benchmark.sk`, then each loads that `variables.csv` 3 times |

## Pins

- `environments/*.json`: one per server, pinned to an exact build and SHA-256. Paper ships builds daily and sometimes breaks plugins (build 211 of 26.3 broke Skript 2.17.0), so bump builds on purpose, not automatically. `skriptReference` marks the servers the reference Skript supports. That excludes Folia, which Skript doesn't support, and 1.21.4, which is older than Skript 2.17.0's minimum (1.21.5).
- `reference-skript.properties`: the Skript release that serves as the spec (D9).

## Corpus

**Real-world scripts** live in `corpus/real-world/`. They're written like the scripts a typical server runs, and use a wide spread of syntax: functions across files, commands with cooldowns, GUIs, list variables, timespans, `if all:` and so on. Each `<name>.sk` must define `selftest_<name>()` (with `-` as `_`) that checks its own logic with `check(suite, label, actual, expected)`, `check_true` and `fail` from `scripts/corpus-assert.sk`. The harness generates a `/corpustest` command that runs every self-test, so a new script is picked up just by adding the file. Keep scripts free of errors and warnings on the reference Skript, since the scenario fails on either.

**Skript's own tests:** `./gradlew :test-harness:fetchSkriptTests` extracts Skript's own test scripts (GPL-3.0) for the pinned version into `build/corpus/skript/`, in the same layout as Skript's repo.

## The differential and Skript's variation

Skript breaks ties between syntax of equal priority by identity hash, so a few units get a different answer from one Skript run to the next: which error a line gets, or none. The `differential` scenario handles that in three ways, and only that: any other difference still fails, unless `known-differences.txt` lists it.

- **Several runs.** It runs Skript `-Pharness.differential.skriptRuns` times (3 by default), each run cached in `.harness-cache/differential` by its inputs, and a unit matches when NeoSkript gives what Skript gave in any of them.
- **Recorded answers.** `corpus/differential/skript-varies.json` lists each unit seen to vary and every answer Skript has given for it, with a hash of the unit's script. A unit there also matches when NeoSkript gives one of those answers, though this time's runs didn't happen to give it. An entry counts only while the script hashes the same; once the script changes, it's ignored until it's recorded again. The report lists the units that matched only so (`matchedARecordedSkriptAnswer`) and the answers its runs found that aren't recorded yet (`skriptAnswersToRecord`).
- **Keeping it up to date.** Run it with more runs and `-Pharness.differential.recordVariations=true` to add what they found, then commit the file:

  ```sh
  ./gradlew :test-harness:harness -Pharness.env=paper-26.3 -Pharness.scenario=differential \
      -Pharness.differential.skriptRuns=10 -Pharness.differential.recordVariations=true
  ```

  Only units that vary between Skript's runs, or are already recorded, are added. A unit where NeoSkript's own answer varies isn't Skript's variation and isn't recorded: fix NeoSkript. For example, NeoSkript parses scripts in parallel, and an event value error Skript gives only the first time (`Use 'attacker' and/or 'victim' in damage/death events`) went to whichever script was parsed first. When one is used up, the scripts are now parsed again one after another, in order.
