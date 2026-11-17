---
title: Skript addons
description: NeoSkriptCompat, the optional plugin that lets Skript addons such as SkBee load on NeoSkript. What works so far, and what doesn't yet.
sidebar:
  label: Skript addons (preview)
---

Skript addons are built against Skript's own code (`ch.njol.skript`), which NeoSkript doesn't have. **NeoSkriptCompat** is an optional plugin that stands in for Skript: an addon finds it as Skript, registers its syntax with it as it would with Skript, and that syntax goes to NeoSkript. It's a preview: it's early, and only part of what addons register works yet.

## Installing it

Put `NeoSkriptCompat.jar` in `plugins/`, next to NeoSkript and the addon. It comes with NeoSkript's releases, at the same version.

- **Don't install Skript as well.** NeoSkriptCompat tells the server it's Skript, so the server loads only one of the two. If Skript's jar is there too, NeoSkriptCompat says so in the console.
- NeoSkriptCompat is opt-in. Without it, nothing about NeoSkript changes, and addons that need Skript don't load.

## What works

**SkBee 3.26.0** enables with all its modules. Its types, expressions, effects, conditions and functions are registered on NeoSkript; its sections, events and structures aren't yet, and the console says what was left out when the server starts.

What's compared with Skript 2.17.0 + SkBee 3.26.0, by NeoSkript's test harness on Paper 26.3:

| SkBee syntax | |
|---|---|
| World heights: `max height of %worlds%`, `min height of %worlds%` | Same as on Skript |
| World auto-save: `world autosave of %worlds%`, and setting it | Same as on Skript |
| Game ticks: `game ticks of %worlds%` | Same as on Skript |
| Chunks: `chunk x/z of %chunk%`, `chunk key of %chunk%`, `chunk key at %location%`, `chunk key at %number%, %number%` | Same as on Skript |
| Keys: `minecraft key of %objects%` (items, worlds, text), `translation key of %objects%` | Same as on Skript |
| `server is paused` | Same as on Skript |

The rest of SkBee's registered syntax may work, but isn't checked yet. Syntax that reaches a part of Skript NeoSkriptCompat doesn't have yet fails when the line runs, with an error that names what's missing.

## What doesn't work yet

- An addon's **sections, events and structures** (for SkBee: text component sections, its events, `on load` style structures).
- **Saving** an addon's own types in variables.
- An addon's **type properties** (Skript 2.17's `name of`, `amount of` for an addon's types).
- An addon element that **changes its argument** (`set`, `add` on what it was given).
- An element that turns down a line (its setup returns false) makes that line an error; on Skript, other syntax would be tried.
- Folia: SkBee doesn't support Folia.

SkBee is the first addon worked on. SkQuery and skript-reflect come after it; skript-reflect reaches deepest into Skript and may never work fully. Progress is tracked on [GitHub](https://github.com/xena-studios/neoskript/issues/45).
