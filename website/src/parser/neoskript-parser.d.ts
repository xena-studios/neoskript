// NeoSkript's frontend compiled to JavaScript by TeaVM (neoskript-web/ in the
// repo). neoskript-parser.js and syntax.json are generated: rebuild them with
// `./gradlew :neoskript-web:website` (or `pnpm parser`), never edit them here.

/** Builds the syntax registry from syntax.json's text; returns what it parses against, e.g. "Skript 2.17.0 syntax". */
export function init(syntaxData: string): string;

/** Parses one script; returns ParseResult (src/lib/parser.ts) as JSON. */
export function parse(source: string): string;
