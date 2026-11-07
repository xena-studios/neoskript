# Progress

What each milestone in the [plan](PLAN.md#6-roadmap) delivered, in order. Add each new step or batch at the end of its milestone.

## At a glance

| Milestone | Status |
|---|---|
| [M0: Foundations](#m0-foundations) | Done 2026-10-21 |
| [M1: Frontend](#m1-frontend) | Done 2026-10-22 |
| [M2: Compiler and runtime core](#m2-compiler-and-runtime-core) | Done 2026-10-22 |
| [M3: Variables and storage](#m3-variables-and-storage) | Done 2026-10-22 |
| [M4: Standard library](#m4-standard-library) | In progress: Skript's whole test suite passes (862 of 862); the exit also needs the 95% differential check |

Skript's own test suite, run by NeoSkript, through the long tail ([step 6 of M4](#step-6-the-long-tail)):

| Batch | Date | Tests passing | Focus |
|---|---|---|---|
| 1 | 2026-10-23 | 127 → 159 | Entity variants |
| 2 | 2026-10-23 | 159 → 166 | Language fixes and block data; 10 more tests run |
| 3 | 2026-10-23 | 166 → 177 | Equality rules, settable names and amounts |
| 4 | 2026-10-23 | 177 → 192 | Items, entities and logic; 29 more tests run |
| 5 | 2026-10-23 | 192 → 201 | Inline functions |
| 6 | 2026-10-23 | 201 → 208 | Script objects |
| 7 | 2026-10-23 | 208 → 241 | Settable conditions |
| 8 | 2026-10-23 | 241 → 250 | Directions, queues and list elements |
| 9 | 2026-10-23 | 250 → 256 | Entity effects and properties |
| 10 | 2026-10-23 | 256 → 282 | And/or lists, arithmetic on other types |
| 11 | 2026-10-23 | 282 → 343 | Default functions and vectors |
| 12 | 2026-10-23 | 343 → 529 | For loops, function references, colours, enchantments, displays |
| 13 | 2026-10-23 | 529 → 692 | Potions, quaternions, Skript's parse errors |
| 14 | 2026-10-24 | 692 → 734 | Functions that wait, unreachable-code warnings |
| 15 | 2026-10-25 | 734 → 776 | Aliases and type hints |
| 16 | 2026-10-25 | 776 → 803 | Most of Skript's events, loot tables, boss bars |
| 17 | 2026-10-25 | 803 → 841 | Returnable sections, equippable components |
| 18 | 2026-10-25 | 841 → 855 | Text components, damage sources, directions |
| 19 | 2026-10-25 | 856 → 862 | Particles; the whole Skript 2.17 suite passes |
| 20 | 2026-10-25 | 862 | Skript elements no test covers |
| 21 | 2026-10-25 | 862 | What Skript has beyond its tests |
| 22 | 2026-10-25 | 862 | Skript's plugin hooks and full patterns |
| 23 | 2026-10-25 | 862 | The parser knows the event |
| 24 | 2026-10-25 | 862 | Time states and auto reload |
| 25 | 2026-10-25 | 862 | Skript's /skript command |
| 26 | 2026-10-25 | 862 | Checked line by line against a real Skript server |

## M0: Foundations

**Done 2026-10-21.** The harness boots NeoSkript on Paper and Folia, from 1.21.4 to 26.3. The reference Skript (2.17.0) runs on Paper 26.3, where its own suite passes 853/853 under our harness, and NeoSkript correctly refuses to run alongside it. Skript can't run on Folia (D18), so the Folia half of the exit criterion is NeoSkript booting there. Baseline on an Apple Silicon dev machine: Skript loads the 600-structure, 5,960-line benchmark in ~1.7 s and reloads it in ~1.1–1.2 s (median). The real-world corpus is 12 scripts written in-house, about 1,100 lines: Skript loads them with no errors or warnings and passes all 12 self-tests. bStats is wired up (plugin ID 34868, `metrics: false` to opt out), and test servers never report to it. JMH microbenchmarks wait until M2, when there's compiled code to measure.

## M1: Frontend

**Done 2026-10-22.** The frontend parses the whole corpus with **zero false errors** against the stub registry: Skript's own tests (537 files, 17,705 lines), the real-world corpus (12 scripts and their assert helper) and the generated benchmark scripts (40 files, 6,000 lines). The `neoskript-parse-corpus` harness scenario checks this on every CI run.
- **Parse time** (median of 5 warm runs on an Apple Silicon dev machine): the 6,000-line benchmark in **40 ms**, the real-world corpus in 17 ms, Skript's tests in about 2 s. Skript takes ~1.7 s to load the benchmark scripts at startup. That's not a like-for-like comparison yet: Skript's number covers its whole load, and NeoSkript's covers parsing only, until M2 adds compilation.
- **What's built:** the structure parser; the pattern compiler, matcher and index; the registry (types, syntax, functions, type relations); and the expression, statement and script parsers.
  - Matching is memoized by span and expected type, filtered per line by the literals a pattern needs, anchored for a pattern's final placeholder, and prefers fully resolved parses over ones that need unresolved word literals.
  - Language features covered: typed literals (`black (color)`), percentages, block data, expression sections, `if any`/`if all`, command subcommands and named or ranged arguments, function overloads, named arguments, `->` return types, and Skript's test-only syntax.
- **Stub registry:** generated from Skript's docs export (`/sk gen-docs` with `-Dskript.testing.genDocs=true`), in the frontend's test fixtures. The export's patterns are in documentation form: no parse marks or tags, display-name types. A few things it leaves out are added by hand, each backed by a corpus script: regex roles, type relations, closed literal sets, a handful of pattern fixes, and the test-only syntax.
- **Known gap:** Skript reports an error in 90 of the corpus's `parse:` test blocks, and NeoSkript catches 21. Most of the rest are checks Skript makes in each element's own setup (an effect that only works on living entities, local variable type hints, unreachable code, expressions limited to certain events), so they come with each element's implementation in M4. The exit criterion was zero *false* errors, and that holds.

## M2: Compiler and runtime core

**Done 2026-10-22.** Skript's own control-flow and function tests run on NeoSkript on Paper and Folia, 1.21.4 and 26.3 (`neoskript-skript-tests`).
- **Results:** 33 of the tests in `SecConditional`, `SecLoop`, `SecWhile`, `EffContinue`, `ExprLoopValue`, `StructFunction` and function overloading pass. The other 8 need later milestones, and the scenario lists each one with the reason:
  - the `location()` function, item literals, entities, vectors and `keyed` lists
  - Skript's test-only loop peeking
  - two error messages worded exactly as Skript has them
- **Running scripts:**
  - The plugin loads `plugins/NeoSkript/scripts`. On its first start it copies Skript's scripts and variables (D19).
  - Each trigger and function compiles to a hidden class with ASM. Elements are called through class-data constants, so each call site has one target the JIT can inline. The compiler implements the core syntax itself.
  - Runtime errors are reported once, at the script line, even through deep recursion.
- **Core language:**
  - local and global variables (lists, dynamic names, case-insensitive, numeric index order) and their changes
  - arithmetic with Skript's precedence, and comparisons
  - `if` / `else if` / `else`, `if any`/`if all` … `then`, and `X if C, else Y`
  - `loop` (`loop-value`, `-index`, `-counter`, `next`/`previous`, `-N`), `while` and `do while`, `exit` / `continue`
  - functions with defaults, named arguments, overloads (with Skript's ambiguity rules and errors), local functions, and recursion
- **`wait`:** a trigger that waits compiles into a resumable state machine. Its frame saves the locals and the enclosing loops, and it carries on through the scheduler: the main thread on Paper, the global region on Folia. The frontend tracks Skript's delay state through branches, loops and `parse:` sections.
- **`/sk reload`** (`/skript`, `/sk`, `/neoskript`, D10):
  - It compiles everything first, then switches over together.
  - A script with errors keeps its last version, and its functions keep their old code (D12). Function handles last across reloads.
  - Replies use Skript's wording. A two-script reload takes about 2 ms (`neoskript-reload`).
- **Test mode** (`-Dneoskript.testing.enabled=true`): it runs Skript's test syntax and writes JSON results like Skript's runner.
- **Parse check:** still zero false errors, and it now catches 30 of the 90 errors Skript reports in its tests (up from 21).
- **Left for later:**
  - Waits in functions, and resuming on entity or region schedulers on Folia, come with events in M4.
  - Saving global variables is M3.

## M3: Variables and storage

**Done 2026-10-22.** NeoSkript reads and writes Skript's `variables.csv` byte for byte, both ways (`neoskript-variables`, run against the reference Skript).
- **The check:**
  - Skript saves 25 variables of every common type, including unicode and long text, lists, nested names, names with commas and spaces, locations, worlds, players, items, vectors and game modes.
  - NeoSkript picks them up through the migration, decodes them, and rewrites a byte-identical file.
  - In the other direction, Skript reads NeoSkript's file correctly and its rewrite is byte-identical too.
- **The format:** worked out from files Skript writes, not from Skript's code:
  - The header, then `name, type, hex` lines in tree order. Indices sort numbers by value; names are lowercased, and quoted if they contain commas, quotes or whitespace. The file ends with an empty line.
  - Values use Skript's binary encoding: raw primitives, compact string lengths, objects as tagged fields, and back-referenced names.
  - On reading, later lines win, and a line without a type deletes the variable.
- **What's decoded:** text, whole numbers, decimals, booleans, timespans, worlds, locations and players. Anything else (items, dates, vectors and so on) is kept as `RawValue` and written back unchanged until M4 gives it a type, so nothing is ever lost.
- **Storage** (`config.yml` → `variables`):
  - Variables load before scripts and save every 5 minutes if anything changed, and when the server stops.
  - Saving writes a temporary file and swaps it in. Skript only writes at shutdown, so a crash loses everything since start; NeoSkript loses at most the last interval.
  - `-` variables stay in memory, as in Skript.
  - Addons register codecs through `Registrar.storage`.
- **Databases:** deferred (D20).

## M4: Standard library

In progress. One PR per step. The order comes from `syntaxUsage` in the `neoskript-parse-corpus` report, which counts how often the corpus uses each Skript element. At the start of M4, the real-world corpus used 152 distinct elements and functions, and NeoSkript had 15 of them. Skript's tests use 785.

### Step 1: The event foundation

- Bukkit events, with event values (`player`, `event-world`) and `cancel event`.
- Types' default values (`send "hi"` goes to the event's player).
- Changing expressions, not just variables (`set name of player to ...`).
- Property expressions and conditions (`X's name`, `name of X`, `X is online`).
- Waits in events resume on the right Folia thread: the entity's for player events, the region's for block events.

**Done 2026-10-22.**
- **API:**
  - `Registrar.event(id, eventClasses, filter, patterns)` and `eventValue(eventClass, type, getter)`.
  - `eventDefault(type)`, so unused optional placeholders get the event's value.
  - `Changeable` for expressions that `set`/`add`/`remove`/`delete`/`reset` can change.
  - `property`/`propertyCondition`, with Skript's pattern shapes in `Patterns`.
- **Event values follow Skript's rules:**
  - It's enough for one of an event's classes to have the value; the closest event class wins, then the exact type.
  - Otherwise it's a compile error: "There's no world in a command event".
- **Listening:** each event class the loaded scripts use gets one Bukkit listener, rebuilt on reload, at `high` priority and skipping cancelled events, as Skript does by default (`config.yml` → `events`).
- **Events so far:** join, first join, quit, chat and command, with `cancel event`, `sender`, and some player syntax (UUID, name, online, permission, kick). The rest comes in step 3.
- **Tested by:**
  - Unit tests (`EventsTest`) with stand-in event classes.
  - `neoskript-events` on all four servers, driven by console commands.
  - The test servers have no players, so player events are only checked when compiled. Running them needs a bot client, which comes with the differential runner in step 6.

### Step 2: Commands

`command /x <args>:` with every entry (aliases, permission, usage, cooldown, executable by), `arg-1` and `sender`, registered with tab completion, and reloadable.

**Done 2026-10-22.**
- **Patterns, as in Skript 2.17:**
  - Words, `[optional]` parts and `(a|b)` choices.
  - Arguments: `<type>`, plural `<numbers>`, named `<x: number>` (the local `{_x}`), ranged `<x: number above 5 and below 10>`, and defaults `<text = home>` or `<player = %player%>`.
  - Subcommands, with `subcommands:` groups whose entries apply to what's in them.
  - Skript's errors for missing triggers, optional subcommands, `executable by` that a parent rules out, repeated argument names, and ranges.
- **Matching:** the first route that what's typed fits wins, subcommands before their command. Arguments take as few words as they can, so the last text argument gets the rest, and optional arguments are skipped when the next one fits.
- **Entries:** aliases, description, usage, permission and permission message, executable by, cooldown with its message and bypass permission. `cooldown storage` is a compile error for now (it needs dates).
- **Syntax:** `arg-1` and every other form of `ExprArgument`, choice arguments, `the [full] command`, `execute command`, cooldown info and `cancel the cooldown`, command info (`the description of command "x"`), `is a script command`, `all [script] commands`, and `X ? Y`.
- **Registration:** in the server's command map under `skript:` (see [plan 3.5](PLAN.md#35-standard-library-neoskript-paper)), replaced on every reload, then each online player gets the new command list.
- **Parser changes:** arithmetic and event values are tried after every library element, and a placeholder can't start inside a token like `arg-1` unless nothing else parses. So `arg-1 ? "x"` and `arg-1 * arg-2` mean what they say.
- **Tested by:**
  - Unit tests (`CommandParserTest`, `CommandsTest`).
  - `neoskript-commands` on all four servers, driven by the console, including a reload that changes one command and removes the others.
- **Not yet:** Skript's `StructCommand` test also needs local variable type hints and item types; `execute command` doesn't report "Failed to execute command" errors yet, as there's no `catch runtime errors`.

### Step 3: The real-world corpus

The text, player, time and location syntax it uses, and converters between types. *Done when* the 10 real-world scripts that don't use items load on NeoSkript and pass their self-tests, on all four servers (`kits.sk` and `warps.sk` need items and inventories, so they move to step 4).

**Done 2026-10-22.**
- **Types:** `date` (saved in `variables.csv` byte for byte), `location`, `game mode`, plus parsers so text can become a value (`"5" parsed as integer`, command arguments).
- **Converters:** `Registrar.converter(from, to, f)`. A placeholder whose type something converts to gets values converted at run time: an entity or block where a location is expected is its location.
- **`Update` effects:** effects that set one of their placeholders, like `replace "a" in {_x} with "b"`, compiled into a `set` through the same path as `set`.
- **Syntax:**
  - Text: case, length, join and split, parts of text, character counts, `matches`, `replace`, new lines, colours, `parsed as`.
  - Lists: `indices of` (sorted too) and `sort`.
  - Time: `now`, `difference between`, Unix timestamps.
  - Places: `location of`, spawn points, worlds by name, `distance between`, `teleport`, and the `location()` and `world()` functions.
  - Players: `player()`/`offlineplayer()`, played before, can fly, flight mode, game mode, food level, tab list name, heal, ignite/extinguish, titles, action bars, sounds, `all players`.
  - Events: `every <timespan>` (cancelled and rescheduled on reload), move, server list ping. Settable chat message and format, join and quit messages, and MOTD.
- **Parser fixes the corpus found:**
  - A loop value's name has to be `value`, `index`, `counter`, `iteration` or a type, so `loop-value in lower case` isn't a loop value called "value in lower case".
  - `f(player)` is a function call, not the literal `f` of type player: a type in brackets needs a space before it.
  - Arithmetic is back before the library's expressions, so `length of {_x} - 1` is `(length of {_x}) - 1`. The rule from step 2 that a placeholder can't start inside `arg-1` still keeps `arg-1 ? "x"` right.
  - The conditions of `if all:`/`if any:` are compiled against their own lines; this used to crash the compiler.
  - `loop-index` works for any loop: values that don't come from a list variable are indexed 1, 2, 3.
- **Tested by:** `neoskript-real-world-corpus` on all four servers: the 10 scripts without items load cleanly and pass. `warps` passes too, though its menu lines don't load yet; `kits` fails. Both are reported but don't fail the check until step 4. Plus regression tests in `LanguageTest`.

### Step 4: Items and inventories

Item types from Paper's registries, inventories and slots. *Done when* all 12 real-world scripts load on NeoSkript and pass their self-tests, on all four servers.

**Done 2026-10-22.**
- **Types:** `item type` (NeoSkript's `ItemType`: an item stack and whether an amount was written), `item`, `inventory`, `inventory holder`, `slot`, with converters between them (a slot is its item, a player is its inventory).
- **Item literals:** `[<amount> [of]] [a|an|all|every] <name> [of <enchantment> [<level>], ...]`, with levels in digits or Roman numerals. Names come from Paper's material registry, singular and plural. Skript's alias files (`any sword`, block states) aren't used yet.
- **API:**
  - `Registrar.literalParser(type, f)`: words a script writes become values of the type. The parser leaves them to the compiler, which resolves them once into constants.
  - `Registrar.changer(class, mode, f)`: how `add`/`remove`... change a value itself. `give X to player` adds to the player's inventory.
- **Syntax:** `named`, `with lore`, item amount, chest inventories, `slot N of`, slot index, `inventory of`, open and close inventories, and inventory click/open/close events with `event-slot`.
- **Tested by:**
  - `neoskript-real-world-corpus`: now 12 of 12, and strict.
  - `neoskript-items` on all four servers: literals, names and lore, parsing items from text, chest inventory slots.
  - Skript's own tests: 34 pass now (one more test can run).
- **Not yet:** items in `variables.csv` (step 4b). Items saved by Skript are still kept as raw bytes, and items set by scripts aren't saved yet.

### Step 4b: Item codecs

`itemtype` and `itemstack` in `variables.csv`, byte for byte with Skript (its item type format nests the item meta as YAML), so items survive restarts and M3's raw item values become real items.

**Done 2026-10-22.**
- **Codecs:** `itemtype`, `itemstack` and `gamemode`. Item meta and item stacks are YAML from Bukkit's own serializer, as in Skript.
- **Format:** the binary format gained null fields and object arrays (an item type is a list of item datas).
- **Safety net:** a saved value is only decoded if it encodes back to the same bytes. So an item NeoSkript doesn't model, like Skript's `any sword` (several items in one), stays as Skript saved it.
- **Conversion on save:** a value without a codec is saved as what it converts to, if that has one: a slot as its item, as in Skript.
- **Tested by:** `neoskript-variables`. NeoSkript decodes the plain, named, enchanted, lore, block and counted items and the item stack the reference Skript saved, and rewrites the file byte for byte. Skript reads the items NeoSkript saves and rewrites them identically. Only `vector` is still kept raw, until vectors come with the world (step 5).

### Step 5: The world

Entities and spawning, blocks, locations and vectors, worlds, potions.

**5a done 2026-10-22.**
- **Progress measure:** the new `neoskript-skript-suite` check (baseline group) runs every one of Skript's 855 tests on NeoSkript and reports how many pass, fail and can't run yet. The reference Skript passes 853 on the same server. M4 ends when NeoSkript does too. Before 5a: not measured. After: 95 pass, 76 fail, 684 can't run yet.
- **Sections in the API:**
  - `Registrar.section(id, valueType, factory, patterns)`: a section's code runs once for each value it returns, and inside it those values are the event values of its type (`entity` in `spawn a pig:`).
  - The code is inlined like a loop's, so locals and waits work.
  - Each section is also an effect when written without code.
- **Entity types:** Skript-style kinds. Every Minecraft entity type, Skript's names for some (`firework`, `lightning`, `dropped item`), and categories (`projectile`, `monster`, `animal`, `mob`...) defined by the entity class they match. Entity types are strict, so `pig` in `spawn pig` is the type, not an event value.
- **Literal checks:** a type's literal parser now also checks words while parsing, so `unknown` isn't read as `all <entity type>`.
- **Stricter types:** item types are strict too, so `ender pearl` (also an entity's name) stays an item where an item is expected.
- **Conversions:** converters tell the parser what converts to what, one step at a time. An entity converts to a location and a location to a block, but an entity isn't accepted as a block.
- **Syntax:**
  - Spawning (section and effect), the last spawned entity, `all <entity types> [in worlds]`, health, `kill`, and deleting entities.
  - Blocks at, above and below locations, settable to an item's block.
  - Vectors, with a byte-exact codec: `vector()`, x/y/z of locations, vectors and blocks, offsets, lengths.
  - `whether <condition>`, infinity and NaN, random numbers, `running [below] minecraft`.
- **Skript's test environment:** `test-location`, `test-block`, `test-world`, and a test event whose `event-location`, `event-block` and `event-world` are those.
- **Robustness:**
  - A bug that throws while compiling one structure now only leaves that structure out, with an error asking for a report. It used to stop NeoSkript loading.
  - Integer literals too big for a whole number become decimals, as in Skript.
- **Next, in 5b:** potions, world borders, entity data, persistent data, block data, chunks.

**5b done 2026-10-23.**
- **World borders:** a world's or player's border, its size (diameter or radius), centre, damage buffer and amount, warning distance and time, all settable, and expanding or shrinking it, optionally over time.
- **Potions:**
  - Potion effect types, by name or Skript's old names (`confusion`, `fast digging`...).
  - Potion effects (`potion effect of speed of tier 2 for 10 seconds`, infinite ones, ones based on another), applying them.
  - An entity's effects, amplifier and duration, `has potion effect`, `is instant`.
  - A type alone converts to its effect (`apply speed to player`).
- **Persistent data:**
  - Tags on items, entities, block entities, worlds and chunks: reading, setting, deleting, `has data tag`, all keys.
  - Values are stored as text, whole numbers, decimals or booleans.
- **Copies in variables (`Registrar.cloner`):** values that can change in place, like items, are copied when stored in a variable, as in Skript. Changing a variable's item doesn't change the item it came from, and items in variables can be changed in place.
- **Also:** chunks (`chunk at`, loaded chunks), midpoints of locations and vectors, `air` as an item.
- **Fixes:**
  - `teleport` to a location without a world uses the entity's world.
  - The block at a worldless location is nothing, not a crash.
  - A random integer with no whole number in its range is nothing, not a crash.
- **Skript's tests:** 127 pass, up from 95 after 5a.
- **Left for step 6:** Skript's entity data (`baby axolotl`, `lucy axolotl`), block data, `catch runtime errors`, changeable conditions (`set whether X can age`), typed and list data tags.

### Step 6: The long tail

Whatever Skript's tests still need, most-used first, then the differential runner for the 95% check.

Done in batches, each a PR: run `neoskript-skript-suite`, rank what stops the most tests, fix it, repeat. Tests NeoSkript deliberately doesn't match get listed here with the reason.

#### Batch 1 (2026-10-23)

**127 → 159 tests pass.**
- **Entity variants, as Skript names them:**
  - Age (`baby`, `adult`, `kid`, and babies' names like `kitten`, `calf`, `pup`).
  - Variants from Paper's registries: axolotls, cats, foxes, frogs, wolves, parrots, llamas, rabbits, mooshrooms, salmon, villager and zombie villager professions, tropical fish patterns and colours, sheep and shulker colours.
  - States: angry/happy bees with or without nectar, powered creepers, angry/peaceful wolves with collar colours, screaming goats, shivering striders, saddled pigs and striders, panda genes (`lazy-aggressive panda`).
  - `dropped <item>`, `falling <block>`, `enderman holding <block>`, displays `of <block or item>`, minecart kinds.
  - Spawning a variant makes it, and `is` follows Skript: `lucy axolotl is axolotl`, `{_entity} is {_data}`.
- **API:**
  - `Registrar.comparison`: what `is` means between two types.
  - `RuntimeErrors`: non-fatal errors syntax reports, collected by `catch runtime errors:` for `last caught runtime errors`, otherwise logged. `execute command` reports failed script commands as Skript does.
- **Parser:**
  - A kind of entity is the event's entity only after `the` or `event-` (`the zombie`). Alone, `baby chicken` is an entity type.
  - `all <entity types>` needs `all` or `the`.
  - A word can stand for a type that converts to the expected one (`apply speed`).
  - Function arguments try words as the parameter's type first.
- **Syntax:** max health, AI, leashed, charged, alive/dead, invulnerable, `drop`, random UUIDs and vectors, hashing.
- **Not yet:** the 1.21.5+ animal variants (cow, chicken and pig temperatures) need a newer Paper API than NeoSkript's 1.21.4 floor; baby frogs (tadpoles).

#### Batch 2 (2026-10-23)

**159 → 166 tests pass, and 10 more run.**
- **Language fixes from Skript's tests:**
  - Values of other types never reach a text, number, boolean, timespan or date placeholder, as in Skript: `length of 5` is nothing.
  - A missing number in arithmetic is 0: `{_x} + 1` is 1 when `{_x}` isn't set.
  - `doesn't match` is the opposite of every text matching every pattern.
  - `round` rounds halves away from zero and takes negative places.
  - `indices of` includes indices that only have lists under them.
  - `variables:` sets global variables that aren't set yet.
- **Syntax:**
  - Block data (`campfire[lit=false]`), settable on blocks, falling blocks and block displays.
  - `type of`, Minecraft/Bukkit/Skript versions, `is a baby`, `leash`.
  - Text converts to a world (`spawn of "world"`).
  - Entity plurals (`all entities`).

#### Batch 3 (2026-10-23)

**166 → 177 tests pass.**
- **Language:**
  - Decimals within 1e-10 of each other are equal, as in Skript.
  - Locations compare with their yaw and pitch normalised.
  - `round` with negative places rounds to a whole number first, as Skript does (1044.5 to 1045 to 1050).
  - Timespans can be written short (`1d`, `2h`, `30m`).
- **Syntax:**
  - Names of entities and items can be set.
  - Item amounts can be set, added to and removed from, on items, item stacks and slots.
  - Blocks with inventories (chests) are their inventory.
  - Panda states, eating, valid, persistent, shivering, conditional command blocks, spawner types.
  - `is between`, `is a block`, the last dropped item, `primed tnt`.

#### Batch 4 (2026-10-23)

**177 → 192 tests pass, and 29 more run.** Picked by parsing each blocked test's failing line with Skript's registry and counting the Skript elements NeoSkript lacks.
- **Items:**
  - Slots can be equipment slots: `the helmet of`, `the armor of`.
  - `X of <item>`, settable lore and lore lines, durability and max durability, max stack size.
  - `has <items>` for inventories, `is of type`.
- **Entities:** detonate, make (in)vulnerable, make adult or baby, scream, eat, leash holder, custom name visibility, explosive yield, love time, the nearest entity of a type.
- **Logic:** `starts with`/`ends with`, `is divisible by`, `except`, `random X out of`, angles in degrees and radians.
- **World:** `all worlds`, `vector from x, y, z`, `is loaded` for chunks and worlds.
- **Time:** `ago`/`later`, before or after a date.
- **Potions:** `speed 5` as a potion effect.

#### Batch 5 (2026-10-23)

**192 → 201 tests pass.**
- **Inline functions:** compiled as loops inside the expression.
  - `%objects% where [condition]`, `%objects% transformed using [expression]`, `%objects% reduced with [expression]`, with `input`, `<type> input`, `input index` and `reduced value`.
  - `transform %list% using ...`, `sort %list% by ...`, and the `filter %list% to match [any|all]:` section change the list in place.
  - `sorted %objects%`, `keyed %objects%`.

#### Batch 6 (2026-10-23)

**201 → 208 tests pass.**
- **Script objects:**
  - `the current script`, `script named ...`, `scripts in folder ...`, `all/loaded/disabled scripts`.
  - Script names, and `is loaded` and `is valid` for scripts.
  - In test mode, scripts are named by the paths Skript's test runner gives them (`neoskript.testing.scriptsRoot`), so Skript's script tests see what they expect.
- **Parser:** arithmetic is ruled out when an operand has a type it can't work on, as in Skript, so `the script named {@path} + "x.sk"` is the script named the joined text.
- **Literals:** scientific notation (`1.23e+4`).

#### Batch 7 (2026-10-23)

**208 → 241 tests pass.**
- **Settable conditions (`SettableCondition` in the API):** `set whether {_pig} can breed to true` makes a condition true or false, as in Skript.
- **Conditions, all settable where Minecraft allows:**
  - can age, can breed, is adult, is a baby, is screaming, is incendiary.
  - Text displays' drop shadow and see-through.
  - can duplicate, can pick up items, has been stared at.
  - is silent, sheared, saddled, invisible/visible, left/right handed, tamed, gliding, swimming, riptiding, in love, playing dead, responsive, frozen, whitelisted.
  - Items: unbreakable, fire resistant, enchantment glint overridden.
  - Setters added to the existing charged, invulnerable, panda, eating, persistent, shivering, conditional and custom name visibility conditions.

#### Batch 8 (2026-10-23)

**241 → 250 tests pass.**
- **Directions:** `5 below`, `north of`, `above`..., chainable.
  - `the block <direction> X` and `the location <direction> X`.
  - `blocks <direction> X`: the starting block plus as many as the direction says, or 100 without a number, as in Skript.
  - `blocks between X and Y`.
- **Queues:** `a queue of ...`. `first/last element of` a queue takes it out, `start/end of` only looks. Adding to the start or end, removing, clearing, `is empty`, and a queue's size.
- **Elements:** `first/last N elements`, `N random elements`, `the Nth [to last] element`, `elements from N to M`.
- **Lists:** `copy {_a::*} into {_b::*}` with nested lists and indices, `recursive {_a::*}`.
- **`is empty`** for text, queues, inventories and slots.

#### Batch 9 (2026-10-23)

**250 → 256 tests pass.**
- **Entity effects:** ride/dismount/eject, poison/cure, left/right handed, (in)visible, allow/forbid picking up items.
- **Entity properties, settable:** burning time, freeze time, portal cooldown, invulnerability ticks and time, arrows stuck, villager level and experience, interaction width and height, attributes (`the movement speed attribute of`).
- **Other syntax:** vehicles, is poisoned, tameable, lootable.
- **Fixes:**
  - Words of named types like equipment slots now compile as literals (`boots of X`).
  - Age words work on categories (`a baby entity`).
  - Attribute names are looked up when a script uses them, so nothing touches the server's registries while syntax registers.

#### Batch 10 (2026-10-23)

**256 → 282 tests pass.**
- **And/or lists:** conditions see whether a list was written with `or`, and check it as Skript does: `"hey" doesn't start with "hey" and "e"` is true. This covers contains (texts, inventories, other values), starts/ends with, matches and the left side of comparisons.
- **Arithmetic for other types:** timespans (saturating, as in Skript), dates plus or minus timespans, and vectors. Addons can register their own (`Registrar.arithmetic`).
- **Keyed values:** `keyed {_list::*}` keeps indices when setting a list, passing function arguments and returning lists. `first 2 elements of` and `sorted` keep them too, and so does looping a sorted list. Loops over list variables now line indices up with values.
- **Changing in place:** `add` on a variable holding a queue or inventory changes that value. The x/y/z of vectors and locations can be set, added to and removed from, and so can vector length. Vectors and locations are copied into variables and functions.
- **Fixes:**
  - Timespans parse word by word as Skript does (`30m and 20s`, `1mo 2w`, `1:30`).
  - `add ... to {_a::*} and {_b::*}` changes both lists.
  - Sorted indices skip entries without a value.
  - `elements from 2 to 1`, `10 random elements` (repeats allowed unless `distinct`), and `amount of` an item.
  - `parsed as` is tried after other expressions, and `size of` before elements, matching Skript's order.
  - A bare `chest` is the item, not the equipment slot.

#### Batch 11 (2026-10-23)

**282 → 343 tests pass.**
- **Default functions:** isNaN, root, factorial, permutations, combinations (rounding exactly as Skript's do), mean, median, clamp (keeps indices when keyed), toBase/fromBase, formatNumber, concat, case_equals, and `vector(n)`.
- **Properties:** numeric and timespan properties can be added to, removed from and deleted. Yaw and pitch of entities and locations are settable. Explosive yield covers creepers and ghasts.
- **Settable conditions:** creeper ignition, ghast charging, allay dancing, camel dashing, jumping, despawning when far away, item despawning, goat horns, PvP, tooltips, and server whitelist mode and enforcement.
- **Vectors:** between locations, of a location, location from a vector, normalized, squared length, dot and cross products, angle between, from yaw and pitch, rotation around another vector.
- **Other syntax:** `recursive size of`, `random number out of X` (keeps only that type), inventories of any type (`a hopper inventory named "x"`), `items in`, `type of` an inventory, `-infinity value`.
- **Fixes:**
  - NaN never equals anything, as in Skript.
  - Invisible/visible and left/right-handed conditions had their tags reversed.
  - `3 of stone` and `number within X` are tried late, matching Skript's order, so `y coordinate of spawn of {_w}` and `items in X` parse as Skript parses them.
  - Inventory kinds and attribute names are only looked up when a script runs, so registration never touches the server.
  - `X is stone` holds for any stone, named or not; `X is stone named "a"` only for that name.

#### Batch 12 (2026-10-23)

**343 → 529 tests pass.**
- **Language:**
  - `for {_key}, {_value} in X` loops.
  - Function references: `function "x" [from script]`, `run`, `result of`, `all functions from`.
  - `load`/`reload`/`unload`/`disable` scripts while running.
  - `assert … to fail` now parses as Skript does: a condition doesn't lean on unresolved words when anything else fits.
- **Types:**
  - Colours: the sixteen named ones, `rgb()`, red/green/blue/alpha values and hex codes.
  - Time of day: `12:30` and `5pm`, and the settable time of worlds.
  - Enchantments and enchantments with levels: `enchant`, `disenchant`, enchantment levels, `is enchanted with … or greater`.
  - `forever`, which stays forever through arithmetic.
  - Types as values (`X is a color`) and `all colours`.
- **Persistent data:** list tags, typed reads (`string data tag`, `strings data tag`, `persistent location data tag`) with Skript's errors, any storable value via the storage codecs, and add/remove for both lists and single values.
- **Other syntax:**
  - Text: string cases, `repeated`, codepoints, random characters, `percent of`, `toggle`/`inverse`.
  - Lists: reversed and shuffled lists (keeping indices), `indices of the value`, `dequeued`.
  - Rounding, `pi`, unix dates, formatted dates, time since/until, past/future dates.
  - Entities: line of sight, highest/lowest solid block, velocity, `is within`, PvP, taming, tooltips, panda/strider/axolotl/sleep states, silence, breeding, ageing, charging, whitelist enforcement.
  - Entity names: bamboo boats, `regular minecart`, `zombie farmer`, `stone block display`, `tamed ocelot`, `wild`/`sheared` variants.
- **Displays:** size, shadow, view range, interpolation and teleport durations, brightness, glow colour, billboard, item transform, and text displays' text, alignment, line width, opacity, shadow and see-through, each kept in Minecraft's limits and resettable.
- **World borders:** every property can be added to, taken from and reset, kept in Minecraft's limits, with Skript's errors for NaN and infinity.
- **More syntax:**
  - Entities: damaging and healing (items by durability too), making one attack or damage another, last damage, villager types and professions, zombifying, remaining air, view and simulation distance, world environments.
  - Items: enchantment glint, fire resistance and tooltips (`diamond with fire resistance`), custom max durability and stack size, item flags, custom model data, item model, rarity, `unbreakable X`, `dirt block` names, replacing items in inventories, dropped items' lifetime.
  - Tags: `item tag "slabs"`, Paper's (`paper:helmets`) and scripts' own (`register an item tag named …`), `is tagged as`, `tag contents of`, `tags of`, `all tags`.
  - Built-in functions can have defaults for arguments left out (`location(1, 2, 3)` is in the main world; given as nothing, it has none).
  - Equipment: `equip X with Y` and `unequip`, horse armour and saddles, `is wearing`, `has a saddle`.
  - Text: tags as well as colour codes (`<red>`, `<bold>`, `<#ff0000>`, `<unicode:00A7>`), parsed only when there's a tag; `uncolored` strips them repeatedly; lore reads back with its tags; `string colours of X` and their codes.
  - Books: title, author, pages and single pages.
  - Expression sections: `set {_border} to a worldborder:` runs its code for the new value (`event-worldborder`, or left out).
  - Blocks compare with items and block data (`block at X is bookshelf`).
  - Other: the whitelist, `characters between`, `ticks of X`, `max integer value`, named times (`night`), `spawn 2 cows`.
- **Fixes:**
  - List indices sort exactly as Skript's (numbers by value, with signs, decimals and leading zeros) and are kept in lower case.
  - A block's location is its centre.
  - Test location and test block are where Skript puts them.
  - Updates like `replace` and `enchant` keep a list's indices.
  - Plain items remove any stack of that item, named or not.
  - Expression order follows Skript where they compete: block before offset before spawn.
  - Property conditions are false for things they don't apply to, so their negation holds, as in Skript.
  - Words in a comparison or a `set` are read as the type the other side takes (`bill-boarding of X is fixed`).
  - `damage value` (damage taken) and `durability` (what's left) are told apart.

#### Batch 13 (2026-10-23)

**529 → 692 tests pass.**
- **Potion module:** potion effects that can be changed (tier, length, ambient, infinite, particles, icon), entities' active and hidden effects (Minecraft's weaker effects kept under a stronger one), potions' effects, effect type categories and colours. Changing an entity's effect re-applies it with its hidden ones; hidden effects can't be changed, as Skript says when parsing.
- **Quaternions:** `quaternion()`, `axisAngle()`, their w/x/y/z parts, rotation angle and axis, rotating around global and local axes and by x/y/z angles, and displays' rotations, scale and translation, all with Skript's float results.
- **Language:**
  - Elements can refuse a match with an error while parsing (`SyntaxError`), as Skript's do, and `parse:` sections see those errors.
  - Elements see the types of their arguments when they're known.
  - Words several types have (`normal`, `cod`) are read as the type the other side wants, else the type Skript tries first; entity types come before items, unless they have an amount (`32 ender pearls` are items).
  - Of several parses leaning on unknown words, the one leaning on the least wins, and words a type's parser accepted count as known.
  - `any of X` is an or-list; empty values make negated conditions hold, as in Skript.
- **Entities:** amounts (`spawn 5 villagers`), entities in chunks, within a radius or between corners, `zombies` on its own, farm animal variants (`warm cow`), professions as kinds, the plurals Skript uses (wolves, calves, endermen), fireballs as a category, panda genes, riding, passengers, owners, tools, entity size, allay duplication, enderman blocks, warden anger, metadata, persistence, creeper ignition, display entity kinds.
- **Blocks and items:** furnaces, brewing stands and beehives, suspicious sand, command blocks, lids, skull owners, spawner types (trial spawners too), first empty slots, adding to and taking from slots, slots compared as their items, inventory holders, lore of slots, `potion of X` items, `type of` a block.
- **Other syntax:** experience (`5 xp`), relative directions and pushing, block lines traced as Skript does, vectors (cylindrical, spherical, normalized), angles, `world of X`, chunks by coordinates, UUIDs of and from things, time played, display names, test players, alphabetical sorting.
- **Errors as Skript gives them:** `parse:` sections and blocks compile their code apart to catch what compiling finds (cancelling events that can't be cancelled, functions that return nothing used as values, literals of types that can't be written, invalid regular expressions written out, reversing one value); `assert … to fail` holds when its condition can't be set up; function arguments: mixing named and unnamed ones, lists of lists.
- **More:** `connect` and `swim toggle` events, damage causes, `is burning`, `item of` entities, transform keeping indices, script names by `using script reflection`, reduce reporting skipped values, or-lists setting one of their variables, literals with `and` in them read whole, lists in lists (`1, (2, 3)`), boats compared with their items, tropical fish colours (`red-blue kob`), display entities of enchanted items, item rarity defaults, display brightness from infinity.
- **Fixes:** location equality allows for float error; Paper's rewriting of `Cow` for older plugins is handled; tags written mid-alternative are fixed; numbers too big for a whole number are kept in range everywhere instead of wrapping round; the harness stops at once when the plugin fails to start.

#### Batch 14 (2026-10-24)

**692 → 734 tests pass.**
- **Language:**
  - Functions can wait: the caller carries on when the function starts waiting, and the rest runs later where the caller runs (its region, on Folia), as in Skript.
  - A section that always stops (`stop`, `return`) doesn't carry a delay out of it, as Skript's execution intents have it.
  - Code after `stop`, `return`, `exit` or `continue` gets Skript's "Unreachable code" warning, and `suppress … warnings` (alone or as a section) turns warnings off as in Skript.
  - `using` names Skript's experiments by any of their names, and `the current script is using "for loops"` checks them. `example:` code is parsed but never run.
  - Lists prefer items that don't lean on unknown words (`1 or 0 or -1` is three numbers), and an `and` list only fits the plural types of `%number/booleans%`.
  - A kind of entity alone is the event's entity where an entity type can't go (`yaw of pig` in `spawn a pig:`, `delete pig`), as Skript's entity expression does.
  - `X if C otherwise Y` and `X otherwise Y` have their values' type when they share one, so `length of (5 otherwise 10)` doesn't parse.
  - `parse if` code that can't compile here is left out, as Skript only parses it when the condition holds.
  - Loops over a queue take from it as they go, so values added while looping are looped too.
  - Key-aware expressions can keep their indices without `keyed` (`KeyAware.keysRecommended`).
- **Commands:** `execute command` fires the command events first, as Skript does, `on command "x y"` matches as Skript's does, and script commands belong to "Skript".
- **Syntax:**
  - Explosions (`on explode`, `the exploded blocks`, with Skript's error outside it), incendiary explosions, shooting (`shoot an arrow from X`, `make X shoot a pig at speed 2`, its section, `the shooter`), teleport flags and directions.
  - Biomes, by Skript's names or keys, and the biome at a location. Trees (`grow oak tree above X`), blocks in a radius, `update X as Y without physics`.
  - `X where "a" is replaced with "b"`, the (real) max players, (max) domestication levels, `is between` with lists and Skript's wrap-around for times, milliseconds in timespans.
- **Fixes:** baby frogs (ages as Skript's entity data has them), text display backgrounds reset to Minecraft's default, chests placed from items keep their contents, the inventory of a container item, `open X inventory` with Skript's error for kinds that can't be made, yaw and pitch ignore infinity and NaN, random numbers with a written amount of nothing give nothing.
- **Skript's test mode:** tests start 5 seconds after loading, with the test chunks ticking, as in Skript. Its test syntax: `test expr list`, `test string literal`, `test loop peeking`, `method exists`, `test key values`, `experimental only`.
- **Still to do, largest first:** most of Skript's events (only about a dozen exist so far), aliases (`any boots`), local variable type hints, text components, equippable components, particles, loot tables, boss bars, script config, custom operators, banners.

#### Batch 15 (2026-10-25)

**734 → 776 tests pass.**
- **Aliases:** Skript's default alias files are bundled and loaded (potions, tipped arrows, firework flights, `any boots` and the rest), with their version conditions and variations. Scripts' own `aliases:` sections work, with Skript's errors for unknown variations. Item types stand for several items (`any sword`, a random one when given), `plain` items, and Minecraft ids with block states (`minecraft:oak_stairs[facing=north]`).
- **Type hints:** `using local variable type hints` checks what local variables hold as Skript does, from `set`, `add`, `copy` and `transform`, through loops and sections, from function parameters and command arguments, with `suppress type hints` to turn it off.
- **Config nodes:** `the skript config`, scripts as nodes, nodes and subnodes by path, and the values of nodes.
- **Items:** holding items, room for items, damaged items, stored enchantments, item cooldowns, fuel, stackable items, exact items, `items of type`, banner patterns, banner pattern items and the patterns of banners.
- **Entities and blocks:** targets, ages, sounds, signs, entity snapshots, storage blocks, interactions (and whether they respond), with many more properties of entities and blocks.
- **Players, server and world:** the server's operators, online player counts (real and shown), plugins, world properties, and Skript's location expression with directions (`spawn a pig 2 meters above X`).
- **Still to do, largest first:** most of Skript's events, text components, equippable components, particles, loot tables, boss bars, custom operators, expression sections.

#### Batch 16 (2026-10-25)

**776 → 803 tests pass.**
- **Events:** most of Skript's events, with their filters, event values (past and future ones too, and plural ones like `event-blocks`) and Skript's errors; NeoSkript fires the ones Bukkit has no event for (`at 6:00`, `on server start`, `on script unload`, experience spawns). Event expressions: damage, attacker, victim, drops, dropped experience, messages, reasons, `clicked`, moved and absorbed blocks, and more.
- **Plurality:** several values where only one goes is an error, as in Skript ("'{_l::*}' can only be a single location, not more."), going by Skript's own elements.
- **Expression sections that run later** (Skript's test runnables), with Skript's errors when two things claim a line's section, and errors for a left-out value the event doesn't have ("There's no potion effect in an on test case event").
- **Loot tables and boss bars**, as Skript's modules have them.
- **Entity data:** panda genes, thrown potions, zombie nautiluses; literal specifications (`black (cat type)`), `all biomes`, Skript's enum types by their names, `using local axes`, blocks that aren't items (`oak wall sign`, `void air`), and tags before a choice (`:(a|b)`) as Skript sets them.

#### Batch 17 (2026-10-25)

**803 → 841 tests pass.**
- **Language:** returnable sections, custom default values (a left-out number is 1, as in Skript), arithmetic by operator priority with Skript's custom test operators, text parsed with a pattern of types (`"1, 2" parsed as "%integers%"`, grouped into sublists when set into a list), function arguments that take a section (`f(a worldborder):`), lists of what a condition checks checked item by item as Skript's expression lists, several values refused for a single function parameter, and Skript's errors for words nothing parses in a list and for changes of the wrong type ("... can't be added to ... because the former is not a time span").
- **Items:** equippable components, custom model data, enchantment glint overrides, skull textures, armour, body armour and saddle slots by Skript's names, `all items` and `all blocks`, removing all of an item from inventories, and item comparisons as Skript makes them.
- **Literals that say their type** (`unknown (heal reason)`) are read with that type's parser.

#### Batch 18 (2026-10-25)

**841 → 855 tests pass (of 862).**
- **Text components** as Skript 2.17's module has them (its tag parser, safe and unsafe tags, `raw`, `resolved for`), names and lore as components, and **damage sources** (`a custom damage source:` and its parts, with Skript's errors).
- **Directions** as Skript's `Direction` and `ExprDirection`: relative and absolute ones, diagonals, `in the direction of`, chains, storable in variables; block lines and cuboids as Skript's `ExprBlocks`.
- **Language:** Skript's variable name checks and single-colon warning, its pretty quotes error, its warning for words with several types (`'unknown' has multiple types (...)`), function call errors as Skript gives them (unreadable arguments as `?`, the function probably meant), a time unit alone (`wait tick`), hyphenated words kept whole (`loop-value - 1`), block displays of block data, and warnings logged as warnings.

#### Batch 19 (2026-10-25)

**856 → 862 tests pass (of 862): the whole Skript 2.17 test suite passes.**
- **Particles and game effects** as Skript's particle module has them: particles by Skript's names with counts, directional, converging and scalable particles, particles with data, offset, distribution, speed and velocity, `draw`/`play`/`show` for particles and game effects, entity effects, and play sound with Skript's patterns (seed, category, both orders).
- **Language:** an effect section that refuses a section written under it gives Skript's error; within one pattern a split with no unresolved words wins.
- Known differences: particles and game effects can't be saved in variable files yet, and a particle that needs data gives the generic error rather than Skript's "requires data".

#### Batch 20 (2026-10-25)

**862 of 862 tests still pass; Skript elements no test covers.**
- **Event syntax:** brewing (fuel, results, time), anvils (repair cost, text), enchanting (costs, bonus, hints, offers, applied enchantments, level limits), furnaces, fishing (lure, approach angle, bite and wait times, hook, hooked entity), fireworks (effects, launching, last launched firework), the thrown egg, portal blocks, the hover list, the sign side changed, the command executor, experience cooldown, interrupting item use, and inventory rows and slots. The server list ping event is Paper's, as Skript's is.
- **Misc elements:** number limits as Skript's `Lit*Value` (the min double and float are the smallest positive ones), `loop-iteration` for while loops and Skript's errors for it, `parse error`, `debug info`, `script is loaded`, `all scripts` as paths, tag keys, `created effect`, `dye`, `fail execution`, value-within errors, and items with glint, fire resistance, flags or models as Skript 2.17's patterns.
- **Type properties** as Skript 2.17 (on by default): amount, size and number of one value or several, contains of values or their contents, empty items, display names of entities, slots and inventories, and one scale property for displays and particles.
- Known differences: type-property parse errors for types without the property aren't reported, and the script command `suggestions:` entry isn't there yet.

#### Batch 21 (2026-10-25)

**862 of 862 tests still pass; what Skript has beyond its tests.**
- **Commands:** the `suggestions:` entry with Skript's suggestions, filtering and input syntax, and tab completion that follows a command's arguments.
- **Functions:** Skript's remaining defaults: `sin`, `cos`, `tan`, `asin`, `acos`, `atan`, `atan2`, `ln`, `log`, `exp`, `sum`, `product`, `calcExperience`, `date`, `uuid` and `entity`.
- **Variables:** players are their UUIDs in variable names, as Skript's default config has it (`variables.player-uuids-in-names`; moving over keeps Skript's setting, so its saved variables are found), and particles are saved as Skript saves them (a bug in how nested names were numbered is fixed too).
- **Elements:** the pick item event and picked item (1.21.5+), `exit all loops` and Skript's exit errors, Skript's 'requires data' error, and Skript's patterns for script files, see-through displays, spawnable types, light overrides and unrolled queues.
- Found with a check that writes a sample line for every pattern in Skript's syntax list and parses it with NeoSkript's library.

#### Batch 22 (2026-10-25)

**862 of 862 tests still pass; Skript's plugin hooks and full patterns.**
- **Hooks** as Skript's: Vault money (`$5`, `5 dollars`), balances, prefixes and suffixes, and groups; WorldGuard and GriefPrevention regions (`region` type, can build, is member, contains, blocks and members of regions, regions at, region enter and exit). Only when the plugin is installed; scripts then load on the first tick, after the plugins they hook into, as in Skript.
- **Events:** attack attempts, player input (toggle, press, release), and `every X in world "..."`.
- **Patterns:** Skript's full patterns for silence, breedable, aging, playing dead, shivering, custom names, creepers, force attack, run, enchant at level, warden anger, carried blocks, item despawning and reeling in hooked entities.
- Known differences: money in a variable is saved as a number, and quoted region names are looked up when the script runs. `was` and `will be` comparisons aren't there yet.

#### Batch 23 (2026-10-25)

**862 of 862 tests still pass; the parser knows the event.**
- **Event values while parsing:** a reading that needs a value the event doesn't have is turned down and another tried, as Skript's event value expressions fail to initialise (`{_inv}'s amount of slots` in `on load` is the inventory's slots, not an amount of the event's slots). When nothing else parses, Skript's "There's no X in ..." is still the error.
- **Comparisons before and after the event:** `was`, `were`, `will be` and `is turning into`, with Skript's time states on placeholders (literals can't have one; none after a delay).
- **Command arguments:** Skript's errors for `the argument` in a command with several arguments, `the arguments` in script commands, and typed arguments (including its quirks: of two arguments of a type, the second; messages naming CLASSINFO).

#### Batch 24 (2026-10-25)

**862 of 862 tests still pass; time states and auto reload.**
- **Time states** as Skript's `setTime`: expressions declare the events they have a past or future state in (`Registrar.timeStates`), so `player's gamemode will be creative`, `past tool`, `former level of player` and `player's level was 3` read the event's old or new value: tool, hotbar slot, game mode, food level, level, weather, world, spawn, vehicle, passengers, spectator target, target, sign text and input keys. Food level is in bars (0 to 10), as in Skript.
- **`auto reload`:** a script with it reloads when its file changes, with Skript's messages to its recipients or those with the permission; a version with errors keeps the old one running (`neoskript-auto-reload`).
- **Errors:** a comparison of words nothing reads isn't understood, as in Skript.
- Known differences: `auto reload` doesn't need async loading; event values read after a wait still come from the event.

#### Batch 25 (2026-10-25)

**862 of 862 tests still pass; Skript's /skript command.**
- **`/skript`** as Skript's: `reload`, `enable`, `disable`, `list`, `info`, `restore` and `help`, with Skript's messages and `skript.*` permissions. Disabled scripts and folders start with `-`, and `.` folders are skipped. Reloading or unloading a script runs its `on unload` events.
- Known differences: a name ending in `/` is a folder. Skript 2.17 never finds folders, because of a bug.

#### Batch 26 (2026-10-25)

**862 of 862 tests pass; checked line by line against a real Skript server.**
- **Differential probes:** scripts are loaded on a Skript 2.17.0 server and a NeoSkript one side by side, and each line's messages compared. The fixes below came from them.
- **Errors:** every bad line in a trigger is reported, not only the first. Each message matches Skript's: words nothing understands (in a change, a text's `%...%`, a condition, a loop, or the line), `Can't compare`, `else` with no `if`, `if any` without `then` or with one condition, unclosed quotes and brackets, structures, indentation (one unit per structure, set by its first indented line), command entries, function parameters and return types, functions that don't exist, `return` where nothing returns, command argument types and options. The empty section warning is ported.
- **Event values** as Skript resolves them: subclasses and superclasses checked when they run, converters and chains of them, and Skript's errors. Left-out placeholders use the event's value for the same types as Skript (`fuel slot` in a smelt event). The command, script load and vehicle events gained Skript's values.
- **Warnings:** Skript's parse-time warnings from elements, with its suppression: deprecations (regions, MD5, `permission message` and more), lists missing `and`/`or`, variable names, values that can't be saved, constant conditions, a text inside `%...%`, loops and delays, experiments, default variables and `on server start`. Region names that don't exist are errors.
- **Runtime errors** print as Skript prints them, with its per-frame limits (`config.yml` → `runtime-errors`) and `skript.see_runtime_errors` (`neoskript-runtime-errors`).
- **Effect commands:** `!effect` from the console and chat, with Skript's options, permission, messages, `me` and a cancellable `EffectCommandEvent`. Moving over from Skript copies the options.
- **Hooks** tested against the real WorldGuard, Vault, EssentialsX and LuckPerms (`neoskript-hooks`, baseline). Money with money is money; `add 1 to {_money}` leaves it, as in Skript.
- Known differences: Skript 2.17's warning frames never reset because of a bug, and NeoSkript resets them as intended. The code under a section whose header fails is still checked, so its errors show too. Event names in errors are as the script writes them.
