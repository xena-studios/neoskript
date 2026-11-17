// Checks the guides' links into the syntax reference, which starlight-links-validator
// can't see (/syntax is a custom page): each /syntax/<category>/#<slug> has to
// name a category and an entry on it, from src/data/syntax.json. Runs in `pnpm build`.
import { readdirSync, readFileSync } from "node:fs";
import { join, relative } from "node:path";
import { fileURLToPath } from "node:url";

const docs = fileURLToPath(new URL("../src/content/docs", import.meta.url));
const syntax = JSON.parse(readFileSync(fileURLToPath(new URL("../src/data/syntax.json", import.meta.url)), "utf8"));

const kinds = {
  events: "event",
  conditions: "condition",
  effects: "effect",
  expressions: "expression",
  sections: "section",
  structures: "structure",
};
const slugs = { types: new Set(syntax.types.map((t) => t.slug)), functions: new Set(syntax.functions.map((f) => f.slug)) };
for (const [path, kind] of Object.entries(kinds)) {
  slugs[path] = new Set(syntax.elements.filter((e) => e.kind === kind).map((e) => e.slug));
}

function pages(dir) {
  return readdirSync(dir, { withFileTypes: true }).flatMap((f) =>
    f.isDirectory() ? pages(join(dir, f.name)) : /\.mdx?$/.test(f.name) ? [join(dir, f.name)] : [],
  );
}

const broken = [];
for (const file of pages(docs)) {
  const text = readFileSync(file, "utf8");
  for (const [, category, slug] of text.matchAll(/\]\(\/syntax\/(?:([a-z]+)\/(?:#([a-z0-9-]+))?)?\)/g)) {
    if (category === undefined) continue;
    if (!slugs[category]) broken.push(`${relative(docs, file)}: /syntax/${category}/ isn't a category`);
    else if (slug && !slugs[category].has(slug)) broken.push(`${relative(docs, file)}: /syntax/${category}/#${slug} isn't an entry`);
  }
}
if (broken.length > 0) {
  console.error(`Broken links into the syntax reference:\n  ${broken.join("\n  ")}`);
  process.exit(1);
}
