// A small Skript highlighter for the custom pages, in the brand's code style:
// keywords magenta, strings pale, punctuation and comments muted, everything
// else text. The landing page colours short, known samples with it, and the
// parser's editor colours whole scripts; the docs use Starlight's Expressive Code.
const KEYWORDS = new Set([
  "on", "send", "to", "if", "else", "is", "not", "set", "add", "remove", "broadcast",
  "of", "split", "at", "repeated", "times", "where", "replaced", "with", "whether",
  "matches", "function", "loop", "stop", "return", "and", "or",
]);

export type Token = { text: string; kind: "kw" | "str" | "punct" | "comment" | "text" };

export function tokens(line: string): Token[] {
  const out: Token[] = [];
  const start = commentStart(line);
  const code = start < 0 ? line : line.slice(0, start);
  // Every character lands in a token (the editor overlays these on the text),
  // so a string left open runs to the end of the line.
  const re = /("(?:[^"]|"")*"?)|([(),:])|([A-Za-z]+)|(\s+|[^\sA-Za-z"(),:]+)/g;
  for (const [, str, punct, word, other] of code.matchAll(re)) {
    if (str) out.push({ text: str, kind: "str" });
    else if (punct) out.push({ text: punct, kind: "punct" });
    else if (word) out.push({ text: word, kind: KEYWORDS.has(word) ? "kw" : "text" });
    else out.push({ text: other, kind: "text" });
  }
  if (start >= 0) out.push({ text: line.slice(start), kind: "comment" });
  return out;
}

/**
 * Where the line's comment starts, or -1. As NeoSkript's parser reads it
 * (neoskript-frontend's Comments.java): a # outside quotes and {variables}
 * starts a comment, and ## is a literal #.
 */
export function commentStart(line: string): number {
  const contexts: ("code" | "string" | "variable" | "expression")[] = ["code"];
  for (let i = 0; i < line.length; i++) {
    const c = line[i];
    const next = line[i + 1];
    const context = contexts[contexts.length - 1];
    if (context === "code" || context === "expression") {
      if (c === "#") {
        if (context === "code" && next === "#") {
          i++;
          continue;
        }
        return i;
      }
      if (c === '"') contexts.push("string");
      else if (c === "{") contexts.push("variable");
      else if (c === "%" && context === "expression") contexts.pop();
    } else if (context === "string") {
      if (c === '"') {
        if (next === '"') i++;
        else contexts.pop();
      } else if (c === "%") {
        if (next === "%") i++;
        else contexts.push("expression");
      }
    } else if (c === "}") {
      contexts.pop();
    } else if (c === "%") {
      contexts.push("expression");
    }
  }
  return -1;
}
