# NeoSkript Syntax Guide

This is the curated reference for the syntax NeoSkript implements today. A machine-generated listing
of everything currently registered (including addons) can be produced at runtime with
`/neoskript docs`, which writes `syntax.md` into the plugin's data folder.

> NeoSkript aims to run existing `.sk` scripts unchanged. Coverage is broad and growing; if a valid
> Skript construct is missing, that's a gap to fill, not a difference by design.

## Script structure

Scripts are `.sk` files in `plugins/NeoSkript/scripts/`. Indentation (spaces or tabs) defines blocks.
Lines starting with `#` are comments.

```sk
# a comment
on join:
    send "Welcome!" to player
```

## Events

```sk
on join:        # also: on player join
on quit:        # also: on disconnect
on chat:        # also: on player chat
on death:       # also: on player death
on break:       # also: on block break, on mine
on place:       # also: on block place

on load:        # runs once when scripts load (also: on enable)

every 5 seconds:   # periodic; also minutes, hours, ticks
```

## Effects

```sk
broadcast "<text>"
send "<text>" [to <player>]        # message ... is an alias
set {variable} to <value>
add <value> to {variable}          # numbers add; lists append
remove <value> from {variable}     # numbers subtract; lists remove a match
delete {variable}                  # clear / reset are aliases
return <value>                     # inside a function
stop                               # abort the current trigger (exit)
```

## Conditions (used in `if` / `while`)

```sk
<a> is <b>                 # is equal to, =
<a> is not <b>             # isn't, !=
<a> is greater than <b>    # is more than, >
<a> is less than <b>       # <
<a> is at least <b>        # is greater than or equal to, >=
<a> is at most <b>         # is less than or equal to, <=
{variable} is set
{variable} is not set
```

## Control flow

```sk
if {_x} is greater than 10:
    broadcast "big"
else if {_x} is 10:
    broadcast "exactly ten"
else:
    broadcast "small"

while {_n} is less than 5:
    add 1 to {_n}

loop 5 times:
    broadcast "iteration %loop-number%"

loop {scores::*}:
    broadcast "value: %loop-value%"
```

## Expressions

```sk
player                              # the player in the event
console                            # the server console sender
name of <player>                   # also: <player>'s name
loop-value                         # current loop element
loop-number / loop-index           # current loop iteration (1-based)
size of <values>                   # element count (amount/number of)
random number between <a> and <b>  # from/to also accepted
random integer between <a> and <b>
2 + 3 * 4                          # arithmetic: + - * / and parentheses
```

## Variables

- `{name}` — global, shared across triggers and persisted between restarts (simple types).
- `{_name}` — trigger-local.
- `{list::index}` — a list entry; the index may interpolate (`{deaths::%player%}`).
- `{list::*}` — all direct children, e.g. for `loop {list::*}:`.

## Strings

Double-quoted, with `%expression%` interpolation and `%%` for a literal percent:

```sk
broadcast "Hello %player%, you have %{coins::%player%}% coins!"
```

## Functions

```sk
function greet(name):
    broadcast "Hello, %{_name}%!"

function double(n):
    return {_n} * 2

on join:
    greet(name of player)
    set {_twenty} to double(10)
```

## Commands

- `/neoskript` — status
- `/neoskript reload` — reload all scripts
- `/neoskript profile [on|off|reset]` — control the built-in profiler and report hot triggers
- `/neoskript docs` — write the generated syntax reference to the data folder

(`nsk` and `ns` are aliases. Management actions require the `neoskript.admin` permission.)
