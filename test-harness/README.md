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
| `neoskript-reload-soak` | baseline | 26.3 | Reloads a varied set of scripts 350 times (events, commands, a GUI, functions, timers, waits in flight, a script broken every other time) and checks with the harness bot that the loaded classes, the heap after GC, and NeoSkript's listeners, commands and tasks stay flat, then that `/sk disable all` leaves nothing, `/reload confirm` lets old NeoSkripts go (Paper; Folia has no `/reload`), and stopping the server saves the variables. `-Pharness.soak.reloads=N` for more |
| `neoskript-auto-reload` | smoke | all | A script with `auto reload` reloads itself when its file changes, with Skript's messages. A version with errors keeps the old one running |
| `neoskript-runtime-errors` | smoke | all | Runtime errors print as Skript prints them, with its per-frame limits and timeouts |
| `neoskript-events` | smoke | all | Server events, driven by console commands: triggers and their filters run for the right events, `cancel event` stops the command, a message without `to` goes to the event's sender, and a trigger that waits carries on |
| `neoskript-commands` | smoke | all | Script commands, run from the console: arguments, aliases, subcommands, choices, default values, named arguments, `executable by`, usage messages, `execute command`, and a reload that changes one command and removes the others |
| `neoskript-real-world-corpus` | smoke | all | NeoSkript loads the real-world corpus without problems and every script's self-test passes, with no thread violations or cross-region errors in the log (M5's Folia check) |
| `neoskript-regions` | smoke | all | Region ownership, with two pigs in different regions: on Folia, reading one from another region's thread is a runtime error naming the line, changes reach each pig's own thread, and `run on` runs code on its thread in order with the rest of the trigger. On Paper the same script runs with no errors |
| `neoskript-addon` | smoke | all | The addon API: the example addon (`examples/example-addon`) registers a type, an event, an effect, an expression, a condition and a function, and a script uses them. Its command reads the script's variable and calls a function through the API, `/sk info` lists it, and the ticket saved with its codec loads on the next start |
| `neoskript-official-addons` | smoke | all | The official addons in `addons/`, as released: each jar loads next to NeoSkript and registers its syntax, a script uses the template's, and with a jar of the community addon it replaces in `plugins/` (which needs Skript, so Paper can't load it) the template still loads and says to remove that jar |
| `neoskript-pausing` | smoke | all | Addon syntax that pauses the trigger, with the example addon's lottery office, which answers on its own thread: values and a keyed list arrive with the locals and loops kept, a failure and a timeout are runtime errors after which the trigger carries on, on Folia a trigger carries on on its `run on` pig's thread and a console command's on the global thread, a trigger waiting through a reload carries on with its old code, and the server stops cleanly with one still waiting |
| `neoskript-placeholders` | smoke | 26.3 | NeoSkriptPlaceholders (`addons/placeholders`) against skript-placeholders 1.7.1: the same scripts on Skript 2.17.0 with it and on NeoSkript with ours, both with PlaceholderAPI 2.12.3, pinned by SHA-256, must print the same: placeholders registered by scripts and read back through PlaceholderAPI for nobody, players and two players, the errors, and reloads to other prefixes and to none, with PlaceholderAPI's list of hooks checked. On Folia, the same lines, and a read from a thread that doesn't own the player is a runtime error. skript-placeholders' jar is installed on NeoSkript too, which says to remove it |
| `neoskript-db` | smoke | all | NeoSkript DB (`addons/db`) against skript-db: `scripts/neoskript-db.sk` runs on Skript 2.17.0 with skript-db 2.0.0 (pinned by SHA-256) and on NeoSkript with NeoSkript DB, each against a fresh SQLite file, and what it prints, the load warnings and errors of `neoskript-db-errors.sk` and the tables after must match, and match `scripts/neoskript-db.expected` (what Folia and 1.21.4 are held to). NeoSkript only: `synchronously`, a console command, a reload while a slow statement runs, a masked password, no password in the console, and an `on unload` write as the server stops |
| `skill-examples` | smoke | all | Every example script in the AI skill (`plugins/skript`) loads without errors or warnings on NeoSkript, and on the reference Skript in Skript envs |
| `neoskript-items` | smoke | all | Items and inventories, with the server's registries: item literals with amounts, plurals and enchantments, names and lore, items parsed from text, and chest inventory slots |
| `neoskript-hooks` | baseline | Paper 26.x | Skript's plugin hooks against the real plugins, pinned by SHA-256: WorldGuard 7.0.20 (with WorldEdit 7.4.6) regions written into its `regions.yml`, Vault 1.7.3 with EssentialsX 2.22.0 money and balances, and LuckPerms 5.5.71 groups. Baseline, as it downloads about 16 MB of plugins |
| `neoskript-skript-suite` | baseline | all | Every one of Skript's own tests, run by NeoSkript: reports how many pass, fail and can't run yet (M4's progress measure). Only fails if no results come back |
| `neoskript-variables` | smoke | Skript envs | M3's exit check: Skript's `variables.csv` round-trips through NeoSkript byte for byte, and Skript reads and rewrites NeoSkript's file byte for byte. A file with its last line cut short is backed up, the rest loads, and it isn't written over with less |
| `neoskript-native-variables` | smoke | all | Native variable storage across restarts in one server folder: CSV storage saves variables of many types, switching to `native` moves them into `variables.nsv` after a backup and appends changes, a restart reads them back and writes nothing, a flipped bit in a committed save is refused (and said so at startup and shutdown), `variables.recover: true` loads up to the last good save and turns itself off, and switching back to `csv` writes `variables.csv` again with the same values |
| `neoskript-skript-migration` | smoke | all | With only `plugins/Skript`, NeoSkript copies the scripts and alias files over on first start, with config.sk's settings, and runs them |
| `neoskript-aliases` | smoke | all | Alias files in Skript's format: on Skript envs, the same files and probe script on the reference Skript, whose problems and probe output must match line for line. Then `/sk reload aliases` reloads the scripts that use the changed names, keeps a script whose alias went away (D12), reports the files Skript can't load, and survives 30 reloads in a row |
| `neoskript-skript-conflict` | smoke | Skript envs | With Skript installed, NeoSkript refuses to run and Skript still works (D10) |
| `skript-smoke` | smoke | Skript envs | The reference Skript runs `scripts/smoke.sk` |
| `skript-real-world-corpus` | smoke | Skript envs | The reference Skript loads the real-world corpus with no errors or warnings, and every self-test passes |
| `neoskript-parse-corpus` | smoke | Skript envs | NeoSkript's frontend parses Skript's tests, the real-world corpus and the benchmark scripts with zero false errors against the stub registry, and times it. No server needed |
| `skript-test-suite` | baseline | Skript envs | Runs Skript's own test suite in Skript's test mode and records the results |
| `skript-load-benchmark` | baseline | Skript envs | Times Skript loading and reloading 40 generated scripts (600 structures) |
| `neoskript-player-events` | smoke | 26.3 | Real players from the harness bot (`src/bot`): join, chat, commands and quit run their triggers, messages reach the players, and on Paper the same script behaves the same on the reference Skript |
| `differential` | baseline | Skript envs, Folia 26.3 | M4's exit check: Skript's documentation examples, the checks and probes in `corpus/differential` and the real-world corpus, on the reference Skript and on NeoSkript. Each script's load messages, the probes' output and the saved variables are compared; at least 95% must match (every unit does, since 2026-11-11), and any difference `corpus/differential/known-differences.txt` doesn't list fails. Folia is compared with Skript on Paper |
| `neoskript-cli` | smoke | Paper 26.3 | `neoskript-cli check` loads the real-world corpus cleanly, gives the Folia and performance hints for a script with known hazards, and reads the alias files beside a scripts folder |
| `syntax-export` | manual | Skript envs | Not a check: writes the data for neoskript.dev's syntax reference. NeoSkript's registry (`-Dneoskript.syntaxExport`) as `syntax-export.json` and the reference Skript's `sk gen-docs` output as `skript-docs.json`. With the official addons in `addons/` installed, whose elements are named by addon. Run it by name; `pnpm syntax:sync` in `website/` turns them into the site's data |
| `neoskript-load-benchmark` | baseline | Skript envs | The same measurement on NeoSkript, for the comparison on neoskript.dev |
| `event-benchmark` | baseline | Skript envs | What a trigger costs per event, on Skript and NeoSkript with `scripts/event-benchmark.sk`: the harness bot fires each toggle event 20,000 times in a row, 12 rounds, and the median of the last 8 is the result |
| `tick-benchmark` | baseline | Skript envs | Milliseconds per tick with 20 bots online and `scripts/tick-benchmark.sk` running, on Skript, on NeoSkript and with no scripts: the median of 8 readings of the server's mean over 100 ticks |
| `variable-benchmark` | baseline | Skript envs | Loading 140,000 saved variables when the server starts: Skript saves them with `scripts/variables-benchmark.sk`, then each loads that `variables.csv` 3 times, and NeoSkript imports it into its native storage and loads that 3 times |

## Pins

- `environments/*.json`: one per server, pinned to an exact build and SHA-256. Paper ships builds daily and sometimes breaks plugins (build 211 of 26.3 broke Skript 2.17.0), so bump builds on purpose, not automatically. `skriptReference` marks the servers the reference Skript supports. That excludes Folia, which Skript doesn't support, and 1.21.4, which is older than Skript 2.17.0's minimum (1.21.5).
- `reference-skript.properties`: the Skript release that serves as the spec (D9).

## Corpus

**Real-world scripts** live in `corpus/real-world/`. They're written like the scripts a typical server runs, and use a wide spread of syntax: functions across files, commands with cooldowns, GUIs, list variables, timespans, `if all:` and so on. Each `<name>.sk` must define `selftest_<name>()` (with `-` as `_`) that checks its own logic with `check(suite, label, actual, expected)`, `check_true` and `fail` from `scripts/corpus-assert.sk`. The harness generates a `/corpustest` command that runs every self-test, so a new script is picked up just by adding the file. Keep scripts free of errors and warnings on the reference Skript, since the scenario fails on either.

**Skript's own tests:** `./gradlew :test-harness:fetchSkriptTests` extracts Skript's own test scripts (GPL-3.0) for the pinned version into `build/corpus/skript/`, in the same layout as Skript's repo.
