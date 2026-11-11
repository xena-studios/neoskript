// The /parser page: a textarea over a highlighted copy of its text, parsed in
// a Web Worker as you type, with the problems and an outline beside it.
//
// The highlight layer (<pre>) and the textarea sit in one grid cell with the
// same font and padding; the <pre> sizes the cell and the textarea stretches
// over it, so the two line up without measuring anything. Only scrolling to a
// line sets a property from script (CSSOM), never a style attribute.
import { commentStart, tokens } from "../lib/skript";
import { decodeShare, encodeShare } from "../lib/parser";
import type { Diagnostic, FromWorker, OutlineItem, ParseResult, ToWorker } from "../lib/parser";
import mistakes from "../parser/examples/mistakes.sk?raw";
import warps from "../parser/examples/warps.sk?raw";
import tpa from "../parser/examples/tpa.sk?raw";
import ParserWorker from "./parser-worker.ts?worker";

const EXAMPLES: Record<string, string> = { mistakes, warps, tpa };
const DEBOUNCE_MS = 250;

const $ = <T extends HTMLElement>(id: string) => document.getElementById(id) as T;
const textarea = $<HTMLTextAreaElement>("source");
const highlight = $<HTMLPreElement>("highlight");
const gutter = $<HTMLDivElement>("gutter");
const editor = $<HTMLDivElement>("editor");
const status = $<HTMLParagraphElement>("status");
const timing = $<HTMLSpanElement>("timing");
const about = $<HTMLSpanElement>("about");
const problems = $<HTMLOListElement>("problems");
const outline = $<HTMLOListElement>("outline");
const noOutline = $<HTMLParagraphElement>("no-outline");
const share = $<HTMLButtonElement>("share");
const shareStatus = $<HTMLSpanElement>("share-status");
const reducedMotion = matchMedia("(prefers-reduced-motion: reduce)");

// ---- Parsing, in the worker. One parse at a time; edits made meanwhile wait
// for it, and only the newest is parsed next.
const worker = new ParserWorker();
let ready = false;
let nextId = 0;
let inFlight: number | null = null;
let queued: string | null = null;
let parsedSource = "";
let result: ParseResult = { diagnostics: [], outline: [] };
let timer: ReturnType<typeof setTimeout> | undefined;

function requestParse(source: string) {
  if (!ready || inFlight !== null) {
    queued = source;
    return;
  }
  inFlight = ++nextId;
  parsedSource = source;
  worker.postMessage({ id: inFlight, source } satisfies ToWorker);
}

worker.onmessage = (event: MessageEvent<FromWorker>) => {
  const message = event.data;
  if (message.type === "ready") {
    ready = true;
    about.textContent = message.about;
    requestParse(textarea.value);
    return;
  }
  if (message.type === "failed") {
    setStatus("The parser couldn't load. Reload the page to try again.");
    return;
  }
  inFlight = null;
  if (message.type === "result") {
    result = message.result;
    timing.textContent = `Parsed in ${Math.max(1, Math.round(message.ms))} ms`;
    showResult();
  } else {
    result = { diagnostics: [], outline: [] };
    timing.textContent = "";
    showResult();
    setStatus("The parser stopped on this script, which is a bug in NeoSkript. Please report it with the script.");
  }
  if (queued !== null) {
    const next = queued;
    queued = null;
    requestParse(next);
  }
};
worker.onerror = () => setStatus("The parser couldn't load. Reload the page to try again.");

// ---- Rendering.
function setStatus(text: string) {
  // Only touch the live region when the words change, so screen readers
  // announce new results, not every keystroke.
  if (status.textContent !== text) status.textContent = text;
}

const plural = (n: number, word: string) => `${n} ${word}${n === 1 ? "" : "s"}`;

function showResult() {
  const sorted = [...result.diagnostics].sort((a, b) => a.line - b.line || a.column - b.column);
  const errors = sorted.filter((d) => d.severity === "error").length;
  const warnings = sorted.length - errors;
  setStatus(
    sorted.length === 0
      ? "No problems found."
      : [errors && plural(errors, "error"), warnings && plural(warnings, "warning")].filter(Boolean).join(" and ") + ".",
  );
  problems.replaceChildren(...sorted.map(problemItem));
  outline.replaceChildren(...result.outline.map(outlineItem));
  noOutline.hidden = result.outline.length > 0;
  render();
}

function el<K extends keyof HTMLElementTagNameMap>(tag: K, className?: string, text?: string) {
  const node = document.createElement(tag);
  if (className) node.className = className;
  if (text !== undefined) node.textContent = text;
  return node;
}

function problemItem(d: Diagnostic) {
  const button = el("button", `jump ${d.severity}`);
  button.type = "button";
  button.append(
    el("span", "label sev", d.severity === "error" ? "Error" : "Warning"),
    el("span", "where", `Line ${d.line}`),
    el("span", "message", d.message),
  );
  button.addEventListener("click", () => jumpTo(d.line, d.column, d.length));
  const item = el("li");
  item.append(button);
  return item;
}

function outlineItem(o: OutlineItem) {
  const button = el("button", "jump");
  button.type = "button";
  button.append(el("span", "label kind", o.kind), el("span", "where", `Line ${o.line}`), el("code", "message", o.text));
  button.addEventListener("click", () => jumpTo(o.line, 0, 0));
  const item = el("li");
  item.append(button);
  return item;
}

type Mark = { from: number; to: number; severity: Diagnostic["severity"] };

/** Redraws the highlight layer and the gutter from the textarea's text and the last result. */
function render() {
  const lines = textarea.value.split("\n");
  // Diagnostics are for the text that was parsed; once it's changed, keep
  // them on the lines they were on but drop the exact spans.
  const fresh = textarea.value === parsedSource;
  const marks = new Map<number, Mark[]>();
  const worst = new Map<number, Diagnostic["severity"]>();
  for (const d of result.diagnostics) {
    if (d.line < 1 || d.line > lines.length) continue;
    if (worst.get(d.line) !== "error") worst.set(d.line, d.severity);
    if (!fresh) continue;
    const text = lines[d.line - 1];
    const from = d.length === 0 ? text.length - text.trimStart().length : Math.min(d.column, text.length);
    const to = d.length === 0 ? text.length : Math.min(d.column + d.length, text.length);
    if (to > from) marks.set(d.line, [...(marks.get(d.line) ?? []), { from, to, severity: d.severity }]);
  }

  const lineNodes: HTMLElement[] = [];
  const numberNodes: HTMLElement[] = [];
  let inBlockComment = false;
  lines.forEach((text, i) => {
    const number = i + 1;
    const line = el("span", "line");
    const delimiter = text.trim() === "###";
    if (delimiter || inBlockComment) {
      line.append(...spans([{ text, kind: "comment" }], marks.get(number)));
    } else {
      const parts = tokens(text);
      // The overlay has to hold exactly the textarea's text, or the two drift apart.
      const exact = parts.map((p) => p.text).join("") === text;
      line.append(...spans(exact ? parts : [{ text, kind: "text" }], marks.get(number)));
    }
    if (delimiter) inBlockComment = !inBlockComment;
    const severity = worst.get(number);
    const numberNode = el("span", severity ? `n ${severity}` : "n", String(number));
    if (severity) line.classList.add(`has-${severity}`);
    lineNodes.push(line);
    numberNodes.push(numberNode);
  });
  highlight.replaceChildren(...lineNodes);
  gutter.replaceChildren(...numberNodes);
}

/** The line's tokens as spans, split where a diagnostic's span starts or ends. */
function spans(parts: { text: string; kind: string }[], marks: Mark[] = []): Node[] {
  if (marks.length === 0) {
    return parts.map((p) => (p.kind === "text" ? document.createTextNode(p.text) : el("span", p.kind, p.text)));
  }
  const severityAt = (i: number) => {
    let found: Mark["severity"] | undefined;
    for (const m of marks) if (i >= m.from && i < m.to && found !== "error") found = m.severity;
    return found;
  };
  const out: Node[] = [];
  let offset = 0;
  for (const part of parts) {
    let start = 0;
    while (start < part.text.length) {
      const severity = severityAt(offset + start);
      let end = start + 1;
      while (end < part.text.length && severityAt(offset + end) === severity) end++;
      const piece = part.text.slice(start, end);
      const classes = [part.kind !== "text" && part.kind, severity && `mark-${severity}`].filter(Boolean).join(" ");
      out.push(classes ? el("span", classes, piece) : document.createTextNode(piece));
      start = end;
    }
    offset += part.text.length;
  }
  return out;
}

/** Selects a line (or a span of it) in the editor and scrolls it into view. */
function jumpTo(lineNumber: number, column: number, length: number) {
  const lines = textarea.value.split("\n");
  const index = Math.min(Math.max(lineNumber, 1), lines.length) - 1;
  let start = 0;
  for (let i = 0; i < index; i++) start += lines[i].length + 1;
  const text = lines[index];
  const from = length === 0 ? text.length - text.trimStart().length : Math.min(column, text.length);
  const to = length === 0 ? text.length : Math.min(column + length, text.length);
  textarea.focus({ preventScroll: true });
  textarea.setSelectionRange(start + from, start + to);
  const behavior: ScrollBehavior = reducedMotion.matches ? "auto" : "smooth";
  editor.scrollIntoView({ block: "nearest", behavior });
  const line = highlight.children[index] as HTMLElement | undefined;
  if (line) {
    const top = line.offsetTop - editor.clientHeight / 2 + line.offsetHeight / 2;
    editor.scrollTo({ top: Math.max(0, top), left: 0, behavior });
    line.classList.add("current");
    setTimeout(() => line.classList.remove("current"), 1600);
  }
}

// ---- Editing. Tab indents and Shift+Tab outdents, using the script's own
// indentation (a tab, or as many spaces as its first indented line); Enter
// keeps the indentation and adds a level after a line ending in ':'. Esc
// then Tab leaves the editor, so the keyboard is never trapped.
let tabLeaves = false;

function indentUnit() {
  const match = textarea.value.match(/^([ \t]+)\S/m);
  if (!match || match[1].startsWith("\t")) return "\t";
  return match[1].length <= 8 ? match[1] : "    ";
}

/** Replaces [start, end) with text, keeping the browser's undo history where it can. */
function replaceRange(start: number, end: number, text: string) {
  textarea.setSelectionRange(start, end);
  if (!document.execCommand("insertText", false, text)) {
    textarea.setRangeText(text, start, end, "end");
    textarea.dispatchEvent(new Event("input", { bubbles: true }));
  }
}

function shiftLines(outdent: boolean) {
  const { value, selectionStart, selectionEnd } = textarea;
  const unit = indentUnit();
  const blockStart = value.lastIndexOf("\n", selectionStart - 1) + 1;
  const endAt = selectionEnd > selectionStart && value[selectionEnd - 1] === "\n" ? selectionEnd - 1 : selectionEnd;
  let blockEnd = value.indexOf("\n", endAt);
  if (blockEnd < 0) blockEnd = value.length;
  const lines = value.slice(blockStart, blockEnd).split("\n");
  let firstDelta = 0;
  let totalDelta = 0;
  const changed = lines.map((line, i) => {
    let next = line;
    if (outdent) {
      const spaces = line.match(/^ */)![0].length;
      next = line.startsWith("\t") ? line.slice(1) : line.slice(Math.min(spaces, unit === "\t" ? 4 : unit.length));
    } else if (line.length > 0 || lines.length === 1) {
      next = unit + line;
    }
    if (i === 0) firstDelta = next.length - line.length;
    totalDelta += next.length - line.length;
    return next;
  });
  if (totalDelta === 0) return;
  replaceRange(blockStart, blockEnd, changed.join("\n"));
  const newStart = Math.max(blockStart, selectionStart + firstDelta);
  textarea.setSelectionRange(newStart, Math.max(newStart, selectionEnd + totalDelta));
}

textarea.addEventListener("keydown", (event) => {
  if (event.isComposing) return;
  if (event.key === "Escape") {
    tabLeaves = true;
    return;
  }
  if (event.key === "Tab") {
    if (tabLeaves || event.ctrlKey || event.metaKey || event.altKey) {
      tabLeaves = false;
      return;
    }
    event.preventDefault();
    const { selectionStart, selectionEnd, value } = textarea;
    const multiline = value.slice(selectionStart, selectionEnd).includes("\n");
    if (event.shiftKey || multiline) shiftLines(event.shiftKey);
    else replaceRange(selectionStart, selectionEnd, indentUnit());
    return;
  }
  tabLeaves = false;
  if (event.key === "Enter" && !event.shiftKey && !event.ctrlKey && !event.metaKey && !event.altKey) {
    const { selectionStart, selectionEnd, value } = textarea;
    const lineStart = value.lastIndexOf("\n", selectionStart - 1) + 1;
    const before = value.slice(lineStart, selectionStart);
    const indent = before.match(/^[ \t]*/)![0];
    const code = commentStart(before) < 0 ? before : before.slice(0, commentStart(before));
    const opens = code.trimEnd().endsWith(":");
    event.preventDefault();
    replaceRange(selectionStart, selectionEnd, "\n" + indent + (opens ? indentUnit() : ""));
  }
});

textarea.addEventListener("blur", () => {
  tabLeaves = false;
});

let edited = false;
textarea.addEventListener("input", () => {
  edited = true;
  render();
  clearTimeout(timer);
  timer = setTimeout(() => {
    requestParse(textarea.value);
    if (edited) void updateHash();
  }, DEBOUNCE_MS);
});

// ---- Loading scripts: examples, and share links.
function load(source: string, keepUndo: boolean) {
  if (keepUndo) {
    // Through the editing commands, so Ctrl+Z brings back what was there.
    textarea.focus({ preventScroll: true });
    replaceRange(0, textarea.value.length, source);
    textarea.setSelectionRange(0, 0);
    textarea.blur();
  } else {
    textarea.value = source;
    render();
  }
  editor.scrollTo({ top: 0, left: 0 });
  clearTimeout(timer);
  requestParse(source);
}

for (const button of document.querySelectorAll<HTMLButtonElement>("[data-example]")) {
  button.addEventListener("click", () => {
    const source = EXAMPLES[button.dataset.example!];
    if (source === undefined) return;
    load(source, true);
    edited = false;
    history.replaceState(null, "", location.pathname);
    button.focus();
  });
}

async function updateHash() {
  const hash = textarea.value.trim() === "" ? "" : await encodeShare(textarea.value);
  history.replaceState(null, "", hash ? hash : location.pathname);
}

async function loadFromHash() {
  const shared = await decodeShare(location.hash);
  if (shared instanceof Error) {
    load(EXAMPLES.mistakes, false);
    shareStatus.textContent = "The script in this link couldn't be read. It may have been cut off when it was copied.";
    return;
  }
  load(shared ?? EXAMPLES.mistakes, false);
  edited = shared !== null;
}

window.addEventListener("hashchange", () => void loadFromHash());

let shareReset: ReturnType<typeof setTimeout> | undefined;
share.addEventListener("click", async () => {
  await updateHash();
  clearTimeout(shareReset);
  try {
    await navigator.clipboard.writeText(location.href);
    shareStatus.textContent = "Link copied.";
  } catch {
    shareStatus.textContent = "Copy the link from the address bar.";
  }
  shareReset = setTimeout(() => (shareStatus.textContent = ""), 4000);
});

void loadFromHash();
