# NeoSkript — Implementation Gaps

A living comparison of NeoSkript against the [Skript 2.15.3 documentation](https://docs.skriptlang.org/docs.html).

Skript documents roughly **900+ syntax entries**. NeoSkript now implements all of the major
**engine-level** capabilities plus a broad, representative set of built-in syntax. What remains is
largely a **long tail of additional content** (more events/conditions/effects/expressions/types of
the same shapes already supported) and a few deeper engine features.

_Last reviewed: 2026-06-30._

## ✅ Implemented

**Structures:** events, periodic (`every`), `on load`, user functions, **custom commands**
(`command /name:` with permission/description/usage/aliases/trigger and `arg-N`/`args`), **`options:`**
with `{@key}` substitution.

**Sections:** `if`/`else if`/`else`, `while`, `loop … times`, `loop <values>`, **loop control**
(`exit loop`, `continue`), and **top-level `wait <timespan>`** (scheduler-backed delays).

**Events (~21):** join, quit, chat, death, block break/place, interact, inventory click, command,
respawn, teleport, move, drop, sneak toggle, gamemode change, damage, explode, food level, sign
change, weather, world load — plus **event-values** (`event-block`/`event-world`/`event-entity`/…)
and **`cancel event`**/`uncancel event`.

**Conditions:** equality and numeric comparisons, `is set`, `contains`, `starts/ends with`,
`matches` (regex), `is between`, `chance of`, `is op`, `is online/offline`, `is alive/dead`,
`is sneaking/sprinting/flying`, `has permission`.

**Effects:** broadcast, send/message, send actionbar (with legacy `&` colour codes), set, add,
remove, delete/clear, replace-in-text, return, stop, exit loop, continue, cancel/uncancel event,
set health/food/gamemode, kill, heal, feed, op/deop, kick, teleport to player, set walk speed,
allow/disallow flight, set display name, clear inventory, set time of world, make world sunny/stormy,
execute command (`make … execute`, `execute console command`).

**Expressions:** player, console, event-values, loop-value/number/index, name of, size/amount of,
random number/integer, arithmetic (`+ - * /`, parentheses, constant-folded), variables (incl. lists
and dynamic names), function calls; string ops (uppercase, lowercase, length, split, join); list ops
(first/last/random element, reversed, sorted); player properties (health, max health, food, level,
uuid, gamemode, world, location, name); all/online players; gamemode literals; command `sender`,
`arg-N`, `args`; comma/`and`-separated list literals (`1, 2 and 3`).

**Types:** `number`, `text`, `boolean`, `player`, `world`, `gamemode` (registry-backed display/parse).

**Built-in functions:** abs, round, floor, ceil, sqrt, exp, ln, log, sin/cos/tan (+inverse), mod,
atan2, min, max, sum, product.

## ❌ Remaining — content long tail

- **Types:** location, vector, item/itemstack, entity (beyond generic Entity), block (beyond
  event-value), inventory, slot, biome, colour, enchantment, material, chunk, offline player, date,
  direction, …
- **Effects:** set block, give/remove items, play sound/particles, apply/clear potion effects,
  open/close/modify inventories, send title, set time/weather, scoreboard, set fly/walk speed,
  push/launch, ignite, set name/display name, replace-in-string, …
- **Expressions:** location components (x/y/z, world), distance/direction/vectors, item properties
  (amount/name/lore/enchants), world/time/weather, target/looked-at block, nearby entities, date/now
  and time differences, more string/number formatting, …
- **Events:** the remaining ~80 (craft, smelt, enchant, projectile, vehicle, portal, bucket, leash,
  tame, breed, world save/init, chunk load, server ping, tab complete, …).
- **Conditions:** the remaining ~100 (is wearing, can build/fly, is banned/whitelisted, world is,
  is alive/dead, is burning/in water, chance of %, …).
- **Functions:** `vector()`, `location()`, `date()`, `rgb()`/colour, `world()`, `player()`, …

## ❌ Remaining — deeper engine features

1. **Type system depth:** the comparator graph and the converter/serializer graph (cross-type
   comparison/conversion, and persistence of rich types beyond strings/numbers/booleans).
2. **Async/delays beyond the top level:** `wait` inside `if`/`loop`/`while`/functions, and explicit
   `async:`/`run … async` sections (needs a full continuation-based runtime).
3. **Functions:** typed/optional/default parameters and enforced return types; local functions.
4. **Structures:** `aliases:`, `variables:` defaults, `import`/`using`.
5. **Persistence:** SQL backends; serialization of non-simple variable values.
6. **Text:** MiniMessage (`<red>`) in addition to legacy `&` codes.
7. **Pattern engine niceties:** `the`/`a`/`an` articles, plural handling, parse marks/tags from
   alternations, comma-separated expression lists (`a, b and c`).

## Notes

- New content slots into the existing registries with the same builder API — adding events,
  conditions, effects, and expressions is now mostly mechanical and individually testable.
- The hardest remaining item is **(2)** — full continuation-based async; top-level `wait` is supported
  today and the rest is deliberately deferred to keep execution correct.
