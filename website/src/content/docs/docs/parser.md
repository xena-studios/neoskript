---
title: Using the parser
description: Check a script for errors in your browser, jump to each problem, keep several files, and share a link to one.
---

[The parser](/parser/) checks a script for errors and warnings while you type. It's NeoSkript's own parser, the code that reads your scripts when the server loads them, compiled to JavaScript so it runs in your browser.

## Checking a script

Paste a script into the editor, open a `.sk` file from your device (**File**, then **Open from your device**), or start from one of the examples under **Insert**. The parser checks it a quarter of a second after you stop typing; turn that off in the settings (the gear) and it checks only when you press **Parse**, <kbd>Ctrl</kbd>+<kbd>Enter</kbd>, or **Click to parse** in the status bar.

- **Problems**, beside the editor, has a card for each error and warning: *Error on Line 4*, the message, and the line it's about. **Go to line** selects the code the problem is about in the editor. Each card's header folds it away.
- In the editor, errors are underlined with a wavy magenta line and warnings in pale pink, with marks beside the scrollbar and in the minimap. Point at an underline to read its message, or press <kbd>F8</kbd> to step through them.
- The **status bar** counts errors, warnings, functions, variables and commands. Select a count to list them in place of the problems (each with its line, to jump to), and select it again, or **Show problems**, to go back. Functions and commands come from NeoSkript's parser; variables are each distinct `{name}` the script uses, a rough count that leaves out `{@options}`.

Messages use Skript's wording, so what you find about an error on a forum still applies.

## Files

Several scripts can be open at once. The current one's name is the tab on the right of the menu bar: select it to rename it, and its cross closes it. **+** starts a new file, and the list beside it switches between open files. **Insert** also adds a few starting points (an event, a command, a function, a periodic event, options) to the file you're in.

Files are kept in this browser's storage, on this device only, and come back when you return. The save button (or <kbd>Ctrl</kbd>+<kbd>S</kbd>) saves at once; the page also saves as you type. **File**, then **Download .sk**, saves the current file to your device. Clearing your browser's site data deletes them, so download anything you want to keep.

## Keyboard

The editor is [Monaco](https://microsoft.github.io/monaco-editor/), the editor in Visual Studio Code, and works without a mouse:

| Key | What it does |
|---|---|
| <kbd>Ctrl</kbd>+<kbd>Enter</kbd> | Parses the script. |
| <kbd>Tab</kbd>, <kbd>Shift</kbd>+<kbd>Tab</kbd> | Indents or outdents the line, or every selected line. |
| <kbd>Enter</kbd> | Starts a new line at the same indentation, one level deeper after a line ending in `:`. |
| <kbd>F8</kbd>, <kbd>Shift</kbd>+<kbd>F8</kbd> | Goes to the next or previous problem. |
| <kbd>Ctrl</kbd>+<kbd>F</kbd>, <kbd>Ctrl</kbd>+<kbd>H</kbd> | Finds, and replaces. |
| <kbd>Ctrl</kbd>+<kbd>M</kbd> | Makes <kbd>Tab</kbd> move focus out of the editor instead of indenting, and back. The settings can make that the default. |
| <kbd>Alt</kbd>+<kbd>F1</kbd> | Opens the editor's accessibility help. |

On a Mac, use <kbd>Cmd</kbd> for <kbd>Ctrl</kbd>. The menus open with <kbd>Enter</kbd> or the down arrow; the arrow keys move through them and between them, and <kbd>Esc</kbd> closes them. **Help**, then **Keyboard shortcuts**, lists these on the page.

## Sharing a link

**Sharing**, then **Copy share link**, puts a link to the current script on your clipboard. Whoever opens it sees the same script, checked the same way, which helps when you ask for help in a support thread. It opens as a new file next to theirs.

The script is in the link itself, compressed, after the `#`. The address bar keeps the link up to date as you edit. Very long scripts make long links; some chat apps cut those off.

## Privacy

Your script never leaves your browser. The page downloads the parser, the editor and the syntax list from neoskript.dev, then parses on your device: the script isn't uploaded anywhere, and the files you keep are stored only in your browser.

Browsers don't send the part of a link after `#` when they open it, so opening a shared link doesn't send the script to the server either. The link does stay in your browser history, and anyone you give it to can read the script.

Like every page on neoskript.dev, this one counts visits with Cloudflare Web Analytics, which uses no cookies. It reports the page's address without the part after `#`, so it doesn't get the script either.
