// Makes the syntax data neoskript.dev is built from, out of two files the harness writes:
//
//   pnpm syntax:export
//
// runs the harness's syntax-export scenario on Paper 26.3, then this script (`pnpm syntax:sync [reports dir]`,
// default ../test-harness/build/harness/reports/paper-26.3, does just this part).
//
// syntax-export.json is NeoSkript's own registry (every element, type and function it registers, patterns
// as written, and what its parser needs besides). It's copied to src/data/syntax-export.json, which is committed:
// the in-browser parser at /parser checks scripts against it (neoskript-web's `website` task reads it), and
// src/data/syntax.json for /syntax is made from it here, so the two pages can't disagree. syntax.json records the
// export's hash, and scripts/parser.mjs refuses to build when they don't match.
//
// skript-docs.json is the reference Skript's `sk gen-docs` output, which has the names, descriptions, examples
// and "since" for Skript's elements. Each NeoSkript element is joined to Skript's by id, then by Skript's event
// name, then by a pattern both have. What Skript's docs don't cover under the same id is in syntax-extra.json:
// elements and types joined to the Skript entry they're part of there, and docs written here for what Skript
// registers but never documents, and for NeoSkript's own syntax. Skript's docs are GPL-3.0-or-later, used here
// under v3.
import { createHash } from "node:crypto";
import { readFile, writeFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";

const reports = process.argv[2] ?? fileURLToPath(new URL("../../test-harness/build/harness/reports/paper-26.3", import.meta.url));
const out = fileURLToPath(new URL("../src/data/syntax.json", import.meta.url));
const exportFile = fileURLToPath(new URL("../src/data/syntax-export.json", import.meta.url));
const exportText = await readFile(`${reports}/syntax-export.json`, "utf8");
await writeFile(exportFile, exportText);
const neo = JSON.parse(exportText);
const skript = JSON.parse(await readFile(`${reports}/skript-docs.json`, "utf8"));
if (neo.format !== 2) throw new Error(`Unknown syntax export format ${neo.format}`);
const extra = JSON.parse(await readFile(fileURLToPath(new URL("./syntax-extra.json", import.meta.url)), "utf8"));
// Docs written here look like Skript's entries.
const written = (id, w) => (w ? { id, name: w.name, description: w.description, examples: w.examples, since: w.since ?? [] } : null);

const KINDS = ["event", "condition", "effect", "expression", "section", "structure"];
const PLURAL = { event: "events", condition: "conditions", effect: "effects", expression: "expressions", section: "sections", structure: "structures" };

// Skript's docs write patterns without parse marks, tags or placeholder flags; so do we, to compare them.
const bare = (p) =>
  p
    .toLowerCase()
    .replace(/%[^%]*%/g, "%")
    .replace(/(^|[[(|\s])[\w-]+:(?=\S)/g, "$1")
    .replace(/:/g, "")
    .replace(/\s+/g, " ")
    .trim();

const docs = KINDS.flatMap((kind) => (skript[PLURAL[kind]] ?? []).map((d) => ({ ...d, kind })));
const docsById = new Map(docs.map((d) => [d.id, d]));
const docsByPattern = new Map();
for (const d of docs) {
  for (const p of d.patterns ?? []) {
    const key = bare(p);
    if (!docsByPattern.has(key)) docsByPattern.set(key, new Set());
    docsByPattern.get(key).add(d);
  }
}
const eventByName = new Map(skript.events.map((d) => [d.name.toLowerCase().replace(/^on /, ""), d]));

function match(el) {
  const own = (d) => d && (d.kind === el.kind || el.kind === "section" || d.kind === "section");
  let d = docsById.get(el.id);
  if (own(d)) return [d, "id"];
  const alias = extra.aliases.elements[el.id];
  if (alias && docsById.get(alias)) return [docsById.get(alias), "alias"];
  if (extra.written.elements[el.id]) return [written(el.id, extra.written.elements[el.id]), "written"];
  if (el.id.includes("-")) {
    d = docsById.get(el.id.split("-")[0]);
    if (own(d)) return [d, "id"];
  }
  if (el.kind === "event" && el.eventName) {
    d = eventByName.get(el.eventName.toLowerCase().replace(/^on /, ""));
    if (d) return [d, "event name"];
  }
  for (const p of el.patterns) {
    const found = [...(docsByPattern.get(bare(p)) ?? [])].filter((c) => c.kind === el.kind);
    if (found.length === 1) return [found[0], "pattern"];
  }
  return [null, null];
}

// Types, for showing patterns' placeholders by name, as Skript's docs do: %itemtypes% is %item types%.
const types = new Map(neo.types.map((t) => [t.id, t]));
// NeoSkript's English plural (SyntaxRegistry.plural), which patterns use for ids too: %livingentities%.
const pluralize = (w) =>
  /[^aeiou]y$/.test(w) ? `${w.slice(0, -1)}ies` : /(s|x|z|ch|sh)$/.test(w) ? `${w}es` : `${w}s`;
const typeWord = new Map();
for (const t of neo.types) {
  typeWord.set(t.id, t.name);
  typeWord.set(pluralize(t.id), t.plural);
  typeWord.set(t.name, t.name);
  typeWord.set(t.plural, t.plural);
}
const placeholder = (inner) => {
  const flags = inner.match(/^[-*~]*/)[0];
  const body = inner.slice(flags.length).replace(/@-?\d+$/, "");
  return `%${body
    .split("/")
    .map((t) => typeWord.get(t.toLowerCase()) ?? t)
    .join("/")}%`;
};
// A pattern as people read it: no parse marks (1:), tags (:tag, tag:) or placeholder flags, types by name.
function readable(p) {
  let s = "";
  let i = 0;
  while (i < p.length) {
    const c = p[i];
    if (c === "%") {
      const end = p.indexOf("%", i + 1);
      s += placeholder(p.slice(i + 1, end));
      i = end + 1;
    } else if (c === "<") {
      // A regex, as written.
      let depth = 0;
      let j = i;
      for (; j < p.length; j++) {
        if (p[j] === "\\") j++;
        else if (p[j] === "<") depth++;
        else if (p[j] === ">" && --depth === 0) break;
      }
      s += p.slice(i, j + 1);
      i = j + 1;
    } else if (c === "\\") {
      s += p.slice(i, i + 2);
      i += 2;
    } else {
      s += c;
      i++;
    }
  }
  // As Skript's own docs clean them: a word and a colon after a bracket, bar or space is a tag or mark.
  return s.replace(/(^|[[(|\s])[\w-]*[:¦]/g, "$1");
}

// Skript's descriptions are HTML. Keep a few tags, as escaped HTML we write ourselves, and turn its links
// to other elements into links here; drop the rest of the markup, keeping its text.
const escape = (s) => s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
const decode = (s) => s.replace(/&lt;/g, "<").replace(/&gt;/g, ">").replace(/&quot;/g, '"').replace(/&#39;/g, "'").replace(/&amp;/g, "&");
const KEEP = new Set(["code", "strong", "em", "b", "i", "ul", "li"]);
const anchors = new Map(); // Skript docs id -> our href, filled in once elements are joined
function html(text) {
  let outHtml = "";
  const open = [];
  for (const [whole, close, tag, attrs, plain] of text.matchAll(/<(\/?)([a-zA-Z]+)([^<>]*)>|([^<]+|<)/g)) {
    if (plain !== undefined) {
      outHtml += escape(decode(plain));
      continue;
    }
    const name = tag.toLowerCase();
    if (name === "br") {
      outHtml += "<br>";
    } else if (KEEP.has(name)) {
      const t = { b: "strong", i: "em" }[name] ?? name;
      if (close) {
        if (open.at(-1) === t) outHtml += `</${open.pop()}>`;
      } else {
        open.push(t);
        outHtml += `<${t}>`;
      }
    } else if (name === "a") {
      if (close) {
        if (open.at(-1) === "a") outHtml += `</${open.pop()}>`;
        else if (open.at(-1) === "a-") open.pop();
      } else {
        const href = attrs.match(/href\s*=\s*['"]([^'"]*)['"]/)?.[1] ?? "";
        const target = href.startsWith("#") ? anchors.get(decodeURIComponent(href.slice(1))) : null;
        if (target) {
          open.push("a");
          outHtml += `<a href="${escape(target)}">`;
        } else open.push("a-");
      }
    } else if (name === "pre" || name === "p") {
      // Dropped; their text stays.
    } else {
      // Not a tag at all: text like <player> in a sentence.
      outHtml += escape(whole);
    }
  }
  while (open.length) {
    const t = open.pop();
    if (t !== "a-") outHtml += `</${t}>`;
  }
  return outHtml;
}
// One paragraph per line, except that list items (Skript spreads a list over several lines, sometimes after
// an empty <ul></ul>) are gathered into one <ul> block.
const paragraphs = (description) => {
  const blocks = [];
  const lines = (description ?? []).flatMap((d) => d.split(/\n+/)).map((d) => d.trim());
  for (const line of lines) {
    const bare = line.replace(/<\/?ul>/gi, "").trim();
    if (!bare) continue;
    if (/^<li\b/i.test(bare)) {
      if (blocks.at(-1)?.startsWith("<ul>")) blocks[blocks.length - 1] = blocks.at(-1).replace(/<\/ul>$/, "") + bare + "</ul>";
      else blocks.push(`<ul>${bare}</ul>`);
    } else blocks.push(line.replace(/<ul>\s*<\/ul>/gi, ""));
  }
  return blocks.filter(Boolean);
};
const examples = (list) => (list ?? []).map((e) => e.replace(/\s+$/, "")).filter(Boolean);
const since = (s) => (s == null ? [] : Array.isArray(s) ? s : [s]);

const slugify = (s) =>
  s
    .replace(/([a-z])([A-Z])/g, "$1-$2")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-|-$/g, "");
const humanize = (id) =>
  id
    .replace(/^(Expr|Eff|Cond|Evt|Sec|Struct|EffSec|Lit|Prop)(?=[A-Z])/, "")
    .replace(/[-_]/g, " ")
    .replace(/([a-z])([A-Z])/g, "$1 $2")
    .replace(/^./, (c) => c.toUpperCase());

const coverage = {};
const used = new Set();
const joined = neo.elements.map((el) => {
  const [d, how] = match(el);
  const c = (coverage[el.kind] ??= { elements: 0, documented: 0, by: {} });
  c.elements++;
  if (d) {
    c.documented++;
    c.by[how] = (c.by[how] ?? 0) + 1;
  }
  let slug = slugify(el.id.replace(/^(Expr|Eff|EffSec|Cond|Evt|Sec|Struct|Lit)(?=[A-Z])/, "")) || slugify(el.id);
  while (used.has(`${el.kind}/${slug}`)) slug += "-2";
  used.add(`${el.kind}/${slug}`);
  return { el, d, slug };
});
for (const { el, d, slug } of joined) {
  if (d && !anchors.has(d.id)) anchors.set(d.id, `/syntax/${PLURAL[el.kind]}/#${slug}`);
}
const typeSlug = (id) => slugify(id);
for (const t of neo.types) anchors.set(t.id, `/syntax/types/#${typeSlug(t.id)}`);
// Skript's docs link events by name too, as in #entity transform.
for (const { el, d, slug } of joined) {
  if (el.kind === "event" && d) anchors.set(d.name.toLowerCase().replace(/^on /, ""), `/syntax/events/#${slug}`);
}

const typeRef = (id) => (id && types.has(id) ? { id, name: types.get(id).name, slug: typeSlug(id) } : id ? { id, name: id, slug: null } : null);
const eventValue = (v) => {
  const t = types.get(v.type);
  const word = `event-${t ? t.name : v.type}`;
  return { text: v.time === -1 ? `past ${word}` : v.time === 1 ? `future ${word}` : word, type: v.type };
};

const elements = joined.map(({ el, d, slug }) => ({
  kind: el.kind,
  id: el.id,
  slug,
  name: d?.name.replace(/^On /, "") ?? humanize(el.id),
  documented: Boolean(d),
  patterns: el.patterns.map(readable),
  returns: el.kind === "expression" ? typeRef(el.returnType) : null,
  eventValues: (el.eventValues ?? []).map(eventValue),
  cancellable: d?.cancellable ?? null,
  description: paragraphs(d?.description).map(html),
  examples: examples(d?.examples),
  since: since(d?.since),
  requirements: d?.requirements ?? [],
  deprecated: Boolean(d?.deprecated),
}));
elements.sort((a, b) => KINDS.indexOf(a.kind) - KINDS.indexOf(b.kind) || a.name.localeCompare(b.name, "en") || a.id.localeCompare(b.id));

const docsTypes = new Map(skript.types.map((t) => [t.id, t]));
const typeCoverage = { elements: neo.types.length, documented: 0 };
const typeList = neo.types
  .filter((t) => t.id !== "object" || docsTypes.has("object"))
  .map((t) => {
    const d = docsTypes.get(t.id) ?? docsTypes.get(extra.aliases.types[t.id]) ?? written(t.id, extra.written.types[t.id]);
    if (d) typeCoverage.documented++;
    return {
      id: t.id,
      slug: typeSlug(t.id),
      // A type documented as part of another keeps its own name.
      name: (d && !extra.aliases.types[t.id] ? d.name : null) ?? t.name.replace(/^./, (c) => c.toUpperCase()),
      documented: Boolean(d),
      codeName: t.name,
      plural: t.plural,
      usage: (d?.patterns ?? []).filter(Boolean),
      literals: t.literals ?? [],
      description: paragraphs(d?.description).map(html),
      examples: examples(d?.examples),
      since: since(d?.since),
    };
  })
  .sort((a, b) => a.name.localeCompare(b.name, "en"));

const docsFunctions = new Map();
for (const f of skript.functions) if (!docsFunctions.has(f.name)) docsFunctions.set(f.name, f);
const fnCoverage = { elements: neo.functions.length, documented: 0 };
const fnSlugs = new Set();
const functionList = neo.functions.map((f) => {
  const d = docsFunctions.get(f.name) ?? written(f.name, extra.written.functions[f.name]);
  if (d) fnCoverage.documented++;
  let slug = slugify(f.name);
  while (fnSlugs.has(slug)) slug += "-2";
  fnSlugs.add(slug);
  const param = (p) => {
    const t = types.get(p.type);
    return { name: p.name, type: t ? (p.plural ? t.plural : t.name) : p.type, typeSlug: t ? typeSlug(p.type) : null, optional: p.optional };
  };
  return {
    name: f.name,
    slug,
    documented: Boolean(d),
    parameters: f.parameters.map(param),
    returns: f.returnType ? { ...typeRef(f.returnType), plural: f.returnsPlural } : null,
    description: paragraphs(d?.description).map(html),
    examples: examples(d?.examples),
    since: since(d?.since),
  };
});

coverage.type = typeCoverage;
coverage.function = fnCoverage;
const data = {
  // The hash of src/data/syntax-export.json, which scripts/parser.mjs checks.
  exportSha256: createHash("sha256").update(exportText).digest("hex"),
  neoskript: neo.neoskript,
  server: neo.server,
  skript: `${skript.source.name} ${skript.source.version}`,
  coverage,
  elements,
  types: typeList,
  functions: functionList,
};
await writeFile(out, `${JSON.stringify(data)}\n`);

const total = Object.values(coverage).reduce((a, c) => [a[0] + c.elements, a[1] + c.documented], [0, 0]);
for (const [kind, c] of Object.entries(coverage)) console.log(`${kind.padEnd(10)} ${String(c.documented).padStart(4)} / ${String(c.elements).padStart(4)} documented`);
console.log(`all        ${String(total[1]).padStart(4)} / ${String(total[0]).padStart(4)} (${((total[1] / total[0]) * 100).toFixed(1)}%)`);
