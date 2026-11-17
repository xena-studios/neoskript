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

Do the slow part on your own executor (or `threads().runAsync`), and complete the stage from there. `start` itself runs on the trigger's thread, so it mustn't block.

When the stage completes, the trigger carries on from where it stopped, with its local variables and the loops it's in, on the thread it was on: the main thread on Paper; on Folia, the thread of what started the trigger, or of the `run on` section it's in. A stage that's complete already carries straight on. A pausing expression's value arrives before its line runs: each one on a line is worked out first, in the order written. They can be used in effects, conditions and function calls on a line of their own, not in `if`, `loop` or `while` headers; set a variable first.

To store the result where the script says, as `execute ... and store the result in {_rows::*}` does, give the placeholder's index to `PausingEffect.storing`:

```java
registrar.pausingEffect("MyAddonEffQuery", info -> PausingEffect.storing(1, (context, args) ->
        CompletableFuture.supplyAsync(() -> query((String) args[0]), executor)),
        "query %text% [and store [the] result in %-objects%]");
```

The value is stored as `set` would. A `Map` sets a list's indices, and a map or list in it makes a list under its index: `{"name": ["Steve", "Alex"]}` gives `{_rows::name::1}` and `{_rows::name::2}`. A `Collection` or an array is several values, numbered from 1.

**What scripts see.** A pausing element is a delay for Skript's rules, as `wait` is: after it, the event has passed. A stage that fails is a runtime error on the line, with its exception's message, and the trigger carries on: the expression has no value, the variable isn't changed. If the stage takes longer than a minute, the same happens, and NeoSkript tries to cancel it. Change the limit per element:

```java
registrar.timeout("MyAddonEffQuery", Duration.ofSeconds(30));
```

**Reloads and stopping.** A trigger whose script is reloaded or unloaded while it waits carries on with the code it started with, as after a `wait`. When the server stops, triggers still waiting are dropped and their stages cancelled. A stage that completes after a timeout or a stop is ignored, and NeoSkript keeps nothing of the trigger once its pause is over, so holding on to a stage doesn't hold on to old scripts.

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
