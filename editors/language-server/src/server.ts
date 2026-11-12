// NeoSkript's language server for Skript scripts (.sk). It parses with NeoSkript's own frontend, compiled to
// JavaScript (the same parser as neoskript.dev/parser), against NeoSkript's registry of Skript syntax, so what it
// reports is what NeoSkript, and Skript, would say when the script loads. It gives:
//   problems as you type, with the other scripts' functions known;
//   hover docs for each line's syntax, its expressions, and functions;
//   completion of events, effects, conditions, sections, functions, options and variables;
//   an outline (events, commands, functions, options);
//   go to definition for functions, across the workspace, and for {@options}.
import { readFile, readdir } from "node:fs/promises";
import { join } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";
import {
  CompletionItemKind,
  DiagnosticSeverity,
  InsertTextFormat,
  MarkupKind,
  ProposedFeatures,
  SymbolKind,
  TextDocumentSyncKind,
  TextDocuments,
  createConnection,
  type CompletionItem,
  type Diagnostic,
  type DocumentSymbol,
  type Hover,
  type Location,
  type Position,
} from "vscode-languageserver/node";
import { TextDocument } from "vscode-languageserver-textdocument";
// @ts-expect-error The parser is TeaVM's JavaScript, without types.
import { init, parseWith } from "../vendor/neoskript-parser.js";
import syntaxData from "../vendor/syntax.json" with { type: "json" };
import docs from "../generated/docs.json" with { type: "json" };

type ParseResult = {
  diagnostics: { severity: "error" | "warning"; line: number; column: number; length: number; message: string }[];
  outline: { kind: string; line: number; text: string; name?: string; signature?: string }[];
  elements: { line: number; start: number; end: number; kind: string; id?: string; name?: string; signature?: string }[];
};
type Docs = typeof docs;
type ElementDoc = Docs["elements"][keyof Docs["elements"]];

const connection = createConnection(ProposedFeatures.all);
const documents = new TextDocuments(TextDocument);
const about: string = init(JSON.stringify(syntaxData));

/** The last parse of each open document. */
const results = new Map<string, ParseResult>();
/** The global functions each script in the workspace declares, by its URI: name, signature line and text. */
const declared = new Map<string, { name: string; line: number; header: string }[]>();
let folders: string[] = [];

// ---------------------------------------------------------------------------------------------- the workspace

/** A script's global function headers: `function name(...)` at the start of a line, not `local function`. */
function functionsOf(text: string) {
  const found: { name: string; line: number; header: string }[] = [];
  text.split(/\r?\n/).forEach((line, i) => {
    const m = /^function\s+([\p{L}\p{N}_]+)\s*\(.*$/iu.exec(line);
    if (m) found.push({ name: m[1], line: i, header: line.replace(/:\s*(#.*)?$/, "") });
  });
  return found;
}

/** What the other scripts declare, as a script of their function headers for the parser. */
function contextFor(uri: string) {
  const lines: string[] = [];
  for (const [other, functions] of declared) {
    if (other === uri) continue;
    for (const f of functions) lines.push(`${f.header}:`, "\tstop");
  }
  return lines.join("\n");
}

async function scan(dir: string, depth = 0): Promise<string[]> {
  if (depth > 12) return [];
  let entries;
  try {
    entries = await readdir(dir, { withFileTypes: true });
  } catch {
    return [];
  }
  const files: string[] = [];
  for (const entry of entries) {
    if (entry.name.startsWith(".") || entry.name === "node_modules") continue;
    const path = join(dir, entry.name);
    if (entry.isDirectory()) files.push(...(await scan(path, depth + 1)));
    // Skript leaves out scripts whose names start with '-', so their functions don't exist.
    else if (entry.name.endsWith(".sk") && !entry.name.startsWith("-")) files.push(path);
  }
  return files;
}

async function indexWorkspace() {
  for (const folder of folders) {
    for (const file of await scan(folder)) {
      const uri = pathToFileURL(file).toString();
      if (documents.get(uri)) continue;
      try {
        declared.set(uri, functionsOf(await readFile(file, "utf8")));
      } catch {
        // Unreadable: skip it.
      }
    }
  }
  documents.all().forEach(validate);
}

// ---------------------------------------------------------------------------------------------- problems

const pending = new Map<string, ReturnType<typeof setTimeout>>();

function schedule(document: TextDocument) {
  clearTimeout(pending.get(document.uri));
  pending.set(document.uri, setTimeout(() => validate(document), 150));
}

function validate(document: TextDocument) {
  const text = document.getText();
  declared.set(document.uri, functionsOf(text));
  let result: ParseResult;
  try {
    result = JSON.parse(parseWith(text, contextFor(document.uri)));
  } catch (e) {
    connection.console.error(`NeoSkript couldn't parse ${document.uri}: ${e}`);
    return;
  }
  results.set(document.uri, result);
  const lines = text.split(/\r?\n/);
  const diagnostics: Diagnostic[] = result.diagnostics.map((d) => {
    const line = lines[d.line - 1] ?? "";
    // A problem with no length is about the whole line.
    const from = d.length === 0 ? line.length - line.trimStart().length : Math.min(d.column, line.length);
    const to = d.length === 0 ? line.length : Math.min(d.column + d.length, line.length);
    return {
      severity: d.severity === "error" ? DiagnosticSeverity.Error : DiagnosticSeverity.Warning,
      range: { start: { line: d.line - 1, character: from }, end: { line: d.line - 1, character: Math.max(to, from) } },
      message: d.message,
      source: "NeoSkript",
    };
  });
  connection.sendDiagnostics({ uri: document.uri, diagnostics });
}

// ---------------------------------------------------------------------------------------------- hover

function elementDoc(id: string): ElementDoc | undefined {
  const all = docs.elements as Record<string, ElementDoc>;
  return all[id] ?? all[id.split("-")[0]];
}

function describe(doc: ElementDoc) {
  const kind = doc.kind[0].toUpperCase() + doc.kind.slice(1);
  const parts = [`**${doc.name}** · ${kind}${doc.since ? ` · since Skript ${doc.since}` : ""}`];
  if (doc.deprecated) parts.push("*Deprecated.*");
  if (doc.description) parts.push(doc.description);
  if (doc.example) parts.push("```skript\n" + doc.example + "\n```");
  parts.push(`[On neoskript.dev](${doc.url})`);
  return parts.join("\n\n");
}

function functionDoc(name: string, signature?: string) {
  const builtIn = (docs.functions as Record<string, { signature: string; description: string; example: string | null; url: string }>)[name.toLowerCase()];
  if (builtIn) {
    const parts = ["```skript\n" + builtIn.signature + "\n```"];
    if (builtIn.description) parts.push(builtIn.description);
    if (builtIn.example) parts.push("```skript\n" + builtIn.example + "\n```");
    parts.push(`[On neoskript.dev](${builtIn.url})`);
    return parts.join("\n\n");
  }
  return "```skript\nfunction " + (signature ?? name) + "\n```";
}

connection.onHover(({ textDocument, position }): Hover | null => {
  const result = results.get(textDocument.uri);
  if (!result) return null;
  const line = position.line + 1;
  const here = result.elements
    .filter((e) => e.line === line && e.start <= position.character && position.character <= e.end)
    .sort((a, b) => a.end - a.start - (b.end - b.start));
  for (const e of here) {
    const range = { start: { line: position.line, character: e.start }, end: { line: position.line, character: e.end } };
    if (e.kind === "function" && e.name) return { contents: { kind: MarkupKind.Markdown, value: functionDoc(e.name, e.signature) }, range };
    const doc = e.id ? elementDoc(e.id) : undefined;
    if (doc) return { contents: { kind: MarkupKind.Markdown, value: describe(doc) }, range };
  }
  return null;
});

// ---------------------------------------------------------------------------------------------- completion

const STRUCTURES: CompletionItem[] = [
  { label: "on", detail: "An event", insertText: "on ${1:join}:\n\t$0" },
  { label: "command", detail: "A command", insertText: "command /${1:name}:\n\ttrigger:\n\t\t$0" },
  { label: "function", detail: "A function", insertText: "function ${1:name}(${2}):\n\t$0" },
  { label: "options", detail: "Options, used as {@name}", insertText: "options:\n\t${1:name}: ${2:value}" },
  { label: "variables", detail: "Default values of variables", insertText: "variables:\n\t{${1:name}} = ${2:value}" },
  { label: "every", detail: "A periodic trigger", insertText: "every ${1:5 seconds}:\n\t$0" },
].map((item) => ({ ...item, kind: CompletionItemKind.Keyword, insertTextFormat: InsertTextFormat.Snippet }));

const KIND: Record<string, CompletionItemKind> = {
  event: CompletionItemKind.Event,
  effect: CompletionItemKind.Function,
  condition: CompletionItemKind.Operator,
  section: CompletionItemKind.Struct,
};

/** Options a script defines: `name: value` lines under `options:`, by name, with their line. */
function optionsOf(text: string) {
  const options = new Map<string, number>();
  let inside = false;
  text.split(/\r?\n/).forEach((line, i) => {
    if (/^options\s*:\s*(#.*)?$/i.test(line)) inside = true;
    else if (/^\S/.test(line)) inside = false;
    else if (inside) {
      const m = /^\s+([^:#]+?)\s*:/.exec(line);
      if (m) options.set(m[1], i);
    }
  });
  return options;
}

connection.onCompletion(({ textDocument, position }): CompletionItem[] => {
  const document = documents.get(textDocument.uri);
  if (!document) return [];
  const text = document.getText();
  const lineText = text.split(/\r?\n/)[position.line] ?? "";
  const before = lineText.slice(0, position.character);

  // {@ starts an option.
  if (/\{@[^}]*$/.test(before)) {
    return [...optionsOf(text).keys()].map((name) => ({ label: name, kind: CompletionItemKind.Constant, detail: "option" }));
  }
  // { starts a variable: the ones this script uses.
  if (/\{[^}]*$/.test(before) && !/"[^"]*$/.test(before.replace(/%[^%]*%/g, ""))) {
    const names = new Set<string>();
    for (const m of text.matchAll(/\{([^{}%@]+)\}/g)) names.add(m[1]);
    return [...names].map((name) => ({ label: name, kind: CompletionItemKind.Variable }));
  }
  const indented = /^\s+/.test(lineText);
  const items: CompletionItem[] = [];
  const typed = before.trim();
  if (!indented) {
    if (/^on\s+/i.test(typed)) {
      for (const c of docs.completions) {
        if (c.kind === "event") items.push(completion(c));
      }
      return items;
    }
    return STRUCTURES;
  }
  for (const c of docs.completions) {
    if (c.kind !== "event") items.push(completion(c));
  }
  const functions = new Map<string, string>();
  for (const f of Object.values(docs.functions as Record<string, { name: string; signature: string; parameters: string[] }>)) {
    functions.set(f.name, f.signature);
  }
  for (const list of declared.values()) for (const f of list) functions.set(f.name, f.header.replace(/^function\s+/i, ""));
  for (const [name, signature] of functions) {
    items.push({ label: name, kind: CompletionItemKind.Function, detail: signature, insertText: `${name}($1)`, insertTextFormat: InsertTextFormat.Snippet });
  }
  return items;
});

function completion(c: { id: string; kind: string; label: string; insert: string }): CompletionItem {
  const doc = elementDoc(c.id);
  return {
    label: c.label,
    kind: KIND[c.kind] ?? CompletionItemKind.Text,
    detail: doc ? `${doc.name} (${c.kind})` : c.kind,
    data: c.id,
    insertText: c.insert,
    insertTextFormat: InsertTextFormat.Snippet,
  };
}

connection.onCompletionResolve((item) => {
  const doc = typeof item.data === "string" ? elementDoc(item.data) : undefined;
  if (doc) item.documentation = { kind: MarkupKind.Markdown, value: describe(doc) };
  return item;
});

// ---------------------------------------------------------------------------------------------- outline

connection.onDocumentSymbol(({ textDocument }): DocumentSymbol[] => {
  const result = results.get(textDocument.uri);
  const document = documents.get(textDocument.uri);
  if (!result || !document) return [];
  const lines = document.getText().split(/\r?\n/);
  const starts = result.outline.map((o) => o.line - 1);
  return result.outline.map((o, i) => {
    // A structure runs until the next one.
    const last = (starts[i + 1] ?? lines.length) - 1;
    const end = { line: Math.max(last, o.line - 1), character: (lines[Math.max(last, o.line - 1)] ?? "").length };
    const name = o.kind === "function" && o.signature ? o.signature : o.text.replace(/:\s*$/, "");
    const kind = o.kind === "function" ? SymbolKind.Function : o.kind === "command" ? SymbolKind.Method : o.kind === "event" ? SymbolKind.Event : SymbolKind.Namespace;
    const line = o.line - 1;
    return {
      name,
      kind,
      range: { start: { line, character: 0 }, end },
      selectionRange: { start: { line, character: 0 }, end: { line, character: (lines[line] ?? "").length } },
    };
  });
});

// ---------------------------------------------------------------------------------------------- definition

connection.onDefinition(({ textDocument, position }): Location[] | null => {
  const document = documents.get(textDocument.uri);
  if (!document) return null;
  const text = document.getText();
  const lineText = text.split(/\r?\n/)[position.line] ?? "";
  // {@option}
  for (const m of lineText.matchAll(/\{@([^}]+)\}/g)) {
    if (m.index! <= position.character && position.character <= m.index! + m[0].length) {
      const line = optionsOf(text).get(m[1]);
      if (line !== undefined) return [{ uri: textDocument.uri, range: { start: { line, character: 0 }, end: { line, character: 0 } } }];
    }
  }
  // A function call.
  const result = results.get(textDocument.uri);
  const call = result?.elements.find((e) => e.kind === "function" && e.line === position.line + 1 && e.start <= position.character && position.character <= e.end);
  const name = call?.name ?? wordAt(lineText, position);
  if (!name) return null;
  const locations: Location[] = [];
  for (const [uri, functions] of declared) {
    for (const f of functions) {
      if (f.name.toLowerCase() === name.toLowerCase()) locations.push({ uri, range: { start: { line: f.line, character: 0 }, end: { line: f.line, character: f.header.length } } });
    }
  }
  // Local functions are only in their own script.
  text.split(/\r?\n/).forEach((line, i) => {
    const m = /^local\s+function\s+([\p{L}\p{N}_]+)\s*\(/iu.exec(line);
    if (m && m[1].toLowerCase() === name.toLowerCase()) locations.push({ uri: textDocument.uri, range: { start: { line: i, character: 0 }, end: { line: i, character: line.length } } });
  });
  return locations.length ? locations : null;
});

function wordAt(line: string, position: Position) {
  for (const m of line.matchAll(/[\p{L}\p{N}_]+(?=\s*\()/gu)) {
    if (m.index! <= position.character && position.character <= m.index! + m[0].length) return m[0];
  }
  return null;
}

// ---------------------------------------------------------------------------------------------- lifecycle

connection.onInitialize((params) => {
  folders = (params.workspaceFolders ?? []).map((f) => fileURLToPath(f.uri));
  if (folders.length === 0 && params.rootUri) folders = [fileURLToPath(params.rootUri)];
  return {
    capabilities: {
      textDocumentSync: TextDocumentSyncKind.Incremental,
      hoverProvider: true,
      completionProvider: { resolveProvider: true, triggerCharacters: ["{", "@"] },
      documentSymbolProvider: true,
      definitionProvider: true,
    },
    serverInfo: { name: "NeoSkript", version: about },
  };
});

connection.onInitialized(() => {
  connection.console.info(`NeoSkript language server: ${about}`);
  void indexWorkspace();
});

connection.onDidChangeWatchedFiles(async ({ changes }) => {
  for (const change of changes) {
    if (documents.get(change.uri)) continue;
    try {
      declared.set(change.uri, functionsOf(await readFile(fileURLToPath(change.uri), "utf8")));
    } catch {
      declared.delete(change.uri);
    }
  }
  documents.all().forEach(schedule);
});

documents.onDidOpen(({ document }) => validate(document));
documents.onDidChangeContent(({ document }) => schedule(document));
documents.onDidClose(({ document }) => {
  results.delete(document.uri);
  connection.sendDiagnostics({ uri: document.uri, diagnostics: [] });
});

documents.listen(connection);
connection.listen();
