# NeoSkript — Implementation Gaps

A living comparison of NeoSkript against the [Skript 2.15.3 documentation](https://docs.skriptlang.org/docs.html).

Skript documents roughly **900+ syntax entries**. NeoSkript now implements all of the major
**engine-level** capabilities plus a broad, representative set of built-in syntax. What remains is
largely a **long tail of additional content** (more events/conditions/effects/expressions/types of
the same shapes already supported) and a few deeper engine features.

_Last reviewed: 2026-06-30._

## ✅ Implemented

**Structures:** events, periodic (`every`), `on load`, user functions (with default parameters, `local function`, and best-effort typed parameters/return types), **custom commands**
(`command /name:` with permission/description/usage/aliases/trigger and `arg-N`/`args`), **`options:`** with `{@key}` substitution, **`variables:`** defaults, and **`aliases:`** (custom item aliases). `import`/`using` are recognised and skipped (no reflective Java bridge).

**Sections:** `if`/`else if`/`else`, `while`, `loop … times`, `loop <values>`, **loop control**
(`exit loop`, `continue`), and **`wait <timespan>` anywhere in a trigger** incl. inside if/while/loop (scheduler-backed continuation interpreter).

**Events (~61):** join, quit, chat, death, block break/place, interact, inventory click/open/close,
command, respawn, teleport, move, drop, pickup, sneak/sprint/flight toggle, gamemode change, level/exp
change, world change, damage, explode, regen, tame, creature spawn, projectile hit/launch, food level,
consume, fish, bed enter, sign change, craft, smelt, vehicle enter/exit, weather, lightning, world
load/save/init, chunk load/unload, enchant, portal, bucket, redstone, breed, target, shoot bow, server load — plus **event-values** (`event-block`/`event-world`/`event-entity`/…) and
**`cancel event`**/`uncancel event`.

**Comparisons:** type-aware equality/ordering — numeric by value, same-type `Comparable` by natural
order, and a string matches a typed value by its display form (e.g. `gamemode is "survival"`).

**Conditions:** equality and numeric comparisons, `is set`, `contains`, `starts/ends with`,
`matches` (regex), `is between`, `chance of`, `is op`, `is online/offline`, `is alive/dead`,
`is sneaking/sprinting/flying`, `can fly`, `is blocking/gliding/glowing/sleeping/swimming`,
`is on ground`, `is burning`, `is whitelisted/banned`, `is in a vehicle`, `has permission`, `has <item>`, `is holding <item>`, `is wearing <item>`, `is tamed/leashed`, `has played before`, `world is raining/thundering`, `is in water/lava`, `can build`.

**Effects:** broadcast, send/message, send actionbar (legacy `&` and MiniMessage colours), set, add,
remove, delete/clear, replace-in-text, return, stop, exit loop, continue, cancel/uncancel event,
set health/food/gamemode, kill, heal, feed, op/deop, kick, teleport to player, set walk speed, give/take items,
allow/disallow flight, set display name, clear inventory, set time of world, make world sunny/stormy,
set max health, ignite/extinguish, set block, send title,
execute command (`make … execute`, `execute console command`).

**Expressions:** player, console, event-values, loop-value/number/index, name of, size/amount of,
random number/integer, arithmetic (`+ - * /`, parentheses, constant-folded), variables (incl. lists
and dynamic names), function calls; string ops (uppercase, lowercase, length, split, join); list ops
(first/last/random element, reversed, sorted); player properties (health, max health, food, level,
uuid, gamemode, world, location, name); location coordinates (x/y/z) and distance between; all/online
players; gamemode literals; command `sender`, `arg-N`, `args`; comma/`and`-separated list literals
(`1, 2 and 3`); world time (`time of`), current timestamp (`now`), type of <item>/<entity>, and velocity of <entity>.

**Types:** `number`, `text`, `boolean`, `player`, `world`, `gamemode`, `location`, `vector`
(registry-backed display/parse).

**Built-in functions:** abs, round, floor, ceil, sqrt, exp, ln, log, sin/cos/tan (+inverse), mod,
atan2, min, max, sum, product, `vector()`, `location()`, `item()`, `world()`, `player()`, `rgb()`, `date()`.

**Persistence:** flatfile and JDBC (SQLite) backends with rich-type serialization (location, item, vector, …).

## ❌ Remaining — content long tail

- **Types:** entity (beyond generic Entity), block (beyond event-value), inventory,
  slot, biome, enchantment, material, chunk, offline player, direction, … (colour and date are implemented)
- **Effects:** particles, open/modify inventories, set weather amount/duration, scoreboard,
  ban/unban, … (`play sound` and apply/clear potion effects are implemented)
- **Expressions:** direction math, more item properties (name/lore/enchants), weather
  state, target/looked-at block, nearby entities, time differences, more string/number formatting, … (x/y/z and length now work on vectors; amount of an item gives its stack size)
- **Events:** the remaining (leash, tame variants, server ping, tab complete, advancement, and the long tail).
- **Conditions:** the remaining long tail (`can build`, `is in water/lava`, and `is thundering` are now implemented).

## ❌ Remaining — deeper engine features

1. **Pattern engine niceties:** plural handling and parse marks/tags from alternations (leading
   `the`/`a`/`an` articles are now tolerated, and plural type names are normalised in function signatures).

## Completing the gaps

See [`COMPLETION_PLAN.md`](COMPLETION_PLAN.md) for the phased engineering plan to close everything
listed above — type-system graph, async runtime, persistence backends, structures, pattern niceties,
and the content long tail — with designs, tests, risks, and sequencing.

## Notes

- New content slots into the existing registries with the same builder API — adding events,
  conditions, effects, and expressions is now mostly mechanical and individually testable.
- A continuation-based interpreter supports `wait` anywhere in a trigger. Like Skript, NeoSkript has
  no separate `async:` section type — off-thread work is the domain of add-ons.
