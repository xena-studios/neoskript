# NeoSkript for VS Code

Skript (`.sk`) language support for Minecraft servers running [Skript](https://github.com/SkriptLang/Skript) or [NeoSkript](https://neoskript.dev), powered by NeoSkript's own parser.

- **Problems as you type:** every line Skript or NeoSkript wouldn't load, with Skript's own error messages. Functions from your other scripts are known, so calls to them aren't errors.
- **Hover docs:** point at any line, expression or function call to see what it is, with an example and a link to its entry in the [syntax reference](https://neoskript.dev/syntax/).
- **Completion:** events after `on`, effects, conditions and sections, functions (built in and your own), `{@options}` and the variables a script uses.
- **Outline:** events, commands, functions and options, in the Outline view and breadcrumbs.
- **Go to definition:** from a function call to its `function` line, in any script in the workspace, and from `{@option}` to its entry.
- **Highlighting, comments and indentation** for Skript: `#` comments, `:` opens a block, folding by indentation.

The parser runs inside the extension, with no Java or server needed. It knows Skript 2.17's syntax as NeoSkript supports it, including Vault, WorldGuard and GriefPrevention. Syntax from Skript addons (SkBee and others) shows as not understood, because NeoSkript doesn't load addons yet.

## Commands

- **NeoSkript: Restart the language server**

## More

- [neoskript.dev](https://neoskript.dev): the docs, the syntax reference and an in-browser parser
- [Report a problem](https://github.com/xena-studios/neoskript/issues/new/choose)

GPL-3.0-only. Made by [Xena Studios](https://xenastudios.co). Not affiliated with SkriptLang.
