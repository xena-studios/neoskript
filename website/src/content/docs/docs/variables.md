---
title: Saving variables
description: Global variables are saved in Skript's variables.csv, or optionally in NeoSkript's faster native storage.
---

Global variables are saved in `plugins/NeoSkript/variables.csv`, in Skript's format byte for byte, so Skript can read the file and NeoSkript can read Skript's. They load before scripts, and changes are saved every 5 minutes (`variables.save-interval` in `config.yml`) and when the server stops. Variables whose names start with `-`, like `{-cooldown::%player%}`, stay in memory, as in Skript.

A save writes a new file and then swaps it in, so a crash during a save leaves the last one whole.

## Native storage

For servers with many variables, NeoSkript has its own storage. Turn it on in `config.yml` and restart:

```yaml
variables:
  storage: native
```

The variables are then saved in `variables.nsv`, next to where `variables.csv` was.

- **Faster to load.** 140,000 variables load in 277 ms, against 465 ms from `variables.csv` (see below), and the file is a third the size.
- **A save writes only what changed.** Each save adds the variables that changed since the last one to the end of the file, instead of writing every variable again. Now and then, when the file has grown to more than twice what its variables need, a save writes it afresh.
- **A crash can't cut a save in half.** Each save carries a checksum. If the server stops in the middle of one, the next start reads everything up to the last whole save, backs the file up to `backups/`, and drops the rest.

Values are kept in Skript's own format inside it, as in `variables.csv`, so moving between the two loses nothing, values NeoSkript can't read yet included.

### Switching, and going back

- **To native:** when the server starts with `storage: native` and there's no `variables.nsv` yet, NeoSkript copies `variables.csv` to `backups/`, moves its variables into `variables.nsv`, and removes `variables.csv`, so only one file ever holds your variables.
- **Back to CSV:** set `storage: csv` and restart. NeoSkript writes `variables.csv` again, from `variables.nsv`, and moves `variables.nsv` to `backups/`. That `variables.csv` is what Skript reads, so this is also the way back to Skript.

While `variables.nsv` exists, it's the file in use. If a `variables.csv` is next to it, NeoSkript says so and doesn't read it; remove `variables.nsv` (after keeping a copy) to import that file instead.

### How fast

The [variable benchmark](https://github.com/xena-studios/neoskript/blob/main/test-harness/README.md) has Skript make 140,000 variables of the kinds servers keep (numbers, texts, booleans, locations and lists), then times loading them when the server starts, as each logs it (median of 3, Paper 26.3):

| | Load | File |
|---|---|---|
| Skript, `variables.csv` | 600 ms | 7.8 MB |
| NeoSkript, `variables.csv` | 465 ms | 7.8 MB |
| NeoSkript, native | 277 ms | 2.5 MB |

Saving one changed variable out of those 140,000 appends a few dozen bytes, where `variables.csv` writes all 7.8 MB again.

## Databases

Skript's `databases` section in `config.sk` (SQLite and MySQL) isn't supported. Skript 2.17 itself only saves to CSV unless the SQLibrary plugin is installed, so `variables.csv` is what servers use.
