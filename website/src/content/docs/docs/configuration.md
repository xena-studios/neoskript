---
title: Configuration
description: Every setting in NeoSkript's config.yml, and how to make its messages look like your server.
---

The config is `plugins/NeoSkript/config.yml`, written on the first start with a comment on every setting. Apply changes with `/sk reload config`. A setting that isn't valid falls back to its default, and the console says which and why. A file that isn't valid YAML is never overwritten: on a reload the running settings stay, and at startup NeoSkript runs on the defaults until it's fixed.

## Make it look like your server

The `theme` section at the top sets the look of NeoSkript's messages: `/sk`, reload reports and update notices. It's the same as in Xena Studios' other plugins: a prefix, the text colour, and three styles the messages use as tags.

```yaml
theme:
  prefix: "<dark_gray>[<#FF4D9D>NeoSkript</#FF4D9D>]</dark_gray> "
  base: "<gray>"            # text with no tag of its own
  highlight: "<#FF4D9D>"    # names and numbers
  success: "<#5EE08A>"      # done
  error: "<#FF6B6B>"        # something went wrong
```

Styles are [MiniMessage](https://docs.advntr.dev/minimessage/format) tags, such as `<gray>`, `<#FF4D9D>` or `<gold><bold>`. Quote a colour that starts with `#`. The prefix can be anything, gradients too, or `""` for none.

Script problems at startup and runtime errors keep Skript's look, since tools and guides read them.

## What applies when

`/sk reload config` applies the theme, the update checker, `variables.player-uuids-in-names`, `variables.save-interval`, the runtime error limits and effect commands at once. `metrics`, `variables.file` and the `events` settings apply when the server restarts; the reload says so when one of them changed.

## Effect commands

Skript's effect commands: with `effect-commands.enabled`, a console command or chat message starting with the token (`!`) runs as an effect, such as `!set time to 6:00`. Players need `skript.effectcommands`, or to be an op with `allow-ops` on. These commands can do anything, so give the permission carefully.

## Moving from Skript

The first start copies Skript's settings for effect commands and `use player UUIDs in variable names` from `config.sk`. See [moving from Skript](/docs/moving-from-skript/).

## Updating NeoSkript

When a new version adds settings, NeoSkript adds them with their defaults and says so in the console. When the layout changes, `config-version` goes up: the old file is backed up as `config.yml.bak-v<version>`, and the new one keeps every value you set. Files from before NeoSkript had a theme are version 1.

## The full default config

```yaml title="plugins/NeoSkript/config.yml"
# NeoSkript's config. Docs: https://neoskript.dev/docs/configuration/
#
# Apply changes with /sk reload config. Most settings apply at once; those that say so wait for a
# restart. A bad value falls back to its default, with a warning in the console.

# Managed by NeoSkript, to carry your settings forward on updates. Don't edit it.
config-version: 2

# The look of NeoSkript's messages: /sk, reload reports and update notices. They use these as tags, so
# changing a colour here restyles them all. Write a style as MiniMessage tags: <gray>, <#FF4D9D>, or
# <gold><bold>. Script problems and runtime errors keep Skript's own look, which tools read.
theme:
  # <prefix> starts NeoSkript's messages, such as "<dark_gray>[<#FF4D9D>MyServer</#FF4D9D>]</dark_gray> ",
  # or blank for none.
  prefix: "<dark_gray>[<#FF4D9D>NeoSkript</#FF4D9D>]</dark_gray> "
  # The colour of message text that has no tag of its own.
  base: "<gray>"
  # <highlight>: names and values, such as a script or a number.
  highlight: "<#FF4D9D>"
  # <success>: something done, such as a script reloaded.
  success: "<#5EE08A>"
  # <error>: something that went wrong, such as a script's errors.
  error: "<#FF6B6B>"

# Send anonymous usage statistics to bStats: https://bstats.org/plugin/bukkit/NeoSkript/34868
# You can also turn bStats off for every plugin in plugins/bStats/config.yml. Applies on restart.
metrics: true

# Check Modrinth for newer NeoSkript versions when the server starts and once a day. A newer version is
# logged to the console and shown to players with skript.admin when they join. Development builds never check.
update-checker: true

variables:
  # Where global variables are saved, in Skript's variables.csv format (Skript can read it too).
  # Relative to plugins/NeoSkript. Variables whose names start with - aren't saved. Applies on restart.
  file: "variables.csv"
  # How often changed variables are saved, at least every 10 seconds. They're also saved when the server stops.
  save-interval: "5 minutes"
  # Whether players are their UUIDs in variable names ({money::%player%}) or their names, as Skript's
  # 'use player UUIDs in variable names' (on in Skript's default config). Moving over from Skript copies
  # its setting, so the variables it saved are found.
  player-uuids-in-names: true

events:
  # The priority scripts listen to events at: lowest, low, normal, high, highest or monitor. As in Skript,
  # the default is high, so scripts see what most other plugins did to an event, and can still cancel it.
  # Applies on restart.
  priority: "high"
  # Whether 'on <event>' triggers also run for events another plugin cancelled. Applies on restart.
  listen-to-cancelled: false

runtime-errors:
  # As Skript's 'runtime errors' in config.sk. Frames are the duration over which runtime errors are
  # collected; at the end of each, what wasn't printed is summed up.
  frame-duration: "1 second"
  # The most errors printed per frame; more are hidden, and counted at the end of the frame.
  total-errors-per-frame: 8
  # The most errors one line prints per frame, so a line in a loop can't fill the frame.
  errors-from-one-line-per-frame: 2
  # A line with this many errors in a frame is put in timeout: its errors are hidden for a while.
  error-spam-timeout-limit: 4
  # How many frames a line stays in timeout.
  error-timeout-length: 10
  # The same for warnings.
  total-warnings-per-frame: 8
  warnings-from-one-line-per-frame: 2
  warning-spam-timeout-limit: 4
  warning-timeout-length: 10

effect-commands:
  # Skript's effect commands, as its 'enable effect commands': console commands and chat messages starting with
  # the token are effects, run straight away, like '!set time to 6:00'. Players need the permission
  # skript.effectcommands, which ops don't have unless allow-ops is on, as these commands can do anything.
  # Moving over from Skript copies its settings for them.
  enabled: false
  # Skript's 'effect command token'. It can be longer than a character, but shouldn't start with / (a command).
  token: "!"
  # Skript's 'allow ops to use effect commands': ops can use them without the permission.
  allow-ops: false
  # Skript's 'log effect commands': players' effect commands are logged as '<player> issued effect command: <command>'.
  log: false
```
