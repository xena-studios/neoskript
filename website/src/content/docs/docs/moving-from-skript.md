---
title: Moving from Skript
description: Swap the jar. NeoSkript copies your scripts and variables on its first start, and leaves Skript's folder as it was.
---

Swapping the jar is the whole move.

On its first start, NeoSkript copies `scripts/` and `variables.csv` from `plugins/Skript` into `plugins/NeoSkript`, then leaves a marker so it never copies again. It **copies** rather than moves, so Skript's folder stays exactly as it was, and switching back means putting `Skript.jar` back.

A few of Skript's `config.sk` settings carry over to NeoSkript's `config.yml` in that first start: whether players are saved by UUID in variable names, and the effect command settings. The rest of `config.sk` isn't copied; NeoSkript's defaults match Skript's.

## What's different

NeoSkript aims to behave exactly as Skript does, messages included. Where it doesn't yet, the difference is written down in the [progress log](https://github.com/xena-studios/neoskript/blob/main/docs/PROGRESS.md), under each batch's known differences.
