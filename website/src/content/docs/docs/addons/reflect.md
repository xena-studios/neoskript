---
title: NeoSkript Reflect
description: "Java from scripts: imports, methods, fields, constructors and any Bukkit event. Scripts written for skript-reflect run unchanged, and calls are compiled rather than looked up each time."
sidebar:
  label: NeoSkript Reflect
---

NeoSkript Reflect lets scripts use Java directly: import a class, call its methods, read and set its fields, make new objects, and listen to any Bukkit event, including other plugins' events that have no Skript syntax. It's an official addon, released with NeoSkript at the same version.

It takes the syntax of **[skript-reflect](https://github.com/SkriptLang/skript-reflect) 2.6.3**, its latest release, which was called **skript-mirror** before. Scripts written for either run unchanged, as far as this first part covers ([below](#not-yet)).

:::caution[Scripts get full Java access]
Like skript-reflect, this addon gives scripts everything Java can do: read and change any plugin's data, run commands, read files, open network connections, and stop the server. That's the point of it, and NeoSkript can't limit it. Only install it if you trust everyone who can write or upload scripts, as you would with a plugin's jar.
:::

## Installing

1. Put `NeoSkriptReflect-<version>.jar` in `plugins/`, next to NeoSkript, using the same version as NeoSkript.
2. Remove skript-reflect's (or skript-mirror's) jar if it's there. It needs Skript, so it can't load with NeoSkript anyway, and NeoSkript Reflect says so in the console when it starts.
3. Restart the server. `/sk info` lists NeoSkriptReflect.

## Examples

Static methods and fields of an imported class, and an object's own:

```skript nocheck
import:
	java.lang.Math
	java.util.ArrayList
	org.bukkit.Bukkit

on load:
	send "%Math.max(3, 7)%" to console
	set {_list} to new ArrayList()
	{_list}.add("a")
	send "%{_list}.size()% item, on %Bukkit.getName()%" to console
```

Any Bukkit event, by its class:

```skript nocheck
import:
	org.bukkit.event.server.ServerCommandEvent

on ServerCommandEvent:
	send "The console ran %event.getCommand()%" to console
```

A call that fails gives no value and a warning naming the line, and the trigger carries on. With `try` in front, there's no warning, and the error is kept:

```skript nocheck
try {_list}.get(9)
if last java error is set:
	send "No 10th item: %last java error%" to console
```

## Syntax

The [syntax reference](/syntax/) has every pattern, with a badge saying it's from NeoSkriptReflect. In short:

| Syntax | What it does |
|---|---|
| `import:` with full class names, one a line, each optionally `as` another name | Makes classes known to this script by their simple names. Each script has its own imports. |
| `%object%.method(...)`, `%object%.field`, `new Class(...)` | Calls a method, reads a field, or makes an object. On an imported class, the static ones. A field can be set: `set {_point}.x to 5`. |
| `%object%.[Class]method[int, int](...)` | A method from a given class, or with given parameter types, as skript-reflect writes it. |
| `%object%..%text%(...)` | A method or field named by text worked out when the line runs. |
| `try %java call%` | The call without its warning. |
| `[the] [last] java error` | What the last call on this thread threw, or none. |
| `null` | Java's `null`, to pass or set: unlike `none`, a value. |
| `class "java.lang.Thread"`, `%objects%'s class`, `ArrayList.class`, `int.class` | Classes by name, a value's class, and the `Class` object to pass to a method. |
| `new int[3]`, `new String[10]`, `{_array}[1]` | Arrays, and their elements, which can be set. |
| `[1, 2, 3]`, `[... as Class]` | Values as one Java array. |
| `...{_list}` | The elements of an array, collection, iterator or stream as separate values. |
| `%objects% is an instance of %java types%` | Java's `instanceof`. |
| `fields`, `methods`, `constructors`, `field names`, `method names` `of %objects%` | What a class has, as text. |
| `instance of plugin %java type/text%` | A plugin's running instance. |
| `[the] event` | The Bukkit event the trigger is running for. |
| `on <imported event class>:` | A trigger for any Bukkit event. Several with `or`. |

## How calls run

skript-reflect looks a method up each time a line runs, matching the arguments against every overload again, and calls it with `invokeWithArguments`, which the JVM can't turn into a direct call. NeoSkript Reflect looks it up once per line and argument classes, keeps what it found, and calls it through a method handle, which the JVM compiles like a direct call. When the same line is called with arguments of other classes (a whole number one time and a text the next), it keeps the last four.

NeoSkript's test harness times calling `{_list}.size()` in a loop on the same server, on Paper 26.3: **40 to 120 ns a call** here, against **270 to 1,000 ns** with skript-reflect on Skript 2.17.0, over nine runs: three to fifteen times faster in each. Your numbers will depend on the machine.

## Threads and Folia

A Java call runs on the thread running the trigger: the main thread on Paper, and on Folia the thread of the region (or the global thread) the trigger runs on. `last java error` is each thread's own, so a region never sees another region's error.

:::caution[Folia's rules]
On Folia, NeoSkript checks that a script's own syntax only touches what the current region owns, and stops a line that doesn't with a runtime error. It can't check Java code. A Java call that reads or changes an entity, block or chunk another region owns (`Bukkit.getWorlds().get(0).getEntities()` from a player's region, say) breaks Folia's rules: it can corrupt the world or throw from deep inside the server. Keep Java calls to what the trigger's own region owns, or to things that aren't in a world, and use Folia's schedulers (through Java) for the rest.
:::

## Reloads

Imports are read again with every script on each load, and a script's calls keep what they found only as long as the script's code is loaded. Nothing loads classes of its own, so `/sk reload` leaves nothing behind: classes come from the server and its plugins, through NeoSkript Reflect's own plugin class loader, as skript-reflect's came through Skript's.

## Where it differs from skript-reflect

NeoSkript's test harness runs the same script on Skript 2.17.0 with skript-reflect 2.6.3 and on NeoSkript with NeoSkript Reflect, and compares every line it prints (the `neoskript-reflect` scenario). Where they differ, it's on purpose:

- **Overloads:** where several overloads of a method take the arguments, skript-reflect took whichever Java happened to list first, which can change between Java versions: `Math.max(3, 7)` could be the `double` one and give `7.0`. NeoSkript Reflect takes the closest: a parameter of the argument's own class, then of a superclass, then a number of the same kind, so it's the `long` one.
- **Nested classes:** `EnderDragon.Phase.LEAVE_PORTAL` gave no value on skript-reflect, which read `Phase` as a field. It's the nested class here, as in Java. Importing `org.bukkit.entity.EnderDragon$Phase` works on both.
- **NeoSkript's values as Java's:** where nothing else fits a parameter, a timespan is passed as a `java.time.Duration`, a date as an `Instant`, an item type as an `ItemStack` and a slot as its item, through NeoSkript's own conversions. skript-reflect had none, and said no method matched.
- **`last java error`** is each thread's own (skript-reflect's was shared by every thread).
- **Java events** are listened to only for the classes named. A trigger for an event class that has no handler list of its own (an abstract one, like `PlayerEvent`) is an error saying so, rather than a trigger that never runs.

The values scripts get are NeoSkript's own: an item type, a timespan or a date is NeoSkript's class, not Skript's (`ch.njol.skript...`), so calls to Skript's own Java classes' methods don't work. Calls on Bukkit's and Java's classes, which is nearly all of them, are the same.

## Not yet

This is the first part of the addon. These parts of skript-reflect aren't in it yet, so lines using them don't load. They're tracked in [issue #13](https://github.com/xena-studios/neoskript/issues/13).

- Custom syntax: `effect`, `expression`, `condition`, `event` and `property` structures, with `parse`, `get`, `patterns`, `usable in`, `continue` and `expression-N`.
- Computed options (`option` with `get`), sections and `run section`, proxies (`proxy instance of`), function references (`function "name"`), `local` and `bits of`.
- The `import` effect for effect commands, and loading classes from jars in `plugins/skript-reflect/` (external libraries).
- `with priority` and `all` on Java events (`on ServerCommandEvent with priority highest:`).
- `raw %objects%`, and parsing deferred to run time (`$`).
