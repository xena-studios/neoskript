# NeoSkript for Zed

Skript (`.sk`) language support for Zed: highlighting (a small tree-sitter grammar, `editors/tree-sitter-skript`), and NeoSkript's language server for problems as you type, hover docs, completion, an outline and go to definition.

The server is the same as the VS Code extension's. The extension runs it with Zed's own Node.js: it uses `neoskript-language-server` from your `PATH` if you have it, and otherwise downloads `neoskript-language-server.cjs` from the `language-server-v<version>` GitHub release once.

## Trying it from the repository

1. In Zed, run **zed: install dev extension** and pick this folder (`editors/zed`). Zed builds the extension (it needs Rust, from rustup) and the grammar.
2. Open a `.sk` file.

GPL-3.0-only. Made by [Xena Studios](https://xenastudios.co).
