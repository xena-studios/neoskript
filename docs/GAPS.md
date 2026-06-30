# NeoSkript — Implementation Gaps

A gap analysis of NeoSkript against the [Skript 2.15.3 documentation](https://docs.skriptlang.org/docs.html).

For scale, Skript documents roughly **900+ syntax entries** (≈100 events, ≈120 conditions, ≈150
effects, ≈400 expressions, ≈80 types, ≈50 functions, plus structures and sections). NeoSkript
currently implements a **representative vertical slice (~32 syntaxes + control flow)** — the
architecture is in place; built-in *content* is thin and grows against the conformance corpus.

_Last reviewed: 2026-06-29._

## ✅ Implemented today

- **Events (6):** join, quit, chat, death, block break, block place (+ `on load`, `every <timespan>`)
- **Effects (8):** broadcast, send/message, set, add, remove, delete/clear/reset, return, stop
- **Conditions (8):** `is`/`is not`/`=`/`!=`, `>`/`<`/`>=`/`<=` (+aliases), `is set`/`is not set`
- **Expressions (10):** player, console, loop-value, loop-number/index, name of player, size/amount of,
  random number, random integer, arithmetic, variables/literals/function calls
- **Types (1 real):** `number`; `string`/`player`/`object` handled loosely
- **Structures:** event triggers, user functions, periodic, load
- **Control flow:** if / else if / else, while, loop N times, loop list

## ❌ Structures — not implemented

- **`command /name:`** — custom commands (trigger, usage, permission, aliases, cooldown, arguments)
- **`options:`** + `{@option}` substitution
- **`aliases:`** custom item aliases
- **`variables:`** default-value section
- **`using <experimental feature>` / `import`**
- Typed/optional/default function parameters and enforced return types (params are untyped today)
- **`local function`**, function docstrings

## ❌ Sections — not implemented

- **Delays / async:** `wait 5 ticks`, `wait 1 second`, `async:` / `run … async` (the interpreter is
  synchronous — no continuations yet)
- **Loop control:** `exit loop`, `exit N sections`, `continue`; indexed loops over real list keys
- `cancel event` / event-modification sections
- Custom addon sections, `parse:` sections

## ❌ Events — ~95 missing (examples)

damage, click/right-click/left-click (interact), inventory click/open/close/drag, player move,
respawn, sneak/sprint/fly toggle, gamemode change, level/xp change, food/hunger change, item
drop/pickup, item craft/smelt/enchant, sign change, bucket fill/empty, projectile hit/launch, entity
spawn/death/target, explode, ignite/burn, world load/save/init, chunk load, weather/thunder change,
time, server start/stop/list ping, command run, tab complete, vehicle enter/exit, leash, tame, breed,
portal, teleport, bed enter, fish, regen, and many more.

**Plus event mechanics:** event priorities (`with priority highest`), `cancel event`, and event-values
(`event-block`, `attacker`, `victim`, `clicked block`, `chat message`, `damage`, …) — only `player`
is resolved today.

## ❌ Conditions — ~110 missing (examples)

has permission, is online/offline, is sneaking/sprinting/flying/swimming/sleeping/blocking/on
ground/in water/in lava, is burning, is op, can build/fly/see, is alive/dead, is wearing, has played
before, is banned/whitelisted, world is, gamemode is, **contains** (string/list/inventory),
**starts with / ends with**, **matches** (regex), is between/within, is wet, has scoreboard tag, is
holding, exists, chance of %.

## ❌ Effects — ~140 missing (examples)

teleport, give, drop, kill, damage, heal, feed, kick, ban/unban, op/deop, set gamemode, **set block**,
play sound, play/stop particles, spawn (entity), execute command (`make … execute`, `execute console
command`), apply/remove potion effect, set/add/remove from inventory, clear inventory, open/close
inventory, send title/actionbar/tab list, set flight/walk/fly speed, set health/food/level/xp,
set time/weather, push/launch, ignite/extinguish, set name/display name, enchant, **cancel event**,
set scoreboard tag, change game rule.

## ❌ Expressions — ~390 missing (the biggest gap)

- **Entities/players:** health, max health, food level, level, exp, gamemode, uuid, display/tab name,
  ip, ping, world, location, x/y/z, yaw/pitch, held item, inventory, target/targeted block, all
  players, online players, victim/attacker, nearby entities
- **Items/blocks:** item types, amount, name, lore, enchantments, durability, block at location,
  block type
- **World/server:** time, weather, difficulty, biome, light level, spawn, max players, version/TPS
- **Math/geometry:** distance between, vector, direction, location offsets, rounded/absolute
- **Strings:** uppercase/lowercase, replace, split, join, substring, length, contains, indices,
  formatted/colored/uncolored
- **Lists/collections:** first/last/random element, indices of, sorted, reversed, filtered,
  `x, y and z` literal lists
- **Time/date:** now, date, difference between, formatting
- Plus permissions list, scoreboard tags, parsed values (`"5" parsed as number`), etc.

## ❌ Types/Classes — ~78 missing

entity, offline player, item/itemstack/itemtype, location, world, vector, block, inventory, slot,
biome, color, gamemode, difficulty, enchantment, potion effect type, entity type, material, chunk,
**timespan**/date (only timespan *parsing* exists, not as a value type), direction, click type, damage
cause, game rule, region, etc.

**Underlying:** the `Type` SPI exists but the **converter graph, comparator registry, arithmetic
registry, and serializers are not built** — so cross-type comparison/conversion and persistence of
rich types don't work.

## ❌ Built-in functions — all ~50 missing

abs, ceil/floor/round, sqrt, exp, ln/log, sin/cos/tan (+ inverse), mod, min/max, sum/product,
vector(), location(), date(), rgb()/color, world(), random/rand, contains(), etc.

## ⚙️ Engine / semantic gaps that block whole categories

1. **No delays/async** (no continuation runtime) → blocks `wait`, async sections, timed effects.
2. **No event-value system / cancellation** → blocks most event-driven scripts.
3. **No custom commands** → a primary Skript use case.
4. **Shallow type system** → no converters/comparators/arithmetic registry/serializers; list
   variables are flat (no nested `::**`, no real index keys, no sorting/persistence ordering).
5. **Pattern engine limits** → no `the`/`a`/`an`, no plural/`%-type%`/optional-type handling, no parse
   marks/tags from `(a|b)`, no comma-list expressions.
6. **Persistence** → simple types only (no items/locations/entities), no SQL backends.
7. **Text** → no color codes (`&a`, `<red>`), no `formatted`/`uncolored`.

## Suggested priority order

1. Event-values + `cancel event`
2. Custom commands (`command /x:`)
3. Type-system depth (converters/comparators/serializers; core types: location, item, entity, world)
4. The big expression/effect families (player/entity/world/string)
5. Delays / async via a continuation-based runtime
