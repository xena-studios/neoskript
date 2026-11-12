// Makes the language server's docs (generated/docs.json) from neoskript.dev's syntax reference
// (website/src/data/syntax.json): each element's name, kind, description and an example for hover, a link to its
// entry on neoskript.dev, and completion snippets made from its patterns. Run by `pnpm build`.
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";

const source = fileURLToPath(new URL("../../../website/src/data/syntax.json", import.meta.url));
const out = fileURLToPath(new URL("../generated/docs.json", import.meta.url));
const syntax = JSON.parse(await readFile(source, "utf8"));
const SITE = "https://neoskript.dev";
const PLURAL = { event: "events", condition: "conditions", effect: "effects", expression: "expressions", section: "sections", structure: "structures", type: "types", function: "functions" };

/** The site's descriptions are a little HTML; hover shows Markdown. */
function markdown(html) {
  return html
    .replace(/<code>(.*?)<\/code>/g, (_, c) => "`" + c + "`")
    .replace(/<(strong|b)>(.*?)<\/\1>/g, "**$2**")
    .replace(/<(em|i)>(.*?)<\/\1>/g, "*$2*")
    .replace(/<a href="([^"]*)">(.*?)<\/a>/g, (_, href, text) => `[${text}](${href.startsWith("/") ? SITE + href : href})`)
    .replace(/<li>/g, "\n- ")
    .replace(/<\/?(ul|li|p)>/g, "")
    .replace(/<br>/g, "\n")
    .replace(/&lt;/g, "<")
    .replace(/&gt;/g, ">")
    .replace(/&quot;/g, '"')
    .replace(/&#39;/g, "'")
    .replace(/&amp;/g, "&")
    .trim();
}

/**
 * A pattern as something to type: optional parts left out, the first of each choice, and each placeholder a
 * snippet tab stop: `(message|send [message[s]]) %objects% [to %audiences%]` is `message ${1:objects}`.
 */
function snippet(pattern) {
  let p = pattern;
  let previous;
  do {
    previous = p;
    p = p.replace(/\[[^[\]]*\]/g, "");
  } while (p !== previous);
  do {
    previous = p;
    p = p.replace(/\(([^()|]*)(\|[^()]*)?\)/g, "$1");
  } while (p !== previous);
  let n = 0;
  const escape = (s) => s.replace(/[\\$}]/g, (c) => "\\" + c);
  const parts = p.split(/(%[^%]+%|<[^<>]+>)/);
  const text = parts
    .map((part) => {
      if (part.startsWith("%") && part.endsWith("%")) return `\${${++n}:${escape(part.slice(1, -1).split("/")[0])}}`;
      if (part.startsWith("<") && part.endsWith(">")) return `\${${++n}:text}`;
      return escape(part);
    })
    .join("")
    .replace(/\s+/g, " ")
    .trim();
  return text.includes("¦") || text === "" ? null : text;
}

const elements = {};
const completions = [];
for (const e of syntax.elements) {
  elements[e.id] = {
    kind: e.kind,
    name: e.name,
    url: `${SITE}/syntax/${PLURAL[e.kind]}/#${e.slug}`,
    description: e.description.map(markdown).join("\n\n"),
    example: e.examples[0] ?? null,
    patterns: e.patterns,
    since: e.since[0] ?? null,
    deprecated: e.deprecated,
  };
  if (e.deprecated) continue;
  // Statements and events are what people type at the start of a line; expressions come up inside them.
  if (["effect", "condition", "section", "event"].includes(e.kind)) {
    const insert = snippet(e.patterns[0]);
    if (insert) completions.push({ id: e.id, kind: e.kind, label: insert.replace(/\$\{\d+:([^}]*)\}/g, "$1").replace(/\\(.)/g, "$1"), insert });
  }
}

const functions = {};
for (const f of syntax.functions) {
  const params = f.parameters.map((p) => `${p.name}: ${p.type}${p.optional ? " = …" : ""}`).join(", ");
  functions[f.name.toLowerCase()] = {
    name: f.name,
    signature: `${f.name}(${params})${f.returns ? ` :: ${f.returns.plural ? f.returns.name + "s" : f.returns.name}` : ""}`,
    parameters: f.parameters.map((p) => p.name),
    url: `${SITE}/syntax/functions/#${f.slug}`,
    description: f.description.map(markdown).join("\n\n"),
    example: f.examples[0] ?? null,
  };
}

const types = syntax.types.map((t) => ({ name: t.codeName, plural: t.plural, url: `${SITE}/syntax/types/#${t.slug}`, description: t.description.map(markdown).join("\n\n") }));

await mkdir(new URL("../generated/", import.meta.url), { recursive: true });
await writeFile(out, JSON.stringify({ neoskript: syntax.neoskript, skript: syntax.skript, elements, functions, types, completions }));
console.log(`docs: ${Object.keys(elements).length} elements, ${Object.keys(functions).length} functions, ${types.length} types, ${completions.length} completions`);
