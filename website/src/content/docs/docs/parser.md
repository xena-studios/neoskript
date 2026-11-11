---
title: Using the parser
description: Check a script for errors in your browser, jump to each problem, and share a link to it.
---

[The parser](/parser/) checks a script for errors and warnings while you type. It's NeoSkript's own parser, the code that reads your scripts when the server loads them, compiled to JavaScript so it runs in your browser.

## Checking a script

Paste a script into the editor, or start from one of the examples. The parser checks it a quarter of a second after you stop typing.

- **Problems** lists each error and warning with its line number and message. Each one is labelled *Error* or *Warning*. Select one to jump to that line: the editor selects the code the problem is about.
- In the editor, errors are underlined with a wavy line and warnings with a dotted one. The line number of a line with a problem has a solid rail for an error and a dashed one for a warning.
- **Outline** lists the script's events, commands, functions and options with their lines. Select one to jump to it.

Messages use Skript's wording, so what you find about an error on a forum still applies.

## Keyboard

The editor works without a mouse:

| Key | What it does |
|---|---|
| <kbd>Tab</kbd> | Indents the line, or every selected line. |
| <kbd>Shift</kbd>+<kbd>Tab</kbd> | Outdents the line, or every selected line. |
| <kbd>Enter</kbd> | Starts a new line at the same indentation, one level deeper after a line ending in `:`. |
| <kbd>Esc</kbd>, then <kbd>Tab</kbd> | Leaves the editor for the problems list. <kbd>Esc</kbd>, then <kbd>Shift</kbd>+<kbd>Tab</kbd>, leaves it for Copy link. |

Indenting uses whatever the script already uses: a tab, or as many spaces as its first indented line.

## Sharing a link

Copy link puts a link to the script on your clipboard. Whoever opens it sees the same script, checked the same way, which helps when you ask for help in a support thread.

The script is in the link itself, compressed, after the `#`. Once you've edited the script, the address bar keeps the link up to date, so reloading the page doesn't lose your work. Very long scripts make long links; some chat apps cut those off.

## Privacy

Your script never leaves your browser. The page downloads the parser and its syntax list from neoskript.dev, then parses on your device: the script isn't uploaded anywhere.

Browsers don't send the part of a link after `#` when they open it, so opening a shared link doesn't send the script to the server either. The link does stay in your browser history, and anyone you give it to can read the script.

Like every page on neoskript.dev, this one counts visits with Cloudflare Web Analytics, which uses no cookies. It reports the page's address without the part after `#`, so it doesn't get the script either.
