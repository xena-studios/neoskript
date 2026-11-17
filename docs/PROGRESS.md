# Progress

What each milestone in the [plan](PLAN.md#6-roadmap) delivered, in order. Add each new step or batch at the end of its milestone.

## At a glance

| Milestone | Status |
|---|---|
| [M0: Foundations](#m0-foundations) | Done 2026-10-21 |
| [M1: Frontend](#m1-frontend) | Done 2026-10-22 |
| [M2: Compiler and runtime core](#m2-compiler-and-runtime-core) | Done 2026-10-22 |
| [M3: Variables and storage](#m3-variables-and-storage) | Done 2026-10-22 |
| [M4: Standard library](#m4-standard-library) | Done 2026-11-11 |
| [M5: Folia and migration](#m5-folia-and-migration) | Done 2026-11-11 |
| [M6: Beta to 1.0](#m6-beta-to-10) | In progress |

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
| 27 | 2026-11-11 | 862 | 98.8% of the differential identical to Skript; M4 done |
| 28 | 2026-11-11 | 862 | 99.0% of the differential identical; comparisons and parse errors in Skript's order |
| 29 | 2026-11-11 | 862 | 99.7% of the differential identical; Skript's syntax order, types and init checks |
| 30 | 2026-11-11 | 862 | 100% of the differential identical; Skript's unparsed literals and section fallback |

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
  - A script with errors kept its last version, and its functions their old code (D12, since changed to load it without what has them, as Skript does: M6 step 22). Function handles last across reloads.
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
- **Databases:** came later, in M6 step 21 (D20).

## M4: Standard library

**Done 2026-11-11.** Skript 2.17's whole test suite passes on NeoSkript (862 of 862), and 98.8% of the differential's 1,613 units behave exactly as on a real Skript 2.17.0 server, past the 95% the exit needs. The rest are listed, with why, in `test-harness/corpus/differential/known-differences.txt`.

One PR per step. The order comes from `syntaxUsage` in the `neoskript-parse-corpus` report, which counts how often the corpus uses each Skript element. At the start of M4, the real-world corpus used 152 distinct elements and functions, and NeoSkript had 15 of them. Skript's tests use 785.

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

#### Batch 27 (2026-11-11)

**862 of 862 tests pass; 98.8% of the differential identical (1,594 of 1,613 units). M4 is done.**
- **Differential runner:** the `differential` scenario compares Skript 2.17.0 and NeoSkript on every documentation example, the checks and probes in `corpus/differential`, and the real-world corpus. It compares each line's messages, the probes' output, and `variables.csv`. It fails below 95%, on any difference not listed in `known-differences.txt`, or if the variables differ.
- **Errors in Skript's order and words:**
  - problems are reported in line order;
  - function argument errors;
  - a later argument's error wins, as Skript parses later arguments first;
  - errors through the changed values' types ("an entity can't be set to anything");
  - unparsed literals are quoted;
  - expressions are written as Skript's `toString` writes them (`Registrar.describe`).
- **Warnings:** constant conditions, texts inside `%...%`, loops of one value, and `items in` a list variable, all as Skript gives them.
- **Event values:**
  - excluded values, and Skript's errors for them;
  - code in an expression's section can't use the trigger's event values;
  - a left-out value gives the error for its first type.
- **Syntax:**
  - `wait X:` as a section;
  - `cooldown storage`;
  - `arguments` in command events;
  - setting the vehicle;
  - `X is neither A nor B`;
  - past and future worlds in teleport events;
  - `zombify` and `unzombify ... after`;
  - stricter weather, enchantment and world types where Skript has them;
  - `the entity` and `the player` as the event's value.
- **Variables:** entity types are saved as Skript's entity data, byte for byte. The bytes for each type come from a real Skript server.
- **Known differences:** 20 units where Skript's parser picks a different reading or error are listed as still to do. A few of them give different messages from one Skript run to the next.

#### Batch 28 (2026-11-11)

**862 of 862 tests pass; 99.0% of the differential identical (1,598 of 1,613 units).**
- **Comparisons read words as Skript does** (`CondCompare.attemptReconstruction`). Words are read as the first of Skript's types, in its order, that the other side can be compared with, then as the other side's type. Words compared with words are read as any type in Skript's order. This was a runtime bug: `projectile is an arrow` was false. It is now true, because the entity type is compared with the arrow item.
- **Entity types and items** compare as Skript's do. A simple entity type is the item it's registered with, `arrow` is the arrow item, and a category is an item if one of its entity types registered before it is. Other entity types use their alias's related entity (`skript-simple-entities.tsv`, from Skript 2.17.0). An entity compares with an item through its type, so a spruce boat is a `boat`.
- **Parse errors in Skript's order:**
  - a pattern's rest is matched before its placeholder is parsed, so a later placeholder's error is the line's;
  - Skript's best error is kept: the first of the best quality;
  - an event value (`event-x`) or another expression of any type that isn't of the type wanted gives Skript's `X is not a Y`;
  - text isn't said to be the wrong type where only a literal goes.
- **No arrow pierce level:** Skript registers it only where `Arrow` declares `getPierceLevel` itself, which no Paper it runs on does. So `arrow pierce level of X` is read as something else, as in Skript.
- **Fixed in the differential:** ExprEntity-4, ExprArrowPierceLevel-1, CondCanAge-1, EffGenerateLoot-2. The reasons for the units still to do are now more precise.

#### Batch 29 (2026-11-11)

**862 of 862 tests pass; 99.7% of the differential identical (1,609 of 1,613 units, against 3 runs of Skript).**
- **Skript's results that vary:** Skript breaks ties in its syntax order by identity hash codes, so a few units differ from run to run. The differential now runs Skript several times (`-Pharness.differential.skriptRuns=N`, 3 by default, each run cached) and a unit is identical if NeoSkript gives what Skript gave in any of them. The report lists the units that vary: 3 (`ExprLocationAt-1`, `ExprLowestHighestSolidBlock-2`, `ExprPassenger-1`). NeoSkript gives the more common result of 15 runs for the last two; `ExprLocationAt-1` is about half each.
- **Syntax in Skript's order** (`SyntaxRegister.SET_COMPARATOR`): by priority level, then by the complexity of the most complex pattern (`skript-syntax-order.txt`, from Skript's registry), effect sections before effects. Ties keep NeoSkript's registration order. NeoSkript's event values stay last, as they match any type's name.
- **Skript's unparsed literals:** words where only any value goes are taken at the first split Skript's parser tries ('broadcast <the 1st element> in <{queue}>'). A placeholder before one that no type reads tries longer text first, keeping the first match (`TypePatternElement.hasUnparsedLiterals`). Words with brackets are one too. The literal keeps the best error found reading it, which is the error when nothing can use it.
- **Types as Skript takes them:** between Skript's types, a value is taken where another type goes only if its class is under the other's or Skript has a converter (`skript-conversions.txt`, from `Converters.converterExists`), not loosely: 'last shot arrow' isn't a projectile. Types are under others by their Java classes. Arithmetic's values have the types `ExprArithmetic` works out from Skript's operations.
- **Expressions of any type:** those Skript registers as returning `Object` are tried wherever a value goes, and say what they give once set up (`Registrar.returnType`, as `getReturnType`). One that then isn't wanted is the error, written as its `toString` (`Registrar.describe` now reaches the parser), and nothing else is tried there. So is a plural expression where one value goes.
- **Init checks:** what Skript's `init` turns down is turned down while parsing, so another reading is tried: time states of what has none, `recursive` of what can't nest, a direction after a non-direction, `except` on one value, an inventory of `items in`, arithmetic on words no type reads. An event value that isn't there gives Skript's error as it's tried, and before a change effect checks what it's given.
- **Text components:** the chat, join, quit, death, kick, broadcast and unknown command messages and the MOTD are text components, as in Skript 2.17. The player-events scenario now checks the chat message as text, in a variable and compared, and matches Skript.
- **Section headers that fail:** as Skript doesn't read the code in them, its problems aren't reported.
- **Fixed in the differential:** ExprElement-6, ExprElement-7, ExprTagContents-2, ExprAttachedBlock-1, ExprPassenger-1, ExprLowestHighestSolidBlock-2, EffOpenInventory-1, ExprPersistentData-1, ExprClicked-1, EffChange-2, ExprColored-2. Also two of Skript's own tests NeoSkript expected wrongly: Skript reads `1.5 rounded down` as 2 meters below.
- **Still to do:** CondPotionHasParticles-1, ExprItemCompCopy-1, ExprNumberOfCharacters-1 (the message is a text component now; Skript's error comes from re-reading the line's words), ExprSecCreateLootContext-1, and ExprLocationAt-1's more common result. The committed website syntax export predates these type changes; `pnpm syntax:export` remakes it.

#### Batch 30 (2026-11-11)

**862 of 862 tests pass; 100% of the differential identical (1,613 of 1,613 units, against 3 runs of Skript).** Nothing is left in "Not matched yet"; one unit, ExprLocationAt-1, is listed as varying in Skript, where NeoSkript gives one of Skript's results.
- **What's in brackets:** Skript's list parser reads text in brackets again on its own and returns without printing its log, so its errors are dropped where it isn't a value wanted (`(the equippable component of {_item})` where an item component goes). Where any value goes, the unparsed literal keeps them.
- **A failed section header read as a statement:** Skript's script loader reads a section header that fails as a statement too, and keeps that error unless it's that the statement isn't understood (`ScriptLoader.loadItems`). `if the potion effect has particles:` says that all of the particles isn't an item type.
- **A type named in the plural alone** is all its values, as Skript's `[all [[of] the]|the|every] %*classinfo%`: `particles`, written `all of the particles` in errors. In the singular it stays the event's value.
- **A type's name in a pattern is a placeholder:** the rest of the pattern is matched before it, as for Skript's `%*classinfo%`, so its errors come first. A colonless `if number of uppercase chars in message / length of message > 0.5` gives the division's error through `X in %~objects%`. A literal is of no type in arithmetic and comparison errors, as Skript's unparsed literal is (`message / 2` says nothing).
- **Unparsed literals of any text:** where any value goes, text Skript can't read that isn't a list, isn't in brackets and isn't a list's item is an unparsed literal, as Skript's list parser makes it. Conditions and expressions can't use one and fail to set up, saying nothing. A change to a variable fails with the literal's error, or that it can't understand it. Elsewhere the literal gives the best error found reading it. A type hint that isn't met is an error found while reading, and experience is named `experience point`, as in Skript.
- **ExprLocationAt-1:** of 29 runs of Skript, 16 say `'the world arg-4'` and 13 `'arg-4'`. Comparing Skript's syntax order across 14 runs shows what decides it: `the world arg-4` comes when Skript's `location %directions% [%location%]` (tied with ExprLocationAt on priority and complexity, ordered by hash code) is tried first and reads `the world arg-4` through `X of %itemtypes...%`. NeoSkript gives `'arg-4'`; matching the other result needs command arguments refused while parsing outside commands, which gave another argument's error first.
- **Fixed in the differential:** ExprItemCompCopy-1, CondPotionHasParticles-1, ExprNumberOfCharacters-1, ExprSecCreateLootContext-1.

## M5: Folia and migration

**Done 2026-11-11.** The real-world corpus runs on Folia with no thread violations, reads from another region are a clear error with `run on` as the explicit way across, and the migration CLI is released.

### Step 1: The migration CLI

**`neoskript-cli check <scripts>`** reports what NeoSkript makes of a server's scripts before it's installed.
- **How it checks:** item, block and entity names come from a live server's registries, so the CLI starts a throwaway headless Paper server with NeoSkript in a check mode (`-Dneoskript.check`, `-Dneoskript.check.report`). The server is the build the harness pins, checked against its SHA-256 and cached in `~/.neoskript/cache`. The scripts are parsed and compiled with the real standard library and nothing runs. Then the report is written and the server stops.
- **What it reports:**
  - **Errors and warnings:** as on load.
  - **Folia hints:** global events, reads of entities and blocks that may be in another region, entity changes (which run on the entity's region), and read-modify-write of global variables.
  - **Performance hints:** `every` periods under a second that loop players or entities, `all entities`, and busy `while` loops.
- **Tested** by the `neoskript-cli` harness scenario. The corpus loads cleanly, and a script with known hazards gets exactly its hints.

### Step 2: Region ownership on Folia

What [plan 3.3](PLAN.md#33-runtime--threading-paper--folia) says Folia does, done:
- **Checks only on Folia:** on Folia the compiler wraps each call into a syntax element in a check (`RegionalElements`). What the check looks at comes from the element's placeholder types (entities, blocks, locations). Elements that only read what's safe anywhere, or only send something, are exempt (`Regions.SAFE_READS`, `Regions.SENDING`, which the CLI's hints share). On Paper nothing is wrapped and the generated code is unchanged.
- **Reads from another region:** an expression or condition given an entity or block that another region's thread owns reports a runtime error with the line, as Skript prints them: `A pig is in another region, which only that region's thread can read on Folia. Use 'run on' to run code there.` The read gives nothing and the trigger carries on. Anything Folia's own thread checks refuse is reported the same way, never as a stack trace.
- **Effects and changes in another region** run on the entity's scheduler, or the block's or location's region, and the trigger doesn't wait for them. An effect on several entities is split by owner. Errors in an effect run elsewhere still name its line.
- **`run on %entity/location%:`** (NeoSkript only): its code runs on the thread that owns the entity or location, and the trigger waits for it, like `wait`, then goes back to its own thread. A `wait` inside it stays on that thread. On Paper the code just runs.
- **Tested:** unit tests (`RegionOwnershipTest`, against a pretend server) and the `neoskript-regions` harness scenario on all four servers. Two pigs 4096 blocks apart are each in their own region. From the global region, reading one is the runtime error, and changes reach both. `run on` reads each on its own thread, in order with the rest of the trigger, and a read across regions inside it is an error too. On Paper the same script reads and changes everything with no errors. The real-world corpus scenario now also fails on any thread violation in the log, and passes on Folia.
- **No blocking hop for reads (D11, decided 2026-11-11):** it could deadlock two regions, and even from the global thread each read would wait a tick for the other region. Reads across regions stay an error, and `run on` is the escape hatch.

### Step 3: The CLI's first release

**`neoskript-cli` 0.1.0** is on the [releases](https://github.com/xena-studios/neoskript/releases) as a jar with its SHA-256. `neoskript-cli --version` prints the version, from the jar's manifest. With this, M5's exit is met: the corpus runs on Folia without thread violations (`neoskript-real-world-corpus` fails on any), and the CLI is released.

## M6: Beta to 1.0

### Step 1: The update checker

What D15 asks for, done. When the server starts and once a day after, NeoSkript asks Modrinth for its versions (project `neoskript`, Paper and Folia loaders).
- **Who hears of it:** the console gets one line per new version, and players with `skript.admin` get a clickable line when they join, as Skript's permission describes.
- **Which versions count:** a release only hears of newer releases. A beta also hears of newer betas. Alphas never count. Development builds (SNAPSHOT versions) don't check at all; they update from the dev build.
- **Turning it off:** `update-checker: false` in config.yml. Test servers never check.
- **When Modrinth can't be reached:** a debug line, nothing more. Until the project is on Modrinth its answer is a 404, which means nothing newer.
- **Tested** by `UpdateCheckerTest`: semantic-version order, the channels, and skipping alphas and snapshots.

### Step 2: Folia's threads for sections, the global thread and tests

- **Sections run where their entity or location is:** on Folia, a section given an entity or location, like `spawn a pig at X:`, runs on the thread that owns it, as in `run on`, since its code goes on with what it made. That matters most for `every` triggers and console commands, which run on Folia's global thread. Getting there and back are like waits, and only happen when the trigger's thread doesn't own it. `exit loop` and `continue` inside such a section go back to the right thread first. On Paper nothing changes.
- **What only the global thread may change:** world time, weather, gamerules, difficulty and PvP, and console commands, used from a region's thread, now run on the global thread soon, while the trigger carries on, as changes to another region's entities do. Before, Folia's thread checks stopped them with an error. Waiting for them instead would end the event first, so `cancel event` after them would stop working.
- **Skript's tests on Folia** now run on the thread of the region that owns `test-location`, where they act on blocks and entities, instead of the global thread. Skript's suite on Folia 26.3 went from 755 to 847 of 862. Of the other 15, 10 check straight after a console command, a time or a PvP change, which on Folia land a tick later, by the rule above. The rest (growing trees, `all entities`, `items in` a chest at a world's spawn, and the nearest creeper) are still to look at. On Paper it stays 862 of 862.
- **`neoskript-skript-tests` has no pending tests left:** the 8 waiting on M4 pass on every server and now must, including `literal type parsing`, which failed on Folia when its spawn section ran on the global thread.
- **Tested** by the `neoskript-regions` scenario: from the global thread, a spawn section at the far pig's region names its cow there, reads the far pig without an error, and goes back to the global thread after it. `exit loop` in one leaves the loop.

### Step 3: What a trigger costs per event

**`event-benchmark`** (BASELINE, paper-26.3) runs `event-benchmark.sk` on Skript and on NeoSkript. A harness bot joins, and `bot bench` fires a toggle event 20,000 times in a row on the player's thread and times it. There are 12 rounds of each event; the first 4 warm up and the result is the median of the other 8.

| Trigger | Skript | NeoSkript |
|---|---|---|
| One condition (`on flight toggle`) | 0.93 µs | 0.04 µs |
| Global variables and text (`on sneak toggle`) | 7.01 µs | 1.48 µs |
| A loop with arithmetic and a local list (`on sprint toggle`) | 8.73 µs | 3.97 µs |

- **The first run found a slow path:** the loop was slower on NeoSkript than on Skript (12.5 µs against 10.5). Profiling showed half of it was the variable store splitting names on `::` with `String.split`, which compiles a regex for every name. It now walks the name with `indexOf`, and adding to a list looks each index up once.
- **On neoskript.dev:** the home page's benchmark shows the three triggers next to load and reload, from a fresh run of all three benchmarks on 2026-11-12 (startup 1.57 s against 0.77 s, reload 1,127 ms against 214 ms).

### Step 4: Skript's suite on Folia, 860 of 862

- **Tests that wait finish before the next starts:** Skript's test runner in NeoSkript runs one test at a time. A test that waits, or goes to another thread on Folia, finishes before the next one begins, up to a minute. Its event's thread is `test-location`'s. Before, what a test did after a wait was cut off. That alone fixed growing trees, carrying blocks and the nearest creeper on Folia.
- **Global-only lines wait for the global thread where nothing's lost:** a console command, or a change to world time, weather, a gamerule, difficulty or PvP (including `set whether pvp is enabled ...`), goes to Folia's global thread first, so the next line sees the change, as on Paper. The trigger stays there while the lines after it take nothing in a region (no entities, blocks or locations), so the world's clock doesn't move on between them, and then goes back. This happens only in triggers of NeoSkript's own events (tests, `every`, script commands) or once the trigger has waited. In a Bukkit event still being dispatched, waiting would lose `cancel event` and the event's values, so there the line is sent to the global thread as before.
- **Skript's suite:** on Folia 26.3 it went from 847 to 860 of 862, and it stays 862 on Paper. The last two (`items in` a chest at the spawn of `random world out of all worlds`) read a block in another world. On Folia only that region's thread may read it, and D11 rules out blocking reads, so they pass only when the random world is the test's own.
- **Tests for newer versions are skipped, as in Skript:** a test whose `when` condition is false when the script loads (`test "nautilus data" when running minecraft "1.21.11":`) is skipped without its code being loaded, as Skript's test structure does, instead of failing on what older versions don't have. On 1.21.4, 10 tests that couldn't run are now skipped.
- **Fire resistance on 1.21.4:** Paper 1.21.4's `setFireResistant` makes an item fire resistant whatever it's given, so making one not resistant now clears its damage resistance. On Paper 1.21.4 the suite is 850 of 850 that run, apart from `item flags`: there Paper drops `hide attributes` from an item without attribute modifiers when it's stored. Skript 2.17 doesn't test 1.21.4 (its CI starts at 1.21.5) and would do the same.

### Step 5: Tick time with players online, and loading variables

- **`tick-benchmark`** (BASELINE, paper-26.3): 20 harness bots online and `tick-benchmark.sk` running, a busy server's per-player work every tick, every 5 ticks and every second. After 20 seconds the harness reads the server's mean milliseconds per tick (over its last 100 ticks, `bot mspt`) 8 times and takes the median, on Skript, on NeoSkript, and on the same server with neither. The scripts' part is the difference: **2.36 ms a tick on Skript, 0.95 on NeoSkript** (3.54 with no scripts).
  - **The first run found two things.** First, NeoSkript was slower than Skript. Most of its time went to finding variables in each list, ordered as Skript orders indices, which is costly for long indices like UUIDs. Each list now also has a hash index by lower-case index for finding one; the ordered map stays for going through them, and a list's size is counted in constant time. Second, Skript couldn't save variables changed 800 times a tick fast enough and warned that the server would hang, so the benchmark's counters are `{-...}` variables, which neither saves.
  - The event benchmark's trigger with global variables went from 1.48 to 0.88 µs with the hash index.
- **`variable-benchmark`**: Skript makes 140,000 variables of the kinds servers keep and saves them. Then Skript and NeoSkript each load that `variables.csv` 3 times when the server starts: **500 ms on Skript, 438 on NeoSkript** (median; Skript logs tenths of a second). Most of NeoSkript's time is putting each index in Skript's order.
- **The bot plugin:** a bot's channel can be active before it's attached, which made one bot in 16 fail to join. The bot now takes its channel when it's active.
- **neoskript.dev** shows both, from a fresh run of every benchmark on 2026-11-12: startup 1.97 s against 0.87, reload 1,412 ms against 228.

### Step 6: The public beta

**NeoSkript 0.1.0-beta.1** is out:
- on [GitHub Releases](https://github.com/xena-studios/neoskript/releases/tag/v0.1.0-beta.1) (a prerelease, with its SHA-256), from `2c626d6`;
- on [Modrinth](https://modrinth.com/plugin/neoskript) as a beta, for Paper and Folia, Minecraft 1.21.4 to 26.3. The project page now has the beta's install steps, compatibility and benchmarks, and it was sent for Modrinth's review on 2026-11-12.

The jar was booted on all four harness servers before it was published. The update checker looks for it on Modrinth, so betas hear of newer betas and releases. neoskript.dev's download page offers the latest release first, read from GitHub Releases when the site builds, then the development build. The home page, the docs and the README say it's a public beta.

### Step 7: Code editors

Planned for 1.x, done early: NeoSkript's parser in editors (`editors/`).
- **The language server** (`editors/language-server`) is NeoSkript's frontend compiled to JavaScript, the same as neoskript.dev/parser, bundled with the syntax data into one Node file (`dist/server.cjs`, 1.9 MB). It gives problems as you type, with Skript's messages. Each script is parsed with the function declarations of the other `.sk` files in the workspace, so calls to them aren't unknown. It also gives hover docs for each line's element, its expressions and function calls, from the syntax reference with a link to its entry. Completion covers events after `on`, effects, conditions and sections (snippets from their patterns), functions, `{@options}` and variables. There's an outline, and go to definition for functions (across the workspace) and options. The parser now also reports where each element is on its line (`elements`), function signatures, and command names. A script of about 1,150 lines parses in about 260 ms on Node.
- **VS Code** (`editors/vscode`): a TextMate grammar (from the site's Monaco tokenizer), Skript's editing rules (`#` comments, `:` indents, folding by indentation) and the server. Packaged as `neoskript-0.1.0.vsix`, about 600 KB.
- **Zed** (`editors/zed`): a tree-sitter grammar (`editors/tree-sitter-skript`) with highlight, bracket and outline queries, and a Rust extension that runs the server with Zed's Node.js. It uses `neoskript-language-server` from the PATH, else it downloads it from the `language-server-v<version>` release.
- **Tested** by `pnpm test` in `editors/language-server`, which starts the built server over stdio in a workspace of two scripts and checks problems, hover, completion, the outline and both kinds of definition. The grammars are checked by tokenizing and parsing a sample, and the Zed extension builds for `wasm32-wasip2`.
- **neoskript.dev** has a [Code editors](https://neoskript.dev/docs/editors/) guide.

### Step 8: Damaged variable files

A fix for 0.1.0-beta.1, which could wipe `variables.csv`. Skript appends changes to the file, so a crash under Skript can cut its last line short. A value of odd length made the load throw an exception the plugin didn't catch, Paper disabled NeoSkript, and its shutdown save wrote an empty store over the file. A unit test on the old code showed the empty file.
- **Nothing saves until the variables load:** `VariableStorage` refuses to save, at the interval or at shutdown, until a load has succeeded. A file that can't be read at all is left alone, and the console says so.
- **Bad lines are skipped, as in Skript:** a line with the wrong number of fields (Skript's `invalid amount of commas in line N`) or a value that isn't hex is skipped, and the rest load. An earlier line for the same variable still counts. The console lists them with Skript's summary (`1 variable could not be loaded!`, `Affected variables: ...`) and how many lines were skipped. A line cut inside a UTF-8 character no longer fails the read.
- **Backups:** when lines are skipped, the file is first copied to `backups/variables_<time>.csv`, named as Skript names its backups. The first start after migrating from Skript backs up the copied `variables.csv` the same way before it's loaded, as the plan's risks section promised.
- **Interrupted saves:** temporary files a save left beside `variables.csv` are removed at startup. If `variables.csv` is missing they're kept.
- **Tested** by `VariableStorageLoadTest` (a cut last line, odd-length and non-hex values, a wrong field count, a file that can't be read, leftover temporary files), `SkriptMigrationTest`, and a fifth step of `neoskript-variables` that starts a real server on Skript's file with its last line cut short.

### Step 9: The family look for /sk and config.yml

`/sk` and config.yml now look like Xena Studios' other plugins (xUtilities, zChat, xLimbo). Their core isn't a dependency: what NeoSkript needs is written again in `neoskript-plugin`, so the two repos release on their own.
- **Same commands, new look:** `/skript` and `/sk` keep every subcommand and argument, so habits, console automation and the harness keep working. `/neoskript` and `/ns` are the same command. The messages are NeoSkript's own, in one place (`Messages`), written in the theme's `<prefix>`, `<highlight>`, `<success>` and `<error>`.
- **In chat:** `/sk help` lists the subcommands, and clicking one types it. Script names in reports and `/sk list` type the command to reload or enable them. Tab completion offers the subcommands, scripts and folders, and the words (`all`, `config` and so on) only while they match.
- **Reload reports:** what was reloaded, how many scripts, and how long it took. Problems are listed per script with their line numbers; in chat, hovering shows the line of code and clicking copies it, and the console gets the line written out. `auto reload` reports the same way. Problems at startup and runtime errors keep Skript's look, which tools and the differential harness read.
- **`/sk list`** groups the enabled and disabled scripts by folder. **`/sk info`** shows the version, the server, the scripts, addons (plugins that depend on NeoSkript), hooked plugins, the update checker and links to neoskript.dev. **`/sk update`** asks Modrinth through the update checker; `update changes` links to the release notes.
- **config.yml from a schema:** one declaration (`NeoSkriptConfig`) gives each setting's default, what's valid and its comment, and writes the default file. It starts with `config-version` and a `theme`, in NeoSkript's magenta (#FF4D9D). A value that isn't valid falls back to its default with a warning naming the setting and why. Only the prefix may be empty. A file that isn't valid YAML is never overwritten: on `/sk reload config` the running settings stay, and at startup the defaults run. A file that can be read but not written, such as a read-only mount, is used as read, with a warning. A style or prefix that can't be read, even one nested thousands of tags deep, falls back to the default, and a reload that fails part way puts the running settings back. Keys left empty keep their line and comments when the file is rewritten.
- **Carrying the old file forward:** a config.yml without `config-version` or a `theme` is version 1. It's backed up as `config.yml.bak-v1` and rewritten in the new layout with every value the owner set, even one that isn't valid, and keys that aren't settings stay where they were. Every setting keeps its meaning, including the effect command settings copied from Skript's config.sk, which now leave a current file's comments alone and never touch a newer one. Two values read differently, on purpose: `metrics: "false"` in quotes turns bStats off (Bukkit read it as on), and a negative runtime error limit falls back to its default.
- **On reload:** `/sk reload config` applies the theme, the update checker, `player-uuids-in-names`, `save-interval`, the runtime error limits and effect commands. `metrics`, `variables.file` and the `events` settings wait for a restart, and each reload says so while one differs from what the server started with.
- **Tested** by `NeoSkriptConfigTest` (the default file, carrying version 1 forward with its values, backup and other keys, bad values, bad YAML, files that can't be written, newer files, empty settings, and that the docs show the default config), `SettingsReloaderTest` (what a reload applies, and the restart notice) and `MessagesTest` (rendering in a theme, placeholders, clicks and hovers, and reports for chat and the console). The `neoskript-reload`, `neoskript-auto-reload`, `neoskript-commands` and load benchmark scenarios check the new messages, and `neoskript-skript-migration` now checks that config.sk's settings reach config.yml.
- **neoskript.dev** has [Commands](https://neoskript.dev/docs/commands/) and [Configuration](https://neoskript.dev/docs/configuration/) pages.

### Step 10: The addon API declared stable

Planned for 1.x, done early: `neoskript-api` is something addons can depend on.
- **An entry point:** an addon is a plugin with `depend: [NeoSkript]` that calls `NeoSkript.get().addon(name, version, registrar -> ...)` in `onEnable`. NeoSkript then registers its syntax on the first tick, with the addons' after its own and the hooks', and loads variables after that, so values of an addon's types load. Only plugins that depend on NeoSkript make it wait, not soft dependencies; without them nothing waits, as before. `/sk info` lists them. Only NeoSkript's own classes can install the instance `NeoSkript.get()` returns.
- **Ready:** scripts and variables throw `IllegalStateException` until NeoSkript is ready, after scripts load; `whenReady(task)` waits for it. Before, an addon reading scripts in `onEnable` froze the empty syntax registry and nothing loaded.
- **When an addon disables:** values its code saves (its own types, and JDK or Bukkit types it registered a codec for) become their saved bytes, and its codecs are dropped, so saves after it (unload triggers set variables at shutdown) never run its code. No value is deleted: one whose codec fails then keeps what the file has, and one with nothing there keeps its value unsaved, with a warning. An addon can't register a codec for a class that already has one, so disabling it never takes NeoSkript's away. Its `/sk info` entry goes; its syntax stays until a restart, and enabling an addon while the server runs isn't supported. Saving is per entry: one that can't be encoded keeps the bytes it had in the file, the rest are saved, and the console names it. A codec whose decode can't load its classes leaves that value as its bytes and the rest load; NeoSkript's start always ends ready, even if a step fails: it stays enabled and says loudly that scripts didn't all load, where before an exception there disabled it.
- **Scripts, variables and threads:** `scripts()` loads, reloads and unloads scripts and calls functions, `variables()` reads and writes global variables, and `threads()` runs code on the global thread, an entity's or a location's thread, later or off the server's threads, and says which thread owns what. Tasks belong to the addon that scheduled them (found past JDK and library frames, with a warning if no plugin's class did), and one for an entity that isn't valid doesn't run, on Paper and Folia alike. `Registrar.affinity` says an element is safe anywhere or belongs to the global thread on Folia; it only takes the addon's own element ids.
- **Stable, experimental and internal:** what's public is stable unless it's marked `@Experimental` (Skript's quirks, kept for the addon shim: time state exclusions, type properties, `describe`, `returnType`...) or `@Internal` (for NeoSkript's own modules: `lateSyntax`, `effectBefore`, `ElementInfo`'s constructors, `Warnings.install`). The stability promise is in `NeoSkript`'s javadoc and the guide. No JPMS module: annotations and the check below do the job, and the API stays plain for TeaVM.
- **Javadoc for all of it**, and a `package-info.java` per package. The javadoc task runs doclint with warnings as errors, so a missing doc fails the build.
- **The API check:** `api/neoskript-api.api` lists every public signature, erased and generic, with permitted subclasses, annotation defaults and constants, experimental ones marked. `./gradlew build` fails when the API no longer matches it, or when a stable signature uses a class outside the API or an internal one; `./gradlew :neoskript-api:apiDump` writes it after a deliberate change. The check has tests of its own in `build-logic`.
- **Maven Central:** `co.xenastudios.neoskript:neoskript-api` with its sources and javadoc jars, through the Central Portal (`com.vanniktech.maven.publish`). It's signed when a key is given, and not published yet.
- **An example addon** (`examples/example-addon`), a lottery of tickets: a type with a codec, an event with an event value, an effect, an expression, a condition and a function, and a command that reads a variable and calls a function through the API. CI builds it. It isn't shipped.
- **Tested** by the `neoskript-addon` smoke scenario, on all four servers: a script uses the addon's syntax, its command reads what the script saved, `/sk info` lists it, and the next start loads the saved ticket with the addon's codec.
- **neoskript.dev** has an [Addon API](https://neoskript.dev/docs/addon-api/) guide, linked from the addons page.

### Step 11: Native variable storage

Planned for 1.x, done early: an optional store of NeoSkript's own, `variables.storage: native` in config.yml. `variables.csv` stays the default and doesn't change.
- **The format** (`variables.nsv`): three 4 KiB blocks (below), then records, each a length, a CRC32C and its entries. Names are front-coded within a record and types are listed once per record. Values are Skript's binary format, the same bytes as in `variables.csv`, so converting loses nothing, values NeoSkript can't read yet included. No dependency.
- **Saves write only what changed:** the store keeps the bytes it last saved for each variable, and a save appends one record with the variables that changed or were deleted, then syncs it. A variable holding the same text, number, boolean or timespan object isn't encoded again. When the file is over twice what its variables need plus 1 MiB, the save writes the whole set to a temporary file in Skript's order instead, syncs it and moves it over.
- **Blocks:** the file starts with three 4 KiB blocks, each a page of its own: the identity (magic, version, a random file id, a generation counter, checksum), written once when the file is made, and two commit slots, each with its own magic, version, file id, sequence number, end of the committed saves and checksum. Data starts at 12 KiB. A power cut that garbles the page being written can only take that block; the file loads from the other slot even if the identity is garbled too.
- **Spec:** the format is written out in `NativeVariableFile`'s javadoc: big-endian integers; unsigned LEB128 varints of at most 5 bytes, at most 2^31 - 1 and in their shortest form (a reader refuses others); a record's CRC32C seeded with the file id and then over its payload, so a record left from an earlier file (shown again by a crash exactly at the committed end) never reads as this one's; absolute end offsets; zero bytes after each block's fields; names lower-cased with `toLowerCase(Locale.ROOT)`; a commit whose file id differs from a readable identity ignored; the generation as one more than the highest of the live file and its snapshots; at most one save read past the committed end; and the 2 GiB limit, which saves enforce too: a file is compacted before it would pass it, and a save whose compacted file would still be larger is refused, keeping the old file, with an error in the console.
- **Commits:** compaction writes a new file, with a new id, committed to its end, synced and renamed. A save appends its records, syncs them, then writes the other slot with the next sequence and syncs that. The valid slot with the highest sequence (compared unsigned) wins; a slot torn by a crash fails its checksum, and the other still holds the commit before. A save larger than about 1 MiB takes several records, and all but the last say the save goes on, so a save is applied whole or not at all.
- **Crash or damage:** everything up to the committed end was synced before its commit, so any fault there is damage. After the committed end there is at most one save, since the next waits for the commit. If it's whole it's kept; anything else there, cut short, torn in any order or zero-filled, is a crash and is ignored. Loading writes nothing, so a full disk can't lock the server out: the first save backs the file up and writes it afresh.
- **Snapshots:** every compaction (and so every import and the file's creation) also copies the new file to `backups/snapshots/`, synced, keeping the last 3, ordered by a generation counter in the identity block, one more for each file written, rather than by time. Until the next compaction the newest is byte for byte the start of the live file. Disk use is up to about 5 times the variables' size: the file up to twice, plus three snapshots. Temporary files a crash left in `backups/` and `backups/snapshots/` are removed at startup.
- **Repair:** damage inside the newest snapshot's range, with a readable commit after it, is repaired at load from the snapshot, with the committed saves after it from the file: nothing is lost, and the console says so. A snapshot is matched by file id, so it's never used for another file.
- **Recovery:** damage after the snapshot's range refuses to load. The message names the newest snapshot and `variables.recover`. With `variables.recover: true`, the next start first sets the switch back to `false` through the config loader (so it keeps comments and leaves a newer config.yml alone); if that can't be done, the switch isn't used, so it can never stay on and recover later damage nobody chose to. Then it backs up the damaged file, loads the saves before the damage, writes a new file, and lists what's lost: the bytes of committed saves dropped, and the variables changed by every record in them that can still be read, the damaged save's own included. It works when switching back to `csv` and when both files exist, too. A whole record or commit of a newer format is never recovered over.
- **Durability:** the CSV and native files, compactions and backups are synced before they're renamed into place, and the directory is synced after each rename and delete. `variables.csv` saves get this too, still streamed to the temporary file rather than built in memory. Backups are copied through a channel opened for writing, so a read-only file's backup can be synced.
- **Limits and failures:** a file over 2 GiB, or too large for the server's memory, is a clear error, not an `OutOfMemoryError`. A backup is copied to a temporary name and renamed once whole, so a failed one leaves nothing behind. A load that fails closes its file.
- **Console:** if the variables can't be opened or loaded, one error says scripts run without them and nothing is saved, and another says so again when the server stops. A due compaction counts as a change, so it runs at the next save even with nothing changed.
- **Loading:** a compacted file is in Skript's order, so `VariableStore.load` links each list's skip list in one pass (the JDK's sorted-map constructor) instead of sorting each index in. Names saved after the last compaction come in the order they were first saved, which for keys written once, like `{firstjoin::%uuid%}`, may never change; each list keeps a hash index while loading and is sorted once if anything came out of order. 600,000 random UUID keys load in 1,349 ms, where setting them one by one takes 2,398 (`VariableStoreLoadTest`, which fails if loading is over 1.5 times slower). The first version inserted each in place and was quadratic: 800,000 took 19 s.
- **Addons:** when an addon is disabled, its values become their saved bytes, as in CSV storage (step 10), using the bytes last saved when its codec fails then. A codec that fails during a save keeps the variable's last saved bytes.
- **Switching:** with `native` and no `variables.nsv`, `variables.csv` is backed up, moved into `variables.nsv` and removed. With `csv` and a `variables.nsv`, `variables.csv` is written again (the old one backed up) and `variables.nsv` goes to `backups/`. The file read is only removed once the new one and the backup are on disk. While `variables.nsv` exists it's the file in use. An unknown setting keeps whichever file is in use. Going back to Skript is a switch to `csv` and a restart.
- **Both files at once:** with the same variables, a switch was cut off and is finished. A `variables.csv` older than `variables.nsv` is moved to `backups/` unread. A newer one with different variables was saved by something that doesn't read `variables.nsv`, like an older NeoSkript after a downgrade, so the load stops with both times and touches neither file.
- **Downgrading:** older versions and Skript only read `variables.csv`, so native storage warns on every start to switch to `csv` before downgrading. The warning and the check above were chosen over keeping a CSV copy, which would undo the point of saving only what changed.
- **Benchmark** (`variable-benchmark`, paper-26.3, median of 3, 140,000 variables, with the final format): **Skript 400 ms, NeoSkript from `variables.csv` 489 ms, NeoSkript native 275 ms**. The CSV is 7.8 MB, the native file 2.5 MB. The machine was busy (load average about 12), and starts vary: across this and earlier runs, NeoSkript's CSV took 423 to 618 ms and Skript's 400 to 800, native 271 to 328, apart from one run during heavy load (308, 505, 524).
- **Tested** by `NativeVariableStorageTest`: a round trip of every value type, a save cut off at every byte, a large save cut off or torn between its records, zero-filled tails of 8, 64 and 4096 bytes, damage anywhere in a compacted file or an earlier save, a flipped bit in the last save with and without its commit, values that look like records, a whole record it can't read and reserved header flags, a compaction cut off, a read-only file's backup, a CSV save that fails midway, repair from a snapshot, a snapshot of another file, the one-shot recovery, a garbled first page, a full disk after a crash, a failed backup, a file over 2 GiB, unsigned sequences, a newer commit, a failed load's channel, appending only changes, deleted lists, compaction over 500 saves, saving while 4 threads write, CSV to native to CSV byte for byte with Skript's file, an import and an export cut off, a newer CSV beside the native file, and bulk loading against setting one by one. `NativeVariableStorageFailureTest` makes a write, a sync and reopening the file fail mid-save. The `neoskript-native-variables` harness scenario saves in CSV, switches to native, changes values, restarts and reads them back, then switches back to CSV, on Paper and Folia 26.3 and 1.21.4.
- **Not done:** there's no command to export while staying native; switching to `csv` does it. (Skript's `databases` section came in step 21.)

### Step 12: Deleted variables, large files and `loop N times`

What the hardening pass left open about memory.
- **Deleted variables free their memory.** A deleted variable kept its node in the tree, so a script that sets and deletes uniquely named variables, like `{temp::%uuid%::x}` for each event, grew without bound. A variable with no value and nothing under it is now taken out, and so is each list above it left empty. Skript leaves an emptied list's map behind, which only `add` can tell: it skips that index. So an emptied list at a positive integer index (`{x::1}` after `delete {x::1::y}`) is kept, as in Skript, until `delete {x::1::*}` or `delete {x::*}`, which drop it as Skript drops its map. Lists at other indices, like `{temp::%uuid%}`, are taken out, since nothing can tell. A delete walks the name once and reuses its nodes to take them out, and `remove X from {list::*}` takes each value's node out as it empties it. Values still change without locks. Adding a child or taking one out holds the parent's lock, and taking one out holds its own too, parent first. A node is marked dead before it's taken out, so a change that found it just before starts again from the top and isn't lost. Lists stay in Skript's order with their hash index, sizes are still counted in constant time, the bulk load is unchanged, and both storages still save a delete as one, since they tell what changed by comparing against what they last saved. Loops are unaffected: they go through the values taken when they start, as Skript 2.17's do.
- **`variables.csv` is read a line at a time.** Loading read the whole file into memory and then into one string, so a 300 MB file needed a heap of several GB. Saves were already streamed. The parsing is the same, line endings and bad-line handling included, and each entry's bytes are let go once decoded.
- **`add` puts a value at the lowest free index,** as Skript 2.17's `IndexTrackingTreeMap` does: after `delete {x::1}`, `add "e" to {x::*}` puts it at 1. NeoSkript started at the list's size plus one and went up, and a deleted node still counted in the size. The differential's new probe found it. Each list keeps that index as Skript does: appending is constant time amortised, and filling a hole goes on through the indices after it, as Skript's `advanceNextIndex` does.
- **`loop N times` counts as it goes,** as Skript's iterator does, instead of making N numbers first. N is read once, so changing it inside the loop doesn't change the loop. As in Skript, N plus 1e-10 is rounded down (`2.5 times` is 2; NeoSkript rounded it to 3 before), and from 2^63 up, infinity and NaN it's nothing, as Skript's `LongStream.range` is empty there. Above 2^31 - 1 it stops there; Skript's watchdog stops a loop long before that.
- **`N times` outside a loop has a limit,** a deliberate difference: `set {_x::*} to 1000000000 times` makes all of them at once, as Skript's `get()` does, and runs out of memory in Skript. Past 1,000,000 numbers it's now a runtime error that says to loop it instead, and gives nothing. `loop` and `for` count as they go and have no limit.
- **Tested** by `VariableStoreFreeTest`: a million unique names set, added to and deleted leave the tree as it was, with the heap after GC within 16 MB (5,897 KB before, 5,904 KB after), deleting keeps what's under a variable and above it, adding after deletes, an emptied list at a number keeping its index as Skript's does (and in a copy), a loop deleting what it goes through, and 8 threads writing, adding to counters and deleting under a list that keeps going empty, without losing a write. `NativeVariableStorageTest` saves a variable taken out of the tree as deleted, in both formats. `VariableFileStreamingTest` reads 3,000 random files, with bytes that aren't UTF-8 and every line ending, exactly as the old whole-file reader did. The `largeFileTest` task (part of `check`) loads a generated 611 MB `variables.csv` with a 512 MB heap, where the old reader runs out of memory. `LanguageTest` loops a billion times and exits at 3, and checks the limit outside a loop, and the differential's probes compare deleting, adding and `times` with Skript.
- **Benchmarks** (paper-26.3, origin/main then this, back to back, on a machine with a load average of 30 to 90, so single runs vary by a lot): `variable-benchmark` Skript 900 and 900 ms, NeoSkript from CSV 823 and 616, native 1,278 and 447. `tick-benchmark`, the scripts' part over no scripts: 1.70 and 1.48 ms. `event-benchmark` in 4 pairs, as NeoSkript's share of Skript's time per event to cancel out the load: sneak 0.16, 0.32, 0.20, 0.05 before and 0.11, 0.15, 0.21, 0.23 after; sprint 0.34, 0.53, 0.39, 0.32 before and 0.56, 0.38, 0.53, 0.49 after. Without a server, the sprint trigger's variable work takes 1.6 µs against 1.9 before and the sneak trigger's 0.55 against 0.58. After the review's changes, measured in one JVM with both versions alternating: setting and deleting a new name 0.54 µs against 1.08 on main, whose tree keeps growing; the sprint trigger's variable work 1.87 against 2.71; setting and reading a variable 0.090 against 0.093. Deleting an index of a list that's then set again costs 0.34 µs against 0.08, since its node is taken out and made again: the price of freeing it.
- **Left:** after `/reload confirm`, up to 5 old NeoSkript instances stay alive for a few reloads (#22's soak). Not looked into here: finding what holds them needs heap dumps from that soak, which isn't on `main` yet.

### Step 13: Custom aliases

Issue #11. NeoSkript reads alias files in Skript's format from `plugins/NeoSkript/aliases`, with Skript's default aliases, as Skript 2.17.0 reads `plugins/Skript/aliases`.
- **Skript's alias format** (`AliasLoader`): sections of `name = items`, optional parts, choices, `¦` plurals, `@` genders, variations with `-` filled into ids and `{default}`, components in braces, block states in brackets, `relatedEntity`, `deduplicate`, `minecraft version` conditions, and other aliases as items. Names come out in Skript's order, which decides how an item is written. Files load in the order of their paths, folders starting with `.` are skipped, and `aliases-english.zip` in the plugin's folder replaces the jar's defaults, as in Skript.
- **Layered as Skript layers them:** a name defined again adds its items to the alias. Variations stay defined for later files, so custom files can use the defaults' `{potion types}`. An item an alias file names loses the name it has from its id (`ruby = minecraft:red_dye` takes `red dye` away, though `red_dye` still works), and when its id name is already an alias's, the item joins that alias. Items are written with their alias name, as Skript's `AliasesMap` picks it: the first alias that's the same item or better (an alias's item with a name or enchantments added is still that alias), else the plain item's last name, else the first of the same material. `raw name of` gives each item's id as its file wrote it. Placing an alias with block states places them.
- **Matched against Skript** with the same files on a throwaway Skript 2.17.0 server: the messages and lines of every problem (each repeated once per name the line stands for, as Skript does), what each name resolves to, how items are written and their components. The existing default-alias lookup is replaced by this one, so `tipped arrow` is written `tipped arrow of` and an experience bottle `bottle o' enchanting`, as Skript writes them.
- **Fixes of Skript's bugs:** an alias starting with `{`, a block state without `=`, components the server can't read, a `minecraft version` that isn't a version, or a lone `}` stop Skript loading any aliases and can disable it; here each is an error of its line. Names with capitals, which Skript never finds, are found.
- **Production grade:** a bad file never stops the plugin, and nothing from loading aliases escapes startup or `/sk`: if loading fails as a whole, even for lack of memory, the aliases in use stay. Files are read and loaded one at a time, and a problem keeps its line of code, not the file. Files over 16 MB, more than 64 MB in all, more than 10,000 files, folders more than 32 deep, lines that stand for more than 16,384 names and more than two million names in all are left out with an error. The first 1,000 problems are listed, then one line counts the rest. The same problem from one line is reported at most 64 times. Text that isn't UTF-8 is read with replacement characters.
- **`aliases.load-defaults`** in config.yml is Skript's `load default aliases`. `/sk reload aliases` reads it from the file, and `/sk reload all` and a restart apply it too.
- **Moving over** copies `aliases/` and `aliases-english.zip` from `plugins/Skript`, and `load default aliases` from config.sk. They're copied last, file by file: a file NeoSkript already has is kept (the console says so), and a copy that fails never stops the variables backup, the marker or the settings. A server that moved before this gets `aliases/` copied once, recorded on a line of its own in the marker. (Since #36: a file or folder that can't be read is named and the rest are still copied, the marker's alias line is only written once nothing failed, so the next start tries again, and a later move merges into an owner's aliases folder as a first move does. Past the 1,000 problems listed, `/sk reload aliases` and `neoskript-cli check` count the rest as the errors and warnings they are, with one line saying how many weren't listed.)
- **`/sk reload aliases`** reads the files again, then reloads the scripts whose item names, as they were looked up when the scripts compiled (options put in), include a name whose meaning changed, and those that had errors. A name in a comment, a variable or text doesn't count. A script that now has errors loads without what has them (D12, as changed in step 22). `/sk reload all` reloads the aliases too. Alias problems are listed in the reload report as `aliases/<file>`, with `/sk reload aliases` to click.
- **`neoskript-cli check`** reads the `aliases` folder beside the `scripts` folder (from a script or folder anywhere inside it), or `--aliases <folder>`, with Skript's defaults (or the `aliases-english.zip` beside it) unless the config.sk beside it turns them off. Alias problems are listed apart and don't change the exit code.
- **Tested** by `AliasLoaderTest` (names and their order, layering, variations, how items are written, every problem message, the lines that stop Skript, version conditions, huge files and patterns, 2,000 random files, the problem limit, reading folders up to the byte limit, and recording which names each script uses), `SkriptMigrationTest` (an alias file NeoSkript already has, and the marker), and the `neoskript-aliases` scenario: the same alias files and a probe script on Skript 2.17.0 and NeoSkript, whose 17 problem lines and 80 probe lines must match (items changed after they were made included), then `/sk reload aliases` with a script that loads once its alias exists, one that keeps its last version when its alias goes away, one that uses it through an option, scripts that aren't reloaded (one only mentions the name), the files Skript can't load, `load-defaults: false`, and 30 reloads in a row. `neoskript-skript-migration` and `neoskript-cli` check the copy and the CLI.

### Step 14: Where official addons live, and the originals next to them

The build and release side of [issue #12](https://github.com/xena-studios/neoskript/issues/12)'s core prerequisites. How it works for contributors is in [addons/README.md](../addons/README.md).
- **`addons/` in this repository**, one folder per addon, each found by `settings.gradle.kts`, so `./gradlew build` (and CI) builds and tests them. In one repository they're released with NeoSkript at its version and tested against the NeoSkript they ship with; separate repositories would mean pinning and releasing each against each other.
- **A convention plugin**, `neoskript.addon-conventions`: the API and Paper compile-only, plugin.yml made from `neoskriptAddon { ... }` (with `depend: [NeoSkript]`, `folia-supported: true`, NeoSkript's own `api-version` and the replaced addons as soft dependencies), NeoSkript's version, one jar with its libraries shaded and relocated into the addon's own package (a check in the build fails on any class outside it), the licence header and `LICENSE` in the jar.
- **A template addon** (`addons/template`), built and tested, never released, and **shared support code** (`addons/support`), shaded into each.
- **The original next to ours (D21):** an official addon looks for the community addon it replaces, under any name it's gone by: loaded, or else as a jar in `plugins/` by its plugin.yml name. Its jar can't run today (it needs Skript, which NeoSkript doesn't run next to), so ours loads and says to remove it. If it's ever loaded, through the compatibility layer, ours doesn't load, so the two never register the same syntax.
- **Addon syntax on neoskript.dev and in the editors:** the syntax export names the addon each element, type and function comes from, the `syntax-export` scenario installs the official addons, and the syntax reference and the editors' hover say which addon it's from. Docs come from each addon's `syntax-docs.json`.
- **Modrinth:** each addon needs its own project, which only the owner can create; what each needs is written down in addons/README.md.
- **Tested** by `addons/support`'s unit tests and the `neoskript-official-addons` smoke scenario on all four servers: every official addon loads and registers, a script uses the template's syntax, and with a jar of the original in `plugins/` the template loads and says to remove it.

### Step 15: Addon syntax that pauses the trigger

The first of [issue #12](https://github.com/xena-studios/neoskript/issues/12)'s core prerequisites for official addons: syntax that waits for a slow service, as a database query or a web request does, without blocking the server.
- **The API:** `Registrar.pausingEffect` and `pausingExpression` take elements that return a `CompletionStage`. The trigger stops there, through the same frames as `wait`, and carries on when the stage completes: on the main thread on Paper; on Folia, on the thread of what started the trigger (or of the `run on` section it's in), so a console command's trigger carries on on the global thread and one in `run on` a pig on the pig's. A stage that has completed already carries straight on. `PausingEffect.storing(target, ...)` stores the value in a placeholder that can be set, as skript-db's `and store the result in {_rows::*}` does: a map sets a list's indices, nested maps and lists making lists under them (`{_rows::name::1}`). A pausing expression's value arrives before its line runs; each one on a line is worked out first, in order, and they're only allowed in effects, conditions and function calls on a line of their own. Local variables, loops and earlier pausing values on the same line are kept in the frame.
- **Skript's rules:** a line with a pausing element is certainly after a delay from the start of that line, as if a `wait` came first, since the line runs once the value is there: for `has delay before`, `return`, cancelling the event and the like. The parser reads such a line again from that state. A trigger whose script is reloaded or unloaded while it waits carries on with its old code, as Skript's `wait` does.
- **Failures:** a stage that fails, or an element whose `start` throws, is a runtime error on the line, with its exception's message, and the trigger carries on (the expression has no value, the target isn't changed). At most 10,000 triggers wait at once; `/sk info` shows how many are. Each pausing element has a timeout, a minute unless `Registrar.timeout` says otherwise; after it, the same, and NeoSkript tries to cancel the stage. On Folia, a pausing element given what another thread owns doesn't start, with the usual cross-region error.
- **No leaks or hangs:** a pause keeps nothing of the trigger or its event once it's over, so a stage an addon holds on to doesn't keep old scripts' classes or a player alive. When the server stops, `on unload` triggers run first, and what they pause for gets up to 3 seconds to finish (a last database write); then waiting triggers are dropped, as after a `wait`, and the timeout thread stopped. Their stages aren't cancelled: the work is the addon's. A stage completing after that is ignored. Scripts without pausing syntax compile to the same code as before.
- **Tested** by unit tests (`PausingTest`) and the `neoskript-pausing` smoke scenario on all four servers, with the example addon's lottery office, which answers on its own thread: values, a keyed list, loops, a failure, a timeout, the threads on Folia, a reload while waiting and a stop while waiting.

### Step 16: Scripts that break, and fuzzing

Nothing a script does, at load or while it runs, may take NeoSkript or the server down.
- **Fuzzing:** `ParserFuzzTest`, `CompilerFuzzTest` and `StandardLibraryFuzzTest` take the repo's scripts (the corpus, the harness's scripts, the differential's checks, the skill's examples, and Skript's tests when they're fetched), break them at random (lines dropped, repeated, swapped, cut short, re-indented or run together, tokens swapped between scripts, stray characters, very long lines and deep nesting) and parse and compile them. Nothing may throw, hang, report a bug of NeoSkript's own, or make a class the JVM's verifier refuses. Triggers that surely end are run too, and only runtime errors may come out. Seeded: CI runs a few hundred cases; `-Pneoskript.fuzz.iterations=N -Pneoskript.fuzz.seed=S` runs more.
- **What it found:**
  - A list of a few hundred values (`set {_l::*} to "a", "b", ...`) took minutes to parse: the list parser tried every run of items as one item. When each item parses alone, that's the answer, so it's taken first. 400 texts went from over 20 seconds to 74 ms, 1,600 to under a second.
  - Brackets or calls nested about a thousand deep ran the parser out of stack, and the `StackOverflowError` stopped the whole load. It's now an error on that line: "This line is nested too deeply to be understood". The compiler does the same.
  - A line of thousands of statements run together, whose quotes pair up any number of ways, took hours. Reading one line now has 10 seconds, and one script 30 or 20 ms a line if that's more, after which the line is an error ("This line is too complex to be understood"). Real lines take milliseconds.
  - An exception from an item type's parser while checking a script's `aliases:` stopped every script loading. Now it's an error on that alias.
- **Triggers that fail:** whatever a trigger throws (an addon's exception, a `ClassCastException`, a missing class) is reported and the event's other triggers still run; before, only runtime errors were caught there. The same goes for commands, tab completion, effect commands, load events and waits. Recursion that runs out of stack is handled as in Skript 2.17's `TriggerItem.walk`: admins (`skript.admin`) get "[Skript] The script 'x.sk' infinitely (or excessively) repeated itself!", naming the script by its file name; the innermost function call with stack to spare returns nothing, and its caller carries on. Before, the whole trigger stopped, reported at line 0 at times.
- **Loading never disables NeoSkript:** an error while loading the scripts at startup is logged and NeoSkript stays enabled for `/sk reload`. A reload that NeoSkript fails to compile changes nothing (D12). If the server refuses to listen for one event, the error names the scripts whose triggers won't run and the other events are still listened for; listeners and commands are updated independently.
- **Endless loops:** as in Skript, a loop without a wait holds its thread until it ends. Paper's watchdog can't say where: compiled scripts are hidden classes, which stack traces leave out. So a loop that runs for 5 seconds without waiting is named in the console with its script and line, before the watchdog steps in. Compiled loops check every 65,536 times round, and a wait starts the loop's time again, so a loop that waits each time round is never named.
- **Damaged variables:** a value in `variables.csv` whose length said a gigabyte ran the server out of memory, an error the load didn't catch, so NeoSkript didn't start. Lengths are now checked against the bytes there, and such a value is kept as saved.
- **Variables under stress** (`VariableStorageStressTest`): saving while 16 threads write, as Folia's regions do, always leaves a file that loads back whole; a save into a folder that can't be written keeps the old file, and the next save has everything. A 300 MB `variables.csv` (2.2 million variables) loads in 20 seconds.

### Step 17: Reloading hundreds of times

**`neoskript-reload-soak`** (baseline, 26.3) reloads a varied set of scripts 350 times: events, commands with aliases and a cooldown, a GUI, functions and a function reference, list variables, `every` timers, a timer whose trigger is still waiting when the reload comes, a command waiting across reloads, `auto reload`, a script disabled and enabled again, and one broken every other time, which loads without the broken line (it kept its last version until M6 step 22). After 50 to warm up, the harness bot forces a full GC every 50 reloads and samples the loaded classes, the heap, and NeoSkript's listeners, commands (in Brigadier's tree too), tasks and threads.

| 300 reloads | Paper 26.3 | Folia 26.3 |
|---|---|---|
| Loaded classes | 36,184 → 36,310 | 35,452 → 35,552 |
| Heap after GC | 292 → 290 MB | 287 → 285 MB |
| Listeners, script commands | 11, 7 → 11, 7 | 11, 7 → 11, 7 |
| Scheduled tasks | 12 → 12 | 12 → 13 |

The classes that come are the JDK's and the server's, loaded the first time something is used: each compiled trigger and function is a hidden class, and they're all unloaded. `every 1 tick` still runs 40 times in 2 seconds after them. Then `/sk disable all` leaves the listeners, Brigadier's tree and the tasks exactly as they were before any script loaded, and the scripts' classes are unloaded.

- **Bukkit's `/reload confirm`**, 8 times: each old NeoSkript is let go of, though the server keeps up to five for a few reloads (the harness bot's plugin isn't kept at all; what holds NeoSkript's for a while is still to be found). Fixed on the way: bStats' thread outlived the plugin (it's now shut down with it), and a potion effect or damage source made in a section stayed in the server thread's thread-locals, keeping the old plugin and all its classes for good. They're kept beside the threads now.
- **Stopping the server** with a trigger waiting and variables just changed: it stops by itself, nothing throws, and the variables are saved.
- **Also let go of:** each player's last click event, kept to run a two-handed click once, which kept every player who ever clicked after they left (now forgotten when they quit); the files `log ... to "x.log"` writes to, closed with the plugin once nothing scheduled can run, after which none opens; and worlds that were unloaded, which the time-of-day check kept. The last spawned, dropped and launched entities are kept until the next, as in Skript.

### Step 18: NeoSkript Placeholders, the first official addon

[Issue #14](https://github.com/xena-studios/neoskript/issues/14): PlaceholderAPI from scripts, in `addons/placeholders`, replacing skript-placeholders with its 1.7.1 syntax. How to use it is on [neoskript.dev](https://neoskript.dev/docs/addons/placeholders/).
- **Syntax:** the `placeholderapi [relational] placeholder with the prefix "..."` structure, `the placeholder`, `the prefix`, `the identifier`, `the result` (set to anything, deleted or reset), `the first player` and `the second player`, `player` and `event-offlineplayer` in it, and the value of any placeholder, plain or relational, for nobody, players or offline players. Its errors are skript-placeholders' own. Also 1.6's `placeholderapi request for the prefix` and `parse placeholder`. `mvdw placeholder` is skript-placeholders' error for MVdWPlaceholderAPI not being installed: MVdW's plugins don't run on current servers.
- **Registering:** a new API call, `Registrar.eventTriggers`, tells an addon which triggers of its event are loaded each time scripts load, reload or unload, and `LoadedTrigger.run` runs one on the calling thread. The addon keeps the triggers by prefix (one map lookup per request) and registers one expansion per prefix in use, on the global thread; a prefix no script uses any more is unregistered, so nothing is left behind. A request never runs unloaded code: the lookup switches before the expansions follow, and `LoadedTrigger.run` does nothing for a trigger whose script has unloaded.
- **Threads:** a request runs where PlaceholderAPI asks, any thread, and never waits for another. On Folia, NeoSkript's region rules apply: a read of a player another region owns is a runtime error naming the line.
- **Production grade:** a trigger that throws is passed over, a placeholder that asks for itself stops at 16 levels, and one request runs at most 256 triggers (two requests of itself each would be 2^16), another plugin's placeholder that throws gives nothing, and these warnings are written at most once a minute. Without PlaceholderAPI, the syntax stays and its lines say PlaceholderAPI isn't installed. Disabling the addon unregisters everything.
- **Core, found on the way:** syntax that only works in some events (`Registrar.onlyInEvents`, experimental until a second addon uses it) is tried after other readings, as Skript tries the next element when one fails to set up, so `the result` in a placeholder isn't the smelted item; the furnace event items use it. A `SyntaxError` from an event's filter is the line's error, not "couldn't compile". `on load` in several scripts runs in Skript's load order, which compile errors follow too: each folder's entries sorted, its subfolders first, then its own files, so `b/x.sk` before `a.sk` (it was a hash set's order, over full paths sorted). The `probes/load-order` differential probe checks it against Skript. Addons told of their triggers get them in that order, scripts added or enabled since included. `NeoSkript.text(value)` gave a value as a script's text shows it (since step 19, `NeoSkript.values().text(value)`, before the API was first published). skript-placeholders only soft-depends on Skript, so Paper loads it, and it disables itself: D21 now counts an original as running only if it's enabled.
- **Tested** by unit tests (reloads 50 times and to none, order, relational and plain, throwing triggers, recursion, registering off the global thread) and the `neoskript-placeholders` smoke scenario: the same scripts on Skript 2.17.0 with skript-placeholders 1.7.1 and on NeoSkript with the addon, both with PlaceholderAPI 2.12.3, all pinned by SHA-256, print the same 58 lines on Paper 26.3 (values, players online and offline, relational, the result's changes, colour codes, case, several placeholders and players, reloading to other prefixes and to none with PlaceholderAPI's list checked, and each error). On Folia 26.3, NeoSkript prints the same, except that reading a player from the console is a runtime error; PlaceholderAPI asking from the global thread is too. skript-placeholders' jar is in `plugins/` on NeoSkript, and the addon says to remove it.
- **Not done:** a script's structure with a bad line in it was left out whole on NeoSkript, so its prefix wasn't registered; Skript loads it without that line (fixed for every trigger in step 22). The Modrinth project waits for the owner (addons/README.md).

### Step 19: neoskript-yaml, YAML files with skript-yaml's syntax

The second official addon ([issue #17](https://github.com/xena-studios/neoskript/issues/17)), in `addons/yaml`: NeoSkriptYaml replaces [skript-yaml](https://github.com/Sashie/skript-yaml) 1.7.2, its latest release. The docs for server owners are at [neoskript.dev/docs/addons/yaml](https://neoskript.dev/docs/addons/yaml/).
- **Drop-in:** every pattern of skript-yaml's (load, unload, save, delete, ids and paths, values, lists, node keys, nodes, list indices, comments and headers, the conditions, `loadYaml`, and `loop-key`, `loop-node`, `loop-val`, `loop-list`, `loop-subnodekeys`, `loop-id`). Files are written as skript-yaml writes them, with the same SnakeYAML: the header, comments above root keys, one root key at a time with a blank line between, and Skript's values with skript-yaml's tags (`!location`, `!vector`, `!skripttimespan`, `!skriptdate`, `!skriptclass` with `variables.csv`'s bytes...).
- **Two additions to the addon API** it needed: `Registrar.loopValue`, a `loop-<name>` an addon gives loops over one of its expressions (it comes before a type of the same name in those loops, as `loop-node` does on Skript), and `NeoSkript.values()`: a value's type, its text, parsing text as a type, converters, Skript's saved form (`save`/`load`), and dates and timespans as `Instant` and `Duration`.
- **Saving:** a snapshot, numbered, and turned into text on the script's thread, then written by a small pool of threads, one write to a file at a time, to a temporary file that's flushed, moved over the old one in one step, and its folder synced. Only a file's newest save is written: an older snapshot that's turned into text last is dropped. Loading a file, checking it exists and deleting it write its waiting save first, on the calling thread, so scripts see their saves at once. Closing writes every waiting save on the closing thread without interrupting a write; saves after that (`on unload`) are written straight away. A symbolic link is written where it points.
- **Folia:** each file is changed, read and saved one step at a time under its own lock, and the store is shared by every thread. The syntax runs on whichever thread the trigger is on.
- **Production grade:** a file that isn't YAML, has a list at its top, holds itself, is over 32 MB, or holds more than a million values isn't loaded and isn't touched, and an id already loaded keeps what it had, but isn't saved over that file until it loads. Values are counted as SnakeYAML composes the file, before the nodes are held, so a 30 MB list of zeros is refused in a 512 MB heap, and counted again once aliases are followed (alias bombs); comment and header lines, kept apart from the text, count too. A save past either limit is refused, keeping the file, so what's saved always loads; a save counts values exactly as loading does. SnakeYAML's reader copies what it hasn't passed every 1,024 characters, so one long value took time growing with its square (8 MB 15 s, 29 MB 258 s, on the script's thread); a reader over the whole text reads it in 90 ms and 310 ms. Values that can't be read (a world that isn't loaded, a type no plugin has, an unknown tag) are kept and written back as they were. Relative paths stay inside the server's folder; `non-relative` ones are the script's choice.
- **What skript-yaml does wrong, fixed** (each in the docs' list): on Minecraft 26 it fails mid-save on locations, vectors, `!skriptclass` values and text with `&` (it reads 26.3 as a version before 1.13) and leaves the file empty; it loads broken files as empty and wipes them on the next save; a file ending in a comment fails to load; a comment right under the header is dropped; saving replaces values inside nodes and lists in memory; an entity type set isn't stored; `all yaml nodes` repeats paths; numbers from a file aren't removed from lists; only the first of several node keys is added; several paths in a condition fail; `loop-key` over nodes keeps a dot; `save ... to` with both options honours neither.
- **Tested** by unit tests (format round trips byte for byte, broken files, alias bombs, cycles, the saver writing whole files in order from 8 threads, a save that fails leaving the file as it was, paths outside the server) and two harness scenarios. `yaml-differential` (baseline, Paper 26.3) runs `scripts/yaml-differential.sk` on Skript 2.17.0 with skript-yaml 1.7.2 (pinned by SHA-256) and on NeoSkript with NeoSkriptYaml, and compares 115 output lines and 24 files byte for byte: 23 differ, each listed with why in `corpus/yaml/known-differences.txt` (an entry that no longer differs fails the scenario), most of them where skript-yaml fails on Minecraft 26 or Skript 2.17. What it can't save on 26.3 (locations, vectors, `!skriptclass` values, text with `&`) is also run on the reference Skript on a pinned Paper 1.21.11, where all 5 files match skript-yaml byte for byte with no allowance. skript-yaml on Skript 2.17 stores nothing for an entity type, on 1.21.11 too, so those can only be checked through `!skriptclass`'s game mode, and `variables.csv`'s bytes, which `neoskript-variables` checks against Skript. `neoskript-yaml` (smoke, every server) checks the fixes, the files it refuses, a 100,000-key file, one file saved by two regions' threads at once on Folia, `/sk reload`, and D21 with skript-yaml's jar in `plugins/`.
- **`delete all yaml from directory`** is a pausing effect (step 15): the trigger waits while the files are deleted off the server's threads, as skript-yaml's waits. skript-yaml's fails to load on Skript 2.17 (it extends `Delay`, which isn't a statement there any more).
- **Modrinth:** the project waits for the owner; what it needs is in [addons/README.md](../addons/README.md).

### Step 20: NeoSkriptWeb, HTTP requests and JSON

The third official addon, [issue #18](https://github.com/xena-studios/neoskript/issues/18), in `addons/web`. Docs at [neoskript.dev/docs/addons/web](https://neoskript.dev/docs/addons/web/).
- **Which originals:** Reqn 1.2.3, the classic HTTP addon (about 7,000 downloads, last updated 2018), all of it; and skJson 6.0.0, what most servers use now (about 50,000 downloads on GitHub, maintained), its requests and the core of its JSON. Both load on Skript 2.17.0 and Paper 26.3. Left for later, written down on the docs page: skJson's JSON files, caches, watchers, NBT, Minecraft objects as JSON, changing JSON in place, attachments; and receiving webhooks (SkriptWebAPI, about 650 downloads, has syntax for it).
- **Requests** are pausing effects (step 15) on the JDK's HTTP client, HTTP/1.1, on the addon's own threads: a timeout for the whole request (30 s), a connect timeout (10 s), a cap on the answer packed or unpacked (8 MB), read into one buffer per answer, and a budget all answers being read share (128 MB), redirects followed by the addon (5, never https to http, credentials not sent to another scheme, host or port), limits on requests in flight (64) and per minute (1200), and `block-private-addresses` (off, as scripts are staff's; documented). Only http and https. Disabling the addon fails what's running and stops its threads. Warnings are throttled to 10 every 10 seconds.
- **JSON** is Gson, which Paper ships and skJson uses, so it parses and prints as skJson's does. Nesting past 255 isn't parsed, and mapping into variables stops at 60 levels.
- **Core:** a `KeyAware` expression can return a map (nested indices), `recursiveKeys` hands it a list variable with every value under it (`Keyed`), and an `Update`'s map sets the indices it names: for `map {_json} to {_x::*}` and `{_x::*}'s formatted json`. `Registrar.priority` places an addon's syntax at its Skript priority level, after Skript's own, as Skript does: skJson's `keys of`, a simple expression, came after Skript's `key of %minecraft tag%`, a property. And `:text` in the middle of a pattern is now a tag, as in Skript (skJson's `[json] :values [%-string%] of`).
- **Fixed, not copied** (each a line in `known-differences.txt`, with what the original gave on Skript): Reqn lost the trigger's local variables after a request, looked headers up by case, and stopped the trigger with a stack trace for an address that was none; skJson's compact JSON was indented, numbers from JSON lost their fractions in arithmetic (1.5 + 1 was 2), `event-response` was none on Skript 2.17, `values of` gave nothing if the JSON held a null, headers with colons in their values were dropped, its own quoted-header example sent a header named with quotes, and gzip bodies weren't unpacked. With both installed on Skript, whichever loaded first took `body of`, `status of` and the rest, so the other's scripts got nothing; here one property serves both.
- **Tested** by unit tests (every pattern loads, JSON against skJson's results, the engine's limits, redirects, private addresses and closing, the config) and the `neoskript-web` smoke scenario on Paper and Folia 26.3: 133 lines the same as on Skript with the originals, 13 known differences, 21 checks of its own, the originals' jars refused and named (D21), and no connection left open.

### Step 21: Databases (D20)

Skript's `databases:` section, issue #9. In two pull requests: the section, routing and SQLite, then MySQL, `monitor changes` and the outage tests.
- **Settings:** a `databases` section in config.yml, Skript's options by name, in kebab case or Skript's own spelling (`monitor changes`), so a section can be pasted from config.sk. Types `csv` (and Skript's `file` and `flatfile`), `native`, `sqlite`, `mysql`, `disabled` (and `none`). config.yml version 3 moves `variables.file` and `variables.storage` into the `default` database. A database whose settings can't be used is kept with why, and still owns its pattern, so its variables aren't saved elsewhere by mistake (Skript drops it, and its variables go to the next database). The config package gained sections with the owner's own keys (`SectionCodec`), written back as the owner wrote them, comments and all.
- **Moving from Skript:** the move is marked done only once config.yml has Skript's settings, so a failed write is tried again at the next start. A server that moved earlier, whose config.sk has SQL databases that config.yml doesn't, is told once how to copy them. The first start copies the `databases:` section from config.sk, and every CSV and SQLite file it names in Skript's folder (with SQLite's journal), each backed up before its first load. A file outside Skript's folder, which servers may share, is used where it is.
- **Routing** (`Databases`): each variable goes to the first database whose pattern matches its whole name, as in Skript, `.*` and `.+` matching all. A name in two databases at startup is kept from the lower one, with Skript's warnings. One that belongs elsewhere moves, and the old database lets go only once the new one has saved it. A variable no pattern matches is dropped from files and kept in SQL, as in Skript, with its warning.
- **SQL storage** (`SqlVariableStorage`): JDBC directly, with Paper's and Folia's own drivers, checked on all four servers (sqlite-jdbc 3.47.0.0 and mysql-connector-j 9.1.0 on 1.21.4, 3.49.1.0 and 9.2.0 on 26.3): no SQLibrary and nothing added to the jar. Skript's exact `CREATE TABLE`, `REPLACE INTO` and `update_guid`; values in `variables.csv`'s binary format. One thread per database, never the server's: every second or so, what changed since the last write (as the native storage works it out) is written in one transaction. SQLite rowids are given explicitly so they only go up (SQLite reuses the highest after a `REPLACE`, so Skript's monitor can miss rows). Rows it can't read are kept byte for byte.
- **Monitor changes:** Skript's query on `rowid` and `update_guid`, every `monitor interval`. Each check writes this server's changes first, and skips a row for a name changed here since the last write, so a local change is never replaced by an older remote one (Skript writes each change at once). The rows of the last check are read again, as a transaction can commit after a later one, and none is applied twice. Deletions reach other servers (Skript's don't): a deletion is written as a row without a value, which Skript ignores, and such rows are removed later as Skript removes them.
- **Outages:** startup waits at most 10 seconds per database, on the database's own thread; past that it counts as down. A database that's down at startup isn't loaded or written; it's tried in the background, and when it answers it loads only the names it owns, never one another database loaded at startup, and its values win over what scripts set meanwhile, listed in the console. One that drops out holds writes with no queue (the next write works out the changes again), says so once and every 5 minutes, and catches up when it's back. Changes still waiting go to `pending/<database>-<id>.pending.csv` every 30 seconds while it's down and when the server stops, and are written at the next start; a database never reached keeps what scripts set for it in `.unloaded.csv` the same way. The id is a hash of the database's type, place and table, also recorded in the file, which isn't applied to another database, and a kept change is only applied over a row unchanged since this server last saw it (its `update_guid` and a hash of its value, recorded per name: rowids can't say, as SQLite reuses them and MySQL's aren't in commit order). What a never-reached database kept goes into memory at the next start if it's still unreached, so a late load never applies a snapshot over newer script writes; scripts find such a database's variables empty until it loads, unlike Skript, which waits for it. A row the database refuses is named and left out without holding up the rest. Databases close together at shutdown, about 20 seconds at most in all. A missing driver is said once and not retried. Two databases on one file or MySQL table are refused. Passwords never reach the console or `/sk info`.
- **Backups:** Skript's `backup interval` and `backups to keep` for file databases, SQLite through `VACUUM INTO`, named `<name>_<time>_scheduled.<extension>`. Only those are counted and removed; the backups kept for safety (lines that couldn't be read, recoveries, the copy from Skript, older versions' backups) never are. Skript removes every file in `backups/`. A server moving from Skript gets its config.sk values as is (Skript's default keeps every backup); only configs that never had a config.sk, new installs and NeoSkript's version 2 configs, get every 2 hours, keeping 12. `SkriptMigrationTest` checks this through the config loader. After the merge (found reviewing #41): `backup interval: 0`, only the text 0 as Skript checks it, is off and removes nothing, whatever `backups to keep` says; any other interval with `backups to keep: 0` removes them at start, as Skript's `backupPurge(file, 0)`; intervals are used as written, never raised to a minute, and one under a tick makes none, as Skript's task doesn't start. A backup holds no lock while it copies: a CSV save replaces the file in one step, so the channel opened first reads one whole file; the native file is copied up to its size under the header read under the lock; SQLite's `VACUUM INTO` stays on the database's thread, for a consistent copy, and the stop interrupts it, so the last save and the close never wait behind a backup of a big file. Each backup is written as its own name with `.copying.tmp` after and renamed once whole, and those left by a crash are removed at the next start by that exact name (not a bare `.tmp`, which may be an owner's own copy).
- **`/sk info`** shows each database's state.
- **Tested** by `SqlVariableStorageTest` (Skript's table exactly, a table Skript wrote with rows NeoSkript can't read, rowids, a locked file, a full disk, unreachable at startup, changes kept across a restart, a refused row, two storages monitoring one file), `DatabasesTest` (routing, conflicts, moves, a database that fails), `DatabaseSettingsTest` and `SkriptMigrationTest`. The `neoskript-sqlite-variables` smoke scenario has Skript 2.17.0 with SQLibrary 7.1 (pinned by SHA-256) and NeoSkript take turns on one SQLite file, items and locations included. The `neoskript-mysql-variables` baseline scenario (MariaDB 11.4 in Docker, skipped without Docker) does the same on MySQL, then runs two NeoSkript servers with `monitor changes`, a NeoSkript and a Skript server together, MariaDB killed mid-write and while the server stops, MariaDB paused (hung) at startup (about 11.6 s waited) and at shutdown (about 15.6 s to stop), a wrong password and then the right one, and rows it can't read.
- **Skript's MySQL storage on current Paper:** SQLibrary asks for `com.mysql.jdbc.jdbc2.optional.MysqlDataSource`, which Paper's driver (9.x) doesn't have, so it doesn't start. The harness gives the reference Skript the 5.1.49 driver, wrapped as a plugin.

### Step 22: Lines that fail, as in Skript

A trigger with one line that didn't load was dropped whole (#38). Skript 2.17.0 reports the line and loads the trigger without it. Probed on a real Skript server, then matched, and the cases are in the differential (`bad-lines`, `bad-structures`, `checks/bad-commands`):

- **A line** (an effect, a condition, or an expression in one) is left out on its own, and the code around it runs, waits included. The compiler compiles the code once to find every error, then again without the statements that failed, so nothing compiled refers to them: a local set only on a line that failed is just unset.
- **A section whose header fails** (`if`, `else if`, `loop`, `while`, an effect that takes a section) is left out with its code. A line that fails isn't what an `else` comes after, as in Skript's `ScriptLoader.loadItems`, so an `else` after a failed `else if` still belongs to its `if`, and a failed `else if` says "Can't understand this effect: else". A failed `while` says Skript's "Can't understand this condition: X", and `while`/`if` comparisons that fail read the whole line, as Skript's do.
- **An event line or a function header** that fails leaves out the whole trigger or function, and calls to that function say "The function f(integer) does not exist.", in text too.
- **Commands:** entries are read in Skript's order, and the first that fails stops the command: an unexpected entry before anything else, a bad `executable by` ("Invalid command sender type: x", at its entry) before the trigger and the usage are read. A `usage` or `cooldown message` whose code fails is reported at its entry and the command still loads; the deprecated `permission message` isn't read as code. Skript's "There is a cooldown ... set, but not a cooldown" warnings come for a cooldown that isn't a timespan too, and not for a cooldown message whose code fails.
- **Multiline `if` and filters:** one condition that fails fails the whole `if all:`/`if any:` (only the first is reported), so its `then:` and `else:` are errors too and neither runs, and a filter section is left out whole, as in Skript's `SecConditional` and `SecFilter`.
- **Many failing branches:** a failed `else if` doesn't stop the rest of its chain being compiled, so every branch's errors are found in one go and the code is compiled at most twice.
- **`return frob`** in a function gives Skript's "function f (simple event) is declared to return a number, but 'frob' is not of that type.", and a line that fails doesn't stop the code, so the line after it isn't unreachable.
- **`options:` and `variables:`:** a line that isn't an entry is Skript's "'x' is not an entry (like 'name : value')" (`=` for variables), and a default variable whose value or type doesn't parse is reported and left out, the others set. A script with no `options:` leaves `{@x}` as written, with no error.
- **An unreachable line that fails** gets only its error, not the unreachable warning too.
- **D12 changed:** a reload with errors now loads the new version without what has them, as Skript does, instead of keeping the old version. A script NeoSkript itself fails to compile part of (its bug: "NeoSkript couldn't compile this ... Please report it.") keeps the version that was running, and the error says so; `neoskript-reload` checks it with a test hook (`-Dneoskript.testing.compilerCrash`) that makes the compiler throw on one line. The reload message ends "What has errors is left out."

Still different: Skript reports a script's problems structure kind by kind (options and variables, then function headers, then functions, events and commands), NeoSkript in the order of their lines.

### Step 23: NeoSkript DB, SQL with skript-db's syntax

The fourth official addon, [issue #15](https://github.com/xena-studios/neoskript/issues/15), in `addons/db`. How to use it is on [its docs page](../website/src/content/docs/docs/addons/db.mdx).
- **The original:** skript-db 2.0.0 (the 4w3 fork on Hangar, the one that runs on Paper 26.x), read from its jar, plus the older forks' `synchronously`. Its syntax as it is: the database expression (lifetime, driver), `execute` with expressions as parameters, `with arguments`, `with batch arguments`, `generated keys`, the result by column and row, `unsafe`, `the last sql error` per trigger with the last of any as a fallback, and `transaction on %datasource%:`. Its load warnings and errors, word for word, including Skript's "Variables cannot be used here." for a statement that's a variable.
- **Core additions it needed:** `TemplateAware` and `TextTemplate` (experimental until a second addon uses them) (an element gets a quoted text's expressions apart, with their values, and `ElementInfo.argumentText`/`argumentElement` while loading); `Registrar.pausingSection` (a section that pauses before and after its code, experimental) with `PausingSection.LEAVE` and `abandoned`, which the runtime calls as soon as a trigger leaves the section without reaching its end (`PausingSections`, with the compiler telling it at each `stop`, `return`, failed condition and jump past the section, and the trigger's error handler); `PausingEffect.UNCHANGED` (store nothing, no runtime error); `KeyAware.recursiveKeys` for key-aware pausing effects; a stage's `Instant` and `Duration` as a date and a timespan. Addon jars now merge and relocate `META-INF/services`.
- **Threads:** statements run on the addon's threads and the trigger pauses (step 15's API), carrying on on its own thread; in an async event, the statement runs in place, as skript-db does. Fixed from skript-db: `quickly` carried on off the server's threads, and on Folia every trigger carried on on the global thread.
- **Production grade:** a database that's down fails the statement (the pool retries on the next one), a slow statement is stopped at `query-timeout` (30 seconds; skript-db has none, and SQLite's driver ignores one, so the addon cancels it itself, only while that statement still runs, so a cancel can't reach the connection's next one), a list gets at most `max-cells` values, rows times columns (skript-db reads everything), passwords are masked in errors as well as URLs and given to the pool apart from the URL, a database unused for `idle-close` closes its pool, a bad config value falls back alone.
- **Transactions:** a trigger leaving its section another way (stop, return, an error) rolls the transaction back at once (the new `PausingSection.abandoned`); one waiting inside its section keeps it open (skript-db rolls back after 30 idle seconds, then commits the rest one by one with no error); one rolled back from outside fails the trigger's later statements. `exit section` commits, as in skript-db, and `exit 1 conditional` is refused there, as Skript refuses it.
- **By run, not by event:** a transaction and `the last sql error` belong to the run that made them. skript-db (and NeoSkriptDb before review) keyed them by event, which every trigger of the event shares, and NeoSkript gave every function call from nowhere one context: one script's statements joined another's transaction and were rolled back with its failure, and two scripts couldn't each open one. Core now gives every trigger run and function call a context of its own at its start, the same object across its waits and pauses (`SimpleTriggerContext.run`, documented on `TriggerContext` as experimental), and pausing sections are tracked by it, so one trigger throwing no longer abandons another's open section.
- **Stopping:** the addon disables before NeoSkript, so it closes in `NeoSkript.whenStopped` (new; it returns a handle to cancel the task, which the addon uses when it's disabled on its own), after NeoSkript's `on unload` triggers, on a server stop, `/reload` or a plugin manager alike. Then running statements get 5 seconds, are cancelled, open transactions roll back (a statement that won't let go has its connection dropped within a second) and the pools close; it never hangs the thread disabling it. Its threads are daemons.
- **Libraries:** HikariCP 7.1.0, MariaDB Connector/J 3.5.11 and PostgreSQL 42.7.14, shaded and relocated (2.4 MB jar); MySQL's and SQLite's drivers and slf4j are Paper's.
- **Tested** by unit tests (`PartsTest`, `EngineTest` on a real SQLite file: the layout, counts, keys, injection attempts, bad SQL, max cells, arguments, batches, transactions committed, rolled back, left, broken and waited in, stopping with a statement running in a transaction, no password in HikariCP's or the drivers' logging, a database that's down without its password in the error, a statement given up on before it starts, a runaway query stopped at the timeout, idle pools, and no threads left after stopping), core tests in `PausingTest`, and the `neoskript-db` smoke scenario: on paper-26.3 the same script on Skript 2.17.0 with skript-db 2.0.0 and on NeoSkript match line for line, with the same load messages and tables; Folia and 1.21.4 are held to the same expected results, with NeoSkript-only checks: a transaction in a player's command (the player's region thread on Folia), and on Paper a `/reload` whose `on unload` write isn't lost.
- **Not done:** the comparison runs on SQLite only; MySQL against MariaDB in Docker is the next step, on the harness's MySQL setup from #31, which merged after this. Loading a `variables.csv` that skript-db wrote with a saved database isn't tested. The Limework fork's `on complete of sql query` isn't there (2.0.0 dropped it). The Modrinth project waits for the owner (addons/README.md says what it needs).

- **Tested** by `SqlVariableStorageTest` (Skript's table exactly, a table Skript wrote with rows NeoSkript can't read, rowids, a locked file, a full disk, unreachable at startup, changes kept across a restart, a refused row, two storages monitoring one file), `DatabasesTest` (routing, conflicts, moves, a database that fails), `DatabaseSettingsTest` and `SkriptMigrationTest`. The `neoskript-sqlite-variables` smoke scenario has Skript 2.17.0 with SQLibrary 7.1 (pinned by SHA-256) and NeoSkript take turns on one SQLite file, items and locations included.
### Step 24: NeoSkript Proxy, messages and proxy actions across a network

[Issue #19](https://github.com/xena-studios/neoskript/issues/19), in `addons/proxy`: NeoSkript's own network addon, which also takes the syntax networks use today. How to use it is on [its docs page](../website/src/content/docs/docs/addons/proxy.mdx).
- **The originals:** the issue names none (it's NeoSkript's own), so the most used were picked, by Modrinth downloads, each at its latest release, read from its jar: SkProxy 2.4 (1,428 downloads, the most used proxy addon), Skelocity 1.1 (the one current for Velocity; its release has a smaller syntax than its source, and the release is matched) and SkRedis 2.3.5 (the maintained successor of RediSkript, and the one for Redis that runs on 26.x). Not taken: skNetwork (needs a proxy plugin of its own), Skungee (dead for years) and skript-velocity (a scripting plugin for the proxy, not an addon).
- **Own syntax:** `send network message ... on channel ... to all servers` / `to the servers ...`, `on network message [on channel ...]` with its message, channel and sender, and questions to the proxy (`the network servers`, `the network player count [of server ...]`, `the network players [on server ...]`, `the network server of %offline player%`, `this server's network name`) as pausing expressions with a timeout. Sending a player elsewhere is Skript's own `connect`.
- **Transports:** plugin messaging (BungeeCord's channel, which Velocity speaks) carries questions, actions and messages through a player's connection; Redis pub/sub carries messages, in SkRedis's JSON, so a NeoSkript server and one still on SkRedis hear each other. The Redis client is NeoSkriptProxy's own RESP2 code (PUBLISH, PSUBSCRIBE, PING, AUTH, SELECT, TLS), with every reply bounded; Jedis would have meant shading five libraries.
- **Fixed from the originals:** SkProxy and Skelocity answer from what they heard last (the first answer is `0`, `Offline` or nothing); these wait for the current answer. SkProxy throws with nobody online. SkRedis runs its triggers off the server's threads and blocks the main thread on reads. Skelocity's unreleased source waits forever for an answer that doesn't come.
- **Production grade:** with nobody online, questions are refused at once with a runtime error and messages wait for the next player (bounded, a minute each, or dropped if configured). Messages over plugin messaging are signed with the network's `secret` (HMAC-SHA256) and refused if forged, replayed or over a minute old; answers are only taken for a question waiting. Everything is bounded: message size, the queues, questions waiting, messages waiting for the server thread. Redis going down queues or drops messages and reconnects with a growing pause; the console says when it goes and comes back. On disable, plugin messaging stops at once (Bukkit sends nothing for a disabled plugin), and Redis stays until NeoSkript has run its `on unload` triggers.
- **Tested** by unit tests (questions, coalescing, unsolicited answers, timeouts, the queue, the envelope's forgeries, tampering, replays and ages, the RESP client's limits, and Redis round trips and an outage against a small in-test Redis) and the `neoskript-proxy` baseline scenario, on a real network: Velocity 4.3.1 in front of a Skript server with the three originals and two NeoSkript servers (on Paper or Folia), with Redis in Docker. The originals' and NeoSkriptProxy's outputs match; Redis messages cross between SkRedis and NeoSkriptProxy both ways; a signed message from one NeoSkript server reaches the other through the proxy; questions with nobody online are refused, a message waits for a player, and one waits through Redis stopping and starting; D21 with SkProxy's jar.
- **Not done:** network-wide variables (`{network::...}`). The design: Redis owns each variable (one key per variable, holding Skript's saved bytes from `values().save`), every write is one atomic Redis command (`SET`, `INCRBYFLOAT`, a small script for list changes) and is published, each server keeps a bounded local copy updated from those publications and reloads it after a reconnect, so a read never waits and a write is never lost to a race. It needs NeoSkript's variable store to hand a prefix of variables to an addon: #30's `databases:` section routes variables to storages by pattern inside NeoSkript, and opening that to addons is the next step. Also not done: SkRedis's storage syntax (strings, hashes, lists, `exists`) and its `run async` section, and Skelocity's unreleased `fetch ... and store it in` effects.
- **Modrinth:** waits for the owner; what the project needs is in [addons/README.md](../addons/README.md).

### Step 25: NeoSkript Reflect, Java from scripts with skript-reflect's syntax

The sixth official addon, [issue #13](https://github.com/xena-studios/neoskript/issues/13), in `addons/reflect`: NeoSkriptReflect replaces [skript-reflect](https://github.com/SkriptLang/skript-reflect) 2.6.3, its latest release, once called skript-mirror (D21 counts either name). Docs at [neoskript.dev/docs/addons/reflect](https://neoskript.dev/docs/addons/reflect/). This is its first part: what most scripts use. Custom syntax, sections, proxies and the rest come in the next pull requests.
- **Drop-in:** `import:` (each script's own, aliases with `as`, and skript-reflect's messages for a class that doesn't exist, a bad line, a needless alias and a name already taken), method and field calls on imported classes and on any value, `new`, descriptors (`.[Class]name[int, int](...)`), dynamic names (`..%text%`), `try`, setting fields, `null`, `last java error`, classes by name, `'s class`, `X.class` and `int.class`, arrays (`new int[3]`, `{_a}[i]` read and set, `[...]` and `[... as Class]`), spreading (`...{_list}`), `is an instance of`, members and member names, plugin instances, `event`, and triggers for any Bukkit event by its imported class (`on ServerCommandEvent:`, several with `or`). Arguments and results convert as skript-reflect's do: numbers to any numeric parameter, numeric arrays, a one-letter text to a `char`, a class to `Class`, varargs gathered; Java's whole numbers come back as scripts' whole numbers and arrays as one value. A missing method, a wrong overload, null and a method that throws give no value and skript-reflect's warning for the line, with its "Did you misspell the method?" and "Did you pass the wrong parameters?" suggestions, and the trigger carries on.
- **Compiled calls:** each class's members are looked up once and kept with the class (a `ClassValue`, let go of when the addon disables), and each call site keeps its overload choice for the last four combinations of target and argument classes, checked against the values each time, as some conversions depend on the value (a text is a `char` only if it's one letter). The member is called through a method handle adapted to `(Object[])Object` with `invokeExact`, one array a call; skript-reflect re-resolves every call and uses `invokeWithArguments`. `set {_size} to {_list}.size()` in a loop, the fastest of five rounds less the fastest of the same loop without the call, on Paper 26.3 on the same machine: 33 to 120 ns a call against 270 to 1,000 ns for skript-reflect; over six collection classes in turn, about 60 ns against about 200 ns. The harness prints all four. Where overloads tie, the closest is taken rather than whichever Java lists first, so `Math.max(3, 7)` is `long` and prints `7`. Numbers keep their Java type, as in skript-reflect (`{_set}.contains({_list}.size())` finds an `Integer`); NeoSkript's arithmetic, comparisons and texts take any of Java's numbers, and `integer`, `short`, `byte` and `float` are now saved in variables as Skript saves them.
- **Folia:** calls run on the trigger's thread, `last java error` is each run's own, functions it calls included (`TriggerContext.outermostRun()`, new, over #42's per-call contexts), held weakly by the run's context so it goes when the trigger ends, and the call sites are safe to share between regions. The docs say plainly that Java calls can break Folia's region rules, which NeoSkript can't check inside Java code.
- **Reloads:** no class loaders of its own: classes come through the addon's plugin class loader, as skript-reflect's came through Skript's. Imports are read again on each load into new maps; each call keeps its own script's map from when it was set up, so a trigger still running from before a reload keeps its imports. A call site's cache lives in the script's compiled code, so `/sk reload` leaves nothing behind.
- **Security:** nothing beyond what skript-reflect allows, and nothing less: scripts get full Java access, with `setAccessible` where Java's modules allow it. The docs and the Modrinth description say so up front.
- **Core, for it:** an experimental `Registrar.structure` (a top-level block like `import:`, given to the addon's handler as each script loads, before any of the script's triggers are parsed wherever it is in the file, with errors and warnings on its lines), `NeoSkript.scriptBeingLoaded()` for literal parsers and factories, `Registrar.argumentsLikeFunctions` (no "missing 'and' or 'or'" warning for `Math.max(3, 7)`, as Skript gives none), `Registrar.strictType` (only the type itself where it goes, as in Skript, so `Location` in `new Location(...)` is the import, not the event's location; its words count as known where any value goes, so `send "%Math.PI%" to console` is the call and `to console`, not one unparsed literal) and `NeoSkript.runtimeWarning`/`runtimeError`. An addon can only make its own types strict. An element in an `if` header, and an event's filter, now get their text (`ElementInfo.text()`), which skript-reflect's descriptors and event classes are read from.
- **Parse order:** calls are tried after all of Skript's own syntax (`Registrar.priority`, `PATTERN_MATCHES_EVERYTHING`), as skript-reflect's `SHADOW_REALM`, so `{_n} + {_l}.size()` and `{_n} = {_l}.size()` are Skript's arithmetic and comparison around a call. A static method that isn't there (`Math.mux(1, 2)`) is skript-reflect's "mux refers to a non-existent method" at load. As skript-reflect turns on Skript's `disable variable missing and/or warnings`, the addon turns that warning off (`Registrar.noMissingAndOrWarnings()`, new).
- **Fixed, not copied** (each in the docs' list): the closest overload; `EnderDragon.Phase.LEAVE_PORTAL` is the nested class (skript-reflect gave none); where nothing else fits a parameter, NeoSkript's own values convert (a timespan to `Duration`, an item type to `ItemStack`); `last java error` per run; a class whose members can't be looked at (a signature naming a class that isn't there) is a warning for the line, not a stop; an event class with no handler list (`PlayerEvent`) is an error on its line, not a listener that fails at startup.
- **Tested** by unit tests (calls, fields, overloads and the call site's cache, a cached member checked against each value, Java's numbers keeping their type and saved as Skript saves them, a call on six classes looking each up once, a class whose members can't be looked at, warnings indented line by line, the last error per run and let go of, a trigger from before a reload keeping its imports, failures and their messages, imports per script and after the trigger that uses them, arrays, collect, spread, instance of, calls in texts, class names that are also Skript types, Java events, conversions, the strict type guard in `Addons`) and the `neoskript-reflect` smoke scenario: `scripts/reflect-check.sk` on Skript 2.17.0 with skript-reflect 2.6.3 (pinned by SHA-256) and on NeoSkript with NeoSkriptReflect, 35 lines compared on Paper 26.3, one known difference (`nested`); on Folia 26.3 and 1.21.4 NeoSkript's lines are checked against skript-reflect's, with no thread violations. It also checks the missing-method warning and times the calls on both.
- **Not done**, listed on the docs page and in the issue: custom syntax (`effect`, `expression`, `condition`, `event`, `property` structures, `parse`, `get`, `patterns`, `usable in`, `continue`, `expression-N`), computed options, sections and `run section`, proxies, function references, `local`, `bits of`, the `import` effect for effect commands, external libraries, `with priority` and `all` on Java events, `raw`, and deferred parsing (`$`). A script's own values are NeoSkript's classes, not Skript's, so calls to Skript's own Java classes (`ch.njol.skript...`) can't work. The Modrinth project waits for the owner (addons/README.md).

### Step 26: Skript addons on NeoSkript, the core and SkBee's first slice

The compatibility layer for Skript addons (design in [SKRIPT-ADDONS.md](SKRIPT-ADDONS.md), D22-D24, coverage in [issue #45](https://github.com/xena-studios/neoskript/issues/45)), in `neoskript-compat`: NeoSkriptCompat, an opt-in plugin whose main class is `ch.njol.skript.Skript` and which provides `Skript`, so addons find it as Skript. The docs for server owners are at [neoskript.dev/docs/addons/skript-addons](https://neoskript.dev/docs/addons/skript-addons/).
- **D10 by name:** NeoSkript stands down only for the plugin really called Skript, not for one that provides it.
- **Skript's API:** the classes addons need to work are written by hand (the element bases, `SkriptAddon`, `AddonModule`, `SyntaxRegistry`, `SyntaxInfo`, `ClassInfo`, `Classes`, `Converters`, `Comparators`, `EventValue`, `DefaultFunction`, `ParserInstance`, `EntryValidator`...); the rest of Skript 2.17.0's API that SkBee and skript-yaml reach is generated at build time from a listing of names and signatures (`src/stubs/skript-2.17.0.api`, no code), each method throwing to say it isn't there yet. A build step adds the members of Skript's that a hand-written class doesn't declare, so addons link against the whole API.
- **Registration:** what an addon registers is turned into NeoSkript's syntax through one `Registrar` per addon, at the place NeoSkript's standard library marks for addons (the compiler's only change: syntax and types inserted there keep the order they'd have had; addons' literal parsers, named types, event values and event names now go after the standard library's). Each element gets its Skript priority level (`Registrar.priority`), so NeoSkript tries it after its own syntax of that level, as Skript does. With NeoSkriptCompat and SkBee installed, 857 of Skript's 862 tests pass (`neoskript-skript-suite-skbee`, which also runs Skript 2.17 with SkBee and fails on any test that passes there but not here); the other 5 don't pass on Skript with SkBee either, as SkBee's syntax takes those lines there too. Types NeoSkript has already stay NeoSkript's; conversions, comparisons and event values go only where they involve a class NeoSkript has no type for, so NeoSkript's own types behave as before. Each item goes on its own: one that fails is logged and left out.
- **Elements:** an addon's expression, effect or condition is made and set up (`init`) when the line compiles, with its arguments as expressions that give the values NeoSkript worked out, as the classes the addon asks for (`ItemType` and `EntityData` wrap NeoSkript's, D24), and the results come back as NeoSkript's. Changers are honoured.
- **SkBee 3.26.0** enables with all its modules: 78 types, 258 expressions, 77 effects, 31 conditions and 17 functions on NeoSkript. Its 60 sections, 69 events and 2 structures aren't supported yet, and the console says so.
- **Tested** by unit tests and the `skbee-differential` scenario (baseline, Paper 26.3): `scripts/skbee-differential.sk` on Skript 2.17.0 + SkBee 3.26.0 and on NeoSkript + NeoSkriptCompat + SkBee 3.26.0 (pinned by SHA-256), 26 lines (world heights, auto-save, game ticks, chunk coordinates and keys, namespaced and translation keys, `server is paused`), all the same.
- **Skript too:** with Skript's jar next to NeoSkriptCompat, Paper loads only one plugin called Skript. Whichever it is, exactly one enables: NeoSkriptCompat explains Paper's "Ambiguous plugin name" error, or NeoSkript stands down for Skript (D10). `neoskript-skript-conflict` runs both orders of the jars' names.
- **Not done:** `init` at parse time and arguments evaluated when the addon asks (the design's way), sections, events and structures, saving addon types' values, type properties.
