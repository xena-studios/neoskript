// A small Skript highlighter for the custom pages, in the brand's code style:
// keywords magenta, strings pale, punctuation muted, everything else text.
// It only has to colour the short, known samples these pages show; the docs
// use Starlight's Expressive Code.
const KEYWORDS = new Set([
  "on", "send", "to", "if", "else", "is", "not", "set", "add", "remove", "broadcast",
  "of", "split", "at", "repeated", "times", "where", "replaced", "with", "whether",
  "matches", "function", "loop", "stop", "return", "and", "or",
]);

export type Token = { text: string; kind: "kw" | "str" | "punct" | "text" };

export function tokens(line: string): Token[] {
  const out: Token[] = [];
  const re = /("(?:[^"]|"")*")|([(),:])|([A-Za-z]+)|(\s+|[^\sA-Za-z"(),:]+)/g;
  for (const [, str, punct, word, other] of line.matchAll(re)) {
    if (str) out.push({ text: str, kind: "str" });
    else if (punct) out.push({ text: punct, kind: "punct" });
    else if (word) out.push({ text: word, kind: KEYWORDS.has(word) ? "kw" : "text" });
    else out.push({ text: other, kind: "text" });
  }
  return out;
}
