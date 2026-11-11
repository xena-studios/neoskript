/// <reference lib="webworker" />
// The parser's Web Worker: loads NeoSkript's frontend (compiled by TeaVM) and
// the syntax data, then parses each script the page sends, off the main thread
// so typing stays smooth.
import { init, parse } from "../parser/neoskript-parser.js";
import syntaxUrl from "../parser/syntax.json?url";
import type { FromWorker, ToWorker } from "../lib/parser";

const post = (message: FromWorker) => self.postMessage(message);

const ready = fetch(syntaxUrl)
  .then((response) => {
    if (!response.ok) throw new Error(`syntax data: HTTP ${response.status}`);
    return response.text();
  })
  .then((data) => init(data));

ready.then(
  (about) => post({ type: "ready", about }),
  (e) => post({ type: "failed", message: String(e) }),
);

self.onmessage = async (event: MessageEvent<ToWorker>) => {
  const { id, source } = event.data;
  try {
    await ready;
  } catch {
    return;
  }
  const start = performance.now();
  try {
    const result = JSON.parse(parse(source));
    post({ type: "result", id, result, ms: performance.now() - start });
  } catch (e) {
    post({ type: "crash", id, message: String(e) });
  }
};
