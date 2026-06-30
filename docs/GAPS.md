# NeoSkript — Implementation Gaps

A living comparison of NeoSkript against the [Skript 2.15.3 documentation](https://docs.skriptlang.org/docs.html).

Skript documents roughly **900+ syntax entries**. NeoSkript now implements all of the major
**engine-level** capabilities plus a broad, representative set of built-in syntax. What remains is
largely a **long tail of additional content** (more events/conditions/effects/expressions/types of
the same shapes already supported) and a few deeper engine features.

_Last reviewed: 2026-06-30._

## ✅ Implemented

**Structures:** events, periodic (`every`), `on load`, user functions (with default parameters), **custom commands**
(`command /name:` with permission/description/usage/aliases/trigger and `arg-N`/`args`), **`options:`** with `{@key}` substitution, and **`variables:`** defaults.

**Sections:** `if`/`else if`/`else`, `while`, `loop … times`, `loop <values>`, **loop control**
(`exit loop`, `continue`), and **`wait <timespan>` anywhere in a trigger** incl. inside if/while/loop (scheduler-backed continuation interpreter).

**Events (~61):** join, quit, chat, death, block break/place, interact, inventory click/open/close,
command, respawn, teleport, move, drop, pickup, sneak/sprint/flight toggle, gamemode change, level/exp
change, world change, damage, explode, regen, tame, creature spawn, projectile hit/launch, food level,
consume, fish, bed enter, sign change, craft, smelt, vehicle enter/exit, weather, lightning, world
load/save/init, chunk load/unload, enchant, portal, bucket, redstone, breed, target, shoot bow, server load — plus **event-values** (`event-block`/`event-world`/`event-entity`/…) and
**`cancel event`**/`uncancel event`.

**Conditions:** equality and numeric comparisons, `is set`, `contains`, `starts/ends with`,
`matches` (regex), `is between`, `chance of`, `is op`, `is online/offline`, `is alive/dead`,
`is sneaking/sprinting/flying`, `can fly`, `is blocking/gliding/glowing/sleeping/swimming`,
`is on ground`, `is burning`, `is whitelisted/banned`, `is in a vehicle`, `has permission`, `has <item>`, `is holding <item>`, `is wearing <item>`, `is tamed/leashed`, `has played before`, `world is raining`.

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
atan2, min, max, sum, product, `vector()`, `location()`, `item()`, `world()`, `player()`.

**Persistence:** flatfile and JDBC (SQLite) backends with rich-type serialization (location, item, vector, …).

## ❌ Remaining — content long tail

- **Types:** entity (beyond generic Entity), block (beyond event-value), inventory,
  slot, biome, colour, enchantment, material, chunk, offline player, date, direction, …
- **Effects:** play sound/particles, apply/clear potion effects, open/modify inventories,
  set weather amount/duration, scoreboard, ban/unban, …
- **Expressions:** direction/vector math, item properties (amount/name/lore/enchants), weather
  state, target/looked-at block, nearby entities, time differences, more string/number formatting, …
- **Events:** the remaining (leash, tame variants, server ping, tab complete, advancement, and the long tail).
- **Conditions:** the remaining (can build, generic "world is", is in water/lava, and the long tail).
- **Functions:** `date()`, `rgb()`/colour, …

## ❌ Remaining — deeper engine features

1. **Type system depth:** a comparator graph wired into conditions (the converter graph and rich-type serializers are done; conditions still use the simpler Comparisons facade).
2. **Async sections:** explicit `async:` / `run … async` blocks. (`wait` anywhere in a trigger now works via the continuation interpreter; delays inside functions remain disallowed, as in Skript.)
3. **Functions:** typed parameters with enforced return types, and local functions (default/optional params are done).
4. **Structures:** `aliases:` (custom item aliases), `import`/`using`.
5. **Pattern engine niceties:** `the`/`a`/`an` articles, plural handling, parse marks/tags from
   alternations.

## Completing the gaps

See [`COMPLETION_PLAN.md`](COMPLETION_PLAN.md) for the phased engineering plan to close everything
listed above — type-system graph, async runtime, persistence backends, structures, pattern niceties,
and the content long tail — with designs, tests, risks, and sequencing.

## Notes

- New content slots into the existing registries with the same builder API — adding events,
  conditions, effects, and expressions is now mostly mechanical and individually testable.
- A continuation-based interpreter now supports `wait` anywhere in a trigger; explicit `async:` sections are the small remaining piece of that area.
