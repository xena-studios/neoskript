// Bundles the language server, with the parser and its data, into one file: dist/server.cjs, which runs on
// Node 18 or newer with `node dist/server.cjs --stdio`. The VS Code extension ships it, and the Zed extension
// downloads it from GitHub Releases.
import { build } from "esbuild";
import { readFile } from "node:fs/promises";

const pkg = JSON.parse(await readFile(new URL("../package.json", import.meta.url), "utf8"));
await build({
  entryPoints: [new URL("../src/server.ts", import.meta.url).pathname],
  outfile: new URL("../dist/server.cjs", import.meta.url).pathname,
  bundle: true,
  platform: "node",
  target: "node18",
  format: "cjs",
  minify: true,
  legalComments: "none",
  banner: { js: `#!/usr/bin/env node\n// NeoSkript language server ${pkg.version}. GPL-3.0-only. https://github.com/xena-studios/neoskript` },
  logLevel: "info",
});
