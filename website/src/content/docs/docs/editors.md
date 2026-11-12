---
title: Code editors
description: "Skript in VS Code and Zed, with NeoSkript's parser: problems as you type, hover docs, completion, an outline and go to definition."
---

NeoSkript's editor extensions bring its parser into your editor, for scripts that run on Skript or NeoSkript. The parser runs inside the editor, so you don't need Java or a server.

- **Problems as you type:** every line that wouldn't load, with Skript's own error messages. Functions from your other scripts are known, so calls to them aren't errors.
- **Hover docs:** point at a line, an expression or a function call to see what it is, with an example and a link to the [syntax reference](/syntax/).
- **Completion:** events after `on`, effects, conditions and sections, functions (built in and your own), `{@options}`, and the variables a script uses.
- **Outline:** events, commands, functions and options.
- **Go to definition:** from a function call to its `function` line in any script in the folder, and from `{@option}` to its entry.
- **Highlighting, `#` comments and indentation**, with folding by indentation.

Open your server's `plugins/Skript/scripts` (or `plugins/NeoSkript/scripts`) folder, not just one file, so functions from the other scripts are found.

## VS Code

Download `neoskript-<version>.vsix` from [GitHub Releases](https://github.com/xena-studios/neoskript/releases?q=language-server&expanded=true), then run **Extensions: Install from VSIX…** in the command palette and pick it. It's on its way to the Marketplace and Open VSX. It works in VS Code 1.90 or newer, and in editors built on it (VSCodium, Cursor). If you have another Skript extension, turn it off, so the two don't both check `.sk` files.

**NeoSkript: Restart the language server** in the command palette starts it again.

## Zed

It's on its way to Zed's extension list. Until then, install it from a copy of [the repository](https://github.com/xena-studios/neoskript): run **zed: install dev extension** and pick its `editors/zed` folder (Zed needs Rust, from rustup, to build it). It runs the language server with Zed's own Node.js, downloading it from GitHub the first time.

## Other editors

The language server works in any editor that speaks the Language Server Protocol, such as Neovim, Helix or Sublime Text. Download `neoskript-language-server.cjs` from [GitHub Releases](https://github.com/xena-studios/neoskript/releases), run it with `node neoskript-language-server.cjs --stdio`, and use it for `.sk` files.

## What it knows

The extensions know Skript 2.17's syntax as NeoSkript supports it, including Vault, WorldGuard and GriefPrevention, the same as [the parser](/parser/) on this site. Syntax from Skript addons, such as SkBee, shows as not understood, because NeoSkript doesn't load addons yet.
