// Builds the in-browser parser (src/parser/neoskript-parser.js and its
// syntax.json) with Gradle when it isn't there, before `astro build`. It's
// NeoSkript's frontend compiled by TeaVM (neoskript-web/), so it needs a JDK,
// like the rest of the repo. `pnpm parser` rebuilds it after frontend changes;
// a newer export rebuilds it here.
//
// Its syntax data comes from src/data/syntax-export.json, NeoSkript's registry
// as a real server exported it, and so does /syntax's src/data/syntax.json. The
// build stops if syntax.json wasn't made from the export as it is now: run
// `pnpm syntax:sync` (see scripts/syntax.mjs) after changing the export.
import { createHash } from "node:crypto";
import { existsSync, readFileSync, statSync } from "node:fs";
import { execFileSync } from "node:child_process";
import { fileURLToPath } from "node:url";

const exportFile = fileURLToPath(new URL("../src/data/syntax-export.json", import.meta.url));
const reference = JSON.parse(readFileSync(fileURLToPath(new URL("../src/data/syntax.json", import.meta.url)), "utf8"));
const hash = createHash("sha256").update(readFileSync(exportFile)).digest("hex");
if (reference.exportSha256 !== hash) {
  throw new Error("src/data/syntax.json wasn't made from src/data/syntax-export.json as it is: run `pnpm syntax:sync`.");
}

const parser = fileURLToPath(new URL("../src/parser/neoskript-parser.js", import.meta.url));
const syntax = fileURLToPath(new URL("../src/parser/syntax.json", import.meta.url));
const stale = (file) => !existsSync(file) || statSync(file).mtimeMs < statSync(exportFile).mtimeMs;
if (process.env.CI || !existsSync(parser) || stale(syntax)) {
  const root = fileURLToPath(new URL("../..", import.meta.url));
  execFileSync(process.platform === "win32" ? "gradlew.bat" : "./gradlew", [":neoskript-web:website", "-q"], {
    cwd: root,
    stdio: "inherit",
  });
}
