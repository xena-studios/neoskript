// Builds the extension: bundles src/extension.ts with the language client into dist/extension.cjs, and copies the
// language server (built by editors/language-server's `pnpm build`) into server/.
import { build } from "esbuild";
import { copyFile, mkdir } from "node:fs/promises";

const here = (p) => new URL(p, import.meta.url).pathname;
await build({
  entryPoints: [here("../src/extension.ts")],
  outfile: here("../dist/extension.cjs"),
  bundle: true,
  platform: "node",
  target: "node18",
  format: "cjs",
  external: ["vscode"],
  minify: true,
  legalComments: "none",
  logLevel: "info",
});
await mkdir(here("../server"), { recursive: true });
await copyFile(here("../../language-server/dist/server.cjs"), here("../server/server.cjs"));
console.log("server: copied server.cjs");
