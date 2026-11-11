// Publishes the AI skill (plugins/skript in the repo) at /skill/SKILL.md, so
// agents without a plugin system can fetch it. The repo's copy is the only
// one. Runs after `astro build`.
import { copyFile, mkdir } from "node:fs/promises";
import { fileURLToPath } from "node:url";

const from = fileURLToPath(new URL("../../plugins/skript/skills/skript/SKILL.md", import.meta.url));
const dir = fileURLToPath(new URL("../dist/skill/", import.meta.url));
await mkdir(dir, { recursive: true });
await copyFile(from, `${dir}SKILL.md`);
console.log("skill: dist/skill/SKILL.md");
