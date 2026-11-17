---
title: Install
description: Put the NeoSkript jar in your plugins folder and start the server.
---

1. Check your server runs **Paper or Folia, Minecraft 1.21.4 or newer, on Java 21 or newer**.
2. Get the jar from [Modrinth](https://modrinth.com/plugin/neoskript) or [the download page](/download/).
3. If Skript is installed, take `Skript.jar` out of `plugins/`. NeoSkript won't start next to Skript: they'd fight over the same scripts and the same `/skript` command. Leave the `plugins/Skript` folder where it is; see [moving from Skript](/docs/moving-from-skript/).
4. Put the NeoSkript jar in `plugins/` and start the server.

Scripts go in `plugins/NeoSkript/scripts/`, as `.sk` files, like Skript's. `/sk reload <script>` reloads one; `/sk help` lists the rest. See [commands](/docs/commands/).

## Configuration

`plugins/NeoSkript/config.yml` is written on the first start, with a comment on every setting. It starts with a theme for NeoSkript's messages, then covers where variables are saved, the event priority scripts listen at, how runtime errors are rate-limited, effect commands, the update checker, which tells you about newer versions on Modrinth (`update-checker: false` turns it off), and bStats, which you can turn off with `metrics: false`. See [configuration](/docs/configuration/).
