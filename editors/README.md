# Editor support

Skript (`.sk`) in code editors, from NeoSkript's own parser.

| Folder | What it is |
|---|---|
| `language-server/` | The language server: problems, hover, completion, outline, go to definition. NeoSkript's frontend compiled to JavaScript (`neoskript-web`), bundled into one file, `dist/server.cjs`, that runs on Node 18 or newer |
| `vscode/` | The VS Code extension: the TextMate grammar, editing rules, and the server |
| `zed/` | The Zed extension: the grammar's queries, and a Rust (WebAssembly) extension that starts the server with Zed's Node.js |
| `tree-sitter-skript/` | A tree-sitter grammar for highlighting, which Zed builds |

## Building

```sh
./gradlew :neoskript-web:languageServer   # the parser and its syntax data, into language-server/vendor/
cd editors
pnpm install
pnpm --filter ./language-server build     # dist/server.cjs (docs from website/src/data/syntax.json)
pnpm --filter ./language-server test      # starts the server and checks what it answers
pnpm --filter ./vscode package            # neoskript-<version>.vsix
pnpm --filter ./tree-sitter-skript test   # regenerates src/parser.c; commit it with grammar.js
cd zed && cargo build --release --target wasm32-wasip2
```

## Releasing

- **Language server:** a GitHub release tagged `language-server-v<version>` with `neoskript-language-server.cjs` (that's `dist/server.cjs`). The Zed extension downloads it from there, so its version has to match the extension's.
- **VS Code:** `vsce publish` for the Marketplace (publisher `xena-studios`), and `ovsx publish` for Open VSX (VSCodium, Cursor and others).
- **Zed:** a pull request to `zed-industries/extensions` adding this repository as a submodule, with `path = "editors/zed"`. Set `rev` in `zed/extension.toml` to a commit of this repository that has `tree-sitter-skript/src/parser.c`.
