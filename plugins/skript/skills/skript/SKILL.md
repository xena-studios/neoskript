---
name: skript
description: Write, fix, explain and review Skript scripts (.sk files) for Minecraft servers running Skript or NeoSkript on Paper or Folia. Use when someone asks for a Skript script, command, event handler, function or GUI-free game mechanic, pastes a .sk file, or pastes a Skript error such as "Can't understand this effect". Not for Java plugins, datapacks, mcfunction or other scripting plugins such as Denizen.
---

# Writing Skript

Skript is a plugin for Minecraft servers (Paper, Folia) whose scripts read like English. Scripts are `.sk` files in `plugins/Skript/scripts/`. NeoSkript is a drop-in replacement for Skript: the same scripts run unchanged, from `plugins/NeoSkript/scripts/`. Everything here applies to both.

Target Skript 2.17 syntax with no addons. Use addon syntax (SkBee, skript-reflect, skript-yaml and others) only when the user says the addon is installed, and say which addon each such line needs.

## How to work

1. **Never guess syntax.** Skript only accepts its registered patterns; a plausible English sentence that isn't one is a parse error. When you're not sure an element exists or how it's worded, look it up in the syntax reference, https://neoskript.dev/syntax/, which is generated from NeoSkript's registry and lists every event, condition, effect, expression, type, section, structure and function with its patterns. Machine-readable versions are listed at https://neoskript.dev/llms.txt.
2. **Write the whole script,** not fragments, unless the user asked for a fragment. Give the file name.
3. **Check it.** If you can't run a server, tell the user to paste the script into https://neoskript.dev/parser/, which runs NeoSkript's parser in the browser, or to reload it on a test server.
4. **Tell the user how to load it:** put the file in the scripts folder and run `/sk reload <file name>` (or `/sk reload scripts`). Errors are printed to the console and to the player who reloaded, as `Line N: (file.sk)` and the reason.

## Shape of a script

A script is a list of top-level structures. Each ends its header line with `:` and has an indented body. Indent with tabs or spaces, but the same way throughout a file. `#` starts a comment.

```skript
# chat-greeting.sk: greets players and counts joins.
options:
	prefix: <gold>[Server]<reset>

on join:
	add 1 to {joins::%player's uuid%}
	if {joins::%player's uuid%} is 1:
		broadcast "{@prefix} Welcome %player% to the server for the first time!"
	else:
		send "{@prefix} Welcome back! You've joined %{joins::%player's uuid%}% times." to player

on quit:
	set {lastseen::%player's uuid%} to now
```

The structures you'll use most:

- `on <event>:` runs when something happens: `on join:`, `on death:`, `on break of diamond ore:`, `on right click on a chest:`.
- `command /name <arguments>:` adds a command, with entries such as `permission:`, `cooldown:` and `trigger:`.
- `function name(parameter: type) :: return type:` declares a function, called from anywhere as `name(...)`.
- `every 5 minutes:` runs on a timer.
- `options:` declares text you reuse as `{@name}`.

## Variables

- `{name}` is global and saved across restarts (in `variables.csv`). `{_name}` is local to one trigger and gone when it ends. Use locals for anything temporary.
- Lists use `::`. `{homes::%player's uuid%::*}` is every element under that key; `{kills::%player's uuid%}` is one value per player.
- Key per-player data by `%player's uuid%`, not by name: names change.
- `set {x} to 5`, `add 1 to {x}`, `remove 2 from {x}`, `delete {x}`, `add player to {_players::*}`, `clear {_players::*}`.
- An unset variable is `<none>`. `{x} ? 0` gives 0 when `{x}` is unset.
- Delete global lists you no longer need. Huge global lists slow down saving and loading.

## Text

- Insert values with `%...%`: `"%player% has %{coins::%player's uuid%}% coins"`. Write `%%` for a literal percent sign.
- Colours: `<red>`, `<bold>`, `<reset>` and `&c` codes both work.
- `send "..." to player` messages one player, `message "..."` messages the player of the event, `broadcast "..."` messages everyone.

## Commands

```skript
# heal-command.sk: /heal, for yourself or someone else.
command /heal [<player>]:
	permission: server.heal
	cooldown: 30 seconds
	cooldown message: <red>Wait %remaining time% before healing again.
	trigger:
		if arg-1 is set:
			heal arg-1
			send "<green>Healed %arg-1%." to sender
		else if sender is a player:
			heal player
			send "<green>Healed." to player
		else:
			send "Give a player name when using this from the console." to sender
```

- Arguments are `<type>`; `[<type>]` makes one optional. Read them as `arg-1`, `arg-2` and so on.
- `sender` is whoever ran the command, player or console. `player` exists only when a player ran it.
- `executable by: players` stops the console from running a player-only command.

## Functions

```skript
# economy-functions.sk: a tiny coin balance.
function coins(p: player) :: number:
	return {coins::%{_p}'s uuid%} ? 0

function giveCoins(p: player, amount: number = 1):
	add {_amount} to {coins::%{_p}'s uuid%}
	send "<gold>+%{_amount}% coins (now %coins({_p})%)" to {_p}

command /balance:
	executable by: players
	trigger:
		send "<gold>You have %coins(player)% coins." to player
```

Parameters are locals inside the function (`{_p}`). Functions are global across scripts, so give them distinct names.

## Control flow

- `if`, `else if`, `else`; conditions can be joined with `and` / `or`.
- `loop all players:` with `loop-player`; `loop {_list::*}:` with `loop-value` and `loop-index`; `loop 10 times:` with `loop-number`; `while <condition>:`.
- `stop` ends the trigger. `exit loop` leaves the current loop. `continue` skips to the next iteration.
- `wait 2 seconds` pauses this trigger without blocking the server. After a wait, the player may have left: check `if player is online:` before using them.

```skript
# countdown.sk: counts down in chat, then starts.
command /countdown:
	permission: server.countdown
	trigger:
		loop 5 times:
			broadcast "<yellow>Starting in %6 - loop-number%..."
			wait 1 second
		broadcast "<green>Go!"
```

## Events

- Event values are named after the event: `player`, `victim` and `attacker` in a damage event, `event-block` in a block event, `event-item`, `event-location`.
- `cancel event` stops what the event was about (the block break, the damage, the chat message), where the event can be cancelled.
- Restrict events in the header rather than with an `if` when you can: `on break of diamond ore:` instead of checking the block inside `on break:`.

```skript
# no-pvp-in-spawn.sk: no player damage near spawn.
on damage of a player:
	attacker is a player
	distance between victim and spawn of world "world" is less than 50
	cancel event
	send "<red>No fighting near spawn." to attacker
```

A condition on its own line, like `attacker is a player`, stops the trigger when it's false.

## Keeping scripts fast

- Prefer events to timers. Avoid `every tick` and short timers that loop over all players or entities.
- Don't loop over a huge global list on every event; key data so you can read one entry directly.
- Use locals for intermediate values instead of globals.
- NeoSkript compiles scripts to JVM bytecode and loads them faster than Skript, but the same advice holds on both.

## Common errors

- `Can't understand this condition/effect/expression: ...`: the line isn't any registered pattern. Check the wording in the syntax reference, or the line needs an addon that isn't installed.
- Indentation errors: a body that isn't indented under its header, or tabs and spaces mixed.
- `There's no <value> in a <event> event`: that event value doesn't exist there, for example `attacker` in `on join:`.
- A command or function defined twice across scripts: rename one.

## Don't

- Don't invent syntax, addon features or config options. Say when you're not sure, and point to the syntax reference.
- Don't write Java, Denizen or datapack commands when asked for Skript.
- Don't promise something works on a specific server without it being tested there.
