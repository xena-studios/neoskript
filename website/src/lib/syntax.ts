// The syntax reference's data (src/data/syntax.json, made by scripts/syntax.mjs from NeoSkript's registry and
// Skript's docs), and how /syntax groups it.
import data from "../data/syntax.json";

export type TypeRef = { id: string; name: string; slug: string | null; plural?: boolean };
export type Element = {
  kind: string;
  id: string;
  slug: string;
  name: string;
  documented: boolean;
  patterns: string[];
  returns: TypeRef | null;
  eventValues: { text: string; type: string }[];
  cancellable: boolean | null;
  description: string[];
  examples: string[];
  since: string[];
  requirements: string[];
  deprecated: boolean;
};
export type Type = {
  id: string;
  slug: string;
  name: string;
  documented: boolean;
  codeName: string;
  plural: string;
  usage: string[];
  literals: string[];
  description: string[];
  examples: string[];
  since: string[];
};
export type Fn = {
  name: string;
  slug: string;
  documented: boolean;
  parameters: { name: string; type: string; typeSlug: string | null; optional: boolean }[];
  returns: TypeRef | null;
  description: string[];
  examples: string[];
  since: string[];
};

export const syntax = data as unknown as {
  neoskript: string;
  server: string;
  skript: string;
  coverage: Record<string, { elements: number; documented: number }>;
  elements: Element[];
  types: Type[];
  functions: Fn[];
};

export type Category = { path: string; label: string; kind: string; blurb: string; count: number };

const count = (kind: string) => syntax.elements.filter((e) => e.kind === kind).length;
export const categories: Category[] = [
  { path: "events", label: "Events", kind: "event", blurb: "What a trigger listens for, as in on join or on break.", count: count("event") },
  { path: "conditions", label: "Conditions", kind: "condition", blurb: "Checks, for if lines and on their own to stop a trigger.", count: count("condition") },
  { path: "effects", label: "Effects", kind: "effect", blurb: "Lines that do something: send, give, teleport.", count: count("effect") },
  { path: "expressions", label: "Expressions", kind: "expression", blurb: "Values: players, items, numbers, text.", count: count("expression") },
  { path: "types", label: "Types", kind: "type", blurb: "The kinds of value expressions give and patterns take.", count: syntax.types.length },
  { path: "sections", label: "Sections", kind: "section", blurb: "Lines ending in a colon with code under them.", count: count("section") },
  { path: "structures", label: "Structures", kind: "structure", blurb: "Top-level blocks of a script.", count: count("structure") },
  { path: "functions", label: "Functions", kind: "function", blurb: "Built-in functions, called as name(arguments).", count: syntax.functions.length },
];

/** A function's signature as a script calls it: floor(n: number). */
export const signature = (f: Fn) =>
  `${f.name}(${f.parameters.map((p) => `${p.name}: ${p.type}`).join(", ")})`;

export const total = Object.values(syntax.coverage).reduce((n, c) => n + c.elements, 0);
export const documented = Object.values(syntax.coverage).reduce((n, c) => n + c.documented, 0);
