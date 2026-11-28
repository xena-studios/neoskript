# Opt-in language extensions: design

**Status: the opt-in is in; which extensions to build is decided later.** This sets out how a script opts into syntax
Skript doesn't have, what that promises, and which extensions could come first. Only the opt-in mechanism exists; no
extension does. The owner merged it as it is on 2026-11-27 (section 4); choosing which extensions to build, if any,
is the owner's call, later.

PLAN.md's non-goals say new language features that break Skript syntax are out of scope for 1.0, and that "any
extensions are opt-in and come later" (the 1.x row). This is that later.

## 1. The opt-in

### Per script: `using neoskript <name>`

```
using neoskript match sections

on chat:
	match message:
		...
```

A script asks for an extension with Skript's own `using` structure, the one Skript's experiments use
(`using script reflection`, `using queues`). The name is `neoskript` and then the extension's name.

Why this way:
- **It's Skript's syntax already.** Every tool that reads Skript reads it, and it says what it does in plain words.
  Skript scripts already use `using` for features that aren't settled yet.
- **Skript says the script isn't for it.** On Skript 2.17, `using neoskript match sections` gives "The experimental
  feature 'neoskript match sections' was not found.", and each line of the extension's syntax is an error there.
  Nobody runs such a script on Skript by mistake without being told.
- **It's per script, written in the file.** What a script means is in the script, so the CLI, the editors, the
  in-browser parser and another server all read it the same way. Nothing outside the file changes what it means.
- **The prefix can't clash with Skript.** An experiment of Skript's would have to be called `neoskript …`.

What NeoSkript does with it (implemented):
- An extension is the syntax elements registered as experimental with the experiment `neoskript <name>`, the
  machinery Skript's experiments already use. `SyntaxRegistry.languageExtensions()` lists the extensions there are.
- A script that asks for an extension that doesn't exist gets an error on its `using` line: "NeoSkript has no
  language extension called 'x'". That's a typo, or a script written for a newer NeoSkript; either way, its syntax
  won't be there.
- `ParsedScript.extensions()` says which extensions a script asks for.

### Per server: no switch to turn extensions on

A setting in config.yml that turns syntax on for every script would make a script's meaning depend on the server it
runs on. A script copied to another server, or checked in an editor, would read differently there, and nothing in
the file would say so. So extensions are only turned on per script.

**For the owner:** a setting the other way round is possible, `language-extensions.allowed: false` in config.yml,
for networks that want every script to stay runnable on Skript. With it, `using neoskript …` is an error. It isn't
implemented, as nobody has asked for it and nothing needs it until an extension exists.

### How tools show it

A script that uses an extension won't run on Skript, and every place that shows a script says so:
- **`neoskript-cli check`:** the report's JSON lists each script's `extensions`, and the text report says
  "uses NeoSkript's language extension x, so it won't run on Skript" for that script (implemented).
- **The editors (language server):** an information mark on each `using neoskript …` line, "This uses NeoSkript's
  language extension 'x', so the script won't run on Skript." (implemented, from the parser's `extensions`).
- **The in-browser parser:** its JSON has `extensions` (implemented). The page can show a note above the problems,
  once an extension exists to show.
- **The server:** nothing extra at load. A note on every load of every such script would be noise, and the server
  isn't where the question "will this run on Skript?" is asked.
- **The syntax reference:** an extension's elements carry their experiment (`neoskript <name>`) in the export, as
  Skript's experimental elements do, so neoskript.dev can mark them "NeoSkript extension" and show the `using` line.

### `run on`

`run on %entity/location%:` is NeoSkript's own syntax for Folia, and today it needs no opt-in. It can't break a
Skript script: Skript has no such syntax, and Skript doesn't run on Folia, where it's needed. **Decided (2026-11-27):** it
stays as it is, with no opt-in, so Folia scripts keep working. Putting it behind `using neoskript run on` for
consistency was the other option. The CLI could also say a script uses it.

## 2. What's guaranteed

1. **A script without `using neoskript …` behaves exactly like on Skript.** An extension's syntax only exists for a
   script that asks for it. In one that doesn't, its words get Skript's own errors ("Can't understand this
   condition/effect: …"), never a message that names the extension (an extension's elements are always "silent"
   experimental elements). `LanguageExtensionsTest` checks this.
2. **The differential and Skript's suite stay as they are.** No script in them asks for an extension, so they can't
   change. An extension adds its own unit tests and its own scenario. It never adds to the differential corpus, which
   compares with Skript.
3. **An extension never changes the meaning of Skript syntax, even in a script that asks for it.** It only adds new
   syntax. If something Skript already reads would mean something else, that's a different extension design, not this
   one. (Example: `set {_a}, {_b} to f()` already sets both to every value in Skript, so destructuring needs words
   of its own, below.)
4. **An extension's syntax keeps its meaning once released.** A script that worked keeps working. An extension that
   needs to change goes out under a new name.
5. **If Skript adds the same feature, Skript's wins.** NeoSkript implements Skript's version as it does every Skript
   feature. The extension stays for the scripts that use it, marked as superseded in the docs, and the CLI suggests
   Skript's syntax.

Skript's own experiments (`for loops`, `queues`, `script reflection`, `error catching`, `type hints`) aren't
extensions. NeoSkript supports them as Skript does, since they're on their way into Skript.

## 3. Candidates, ranked

What makes a good candidate:
- people want it: Skript issues, or the reason servers install skript-reflect or SkQuery;
- Skript has no experiment or plan for it;
- NeoSkript's compiler makes it cheap or fast;
- it's unlikely to clash with what Skript adds later.

Sources:
- Skript's GitHub: [#4005 flow and execution control](https://github.com/SkriptLang/Skript/issues/4005) (open),
  [#4071 user-created custom types](https://github.com/SkriptLang/Skript/issues/4071) (open), and
  [#8794 setting several variables in one line](https://github.com/SkriptLang/Skript/issues/8794) (closed).
- skript-reflect's custom syntax and structures, used to stand in for these.
- SkQuery's `switch`.

### 1. Background sections: `in the background:`

```
using neoskript background sections

command /stats:
	trigger:
		in the background:
			set {_report} to big_report()   # slow work, off the server's threads
		send {_report} to player            # back on the trigger's thread
```

- **Why:** #4005 is open and wanted. Today, slow work (big loops over saved variables, file and web reads through
  addons) holds the server's thread, or needs an addon that's async in a way scripts can't control. NeoSkript
  already has the machinery: triggers that wait are resumable state machines, and addon calls can pause the trigger
  (PROGRESS, M6 step 15).
- **How it compiles:** like a pausing expression. The section's code runs on a worker thread with a copy of the
  locals it reads, and the trigger waits and carries on on its own thread (Folia: its region's) with the locals the
  section set.
  - Inside the section, only syntax marked thread-safe is allowed: variables, maths, text, functions made of those,
    and addon calls marked safe. It's checked at load, with a clear error ("'teleport' can't be used in a background
    section").
  - A runtime error in the section is reported as usual, and the trigger carries on.
- **Clash risk:** low to moderate. Skript's own proposal uses `do [async]:`, so the words are chosen not to be those.
  If Skript adds async sections, NeoSkript implements Skript's, and this becomes the superseded one.
- **Risk to watch:** global variables changed from two threads. Each operation is atomic already (PLAN 3.3), and the
  docs would say so.

### 2. `match` sections

```
using neoskript match sections

on chat:
	match message:
		"hi", "hello":
			send "Hello!" to player
		"bye":
			send "Bye!" to player
		otherwise:
			send "?" to player
```

- **Why:** long `if`/`else if` chains on one value are everywhere in real scripts. SkQuery's `switch` is installed
  for this. It reads better and runs faster.
- **How it compiles:** the value is worked out once. Then a hash lookup for literal cases (text, numbers, item
  types), or Skript's comparison in order for the others. It's one more section kind for the compiler.
- **Clash risk:** low. `match` isn't a word Skript uses for sections. It's deliberately not `switch`, which SkQuery
  has and the addon shim (D22) may load.

### 3. Custom types: `record` structures

```
using neoskript records

record bounty:
	target: offline player
	reward: number
	placed: date

command /bounty <player> <number>:
	trigger:
		set {bounties::%uuid of arg-1%} to a new bounty with target arg-1, reward arg-2, placed now
		send "Bounty: %reward of {bounties::%uuid of arg-1%}%" to player
```

- **Why:** #4071 is open. Today, scripts fake records with list variables (`{bounty::%id%::reward}`), which are easy
  to get wrong and slow to copy, or with skript-reflect's custom structures, which need Java.
- **How it compiles:** a record is a type, parsed before the scripts like function signatures. Its fields are
  property expressions (`reward of X`, `X's reward`). The compiler makes an immutable hidden class, so reads are a
  field load.
  - Saved variables store a record by name and fields, in CSV and native storage. Changing a record's fields keeps
    old values loadable, with missing fields as nothing.
- **Clash risk:** moderate. Skript may well add custom types (#4071). `record` and `with <field> <value>` keep it
  apart from #4071's proposed `type X:`. If Skript adds its own, it wins (guarantee 5).
- **Why third, not first:** it's the most work (storage format, reloads that change a record, the addon API), and
  it's the one most likely to arrive in Skript.

### 4. Checked local types: `local {_x} is a number`

```
using neoskript typed locals

function total(items: items) :: number:
	local {_sum} is a number
	set {_sum} to 0
	loop {_items::*}:
		add amount of loop-value to {_sum}
	return {_sum}
```

- **Why:** a common source of bugs is a local holding the wrong kind of value without anyone noticing. Skript's
  `type hints` experiment already infers types for the warnings it gives.
- **How it compiles:** a declared local can only be set to its type. That's an error at load where the compiler
  knows the value's type, and a Skript-style runtime error otherwise. A number local can then live in a JVM slot,
  unboxed, which makes loops of arithmetic several times faster.
- **Clash risk:** moderate. Skript's type hints are evolving and could grow declarations. Build on them, and switch
  to theirs if they do.

### 5. Destructuring: `unpack … into …`

```
using neoskript unpacking

unpack location_parts(player's location) into {_world}, {_x} and {_z}
```

- **Why:** #8794 (closed by Skript). Functions that return several values are read back one index at a time.
- **How it compiles:** the value is worked out once, then put into each variable in turn, with `<none>` for missing
  ones. It's trivial.
- **Clash risk:** low. Its own words, not a new meaning for `set` (guarantee 3).
- **Why last:** small value. `set {_l::*} to f()` then `{_l::1}` already works.

### Not recommended

- **Maps with any keys:** list variables already cover what scripts do, and a second kind of collection would split
  every list expression in two.
- **Lambdas or function values:** Skript's `script reflection` experiment already has function references, and
  this overlaps it.
- **try/catch:** Skript's `error catching` experiment covers it.
- **Changing what existing syntax means** (for example, `set {_a}, {_b} to f()` unpacking): never, even opted into
  (guarantee 3).

## 4. What's built, and what's next

Built:
- `using neoskript <name>`, with an error for an extension that doesn't exist.
- Extension syntax is only available to scripts that ask for it, and is silent to those that don't.
- The registry lists the extensions; `ParsedScript.extensions()`.
- The CLI's report, the language server's information mark, and the browser parser's JSON.
- Tests: a made-up extension (`LanguageExtensionsTest`) shows a script without the opt-in is unaffected, a script with
  it gets the syntax, and Skript's own experiments are untouched.

Decided by the owner (2026-11-27):
1. The opt-in and this design are merged as they are, with no extension built.
2. `run on` stays without an opt-in, as recommended above.

Still open, decided later:
1. Which extensions, if any, to build, and in what order. Ranked above: background sections, match sections,
   records, typed locals, unpacking.
2. Whether to offer `language-extensions.allowed: false` in config.yml.
3. The names. Each extension's name is the one scripts write, and it's permanent (guarantee 4).

Each extension, once chosen, ships in its own PR. It comes with its syntax, unit tests, a harness scenario, a
docs page under "NeoSkript extensions" on neoskript.dev, and its elements marked in the syntax reference.
