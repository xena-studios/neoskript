---
title: Moving from Skript
description: Swap the jar. NeoSkript copies your scripts and variables on its first start, and leaves Skript's folder as it was.
---

Swapping the jar is the whole move.

## Check your scripts first

The script checker, `neoskript-cli`, tells you before you switch what NeoSkript makes of your scripts. Download `neoskript-cli-0.1.0.jar` from [its release](https://github.com/xena-studios/neoskript/releases/tag/cli-v0.1.0) (it needs Java 21, or 25 to check against Minecraft 26.3), then point it at Skript's scripts folder:

```sh
java -jar neoskript-cli-0.1.0.jar check plugins/Skript/scripts
```

It starts a throwaway headless Paper server (downloaded once into `~/.neoskript/cache` and checked against its SHA-256), loads the scripts there without running them, and lists each line NeoSkript won't load, Skript's warnings, and hints on what may behave differently on Folia or cost a lot of server time. Options: `--json <file>`, `--hints-only`, `--quiet`, `--minecraft 26.3|1.21.4`, `--verbose`. It exits with 0 when every script loads, 1 when some don't, and 2 when the check couldn't run.

## Switching

On its first start, NeoSkript copies `scripts/` and `variables.csv` from `plugins/Skript` into `plugins/NeoSkript`, then leaves a marker so it never copies again. It **copies** rather than moves, so Skript's folder stays exactly as it was, and switching back means putting `Skript.jar` back.

A few of Skript's `config.sk` settings carry over to NeoSkript's `config.yml` in that first start: whether players are saved by UUID in variable names, and the effect command settings. The rest of `config.sk` isn't copied; NeoSkript's defaults match Skript's.

## What's different

NeoSkript aims to behave exactly as Skript does, messages included. Where it doesn't yet, the difference is written down in the [progress log](https://github.com/xena-studios/neoskript/blob/main/docs/PROGRESS.md), under each batch's known differences.
