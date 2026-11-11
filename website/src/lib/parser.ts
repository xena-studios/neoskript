// What the in-browser parser reports (neoskript-web's WebParser), the messages
// between the page and its worker, and the share-link encoding.

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
