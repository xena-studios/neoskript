---
title: What the parser checks
description: What the in-browser parser finds in a script, what it can't know, and which syntax it checks against.
---

[The parser](/parser/) reads a script the way NeoSkript does when it loads one, against the syntax list described [at the end of this page](#which-syntax-it-checks-against). It doesn't run the script.

## What it finds

- **Structure:** indentation that doesn't fit the lines around it (a line indented too far, or with spaces where the script uses tabs), and events or structures that don't exist (`on blorp:`).
- **Code that doesn't match any syntax:** a misspelt effect such as `sned "hi" to player` is reported as *Can't understand this condition/effect*.
- **Text:** unclosed or misused quotes.
- **Options:** a `{@option}` that the script's `options:` doesn't define.
- **Functions:** calls to functions that don't exist, and arguments of the wrong type, such as text where a function takes a number.
- **Commands:** argument types that don't exist, such as `command /heal <playr>:`.
- **Warnings** NeoSkript gives when it loads a script, such as a list missing `and` or `or`.

## What it can't know

- **It only parses.** Problems that only show up when a script runs, such as a variable that's not set or a player who's offline, aren't found.
- **One script at a time.** A function defined in another of your scripts is reported as not existing. Paste the scripts together to check them as one.
- **Your server's addons.** Syntax from addons isn't known, so lines that use it are reported as errors.
- **Item and entity names.** Most of these are read by code on the server, so the parser accepts any name where one fits: `give player 1 stne` isn't reported.
- **Event values.** It doesn't yet check that an event has the value a line uses, such as `event-block` in `on join:`.

## Which syntax it checks against

The parser checks against the syntax NeoSkript itself registers: every event, effect, condition, expression, section, structure, type and function, exported from NeoSkript running on a real Paper server. The page says which NeoSkript version and server the list came from. It's the same list as the [syntax reference](/syntax/), made from the same file.

The list doesn't have Skript's test-only syntax, such as `assert` and `test "name":`, because NeoSkript only registers that when it runs Skript's test suite. Lines that use it are reported as errors, as a server would.

A few things the list can't hold, so the parser can't check them:

- **Names that only a server can read.** Item, entity, enchantment and biome names are read by code that needs a server. The list keeps the names that code accepted among the words it was tried with; any other words where one of these fits might be a name, so they aren't reported.
- **Lists of item names.** Because the parser can't be sure a word is an item, it sometimes splits a list of items differently than a server does. That changes whether it warns that the list is missing `and` or `or`.
