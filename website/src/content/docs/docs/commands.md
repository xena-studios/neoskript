---
title: Commands
description: /skript, also /sk, /neoskript and /ns. Skript's subcommands, with NeoSkript's messages.
---

`/skript` has the subcommands and arguments Skript's has, so what you type, and any console automation, keeps working. `/sk`, `/neoskript` and `/ns` are the same command. It needs `skript.admin`, which ops have.

In chat, click a command in `/sk help` to type it. Script names in reports and lists are clickable too. Tab completion offers the subcommands, then the scripts and folders that fit: disabled ones for `enable`, enabled ones for `disable`.

| Command | What it does |
|---|---|
| `/sk reload <script>` | Reloads a script. `.sk` can be left off. A folder ends with `/`: `/sk reload games/`. |
| `/sk reload scripts` | Reloads every script. |
| `/sk reload config` | Reads `config.yml` again. See [configuration](/docs/configuration/) for what applies at once. |
| `/sk reload all` | Both. |
| `/sk reload last` | Reloads the script you reloaded last. |
| `/sk reload aliases` | Nothing to reload: NeoSkript's aliases are built in. |
| `/sk enable <script>` | Enables a disabled script or folder and loads it. `all` enables every disabled script. |
| `/sk disable <script>` | Unloads a script or folder and disables it. `all` disables every script. |
| `/sk list` | The enabled and disabled scripts, grouped by folder. Also `/sk show`. |
| `/sk info` | NeoSkript's version, the server, how many scripts, addons, hooked plugins, updates and links. |
| `/sk update` | Asks Modrinth for a newer NeoSkript. `/sk update changes` links to the release notes. |
| `/sk restore` | Writes the loaded scripts, as they were when they loaded, to a new folder in `plugins/NeoSkript/dump`. For when a script's file was deleted. Also `/sk dump`. |
| `/sk help` | The list of subcommands. |

A script is disabled by renaming it with a `-` in front: `-shop.sk`. Disabled scripts don't load when the server starts.

## Reload reports

A reload says what it reloaded, how many scripts, and how long it took:

```
[NeoSkript] Reloading all scripts...
[NeoSkript] Reloaded 12 scripts in 48 ms.
```

When scripts have problems, each script's are listed first, with their line numbers. In chat, hover over a problem to see the line of code, and click it to copy the line. The console gets the line of code written under it:

```
[NeoSkript] Reloading all scripts...
 shop.sk - 1 error
   Line 14: Can't understand this condition/effect: sned "hi" to player
     sned "hi" to player
[NeoSkript] Reloaded 12 scripts with 1 error in 52 ms. Scripts with errors keep their last version.
```

A script with errors keeps running the version it had, so a typo doesn't take it down. A script that wasn't loaded before loads what it can. Players with `skript.reloadnotify` see who reloaded what, and the same report. A script with `auto reload` reports its reloads the same way.

Problems when the server starts, and runtime errors, are written as Skript writes them, so tools and guides that read Skript's log still work.

## Permissions

These are Skript's.

| Permission | Default | What it gives |
|---|---|---|
| `skript.admin` | op | `/skript`, and update notices when joining |
| `skript.reloadnotify` | no one | Hears about others' reloads |
| `skript.see_runtime_errors` | op | Hears that a script had runtime errors |
| `skript.effectcommands` | no one | [Effect commands](/docs/configuration/#effect-commands) |

## How it differs from Skript

- A reload never unloads first. A script with errors keeps its last version.
- `reload config` reads `config.yml`; NeoSkript has no `config.sk`.
- `update` uses NeoSkript's update checker. Development builds don't check.
- `gen-docs` points to the [syntax reference](/syntax/) instead of writing docs files.
- The messages are NeoSkript's own, in the [theme](/docs/configuration/#make-it-look-like-your-server) from `config.yml`.
