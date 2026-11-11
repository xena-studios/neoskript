---
title: Install
description: Put the NeoSkript jar in your plugins folder and start the server.
---

1. Check your server runs **Paper or Folia, Minecraft 1.21.4 or newer, on Java 21 or newer**.
2. Get the jar from [the download page](/download).
3. If Skript is installed, take `Skript.jar` out of `plugins/`. NeoSkript won't start next to Skript: they'd fight over the same scripts and the same `/skript` command. Leave the `plugins/Skript` folder where it is; see [moving from Skript](/docs/moving-from-skript).
4. Put the NeoSkript jar in `plugins/` and start the server.

Scripts go in `plugins/NeoSkript/scripts/`, as `.sk` files, like Skript's. `/skript reload <script>` (or `/sk`, or `/neoskript`) reloads them.

## Configuration

`plugins/NeoSkript/config.yml` is written on the first start, with a comment on every option. It covers where variables are saved, the event priority scripts listen at, how runtime errors are rate-limited, effect commands, and bStats, which you can turn off with `metrics: false`.
