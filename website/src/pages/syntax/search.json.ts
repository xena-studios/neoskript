// The index /syntax searches: every element, type and function with its name, where it is and its patterns.
// Built as a static file; the page fetches it the first time someone types. v is the Skript version an entry
// came in (lib/syntax.ts sinceKey), for the "Added in" filter.
import { categories, signature, sinceKey, syntax } from "../../lib/syntax";

const path = Object.fromEntries(categories.map((c) => [c.kind, c]));

export function GET() {
  const rows = [
    ...syntax.elements.map((e) => ({ n: e.name, c: path[e.kind].path, s: e.slug, p: e.patterns, i: e.id, v: sinceKey(e) })),
    ...syntax.types.map((t) => ({ n: t.name, c: "types", s: t.slug, p: [t.codeName, t.plural], i: t.id, v: sinceKey(t) })),
    ...syntax.functions.map((f) => ({ n: f.name, c: "functions", s: f.slug, p: [signature(f)], i: f.name, v: sinceKey(f) })),
  ];
  return new Response(JSON.stringify(rows), { headers: { "Content-Type": "application/json" } });
}
