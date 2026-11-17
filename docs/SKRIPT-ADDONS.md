# Skript addons on NeoSkript: the compatibility layer

The design for loading real Skript addons (SkBee first) on NeoSkript: [PLAN.md](PLAN.md) D1, D22 and §3.6, the 1.x
row. Nothing here is built yet; this is what the PRs after this one build, slice by slice, each compared with
Skript 2.17.0 and the real addon in the harness. Coverage is tracked in its own issue.

## What it's for

A Skript addon is a plugin built against Skript's own classes (`ch.njol.skript.*`, and since 2.10 the
`org.skriptlang.skript.*` API). NeoSkript doesn't have them, so today an addon fails as Paper enables it. The layer
supplies those classes, backed by NeoSkript, so an addon's jar runs unchanged and scripts use its syntax as they do on
Skript. What doesn't change:

- **Scripts that don't use addons** behave exactly as now: the differential stays at 100%, and Skript's suite at
  862/862. Nothing of the layer is loaded unless an addon is installed with it.
- **No cost for scripts without addon syntax:** the compiler only takes a different path for elements an addon
  registered through the layer.
- **Production grade:** an addon that throws while it registers, in `init`, or while a script runs, doesn't take
  NeoSkript down; it's an error naming the addon and the line, as an official addon's would be. Reloading scripts
  doesn't keep an addon's elements or classes alive.

## What SkBee uses

[`neoskript-cli addon <jar>`](#the-scanner) lists what any addon's jar uses of Skript, from its bytecode. SkBee 3.26.0
(the latest stable release, for Skript 2.15.0 and later and Minecraft 26.1 and later, sha256 `68c0bc5a…`) uses
**156 of Skript's classes, through 795 members, in 677 of its 1,029 classes**. The full list, with every member and
how often it's used, is in [compat/skbee-3.26.0.txt](compat/skbee-3.26.0.txt) (and skript-yaml 1.7.2's in
[compat/skript-yaml-1.7.2.txt](compat/skript-yaml-1.7.2.txt)). The most used:

| Classes using it | Skript's class | What for |
|---:|---|---|
| 420 | `lang.Expression` | arguments: `getSingle`, `getArray`, `getAll`, `toString(event, debug)`, `isSingle`, `check`, `change` |
| 398 | `lang.SkriptParser$ParseResult` | `hasTag`, `mark`, `expr` in `init` |
| 384 | `util.Kleenean` | `init`'s delay state |
| 199 | `classes.Changer$ChangeMode` | `acceptChange` and `change` |
| 142 | `Skript` | `error` and `warning` while loading, `methodExists`/`classExists` checks, version checks |
| 133 | `util.coll.CollectionUtils` | small array helpers |
| 109 | `lang.util.SimpleExpression` | the base of most expressions: `init`, `get`, `isSingle`, `getReturnType`, `toString`, changers |
| 84 | `expressions.base.SimplePropertyExpression` | `the <property> of %things%` |
| 80 | `aliases.ItemType` | Skript's item values, in and out |
| 74 | `lang.TriggerItem` | `walk`: sections running the code under them |
| 71 | `lang.parser.ParserInstance` | `isCurrentEvent`, `getCurrentEvents`, `getHasDelayBefore`, the current node, structure and sections |
| 70 | `config.SectionNode` | sections and structures reading the code under them |
| 59, 54 | `lang.entry.EntryValidator`, `EntryContainer` | structures with entries |
| 56 | `registrations.Classes` | `getExactClassInfo`, `toString`, `registerClass` |
| 53 | `lang.converter.Converter` | converters |
| 52, 23, 14, 16 | `lang.Effect`, `Condition`, `Section`, `SkriptEvent` | the other element bases |
| 35 | `util.Timespan` | timespan values |
| 31 | `variables.Variables` | reading and writing variables |

SkBee registers through Skript 2.10's registration API, not `Skript.registerX`: `SyntaxRegistry.register(key,
SyntaxInfo)` with `SyntaxInfo` and `BukkitSyntaxInfos.Event` builders, `AddonModule`, and
`org.skriptlang.skript.addon.SkriptAddon`, behind its own `api.registration` package. Older addons (skript-yaml 1.7.2:
49 classes) use `Skript.registerEffect` and the like, `@Name`/`@Description` annotations, and `Delay`. The layer covers
both.

SkBee also names some of Skript's own elements and internals: `EffSecSpawn$SpawnEvent`, `CondCompare`,
`ScriptLoader`, `SkriptConfig`, `test.runner.TestMode`, `variables.FlatFileStorage`. Each needs a stand-in that says
what NeoSkript's equivalent would, or the code using it is left to fail where it's used.

## Decisions

### D22: an opt-in compat plugin that provides Skript

The layer ships as its own plugin, **NeoSkriptCompat**, built in this repository (`neoskript-compat`) and released with
NeoSkript at the same version. Its `plugin.yml`:

```yaml
name: NeoSkriptCompat
main: ch.njol.skript.Skript
provides: [Skript]
depend: [NeoSkript]
folia-supported: true
```

Why not inside NeoSkript's own jar, as `provides: [Skript]` in NeoSkript's `plugin.yml`? Tried on Paper 26.3
([experiment](#the-experiment)): it does satisfy SkBee's required `Skript` dependency, but with Skript's own jar also
in `plugins/`, Paper reports "Ambiguous plugin name 'Skript'" and **doesn't load Skript**. That breaks D10, where
Skript keeps working and NeoSkript steps aside. A separate plugin keeps D10 as it is for everyone who doesn't install
it, and makes standing in for Skript a choice the server owner makes.

- **Addons find "Skript":** Paper resolves `depend: [Skript]` (and paper-plugin.yml's `dependencies: server: Skript`)
  to NeoSkriptCompat, enables it after NeoSkript, and the addon after it, with NeoSkriptCompat's classes visible to the
  addon's class loader. `getPluginManager().getPlugin("Skript")` returns it.
- **`ch.njol.skript.Skript` is the plugin's main class**, so `Skript.getInstance()` and casting `getPlugin("Skript")`
  to `Skript` work, and tasks addons schedule on it belong to a real plugin.
- **D10 with NeoSkriptCompat installed:** NeoSkript's check becomes "a plugin *named* Skript is loaded", not
  `getPlugin("Skript") != null`, which returns NeoSkriptCompat. If Skript's jar is there too, Paper loads only one of
  the two "Skript"s, by file order (in the experiment, the jar whose name sorts first). If Skript wins,
  NeoSkriptCompat is refused, NeoSkript sees Skript and steps aside as today, and Skript works. If NeoSkriptCompat
  wins, Skript isn't loaded, so NeoSkript runs, and NeoSkriptCompat finds Skript's jar in `plugins/` (by its
  plugin.yml, as D21's check does) and says so clearly: Paper skipped Skript because NeoSkriptCompat stands in for
  it; remove one. Never both.
- **D21:** NeoSkriptCompat skips an addon an installed official addon replaces (skript-yaml with NeoSkriptYaml): the
  official one wins by default.
- **Plugins that hook into Skript if it's there** (a soft dependency, with their own Skript classes) will see
  "Skript" when NeoSkriptCompat is installed. Their hooks run through the layer like any addon's; one that fails
  fails like an addon's, without taking the plugin down where the layer can catch it. This is another reason the
  layer is opt-in.

### D23: an addon's `init` runs while the line is parsed

Skript calls `SyntaxElement.init(Expression[], int, Kleenean, ParseResult)` while it parses a line, and an element
that returns `false` (often after `Skript.error`) isn't a match, so Skript tries the next one. Its return type and
plurality after `init` decide how the rest of the line is typed. So for elements an addon registered through the
layer, `init` runs during parsing, through the frontend's parse checks, with the arguments it parsed as
not-yet-evaluated expressions. `false` means "not this element"; an error it logged becomes the line's error if
nothing else matches, as on Skript. The initialised element is kept for the compiler. `init` running once per match
attempt, side effects and all, is what Skript does too.

### D24: values cross between NeoSkript and the addon through adapters

Most values are the same objects on both sides: Bukkit's (players, locations, items as `ItemStack`, worlds), Java's
(text, numbers, booleans). Skript's own value classes aren't: an addon expects `ch.njol.skript.aliases.ItemType`,
`util.Timespan`, `util.Date`, `util.Direction`, `util.SkriptColor`, `util.Time`, `util.slot.Slot`,
`entity.EntityData`, `util.Experience`, `util.GameruleValue`, where NeoSkript has its own. The layer's versions of
those classes wrap NeoSkript's values, and are unwrapped when they come back: an argument's `getSingle` gives the
addon Skript's class, and what its expression returns becomes NeoSkript's. NeoSkript's own classes don't change.

## How Skript's API maps onto NeoSkript

NeoSkriptCompat is part of this repository and released at NeoSkript's version, so it uses NeoSkript's compiler and
runtime directly (`@Internal`), not only the public addon API. The public API stays as it is.

| Skript | NeoSkript |
|---|---|
| `Skript.registerEffect/Condition/Expression/Section/Event/Structure`, `SyntaxRegistry.register`, `SyntaxInfo`, `BukkitSyntaxInfos.Event`, `AddonModule`, `SkriptAddon` | Collected while addons enable (`isAcceptRegistrations` until NeoSkript registers syntax, on the first tick), then registered through one `Registrar` per addon, with the addon's name, so `/sk info` and the syntax export name it. Patterns are Skript's pattern language, which NeoSkript reads already. Element ids: the addon's class name. |
| `SyntaxElement.init(Expression[], int, Kleenean, ParseResult)` | Run while parsing (D23). `Expression[]` are the parsed arguments, not evaluated; `int` is the pattern; `Kleenean` comes from the parser's delay state; `ParseResult` has the marks, tags, `expr` and regexes NeoSkript's match has. |
| `Expression.getSingle/getArray/getAll(Event)` on an argument | The compiler compiles each argument of a layer element into a small method of its own, which the argument's `Expression` calls with the running trigger's frame. The `Event` passed is the trigger's; the frame is found from it through the call that's running (a stack per thread, set around every call into the addon). |
| `Expression.change(Event, Object[], ChangeMode)`, `acceptChange` on an argument | The argument's own change path, compiled the same way: a variable, NeoSkript's changeable expressions, a list. |
| `SimpleExpression`, `PropertyExpression`, `SimplePropertyExpression`, `EventValueExpression`, `Condition`, `PropertyCondition`, `Effect` | Each a call-through node: the compiler calls `get`, `check` or `execute` on the initialised element. `isSingle` and `getReturnType` after `init` type the line. `getConvertedExpression` is honoured where NeoSkript converts. |
| `Section`, `EffectSection`, `ExpressionSection`, `SectionExpression`, `TriggerItem.walk`, `loadCode(SectionNode)` | The code under a layer section is compiled by NeoSkript as a section body, and handed to the addon as a `TriggerItem` whose `walk` runs it on the same thread, with the same frame. A body that waits isn't supported at first (it's an error when the script loads, naming the line). |
| `SkriptEvent`, `SimpleEvent`, `init(Literal[], int, ParseResult)`, `check(Event)` | A server event with the addon's event classes, `check` as its filter. |
| `Structure`, `EntryValidator`, `EntryContainer` | A later slice: NeoSkript's structure loading, with the addon's entries checked by its validator. |
| `ClassInfo` (`parser`, `serializer`, `changer`, `cloner`, `defaultExpression`, `supplier`, `user` patterns), `Classes` | `Registrar.type`, `parser`, `literalParser`, `storage` (Skript's `Serializer` writes `Fields`, which NeoSkript's codecs read and write as Skript's bytes), `changer`, `cloner`, `allValues`, `text`. `Classes.getExactClassInfo` and `toString` answer from NeoSkript's registry. |
| `Converters`, `Comparators`, `Arithmetics` | `Registrar.converter` (with Skript's flags), `comparison`, `arithmetic`; `Converters.convert` and `Comparators.compare` run NeoSkript's. |
| `EventValues`, `EventValueRegistry`, `EventValue.builder` | `Registrar.eventValue` with time states. |
| `Functions.register`, `DefaultFunction` | `Registrar.function`. |
| `ParserInstance`: `isCurrentEvent`, `getCurrentEvents`, `getHasDelayBefore`, `getNode`, `getCurrentStructure`, `getCurrentSections` | Answered from the parse that's running (`ElementInfo`'s events, the delay state, the line). |
| `Variables.getVariable/setVariable` | NeoSkript's variable store, local variables through the running frame. |
| `Skript.error/warning`, `SkriptLogger` | The line's error or warning while parsing; the console, naming the addon, otherwise. |
| `Skript.getVersion` | 2.17.0, the Skript NeoSkript matches (D9), so version checks pass. `getMinecraftVersion` and `isRunningMinecraft` are the server's. |
| `methodExists`, `classExists`, `fieldExists` | As Skript's: reflection. |
| `Timespan`, `Date`, `Direction`, `ItemType`, `Slot`, `EntityData`... | The layer's classes, wrapping NeoSkript's values (D24). |

### What the compiler needs

NeoSkript elements get values; Skript's get expressions. For layer elements only:

1. **Arguments as expressions:** each argument compiled into a method of its own (on the trigger's hidden class), with
   its type, plurality, and/or-list, literal value (a `Literal` with `getSingle` at parse time) and text, so the
   layer's `Expression` can answer `isSingle`, `getReturnType`, `getAnd`, `isDefault`, `toString` and be evaluated
   when the addon asks, as many times as it asks, on the trigger's thread.
2. **Changing an argument:** the same for its change path, and its `acceptChange`.
3. **Parse-time `init`** (D23), with `ParserInstance` filled in, and the initialised element's return type and
   plurality used to type the line.
4. **Sections:** the body as a `TriggerItem` (see the table).

PR #4 listed 1 and 2, and the current events (here, `ParserInstance`), as what the API was missing for this. 3 and 4
are new. All four stay internal to NeoSkript and NeoSkriptCompat rather than becoming public API.

### Threads and Folia

An addon's element runs on the trigger's thread, as NeoSkript's own do. On Folia, elements are treated as touching
what they're given (`OWNER`), the default for addon syntax; an addon written for Folia (SkBee says
`folia-supported: true`) does its own scheduling. Tasks an addon schedules on `Skript.getInstance()` belong to
NeoSkriptCompat.

### Reloads and failures

- A script's layer elements belong to its compiled triggers; a reload drops them with the triggers. The layer's
  per-thread frame stack is set and cleared around each call, in `finally`, so nothing of a trigger stays reachable
  from it. A soak test (reloading a script that uses SkBee's syntax hundreds of times) checks the heap and the addon's
  class loader stay level.
- An exception from an addon's `init` is a parse error on that line naming the addon; from `get`, `check`,
  `execute`, `change` or `walk`, a runtime error naming the addon and the line, after which the trigger carries on
  as NeoSkript's other runtime errors do. An addon that fails while it registers keeps what it registered before,
  and the console names it.
- A missing class of Skript's (something the layer doesn't have yet) is a `NoClassDefFoundError` where the addon uses
  it: caught the same way, and named in the error, so it's clear what's missing.

## Plan

1. **This PR:** the design, and the scanner.
2. **The core and the first slice:** NeoSkriptCompat with SkBee's start-up (its version checks, its registration
   path, `SkriptAddon`, `AddonModule`), the D10 check by name, parse-time `init`, arguments as expressions, the
   element bases, `ClassInfo`, and **SkBee's bossbars** (`elements/bossbar`: a type with a parser and serializer,
   expressions with changers, ids). SkBee's own `config.yml` turns its other element modules off, so the slice only
   needs what bossbars use. A harness scenario runs a script using them on Skript 2.17.0 + SkBee 3.26.0 and on
   NeoSkript + NeoSkriptCompat + SkBee 3.26.0 (both pinned by SHA-256), comparing the output.
3. **Slice by slice:** text components, NBT, scoreboards, item components, recipes, bounds, structures and the rest,
   most used first, each compared with Skript + SkBee, until SkBee's commonly used syntax works. Then skript-yaml's
   addons that aren't replaced by an official addon: SkQuery first. skript-reflect, which reaches deepest into Skript
   and may never work fully, is replaced by NeoSkriptReflect (issue #13) instead.

The docs for server owners say what works, by addon and by slice, and nothing more.

## The scanner

`neoskript-cli addon <addon.jar> [--members] [--json <file>]` reads an addon jar's bytecode (nothing in it runs) and
lists every class of Skript's it extends, implements, calls, reads, creates, casts to, or names in a signature or an
annotation, most used first, with each member and which of the addon's classes use it. The coverage issue compares it
with what NeoSkriptCompat has.

To regenerate the list here: `java -jar neoskript-cli.jar addon SkBee-3.26.0.jar --members > docs/compat/skbee-3.26.0.txt`.

## The experiment

On Paper 26.3 (build 210), with SkBee 3.26.0 in `plugins/`:

| Installed | Paper | Then |
|---|---|---|
| NeoSkript with `provides: [Skript]` | loads SkBee after NeoSkript | SkBee fails on the missing `ch.njol.skript.util.Version`, as expected without the layer |
| the same, and Skript 2.17.0 | "Ambiguous plugin name 'Skript'"; **Skript isn't loaded** | D10 broken: this is why the layer isn't in NeoSkript's jar |
| NeoSkript, and a stand-in NeoSkriptCompat with `provides: [Skript]`, `main: ch.njol.skript.Skript` | loads NeoSkriptCompat, then SkBee | today's D10 check sees `getPlugin("Skript")` (the stand-in) and disables NeoSkript: the check has to be by name |
| the same, and Skript 2.17.0 | "Ambiguous plugin name 'Skript'"; Skript isn't loaded, by file order | handled as D22 says |
