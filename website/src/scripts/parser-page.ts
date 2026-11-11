// The /parser page: a Monaco editor (loaded lazily, src/scripts/parser-editor.ts) with the script's problems
// beside it, parsed by NeoSkript's own frontend in a Web Worker (parser-worker.ts). Several files can be open;
// they're kept in this browser's localStorage, never uploaded. Problems show as cards and as Monaco markers
// (squiggles, overview ruler, minimap); the status bar counts errors, warnings, functions, variables and
// commands, and each count lists its items.
import { decodeShare, encodeShare, variables } from "../lib/parser";
import type { Diagnostic, FromWorker, OutlineItem, ParseResult, ToWorker } from "../lib/parser";
import { icon } from "../lib/icons";
import mistakes from "../parser/examples/mistakes.sk?raw";
import warps from "../parser/examples/warps.sk?raw";
import tpa from "../parser/examples/tpa.sk?raw";
import ParserWorker from "./parser-worker.ts?worker";
import type * as Monaco from "monaco-editor/editor/editor.api.js";

const EXAMPLES: Record<string, { name: string; source: string }> = {
  mistakes: { name: "mistakes.sk", source: mistakes },
  warps: { name: "warps.sk", source: warps },
  tpa: { name: "tpa.sk", source: tpa },
};
const SNIPPETS: Record<string, string> = {
  event: 'on join:\n\tsend "Welcome, %player%!" to player\n',
  command: 'command /hello:\n\tdescription: Says hello.\n\ttrigger:\n\t\tsend "Hello, %player%!" to player\n',
  function: 'function greet(p: player):\n\tsend "Hello, %{_p}%!" to {_p}\n',
  every: 'every 5 minutes:\n\tbroadcast "Remember to vote!"\n',
  options: "options:\n\tprefix: &8[&dServer&8]&r\n",
};
const DEBOUNCE_MS = 250;
const FILES_KEY = "neoskript.parser.files";
const SETTINGS_KEY = "neoskript.parser.settings";

const $ = <T extends HTMLElement>(id: string) => document.getElementById(id) as T;
const reducedMotion = matchMedia("(prefers-reduced-motion: reduce)");

// ---- Storage. Everything is wrapped: private windows and blocked storage just mean nothing is remembered.
function read<T>(key: string): T | null {
  try {
    const text = localStorage.getItem(key);
    return text ? (JSON.parse(text) as T) : null;
  } catch {
    return null;
  }
}
function write(key: string, value: unknown) {
  try {
    localStorage.setItem(key, JSON.stringify(value));
    return true;
  } catch {
    return false;
  }
}

type Settings = { live: boolean; minimap: boolean; wordWrap: boolean; tabFocus: boolean; fontSize: number };
const settings: Settings = { live: true, minimap: true, wordWrap: false, tabFocus: false, fontSize: 14, ...read<Partial<Settings>>(SETTINGS_KEY) };

type File = { id: string; name: string; source: string };
type Open = File & { model?: Monaco.editor.ITextModel; view?: Monaco.editor.ICodeEditorViewState | null; result?: ParseResult; parsed?: string; ms?: number };
const stored = read<{ active: string; files: File[] }>(FILES_KEY);
const firstVisit = stored === null;
let files: Open[] = (stored?.files ?? []).filter((f) => typeof f.source === "string").map((f) => ({ ...f }));
let active: Open | undefined = files.find((f) => f.id === stored?.active) ?? files[0];

const newId = () => Math.random().toString(36).slice(2, 10);
const sourceOf = (f: Open) => f.model?.getValue() ?? f.source;

let saveTimer: ReturnType<typeof setTimeout> | undefined;
function save(now = false) {
  clearTimeout(saveTimer);
  const run = () => write(FILES_KEY, { active: active?.id, files: files.map((f) => ({ id: f.id, name: f.name, source: sourceOf(f) })) });
  if (now) return run();
  saveTimer = setTimeout(run, 400);
  return true;
}

function uniqueName(wanted: string) {
  const base = wanted.replace(/\.sk$/i, "") || "Untitled";
  let name = `${base}.sk`;
  for (let n = 2; files.some((f) => f.name.toLowerCase() === name.toLowerCase()); n++) name = `${base} ${n}.sk`;
  return name;
}

// ---- Elements.
const editorHost = $<HTMLDivElement>("editor");
const editorNote = $<HTMLParagraphElement>("editor-note");
const about = $<HTMLSpanElement>("about");
const waiting = $<HTMLDivElement>("waiting");
const cards = $<HTMLOListElement>("cards");
const resultsTitle = $<HTMLHeadingElement>("results-title");
const showProblems = $<HTMLButtonElement>("show-problems");
const statusWord = $<HTMLSpanElement>("status-word");
const timing = $<HTMLSpanElement>("timing");
const live = $<HTMLParagraphElement>("live");
const toastEl = $<HTMLDivElement>("toast");
const tabName = $<HTMLButtonElement>("tab-name");
const tabInput = $<HTMLInputElement>("tab-input");
const fileInput = $<HTMLInputElement>("file-input");
const filesMenu = $<HTMLUListElement>("files-menu");

let toastTimer: ReturnType<typeof setTimeout> | undefined;
function toast(text: string) {
  toastEl.textContent = text;
  toastEl.classList.add("shown");
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => toastEl.classList.remove("shown"), 4000);
}

/** Only touches the live region when the words change, so screen readers hear new results, not keystrokes. */
function announce(text: string) {
  if (live.textContent !== text) live.textContent = text;
}

// ---- Parsing, in the worker. One parse at a time; a request made meanwhile waits, and only the newest runs.
const worker = new ParserWorker();
let ready = false;
let nextId = 0;
let inFlight: { id: number; file: Open; source: string } | null = null;
let queued: Open | null = null;

function requestParse(file: Open | undefined = active) {
  if (!file) return;
  if (!ready || inFlight) {
    queued = file;
    if (ready) setStatus("Parsing");
    return;
  }
  const source = sourceOf(file);
  inFlight = { id: ++nextId, file, source };
  setStatus("Parsing");
  worker.postMessage({ id: inFlight.id, source } satisfies ToWorker);
}

worker.onmessage = (event: MessageEvent<FromWorker>) => {
  const message = event.data;
  if (message.type === "ready") {
    ready = true;
    about.textContent = message.about;
    if (queued) {
      const next = queued;
      queued = null;
      requestParse(next);
    } else {
      setStatus("Ready");
    }
    return;
  }
  if (message.type === "failed") {
    setStatus("Failed");
    announce("The parser couldn't load. Reload the page to try again.");
    toast("The parser couldn't load. Reload the page to try again.");
    return;
  }
  const done = inFlight;
  inFlight = null;
  if (done && message.id === done.id) {
    if (message.type === "result") {
      done.file.result = message.result;
      done.file.ms = message.ms;
    } else {
      done.file.result = { diagnostics: [], outline: [] };
      done.file.ms = undefined;
      toast("The parser stopped on this script, which is a bug in NeoSkript. Please report it with the script.");
    }
    done.file.parsed = done.source;
    if (done.file === active) showResult(message.type === "crash");
  }
  if (queued) {
    const next = queued;
    queued = null;
    requestParse(next);
  }
};
worker.onerror = () => {
  setStatus("Failed");
  toast("The parser couldn't load. Reload the page to try again.");
};

// ---- Results: cards, markers, counts.
type View = "problems" | "errors" | "warnings" | "functions" | "variables" | "commands";
let view: View = "problems";

function setStatus(word: "Loading" | "Ready" | "Parsing" | "Parsed" | "Changed" | "Failed") {
  statusWord.textContent = word;
  statusWord.classList.toggle("parsed", word === "Parsed");
}

const plural = (n: number, word: string) => `${n} ${word}${n === 1 ? "" : "s"}`;

function el<K extends keyof HTMLElementTagNameMap>(tag: K, className?: string, text?: string) {
  const node = document.createElement(tag);
  if (className) node.className = className;
  if (text !== undefined) node.textContent = text;
  return node;
}

let cardId = 0;
/** A collapsible card: a header with what and where, a Go to line button, then its body. */
function card(kind: string, title: [string, string], line: number, body: Node[], goto: () => void) {
  const id = `card-${++cardId}`;
  const item = el("li", `card ${kind}`);
  const head = el("div", "card-head");
  const toggle = el("button", "toggle");
  toggle.type = "button";
  toggle.setAttribute("aria-expanded", "true");
  toggle.setAttribute("aria-controls", id);
  toggle.append(el("span", "sev", title[0]), document.createTextNode(" on "), el("strong", undefined, title[1]), icon("chevron"));
  const go = el("button", "goto", "Go to line");
  go.type = "button";
  go.setAttribute("aria-label", `Go to line ${line}`);
  go.addEventListener("click", goto);
  head.append(toggle, go);
  const content = el("div", "card-body");
  content.id = id;
  content.append(...body);
  toggle.addEventListener("click", () => {
    const open = toggle.getAttribute("aria-expanded") !== "true";
    toggle.setAttribute("aria-expanded", String(open));
    content.hidden = !open;
  });
  item.append(head, content);
  return item;
}

function lineText(source: string, line: number) {
  return source.split("\n")[line - 1] ?? "";
}

function code(text: string) {
  const pre = el("pre", "code");
  pre.append(el("code", undefined, text.replace(/^\s+/, "")));
  return pre;
}

function counts(file: Open) {
  const result = file.result ?? { diagnostics: [], outline: [] };
  const source = file.parsed ?? sourceOf(file);
  const errors = result.diagnostics.filter((d) => d.severity === "error");
  const warnings = result.diagnostics.filter((d) => d.severity === "warning");
  const byKind = (kind: string) => result.outline.filter((o) => o.kind === kind);
  return { errors, warnings, functions: byKind("function"), commands: byKind("command"), variables: variables(source) };
}

function showResult(crashed = false) {
  const file = active;
  if (!file) return;
  applyMarkers(file);
  const parsed = file.result !== undefined;
  const c = counts(file);
  for (const key of ["errors", "warnings", "functions", "variables", "commands"] as const) {
    $(`count-${key}`).textContent = parsed ? String(c[key].length) : "0";
  }
  timing.textContent = parsed && file.ms !== undefined ? `${Math.max(1, Math.round(file.ms))} ms` : "";
  if (!parsed) {
    setStatus(ready ? "Ready" : "Loading");
  } else if (crashed) {
    setStatus("Failed");
  } else {
    setStatus(sourceOf(file) === file.parsed ? "Parsed" : "Changed");
  }
  if (parsed && !crashed) {
    const problems = c.errors.length + c.warnings.length;
    announce(problems === 0 ? "No problems found." : [c.errors.length && plural(c.errors.length, "error"), c.warnings.length && plural(c.warnings.length, "warning")].filter(Boolean).join(" and ") + ".");
  }
  renderCards();
}

function renderCards() {
  const file = active;
  for (const button of document.querySelectorAll<HTMLButtonElement>(".count")) {
    button.setAttribute("aria-pressed", String(button.dataset.view === view));
  }
  showProblems.hidden = view === "problems";
  const titles: Record<View, string> = { problems: "Problems", errors: "Errors", warnings: "Warnings", functions: "Functions", variables: "Variables", commands: "Commands" };
  resultsTitle.textContent = titles[view];
  if (!file || file.result === undefined) {
    waiting.hidden = false;
    cards.hidden = true;
    cards.replaceChildren();
    return;
  }
  waiting.hidden = true;
  cards.hidden = false;
  const source = file.parsed ?? "";
  const c = counts(file);
  const items: HTMLElement[] = [];
  const diagnostic = (d: Diagnostic) =>
    card(d.severity, [d.severity === "error" ? "Error" : "Warning", `Line ${d.line}`], d.line, [el("p", "message", d.message), code(lineText(source, d.line))], () => jumpTo(d.line, d.column, d.length));
  const structure = (o: OutlineItem, label: string) => card("outline", [label, `Line ${o.line}`], o.line, [code(o.text)], () => jumpTo(o.line, 0, 0));
  const sortByLine = (a: Diagnostic, b: Diagnostic) => a.line - b.line || a.column - b.column;

  if (view === "problems" || view === "errors") items.push(...[...c.errors].sort(sortByLine).map(diagnostic));
  if (view === "problems" || view === "warnings") items.push(...[...c.warnings].sort(sortByLine).map(diagnostic));
  if (view === "functions") items.push(...c.functions.map((o) => structure(o, "Function")));
  if (view === "commands") items.push(...c.commands.map((o) => structure(o, "Command")));
  if (view === "variables") {
    items.push(...c.variables.map((v) =>
      card("outline", ["Variable", `Line ${v.line}`], v.line, [code(`{${v.name}}`), el("p", "muted", `Used ${v.uses === 1 ? "once" : `${v.uses} times`}.`)], () => jumpTo(v.line, 0, 0)),
    ));
  }
  if (items.length === 0) {
    const empty: Record<View, string> = {
      problems: "No problems found.",
      errors: "No errors.",
      warnings: "No warnings.",
      functions: "No functions in this script.",
      variables: "No variables in this script.",
      commands: "No commands in this script.",
    };
    const item = el("li", "card ok");
    const body = el("div", "card-body");
    body.append(el("p", "message", empty[view]));
    if (view === "problems") body.append(el("p", "muted", "NeoSkript's parser accepts this script. It can't check what only a running server knows, such as aliases and other plugins' syntax."));
    item.append(el("div", "card-head"), body);
    items.push(item);
  }
  cards.replaceChildren(...items);
}

function setView(next: View) {
  view = view === next ? "problems" : next;
  renderCards();
}

for (const button of document.querySelectorAll<HTMLButtonElement>(".count")) {
  button.addEventListener("click", () => setView(button.dataset.view as View));
}
showProblems.addEventListener("click", () => setView("problems"));

// ---- The editor.
let monaco: typeof Monaco | undefined;
let editor: Monaco.editor.IStandaloneCodeEditor | undefined;

function applyMarkers(file: Open) {
  if (!monaco || !file.model) return;
  const model = file.model;
  // Markers are for the text that was parsed. Monaco moves them with later edits, so they stay put until the
  // next parse replaces them; a result for text that's since changed is still the best there is.
  const markers = (file.result?.diagnostics ?? []).map((d) => {
    const line = Math.min(Math.max(d.line, 1), model.getLineCount());
    const text = model.getLineContent(line);
    const from = d.length === 0 ? text.length - text.trimStart().length : Math.min(d.column, text.length);
    const to = d.length === 0 ? text.length : Math.min(d.column + d.length, text.length);
    return {
      severity: d.severity === "error" ? monaco!.MarkerSeverity.Error : monaco!.MarkerSeverity.Warning,
      message: d.message,
      source: "NeoSkript",
      startLineNumber: line,
      startColumn: from + 1,
      endLineNumber: line,
      endColumn: Math.max(to, from + 1) + 1,
    };
  });
  monaco.editor.setModelMarkers(model, "neoskript", markers);
}

/** Selects a line (or a span of it) in the editor, scrolls it to the middle and focuses the editor. */
function jumpTo(lineNumber: number, column: number, length: number) {
  if (!editor || !active?.model) return;
  const model = active.model;
  const line = Math.min(Math.max(lineNumber, 1), model.getLineCount());
  const text = model.getLineContent(line);
  const from = length === 0 ? text.length - text.trimStart().length : Math.min(column, text.length);
  const to = length === 0 ? text.length : Math.min(column + length, text.length);
  editor.setSelection({ startLineNumber: line, startColumn: from + 1, endLineNumber: line, endColumn: to + 1 });
  editor.revealLineInCenter(line, reducedMotion.matches ? 1 : 0);
  // On a phone the editor is above the results: bring it back into view.
  editorHost.scrollIntoView({ block: "nearest", behavior: reducedMotion.matches ? "auto" : "smooth" });
  editor.focus();
}

let parseTimer: ReturnType<typeof setTimeout> | undefined;
function modelFor(file: Open) {
  if (!monaco) return undefined;
  if (!file.model) {
    file.model = monaco.editor.createModel(file.source, "skript", monaco.Uri.parse(`file:///${file.id}/${encodeURIComponent(file.name)}`));
    file.model.onDidChangeContent(() => {
      save();
      if (file !== active) return;
      if (file.result) setStatus("Changed");
      clearTimeout(parseTimer);
      if (settings.live) {
        parseTimer = setTimeout(() => {
          requestParse(file);
          void updateHash();
        }, DEBOUNCE_MS);
      } else {
        parseTimer = setTimeout(() => void updateHash(), DEBOUNCE_MS);
      }
    });
  }
  return file.model;
}

async function loadEditor() {
  try {
    const module = await import("./parser-editor");
    monaco = module.monaco;
    editorNote.remove();
    editor = module.createEditor(editorHost, { fontSize: settings.fontSize, minimap: settings.minimap, wordWrap: settings.wordWrap });
    editor.updateOptions({ tabFocusMode: settings.tabFocus });
    editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.Enter, () => parseNow());
    editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.KeyS, () => saveNow());
    if (active) activate(active, false);
  } catch {
    editorNote.textContent = "The editor couldn't load. Reload the page to try again.";
    setStatus("Failed");
  }
}

function parseNow() {
  clearTimeout(parseTimer);
  requestParse(active);
  void updateHash();
}

function saveNow() {
  const ok = save(true);
  toast(ok ? "Saved. Your files are kept in this browser only, on this device." : "This browser isn't letting the page store files, so they won't be kept after you close it.");
}

// ---- Files.
function renderFiles() {
  if (!active) return;
  tabName.textContent = active.name;
  tabName.setAttribute("aria-label", `${active.name}. Rename`);
  $("tab-close").setAttribute("aria-label", `Close ${active.name}`);
  filesMenu.replaceChildren(
    ...files.map((f) => {
      const li = el("li");
      li.setAttribute("role", "none");
      const b = el("button", undefined, f.name);
      b.type = "button";
      b.setAttribute("role", "menuitemradio");
      b.setAttribute("aria-checked", String(f === active));
      b.addEventListener("click", () => {
        closeMenus();
        activate(f);
      });
      li.append(b);
      return li;
    }),
  );
  document.title = `${active.name} · Parser · NeoSkript`;
}

function activate(file: Open, focus = true) {
  if (editor && active && active !== file && active.model && files.includes(active)) active.view = editor.saveViewState();
  active = file;
  renderFiles();
  save();
  if (editor) {
    const model = modelFor(file)!;
    editor.setModel(model);
    if (file.view) editor.restoreViewState(file.view);
    if (focus) editor.focus();
  }
  view = "problems";
  showResult();
  if (file.result === undefined || file.parsed !== sourceOf(file)) {
    if (settings.live) requestParse(file);
  }
  void updateHash();
}

function openFile(name: string, source: string) {
  const file: Open = { id: newId(), name: uniqueName(name), source };
  files.push(file);
  activate(file);
  return file;
}

function closeFile(file = active) {
  if (!file) return;
  const index = files.indexOf(file);
  files.splice(index, 1);
  if (file === active) editor?.setModel(null);
  file.model?.dispose();
  file.model = undefined;
  if (files.length === 0) files.push({ id: newId(), name: "Untitled.sk", source: "" });
  activate(files[Math.min(index, files.length - 1)]);
  toast(`Closed ${file.name}.`);
}

function startRename() {
  if (!active) return;
  tabInput.value = active.name;
  tabName.hidden = true;
  tabInput.hidden = false;
  tabInput.focus();
  tabInput.setSelectionRange(0, active.name.replace(/\.sk$/i, "").length);
}
function endRename(commit: boolean) {
  if (tabInput.hidden) return;
  if (commit && active) {
    const wanted = tabInput.value.trim().replace(/[\\/:*?"<>|]+/g, "");
    if (wanted && wanted !== active.name) {
      const others = files.filter((f) => f !== active);
      let name = /\.sk$/i.test(wanted) ? wanted : `${wanted}.sk`;
      if (others.some((f) => f.name.toLowerCase() === name.toLowerCase())) {
        const saved = files;
        files = others;
        name = uniqueName(name);
        files = saved;
      }
      active.name = name;
      save();
    }
  }
  tabInput.hidden = true;
  tabName.hidden = false;
  renderFiles();
  tabName.focus();
}
tabName.addEventListener("click", startRename);
tabInput.addEventListener("keydown", (event) => {
  if (event.key === "Enter") {
    event.preventDefault();
    endRename(true);
  } else if (event.key === "Escape") {
    event.preventDefault();
    endRename(false);
  }
});
tabInput.addEventListener("blur", () => endRename(true));
$("tab-close").addEventListener("click", () => closeFile());
$("new-file").addEventListener("click", () => openFile("Untitled", ""));

fileInput.addEventListener("change", async () => {
  const chosen = fileInput.files?.[0];
  fileInput.value = "";
  if (!chosen) return;
  if (chosen.size > 2_000_000) {
    toast("That file is over 2 MB, too big to be a script.");
    return;
  }
  const text = (await chosen.text()).replace(/\r\n?/g, "\n");
  openFile(chosen.name, text);
  toast(`Opened ${chosen.name}. It stays in this browser.`);
});

function download() {
  if (!active) return;
  const blob = new Blob([sourceOf(active)], { type: "text/plain;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const a = el("a");
  a.href = url;
  a.download = active.name;
  document.body.append(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

function insertExample(id: string) {
  const example = EXAMPLES[id];
  if (!example) return;
  const same = files.find((f) => sourceOf(f) === example.source);
  if (same) activate(same);
  else if (active && sourceOf(active).trim() === "" && !active.model?.canUndo()) {
    // An empty file takes the example, rather than leaving a blank tab behind.
    active.name = uniqueName(example.name);
    if (active.model) active.model.setValue(example.source);
    else active.source = example.source;
    activate(active);
  } else openFile(example.name, example.source);
}

function insertSnippet(id: string) {
  const snippet = SNIPPETS[id];
  if (!snippet || !editor || !active?.model) return;
  const model = active.model;
  const position = editor.getPosition();
  // Snippets are top-level blocks: they go on an empty top-level line where the cursor is, or at the end.
  let line: number;
  let text = snippet;
  if (position && model.getLineContent(position.lineNumber).trim() === "") {
    line = position.lineNumber;
  } else {
    line = model.getLineCount();
    const last = model.getLineContent(line);
    if (last.trim() !== "") text = "\n\n" + snippet;
    else if (line > 1 && model.getLineContent(line - 1).trim() !== "") text = "\n" + snippet;
  }
  const column = model.getLineMaxColumn(line);
  const start = position && model.getLineContent(position.lineNumber).trim() === "" ? 1 : column;
  editor.executeEdits("insert", [{ range: { startLineNumber: line, startColumn: start, endLineNumber: line, endColumn: column }, text, forceMoveMarkers: true }]);
  editor.pushUndoStop();
  editor.revealLineInCenter(model.getLineCount());
  editor.focus();
}

async function copy(text: string, done: string) {
  try {
    await navigator.clipboard.writeText(text);
    toast(done);
  } catch {
    toast("Couldn't copy. Copy it from the address bar instead.");
  }
}

// ---- Share links: the script in the URL fragment (src/lib/parser.ts), kept up to date as you edit.
async function updateHash() {
  if (!active) return;
  const source = sourceOf(active);
  const hash = source.trim() === "" ? "" : await encodeShare(source);
  if (location.hash !== hash) history.replaceState(null, "", hash || location.pathname);
}

async function loadFromHash() {
  const shared = await decodeShare(location.hash);
  if (shared instanceof Error) {
    toast("The script in this link couldn't be read. It may have been cut off when it was copied.");
    return false;
  }
  if (shared === null) return false;
  const same = files.find((f) => sourceOf(f) === shared);
  if (same) activate(same, false);
  else openFile("Shared", shared);
  return true;
}
window.addEventListener("hashchange", () => void loadFromHash());

// ---- Menus: a button opens a list of items; arrows move, Esc closes, Left and Right go to the next menu.
const menus = [...document.querySelectorAll<HTMLElement>(".menu")];
function itemsOf(list: HTMLElement) {
  return [...list.querySelectorAll<HTMLElement>('[role="menuitem"], [role="menuitemradio"]')];
}
function closeMenus(except?: HTMLElement) {
  for (const menu of menus) {
    if (menu === except) continue;
    const button = menu.querySelector<HTMLButtonElement>("[aria-haspopup]")!;
    button.setAttribute("aria-expanded", "false");
    menu.querySelector<HTMLElement>(".menu-list")!.hidden = true;
  }
}
function openMenu(menu: HTMLElement, focus: "first" | "last" | null = "first") {
  closeMenus(menu);
  const button = menu.querySelector<HTMLButtonElement>("[aria-haspopup]")!;
  const list = menu.querySelector<HTMLElement>(".menu-list")!;
  button.setAttribute("aria-expanded", "true");
  list.hidden = false;
  const items = itemsOf(list);
  const checked = items.find((i) => i.getAttribute("aria-checked") === "true");
  if (focus) (focus === "last" ? items.at(-1) : (checked ?? items[0]))?.focus();
}
for (const menu of menus) {
  const button = menu.querySelector<HTMLButtonElement>("[aria-haspopup]")!;
  const list = menu.querySelector<HTMLElement>(".menu-list")!;
  button.addEventListener("click", () => {
    if (button.getAttribute("aria-expanded") === "true") closeMenus();
    else openMenu(menu);
  });
  button.addEventListener("keydown", (event) => {
    if (event.key === "ArrowDown" || event.key === "ArrowUp") {
      event.preventDefault();
      openMenu(menu, event.key === "ArrowUp" ? "last" : "first");
    }
  });
  list.addEventListener("keydown", (event) => {
    const items = itemsOf(list);
    const at = items.indexOf(document.activeElement as HTMLElement);
    const move = (to: number) => {
      event.preventDefault();
      items[(to + items.length) % items.length]?.focus();
    };
    if (event.key === "ArrowDown") move(at + 1);
    else if (event.key === "ArrowUp") move(at - 1);
    else if (event.key === "Home") move(0);
    else if (event.key === "End") move(items.length - 1);
    else if (event.key === "Escape") {
      event.preventDefault();
      closeMenus();
      button.focus();
    } else if (event.key === "Tab") {
      closeMenus();
    } else if (event.key === "ArrowRight" || event.key === "ArrowLeft") {
      event.preventDefault();
      const next = menus[(menus.indexOf(menu) + (event.key === "ArrowRight" ? 1 : -1) + menus.length) % menus.length];
      openMenu(next);
      next.querySelector<HTMLButtonElement>("[aria-haspopup]")!.setAttribute("aria-expanded", "true");
    } else if (event.key.length === 1 && /\S/.test(event.key)) {
      // Typing a letter moves to the next item starting with it.
      const key = event.key.toLowerCase();
      const order = [...items.slice(at + 1), ...items.slice(0, at + 1)];
      order.find((i) => i.textContent?.trim().toLowerCase().startsWith(key))?.focus();
    }
  });
  // Any item closes its menu, and puts focus back where it makes sense for what it did.
  list.addEventListener("click", (event) => {
    if ((event.target as HTMLElement).closest('[role="menuitem"]')) closeMenus();
  });
}
document.addEventListener("pointerdown", (event) => {
  if (!(event.target as HTMLElement).closest(".menu")) closeMenus();
});

const actions: Record<string, () => void> = {
  new: () => openFile("Untitled", ""),
  open: () => fileInput.click(),
  download,
  rename: startRename,
  close: () => closeFile(),
  share: async () => {
    await updateHash();
    await copy(location.href, "Share link copied. Whoever opens it sees this script, checked the same way.");
  },
  copy: () => active && void copy(sourceOf(active), "Script copied."),
  keys: () => $<HTMLDialogElement>("keys").showModal(),
};
for (const item of document.querySelectorAll<HTMLButtonElement>("[data-action]")) {
  item.addEventListener("click", () => actions[item.dataset.action!]?.());
}
for (const item of document.querySelectorAll<HTMLButtonElement>("[data-example]")) {
  item.addEventListener("click", () => insertExample(item.dataset.example!));
}
for (const item of document.querySelectorAll<HTMLButtonElement>("[data-snippet]")) {
  item.addEventListener("click", () => insertSnippet(item.dataset.snippet!));
}
for (const id of ["parse", "waiting-parse", "status-parse"]) $(id).addEventListener("click", parseNow);
$("save").addEventListener("click", saveNow);

// ---- Settings.
const settingsDialog = $<HTMLDialogElement>("settings");
const setLive = $<HTMLInputElement>("set-live");
const setMinimap = $<HTMLInputElement>("set-minimap");
const setWrap = $<HTMLInputElement>("set-wrap");
const setTab = $<HTMLInputElement>("set-tab");
const setFont = $<HTMLSelectElement>("set-font");
$("open-settings").addEventListener("click", () => {
  setLive.checked = settings.live;
  setMinimap.checked = settings.minimap;
  setWrap.checked = settings.wordWrap;
  setTab.checked = settings.tabFocus;
  setFont.value = String(settings.fontSize);
  settingsDialog.showModal();
});
function applySettings() {
  settings.live = setLive.checked;
  settings.minimap = setMinimap.checked;
  settings.wordWrap = setWrap.checked;
  settings.tabFocus = setTab.checked;
  settings.fontSize = Number(setFont.value) || 14;
  write(SETTINGS_KEY, settings);
  editor?.updateOptions({
    fontSize: settings.fontSize,
    lineHeight: Math.round(settings.fontSize * 1.6),
    minimap: { enabled: settings.minimap },
    wordWrap: settings.wordWrap ? "on" : "off",
    tabFocusMode: settings.tabFocus,
  });
  if (settings.live && active && active.parsed !== sourceOf(active)) requestParse(active);
}
for (const input of [setLive, setMinimap, setWrap, setTab, setFont]) input.addEventListener("change", applySettings);

// Ctrl+S anywhere on the page saves, not the browser's "save page".
document.addEventListener("keydown", (event) => {
  if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "s" && !event.altKey) {
    event.preventDefault();
    saveNow();
  }
});

// ---- Start: the files this browser kept, then a shared link on top, else the mistakes example.
async function start() {
  const fromLink = await loadFromHash();
  if (!fromLink && files.length === 0) {
    files.push({ id: newId(), name: EXAMPLES.mistakes.name, source: EXAMPLES.mistakes.source });
  }
  if (!fromLink) activate(active ?? files[0], false);
  if (firstVisit) toast("Your files are kept in this browser only. Nothing is uploaded.");
  await loadEditor();
}
void start();
