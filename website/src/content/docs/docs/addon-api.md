---
title: Addon API
description: "Add your own syntax to NeoSkript from a plugin: types, events, effects, expressions, conditions, sections and functions, plus scripts, variables and Folia's threads."
---

An addon is a Paper plugin that adds syntax to NeoSkript: its own types, events, effects, expressions, conditions, sections and functions. It uses `neoskript-api`, the same API NeoSkript's own standard library is built on, so an addon's syntax works the same way NeoSkript's does: on Paper and Folia, in `/sk reload`, in saved variables.

This is NeoSkript's own API. Skript addons, built against Skript's `ch.njol.skript` classes, don't load on NeoSkript yet. A compatibility layer for them is planned, SkBee first.

The [example addon](https://github.com/xena-studios/neoskript/tree/main/examples/example-addon) in the repository uses every part of this page. The harness runs it on Paper and Folia with every change to NeoSkript. NeoSkript's own addons, which replace community Skript addons, live in the repository's [`addons/`](https://github.com/xena-studios/neoskript/tree/main/addons) folder, with how they're built and released.

## Getting started

Add the API as a compile-only dependency, from Maven Central. NeoSkript's jar has the API in it at run time, so don't shade it: an addon with its own copy can't reach NeoSkript, and `NeoSkript.get()` says so.

```kotlin
// build.gradle.kts
repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    compileOnly("co.xenastudios.neoskript:neoskript-api:<version>")
}
```

| | |
|---|---|
| Group | `co.xenastudios.neoskript` |
| Artifact | `neoskript-api` |
| Version | the NeoSkript version you build against |
| Java | 21 |

With Maven:

```xml
<dependency>
    <groupId>co.xenastudios.neoskript</groupId>
    <artifactId>neoskript-api</artifactId>
    <version><!-- version --></version>
    <scope>provided</scope>
</dependency>
```

:::note
The first release on Maven Central is coming. Until it's there, build the API from [the repository](https://github.com/xena-studios/neoskript) with `./gradlew :neoskript-api:publishToMavenLocal`, and add `mavenLocal()` to your repositories.
:::

Make NeoSkript a dependency in `plugin.yml`, so your addon enables after it. It has to be `depend`: NeoSkript only waits for the plugins that depend on it, so with `softdepend` alone your syntax may come too late.

```yaml
depend: [NeoSkript]
folia-supported: true
```

Then register your syntax in `onEnable`:

```java
public final class MyAddon extends JavaPlugin {
    @Override
    public void onEnable() {
        NeoSkript.get().addon(getName(), getPluginMeta().getVersion(), registrar -> {
            registrar.effect("MyAddonEffWave", info -> (context, args) -> wave((Object[]) args[0]),
                    "wave at %players%");
        });
    }
}
```

NeoSkript runs that registration once, on the server's first tick: after its own types and syntax, so your patterns can use `%player%`, and before any script loads. `/sk info` lists your addon. Syntax can't be added after scripts have loaded.

In `onEnable`, scripts and saved variables haven't loaded yet, so `scripts()` and `variables()` throw `IllegalStateException`. Wait for NeoSkript to be ready:

```java
NeoSkript.get().whenReady(() -> {
    Object last = NeoSkript.get().variables().get("lottery::last");
    getLogger().info("Last winner: " + last);
});
```

`whenReady` runs the task right after scripts load, on the global thread, or straight away if NeoSkript is ready already. `isReady()` says which.

## How elements work

Each element has an **id**, unique among elements of its kind. Start yours with your addon's name (`MyAddonEffWave`), and leave out `-`.

Each element is made by a **factory** for every place a script uses it, while the script loads. The factory gets an `ElementInfo`: which pattern matched (`info.pattern()`), the parse tags used (`info.hasTag("all")`), whether an argument was written as an and-list or an or-list (`info.and(0)`), and so on. It may be called more than once for the same place and on any thread, so keep it free of side effects.

The element itself is called with its **arguments**: the values of the pattern's placeholders, by index. A plural placeholder (`%players%`) gives an `Object[]`, a singular one (`%player%`) one value, and a placeholder in an optional part that wasn't written gives `null`. Values are what scripts have: a number is a `Long` or a `Double`, a text is a `String`, a player is a `Player`. NeoSkript evaluates the arguments itself, so an element never evaluates other syntax.

Patterns use Skript's pattern language: `[optional]`, `(this|that)`, `%type%`, `%-type%` for one that may be left out, parse marks (`1¦`) and tags (`all:`).

## Types

```java
registrar.type("ticket", "ticket", Ticket.class);
registrar.text(Ticket.class, ticket -> "ticket #" + ticket.number());
```

The id is what patterns use (`%ticket%`, `%tickets%`), and the name is how messages write it. Other ways to describe a type:

- `parser(typeId, text -> ...)`: how text people type, like a command argument, becomes a value.
- `literalParser(typeId, text -> ...)`: words written in a script as a value (`golden ticket`).
- `namedType(id, name, class, values)`: a type with a fixed set of named values, like game modes.
- `subtype(sub, supertype)` and `alias(typeId, singular, plural)`.
- `cloner(class, copy)`: how a value that can change in place is copied when it's stored in a variable.
- `storage(name, class, codec)`: how values are saved in `variables.csv`. Pick a name Skript doesn't use, like `myaddon:ticket`. Without it, variables holding your type aren't saved.

```java
registrar.storage("myaddon:ticket", Ticket.class, new VariableCodec<>() {
    public void encode(Ticket value, BinaryWriter out) {
        out.beginObject(2);
        out.stringField("owner", value.owner());
        out.longField("number", value.number());
    }

    public Ticket decode(BinaryReader in) {
        in.beginObject();
        return new Ticket(in.stringField("owner"), in.longField("number"));
    }
});
```

## Effects

```java
registrar.effect("MyAddonEffWave", info -> (context, args) -> {
    for (Object player : (Object[]) args[0]) {
        ((Player) player).swingMainHand();
    }
}, "wave at %players%");
```

`context.event()` is the event the trigger is running for, such as a Bukkit event.

## Expressions

```java
registrar.expression("MyAddonExprNewTicket", "ticket",
        info -> (context, args) -> new Ticket((String) args[0], next()),
        "[a] new ticket for %text%");
```

An expression returns one value, an `Object[]` of several, or `null` for none. For the common shape `[the] <property> of %owners%` and `%owners%'[s] <property>`, use `property`:

```java
registrar.property("MyAddonExprTicketNumber", "integer", "ticket number", "tickets", Ticket.class,
        Ticket::number);
```

To let scripts change an expression with `set`, `add`, `remove`, `delete` and `reset`, have the object the factory returns implement `Changeable` as well as `Expression`: `acceptChange(mode)` says what each mode takes, and `change(...)` does it.

## Conditions

```java
registrar.condition("MyAddonCondWinning", info -> (context, args) -> {
    Object[] tickets = (Object[]) args[0];
    boolean holds = Condition.check(tickets, info.and(0), ticket -> ((Ticket) ticket).winning());
    return info.pattern() == 1 ? !holds : holds;
}, "%tickets% (is|are) winning", "%tickets% (isn't|is not|aren't|are not) winning");
```

`Condition.check` checks a list as Skript does: all of `a and b`, any of `a or b`. The negated pattern is yours to handle, by its index. For the common shape, `propertyCondition` does all of that:

```java
registrar.propertyCondition("MyAddonCondWinning", "winning", "tickets", Ticket.class, Ticket::winning);
```

## Sections

A section runs its code for each value it returns, as `spawn a pig:` does for the pig. Written without a `:`, it's an effect.

```java
registrar.section("MyAddonSecTicket", "ticket", info -> (context, args) -> new Ticket((String) args[0], next()),
        "print [a] ticket for %text%");
```

```
print a ticket for "Steve":
    send "Printed %event-ticket%" to console
```

## Events

A server event is a Bukkit event class, your own included (with the usual `getHandlerList`):

```java
registrar.event("MyAddonEvtDraw", List.of(LotteryDrawEvent.class), null, "lottery draw");
registrar.eventValue(LotteryDrawEvent.class, "ticket", LotteryDrawEvent::winner);
```

Scripts then write `on lottery draw:`, and get the winner as `event-ticket`. A filter, in place of `null`, gets the values of the pattern's placeholders, as in `on draw of %tickets%`. `eventDefault(typeId)` makes a placeholder of that type, when left out, take the event's value, as `send "hi"` goes to the event's player.

### Events an addon runs itself

Some events don't come from Bukkit: another plugin asks the addon for something, and a script's trigger answers, as a placeholder asked for by PlaceholderAPI. Register the event as above, and ask to be told which of its triggers are loaded:

```java
registrar.event("MyAddonEvtQuestion", List.of(QuestionEvent.class), info -> new TopicFilter(info.argumentLiteral(0)),
        "question about %*text%");
registrar.eventTriggers("MyAddonEvtQuestion", triggers -> {
    // Every loaded trigger of the event, in script order, each time scripts load, reload or unload.
    for (LoadedTrigger trigger : triggers) {
        TopicFilter topic = (TopicFilter) trigger.filter();  // the filter your factory made for its line
        ...
    }
});
```

Each list replaces the one before: drop the old triggers when it arrives. A trigger whose script has unloaded or reloaded since runs nothing. Each `LoadedTrigger` can `run(event, owner)` right away, from any thread, on that thread: its filter, then its code, until it ends or waits. A runtime error in it is logged as any trigger's, and doesn't reach your code. `owner` is the entity, block or location whose thread it carries on on after a `wait`, or `null` for the global thread. The list is given on the thread that loaded the scripts, the global thread on Folia, with an empty list once none are left, so what you register for them can follow scripts as they reload, with nothing left behind. On Folia, NeoSkript's region rules apply to what the code reads, as for any trigger. A trigger's code may run another (a placeholder asking for one); limit how deep and how much yourself. NeoSkript's own [placeholders addon](/docs/addons/placeholders/) is built this way.

### Syntax for some events only

This is experimental: it may change in a minor release.

Skript's elements that only work in some events fail to set up elsewhere, and Skript then tries the line's other readings: `the result` is the smelted item in a smelting event and a placeholder's result in a placeholder. Say which events an element is for, and NeoSkript does the same:

```java
registrar.onlyInEvents("MyAddonExprQuestion", -1, QuestionEvent.class);  // -1: every pattern
```

Elsewhere, other syntax that reads the text is tried first. Only if none does is your element used, and its factory throws the `SyntaxError` the script's author sees.

## Functions

```java
registrar.function("lotteryPrize", List.of(Parameter.of("tickets", "integer")), "integer", false,
        args -> ((Number) args[0]).longValue() * 100);
```

Scripts call it as `lotteryPrize(3)`. A script's own function with the same name takes precedence.

## Waiting for a result

Some syntax has to wait for something slow: a database query, a web request, another server. Never make the server wait for it. A **pausing** element starts the work and returns a `CompletionStage`, and the trigger stops there, as it does at `wait`, until the stage completes:

```java
registrar.pausingEffect("MyAddonEffPing", info -> (context, args) ->
        CompletableFuture.runAsync(() -> ping((String) args[0]), executor),
        "ping %text%");

registrar.pausingExpression("MyAddonExprStatus", "text", info -> (context, args) ->
        CompletableFuture.supplyAsync(() -> fetchStatus((String) args[0]), executor),
        "[the] status of [the] server %text%");
```

```
ping "lobby"
set {_status} to the status of the server "lobby"
send "Lobby: %{_status}%"
```

Do the slow part on your own executor (or `threads().runAsync`), and complete the stage from there. `start` itself runs on the trigger's thread, so it mustn't block. Read what you need from the arguments and `context.event()` in `start`, and don't touch the `TriggerContext`, the event or anything a region owns from your executor's thread: the trigger has stopped, and on Folia only the owner's thread may touch those. If `start` throws, that's the same as a failed stage.

When the stage completes, the trigger carries on from where it stopped, with its local variables and the loops it's in, on the thread it was on: the main thread on Paper; on Folia, the thread of what started the trigger, or of the `run on` section it's in. A stage that's complete already carries straight on. A pausing expression's value arrives before its line runs: each one on a line is worked out first, in the order written. They can be used in effects, conditions and function calls on a line of their own, not in `if`, `loop` or `while` headers; set a variable first.

To store the result where the script says, as `execute ... and store the result in {_rows::*}` does, give the placeholder's index to `PausingEffect.storing`:

```java
registrar.pausingEffect("MyAddonEffQuery", info -> PausingEffect.storing(1, (context, args) ->
        CompletableFuture.supplyAsync(() -> query((String) args[0]), executor)),
        "query %text% [and store [the] result in %-objects%]");
```

The value is stored as `set` would. A `Map` sets a list's indices, and a map or list in it makes a list under its index: `{"name": ["Steve", "Alex"]}` gives `{_rows::name::1}` and `{_rows::name::2}`. A `Collection` or an array is several values, numbered from 1.

**What scripts see.** A pausing element is a delay for Skript's rules, as `wait` is, from the start of its line: the line runs once the value is there, so on that line and after it the event has passed. `return` can't be used there, nor `cancel event` and the like. A stage that fails is a runtime error on the line, with its exception's message, and the trigger carries on: the expression has no value, the variable isn't changed. If the stage takes longer than its timeout, the same happens, and NeoSkript tries to cancel it. The timeout is a minute unless you set one; an addon whose work has its own time limit (a database query, a web request) should set the element's to match it, so scripts see the addon's error rather than NeoSkript's:

```java
registrar.timeout("MyAddonEffQuery", Duration.ofSeconds(30));
```

At most 10,000 triggers wait at once; past that, a pausing element fails at once with a runtime error. `/sk info` shows how many are waiting.

**Reloads and stopping.** A trigger whose script is reloaded or unloaded while it waits carries on with the code it started with, as after a `wait`. When the server stops, `on unload` triggers run first, and what they pause for (a last write to a database) gets up to 3 seconds to finish before the server exits. Then triggers still waiting are dropped without carrying on, as after a `wait`. Their stages aren't cancelled: the work may still be running, and finishing or stopping it is up to your addon's own `onDisable`. A stage that completes after a timeout or a stop is ignored, and NeoSkript keeps nothing of the trigger or its event once its pause is over, so holding on to a stage doesn't hold on to old scripts or players.

## Loop values

An expression scripts loop can give its loops a `loop-<name>` of its own, as NeoSkriptYaml's `loop yaml node keys "homes" of "data"` gives `loop-node`, the full path of each key. The value is worked out each time it's used, from the looped expression's arguments (evaluated again) and the loop's current value:

```java
registrar.loopValue("MyAddonExprKeys", "path", info -> (context, args, current) -> args[0] + "." + current);
```

Only loops over that expression have it; elsewhere `loop-path` is Skript's error, "There's no loop that matches 'loop-path'". It can be written `loop-path-2` for the second such loop out, and in those loops it comes before a type of the same name. The factory can throw `SyntaxError` where the loop, as matched, has no such value. `loopValue` is `@Experimental`.

## Values

`NeoSkript.get().values()` is for an addon that writes values somewhere else, as a file or a database, the way scripts and Skript have them:

```java
Values values = NeoSkript.get().values();
values.type(value);                    // "timespan", "itemtype", "player"... or null
values.text(value);                    // as "%value%" writes it; works before NeoSkript is ready too
values.parse("5 minutes", "timespan"); // text as a command argument of that type is read
values.convert(slot, ItemStack.class); // through the converters scripts use
Values.Saved saved = values.save(value);         // as variables.csv saves it: Skript's type name and bytes
Object back = values.load(saved.type(), saved.data());
values.toJava(date);                   // a date as an Instant, a timespan as a Duration
values.fromJava(Instant.now());        // and back; whole numbers as Longs, decimals as Doubles
```

## Converters, comparisons and arithmetic

```java
// A ticket where a text is wanted is its owner's name.
registrar.converter(Ticket.class, String.class, Ticket::owner);
// {_a} is {_b}
registrar.comparison(Ticket.class, Ticket.class, (a, b) -> a.number() == b.number());
// {_ticket} + 1
registrar.arithmetic('+', Ticket.class, Number.class, (ticket, n) -> ticket.plus(n.longValue()));
```

`changer(class, mode, ...)` says how `set`, `add` and so on change a value of your type that's the target itself, as `give a diamond to player` adds to the player's inventory.

## Scripts and variables

`NeoSkript.get()` also reaches the scripts themselves:

```java
NeoSkript neoskript = NeoSkript.get();
Object coins = neoskript.variables().get("coins::" + player.getUniqueId());
neoskript.variables().set("coins::" + player.getUniqueId(), 100L);
Map<String, Object> all = neoskript.variables().list("coins");       // {coins::*}
Object prize = neoskript.scripts().call("lotteryPrize", 3L);         // a script's function, or a built-in one
neoskript.scripts().reload("quests/main.sk");
```

`neoskript.values().text(value)` writes any value as a script's text would show it (`"%value%"`): `5` for the number 5, a player's name for a player. Unlike the rest, it works before NeoSkript is ready too. The rest of `values()` is [below](#values).

Use them once NeoSkript is ready (see `whenReady` above). A variable's name is written as in a script, without braces, with `%...%` already filled in. Players are their UUIDs in variable names, as in Skript's default config. Each variable call is atomic and works from any thread; a sequence of them isn't.

When your addon disables, the variables your codecs save are kept as the bytes they save as, so they're still saved and load again with your codecs on the next start. If a codec fails then, the variable keeps what `variables.csv` already has for it. A variable with nothing there keeps its value until the server stops, isn't saved, and the console says so.

A codec can only be registered for a class nothing saves yet: registering one for a class NeoSkript already saves, like a location, throws `IllegalArgumentException`.

## Disabling and reloading addons

Restart the server to add, update or remove an addon:

- **After an addon disables while the server runs,** its syntax stays registered until a restart, because scripts are compiled against it. Scripts that use it fail at run time.
- **Enabling an addon while the server runs** (PlugMan and the like) isn't supported. Its `addon(...)` call fails with "Scripts have loaded", because syntax can only be registered before scripts load.

## Threads and Folia

On Paper, everything runs on the main thread. On Folia, an entity, a block and a location each belong to a region, and only that region's thread may touch them. NeoSkript runs each trigger on the thread that owns what started it: a player's event on the player's region, `on load` and `every` on the global thread.

Your elements run on the trigger's thread. By default, NeoSkript treats them as touching the entities, blocks and locations they're given:

- an expression or condition given one that another thread owns is a runtime error naming the line, and gives nothing;
- an effect given one runs on the owner's thread, soon, while the trigger carries on;
- a pausing effect or expression given one is a runtime error, and doesn't start: it starts where the trigger is, so it can't run elsewhere.

Say so when an element is different, by the id of one of your own elements:

```java
registrar.affinity("MyAddonCondWinning", ThreadAffinity.ANY);       // only reads what's safe anywhere, or only sends
registrar.affinity("MyAddonEffEclipse", ThreadAffinity.GLOBAL);     // changes world-wide state, like the time
```

To run code yourself, use `NeoSkript.get().threads()`:

```java
Threads threads = NeoSkript.get().threads();
threads.run(player, () -> player.setHealth(20));       // on the player's thread, soon
threads.runLater(block, 20, () -> block.setType(Material.AIR));
threads.runGlobal(() -> world.setTime(0));             // on the global thread
threads.runAsync(() -> fetchFromTheWeb());             // off the server's threads
if (threads.owns(entity)) { /* safe to touch it here */ }
```

The same code works on Paper, where all of these run on the main thread. A task for an entity that isn't valid when it would run (removed, dead, or a player who left) doesn't run. Tasks belong to your plugin, so they're cancelled when it disables.

## What's stable

Everything public in `co.xenastudios.neoskript.api` is stable, unless it's marked:

- **`@Experimental`**: you can use it, but it may change in a minor release. Most of it matches one of Skript's quirks and is kept for the Skript addon compatibility layer.
- **`@Internal`**: for NeoSkript's own modules. Don't use it.

From 1.0, the stable API only changes in a way that breaks addons in a major release: an addon built against 1.0 runs on every 1.x. New methods may appear on interfaces you call, like `Registrar` and `NeoSkript`, and interfaces you implement, like `Effect` and `Expression`, only get new methods with a default. Every change to the API's signatures is checked in NeoSkript's build against a committed file, so none happens by accident.

Until 1.0, the API may still change between betas. Changes are listed in the release notes.
