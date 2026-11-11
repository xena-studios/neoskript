// Builds the in-browser parser (src/parser/neoskript-parser.js and its
// syntax.json) with Gradle when it isn't there, before `astro build`. It's
// NeoSkript's frontend compiled by TeaVM (neoskript-web/), so it needs a JDK,
// like the rest of the repo. `pnpm parser` rebuilds it after frontend changes.
import { existsSync } from "node:fs";
import { execFileSync } from "node:child_process";
import { fileURLToPath } from "node:url";

const parser = fileURLToPath(new URL("../src/parser/neoskript-parser.js", import.meta.url));
const syntax = fileURLToPath(new URL("../src/parser/syntax.json", import.meta.url));
if (process.env.CI || !existsSync(parser) || !existsSync(syntax)) {
  const root = fileURLToPath(new URL("../..", import.meta.url));
  execFileSync(process.platform === "win32" ? "gradlew.bat" : "./gradlew", [":neoskript-web:website", "-q"], {
    cwd: root,
    stdio: "inherit",
  });
}
