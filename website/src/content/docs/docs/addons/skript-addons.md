---
title: Skript addons
description: NeoSkriptCompat, the optional plugin that lets Skript addons such as SkBee load on NeoSkript. What works so far, and what doesn't yet.
sidebar:
  label: Skript addons (preview)
---

Skript addons are built against Skript's own code (`ch.njol.skript`), which NeoSkript doesn't have. **NeoSkriptCompat** is an optional plugin that stands in for Skript: an addon finds it as Skript, registers its syntax with it as it would with Skript, and that syntax goes to NeoSkript. It's a preview: it's early, and only part of what addons register works yet.

## Installing it

Put `NeoSkriptCompat.jar` in `plugins/`, next to NeoSkript and the addon. It comes with NeoSkript's releases, at the same version.

- **Don't install Skript as well.** NeoSkriptCompat tells the server it's Skript, so the server loads only one of the two, with an "Ambiguous plugin name" error. If it loads NeoSkriptCompat, NeoSkriptCompat says so in the console and NeoSkript runs the scripts; if it loads Skript, NeoSkript stands down.
- An addon's syntax is tried where Skript tries it, so a line reads as it does on Skript with the same addon. On Skript, some of SkBee's syntax comes before Skript's own (`item model of`, `stored enchantments of`, damage sources), and it does on NeoSkript too.
- NeoSkriptCompat is opt-in. Without it, nothing about NeoSkript changes, and addons that need Skript don't load.

## What works

**SkBee 3.26.0** enables with all its modules. Its types, expressions, effects, conditions, events and functions are registered on NeoSkript; its sections and structures aren't yet, and the console says what was left out when the server starts.

What's compared with Skript 2.17.0 + SkBee 3.26.0, by NeoSkript's test harness on Paper 26.3:

| SkBee syntax | |
|---|---|
| World heights: `max height of %worlds%`, `min height of %worlds%` | Same as on Skript |
| World auto-save: `world autosave of %worlds%`, and setting it | Same as on Skript |
| Game ticks: `game ticks of %worlds%` | Same as on Skript |
| Chunks: `chunk x/z of %chunk%`, `chunk key of %chunk%`, `chunk key at %location%`, `chunk key at %number%, %number%` | Same as on Skript |
| Keys: `minecraft key of %objects%` (items, worlds, text), `translation key of %objects%` | Same as on Skript |
| `server is paused` | Same as on Skript |
| Events: `on entity removed from world`, `on server tick start`, `on async chat` (with `async chat message`), `on unknown command` (with `event-string`), `on player chunk send`, `on tab complete of "/a" and "/b"`, `on damage of zombie by cactus or magma block` | Same as on Skript |
| NBT: `nbt compound from %string%`, `%nbttype% %string% of %nbtcompound%` (int, string, double, int list, nested with `;`), setting and deleting tags, `has tag`, `tag type of tag`, `custom nbt of %itemtype%`, `%itemtype% with custom nbt %nbtcompound%`, `is an empty nbt compound`, and saving an NBT compound in a variable across a restart | Same as on Skript |

The rest of SkBee's registered syntax may work, but isn't checked yet. Syntax that reaches a part of Skript NeoSkriptCompat doesn't have yet fails with an error that names what's missing: when the script loads if it reaches it while the line is set up, otherwise when the line runs.

## What doesn't work yet

- An addon's **sections and structures** (for SkBee: text component and bound sections, its structures).
- **Saving** an addon's own types that are enums in variables. Its other types are saved (SkBee's NBT compounds, text components, keys...), in Skript's format.
- An addon's **type properties** (Skript 2.17's `name of`, `amount of` for an addon's types).
- An addon's effects that **wait** and run the rest of the trigger themselves (SkBee's `load chunk`, `load world`).
- An addon element that **changes its argument** (`set`, `add` on what it was given).
- An element that turns down a line (its setup returns false) makes that line an error; on Skript, other syntax would be tried.
- An element that needs a written value for an argument works only with one value there, not a list (`entities in range of beacon` with `zombies and skeletons`).
- Folia: SkBee doesn't support Folia.

SkBee is the first addon worked on; SkQuery comes after it. skript-reflect reaches deepest into Skript, so it has an official replacement instead, [NeoSkript Reflect](/docs/addons/reflect/). Progress is tracked on [GitHub](https://github.com/xena-studios/neoskript/issues/45).
