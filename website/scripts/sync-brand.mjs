// Copies the site's brand assets from a checkout of xena-studios/brand, the
// single source of truth for logos, colours and scenes. The copies are
// committed, so builds never fetch the brand repo and the site only changes
// when someone syncs on purpose. Same script as xenastudios.co's.
//
//   pnpm brand:sync                    # expects ../../brand next to the neoskript repo
//   BRAND_DIR=/path/to/brand pnpm brand:sync
import fs from "node:fs";
import path from "node:path";
import { execFileSync } from "node:child_process";

const ROOT = path.resolve(import.meta.dirname, "..");
const BRAND = path.resolve(process.env.BRAND_DIR ?? path.join(ROOT, "../../brand"));

if (!fs.existsSync(path.join(BRAND, "tokens/neoskript.css"))) {
  console.error(`No brand checkout at ${BRAND}. Clone xena-studios/brand there or set BRAND_DIR.`);
  process.exit(1);
}

// [from (in brand), to (in website)]
const files = [
  ["tokens/neoskript.css", "src/brand/neoskript.css"],
  ["tokens/neoskript.json", "src/brand/neoskript.json"],
  ["neoskript/logo-dark.svg", "src/brand/logo-dark.svg"],
  ["neoskript/logo-light.svg", "src/brand/logo-light.svg"],
  ["neoskript/icon.svg", "src/brand/icon.svg"],
  ["logo/xena.svg", "src/brand/xena.svg"],
  ["neoskript/icon.svg", "public/favicon.svg"],
  ["neoskript/icon.png", "public/icon.png"],
  ["neoskript/social.png", "public/og.png"],
];

for (const [from, to] of files) {
  const dest = path.join(ROOT, to);
  fs.mkdirSync(path.dirname(dest), { recursive: true });
  fs.copyFileSync(path.join(BRAND, from), dest);
}

// The horizon in NeoSkript's colours. The brand repo draws every product's
// banner on the same scene in its own colours ("NeoSkript: night sky, magenta
// limb") but exports the scene alone only in Xena's, so this recolours that
// file with NeoSkript's tokens instead of redrawing it.
const ns = JSON.parse(fs.readFileSync(path.join(BRAND, "tokens/neoskript.json"), "utf8"));
const horizon = fs
  .readFileSync(path.join(BRAND, "scene/horizon.svg"), "utf8")
  .replaceAll("#06080D", ns.night)
  .replaceAll("#121824", ns["night-raised"])
  .replaceAll("#5CB4FF", ns.magenta);
fs.writeFileSync(path.join(ROOT, "src/brand/horizon.svg"), horizon);

const git = (...args) => execFileSync("git", ["-C", BRAND, ...args], { encoding: "utf8" }).trim();
const source = { repo: "xena-studios/brand", commit: git("rev-parse", "HEAD"), dirty: git("status", "--porcelain") !== "" };
fs.writeFileSync(path.join(ROOT, "src/brand/SOURCE.json"), JSON.stringify(source, null, 2) + "\n");

console.log(`synced ${files.length + 1} files from brand@${source.commit.slice(0, 7)}${source.dirty ? " (dirty)" : ""}`);
