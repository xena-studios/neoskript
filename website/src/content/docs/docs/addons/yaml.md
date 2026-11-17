---
title: YAML files
description: NeoSkriptYaml, the official addon for YAML files from scripts. It takes skript-yaml's syntax, so scripts written for skript-yaml run unchanged, and saves safely on Paper and Folia.
sidebar:
  label: YAML files (skript-yaml)
---

NeoSkriptYaml lets scripts read and write YAML files: their own config files, and data that doesn't belong in variables. It replaces [skript-yaml](https://github.com/Sashie/skript-yaml) and takes its syntax, pattern for pattern, from its latest release (1.7.2), so scripts written for skript-yaml run unchanged. Files it writes read the same in skript-yaml, and the other way round.

What it does better:

- **Saving never holds up the server or breaks a file.** A save is written off the server's threads, to a temporary file that then replaces the old one in one step. The file is always either as it was or as it was saved, even if the server dies mid-save. Scripts still see their saves at once: loading the file again, or checking that it exists, waits for the save.
- **Folia.** Every script on every region's thread can use the same file at once.
- **A broken file is left alone.** skript-yaml loads a file that isn't valid YAML as empty, and its next save wipes the file. NeoSkriptYaml doesn't load it, says why in the console, and doesn't touch it.

## Installing

1. Install [NeoSkript](/docs/install/).
2. Put `NeoSkriptYaml-<version>.jar` in `plugins/`, the same version as NeoSkript's. They're released together.
3. Remove skript-yaml's jar. It needs Skript, so it can't load with NeoSkript anyway; if it's still there, the console says so when NeoSkriptYaml starts.
4. Restart the server.

`/sk info` lists NeoSkriptYaml among the addons once it's running.

## An example

```skript nocheck
on load:
	load yaml "plugins/MyScript/config.yml" as "config"
	if yaml path "spawn" of "config" doesn't exist:
		set yaml value "spawn.world" of "config" to "world"
		set yaml value "spawn.message" of "config" to "&aWelcome!"
		set the header of "config" to "MyScript's settings"
		save yaml "config"

command /homes:
	trigger:
		loop yaml node keys "homes.%player's uuid%" of "data":
			send "%loop-key%: %loop-val%" to player
```

## Syntax

The same as skript-yaml's. Each element is in the [syntax reference](/syntax/), marked as NeoSkriptYaml's.

| What | Syntax |
|---|---|
| Load | `load yaml "path"`, `load yaml "path" as "id"`, `load yaml "path" using the file path as the id`, `load all yaml from directory "folder"` |
| Unload | `unload yaml "id"`, `unload yaml directory "folder"` |
| Save | `save yaml "id"`, with `with an indentation of 2`, `without extra lines between nodes`, or `to "other path"` |
| Delete | `delete yaml "id"`, `delete yaml "id" and keep loaded in memory`, `delete all yaml from directory "folder"` (the trigger waits while the files are deleted, off the server's threads, as with skript-yaml) |
| Ids and paths | `change yaml id from "a" to "b"`, `change yaml "id" file path to "path"` |
| Values | `yaml value "a.b" of "id"`, `yaml list "a" of "id"`, `yaml node keys "a" of "id"`, `yaml node list "a" of "id"`, `index 1 of yaml list "a" of "id"`. Set, add to, remove from and delete them |
| Comments | `the comments of yaml node "a" of "id"`, `the header of "id"` |
| Listing | `all yaml nodes of "id"`, `the loaded yaml`, `the unloaded yaml files from directory "folder"`, `the loaded yaml directories` |
| Conditions | `yaml "id" is loaded`, `is empty`, `is modified`; `yaml path "a" of "id" exists`, `has value`, `has list`; `yaml node "a" of "id" is a list/node/value`; `yaml file "path" exists` |
| Loops | in a loop over node keys, nodes or a list: `loop-key`, `loop-node`, `loop-val`, `loop-list`, `loop-subnodekeys`, `loop-id` |
| Function | `loadYaml("id", "path")`, `loadYaml("id", "path", true)` for a non-relative path; true if it loaded |

Text set without `without string checks` is stored as what it reads as: `"true"` as a boolean, `"5"` as a number, as skript-yaml does. Text read has its `&` colour codes turned into colours.

## Paths

Paths are from the server's folder, the one the server was started in: `plugins/MyScript/config.yml`. With `non-relative`, they're from the file system's root.

A relative path has to stay inside the server's folder: `../other-server/config.yml` isn't loaded, and the console says why. So text from a player in a path (`"plugins/MyScript/%arg-1%.yml"`) can't reach other files on the machine. A `non-relative` path may point anywhere, because the script asks for that by name, and scripts are trusted as much as plugins are. skript-yaml allows any path.

## Values in files

Values are written as skript-yaml writes them, so a file moves between the two:

| Value | In the file |
|---|---|
| Text, numbers, booleans, lists, nodes | as YAML's own. Text with `&` is quoted |
| Locations, vectors | `!location` and `!vector` with their numbers |
| Items | as Bukkit writes them, with `==: org.bukkit.inventory.ItemStack` |
| Block data | `!blockdata` |
| Timespans, dates, times, colours, weather | `!skripttimespan 5 minutes`, `!skriptdate`, `!skripttime`, `!skriptcolor`, `!skriptweather` |
| Anything else (entity types, players...) | `!skriptclass` with its type and the bytes `variables.csv` would have for it |

## Saving

`save yaml` takes what the file holds at that moment and writes it in the background. Saves of one file are written in the order they happen; several before the first is written write only the last. When the server stops, saves still waiting are written before it does, and scripts that save as the server stops (in `on unload`) write straight away.

If a save can't be written (the disk is full, or a folder can't be made), the console says so, and the file on disk stays as it was.

## Folia

Loaded files are shared by every script on every thread. Each change, read and save is one step that another thread can't land in the middle of, so two regions changing and saving one file at once leave it whole, with both changes. The syntax doesn't touch the world, so it runs on whichever thread the trigger is on.

## Limits

To keep a bad file from taking the server down, a file is refused (and left alone) if it:

- is larger than 64 MB of text, or holds more than 5 million values once read (YAML's aliases can make a small file read as billions);
- nests more than 50 levels deep, or has a list or node that holds itself;
- has a list or a single value at its top level, rather than keys.

A value with a tag nobody knows, a location in a world that isn't loaded, or a `!skriptclass` of a type no plugin has, reads as nothing, and is written back unchanged when the file is saved.

## Differences from skript-yaml

Scripts behave the same, except where skript-yaml 1.7.2 is broken. The [test harness](https://github.com/xena-studios/neoskript/tree/main/test-harness) runs the same script on Skript 2.17 with skript-yaml and on NeoSkript with NeoSkriptYaml, and compares what each prints and every file each writes, byte for byte. These are the differences, each on purpose:

- **Broken files.** A file that isn't valid YAML, or whose top level isn't keys, isn't loaded, and is left alone. skript-yaml loads it empty, and its next save overwrites the file.
- **Files ending in a comment.** skript-yaml adds the text `null` after a file's last line when it's a comment, so the file fails to load. These load.
- **A comment right under the header** is kept. skript-yaml drops it when it saves.
- **Values it can't read are kept.** skript-yaml makes them nothing, so its next save loses them, or fails to load the file.
- **Saving on Minecraft 26.** skript-yaml takes Minecraft 26 for a version before 1.13, and fails to save locations, vectors and text with `&`, leaving the file half written. NeoSkriptYaml writes them as skript-yaml does on 1.21.
- **What's read after a save is what was set.** skript-yaml replaces values in memory with what it wrote, so after a save, an entity type read back is skript-yaml's own wrapper.
- **`all yaml nodes`** lists each path once, and includes keys with nothing in them. skript-yaml lists nested paths twice, and leaves out a top-level key with no value.
- **Numbers from files are numbers.** `remove 2 from yaml list "x" of "id"` removes a 2 read from the file. skript-yaml compares the file's whole number with the script's and finds them different. Numbers read are whole numbers or decimals, as scripts' are.
- **Lists with empty items.** `yaml list` gives the items that are there. skript-yaml gives nothing at all for a list with an empty item.
- **Several node keys at once.** `add "a" and "b" to yaml node keys "x" of "id"` adds both, and the root's keys (`""`) work. skript-yaml only adds the first, and at the root, adds a key with a dot in front.
- **Several paths in conditions.** `yaml path "a" and "b" of "id" exists` checks both. skript-yaml fails with an error.
- **`loop-key` in a loop over `yaml node list`** is the key, `b`. skript-yaml gives it with the dot in front, `.b`.
- **`delete all yaml from directory`** works. On Skript 2.17, skript-yaml's fails as the script loads, so the script has an error there.
- **Saving with both options.** `save yaml "id" to "path" without extra lines between nodes` with `non-relative` honours both. skript-yaml honours neither.
- **Deleting a key** whose path shares a part with another (`a`, and `b.a.c`) leaves the other in `all yaml nodes`. skript-yaml removes it from the list.
- **Folders.** `load all yaml from directory` loads every folder given, in order of file name, even if one isn't a folder. skript-yaml stops at the first that isn't. A folder's files are found with or without a `/` at the end.
- **Aliases** (`&anchor` and `*anchor`) are written out in full when the file is saved, and changing one key never changes another. skript-yaml shares them, so a change to one shows in both.
- **Warnings** name the line's syntax rather than its script and line number, and appear as NeoSkriptYaml's.
