// What the in-browser parser reports (neoskript-web's WebParser), the messages
// between the page and its worker, the share-link encoding, and the counts the
// parser's status bar shows that the parser doesn't report.
import { commentStart } from "./skript";

export type Diagnostic = {
  severity: "error" | "warning";
  /** 1-based. */
  line: number;
  /** 0-based, in the line's full text, indentation included. */
  column: number;
  /** 0 when the problem covers the whole line. */
  length: number;
  message: string;
};

export type OutlineItem = { kind: string; line: number; text: string };

export type ParseResult = { diagnostics: Diagnostic[]; outline: OutlineItem[] };

export type FromWorker =
  | { type: "ready"; about: string }
  | { type: "failed"; message: string }
  | { type: "result"; id: number; result: ParseResult; ms: number }
  | { type: "crash"; id: number; message: string };

export type ToWorker = { id: number; source: string };

// Share links carry the script in the URL fragment, which browsers never send
// to the server: deflated, then base64url. #s=<data>.
const PREFIX = "#s=";

export async function encodeShare(source: string): Promise<string> {
  const stream = new Blob([source]).stream().pipeThrough(new CompressionStream("deflate-raw"));
  const bytes = new Uint8Array(await new Response(stream).arrayBuffer());
  let binary = "";
  for (let i = 0; i < bytes.length; i += 0x8000) {
    binary += String.fromCharCode(...bytes.subarray(i, i + 0x8000));
  }
  return PREFIX + btoa(binary).replaceAll("+", "-").replaceAll("/", "_").replace(/=+$/, "");
}

/** The script in a share fragment, null if there's none, or an Error if it can't be read. */
export async function decodeShare(hash: string): Promise<string | null | Error> {
  if (!hash.startsWith(PREFIX)) return null;
  try {
    const base64 = hash.slice(PREFIX.length).replaceAll("-", "+").replaceAll("_", "/");
    const bytes = Uint8Array.from(atob(base64), (c) => c.charCodeAt(0));
    const stream = new Blob([bytes]).stream().pipeThrough(new DecompressionStream("deflate-raw"));
    return await new Response(stream).text();
  } catch (e) {
    return e instanceof Error ? e : new Error(String(e));
  }
}

/**
 * The script's variables, for the parser's status bar: each distinct {name} outside comments, with the line
 * it's first used on and how often. A rough count, as the status bar says: {_local} and {list::*} count,
 * {@option} references don't, and %expressions% inside a name stay as written.
 */
export function variables(source: string): { name: string; line: number; uses: number }[] {
  const found = new Map<string, { name: string; line: number; uses: number }>();
  let inBlockComment = false;
  source.split("\n").forEach((text, i) => {
    if (text.trim() === "###") {
      inBlockComment = !inBlockComment;
      return;
    }
    if (inBlockComment) return;
    const end = commentStart(text);
    for (const name of braces(end < 0 ? text : text.slice(0, end))) {
      if (name.startsWith("@")) continue;
      const key = name.toLowerCase();
      const seen = found.get(key);
      if (seen) seen.uses++;
      else found.set(key, { name, line: i + 1, uses: 1 });
    }
  });
  return [...found.values()];
}

/** The outermost {...} of a line of code, with any nested ones left inside. */
function braces(code: string): string[] {
  const out: string[] = [];
  let depth = 0;
  let start = -1;
  for (let i = 0; i < code.length; i++) {
    if (code[i] === "{") {
      if (depth++ === 0) start = i;
    } else if (code[i] === "}" && depth > 0 && --depth === 0) {
      out.push(code.slice(start + 1, i));
    }
  }
  return out;
}
