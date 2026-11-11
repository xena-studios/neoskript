// NeoSkript's frontend compiled to JavaScript by TeaVM (neoskript-web/ in the
// repo). neoskript-parser.js and syntax.json are generated and not committed:
// `pnpm build` makes them (scripts/parser.mjs), `pnpm parser` remakes them.

/**
 * Builds the syntax registry from syntax.json's text, NeoSkript's syntax export; returns what it parses against,
 * e.g. "NeoSkript 0.1.0 on Paper 26.3".
 */
export function init(syntaxData: string): string;

/** Parses one script; returns ParseResult (src/lib/parser.ts) as JSON. */
export function parse(source: string): string;
