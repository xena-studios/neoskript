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
- **Item and entity names.** These depend on the server's aliases, so the parser accepts any name where one fits: `give player 1 stne` isn't reported.
- **Event values.** It doesn't yet check that an event has the value a line uses, such as `event-block` in `on join:`.

## Which syntax it checks against

For now the parser checks against the syntax in Skript 2.17.0's documentation export: every event, effect, condition, expression, section, structure, type and function Skript 2.17.0 documents. It's the same syntax list NeoSkript's own tests parse their corpus of scripts against. The list also has Skript's test-only syntax, such as `assert` and `test "name":`, so the parser accepts those.

Once NeoSkript can export the syntax it registers on a real server, the parser will check against that instead, and this page will say so.
