// The parser's editor: Monaco, bundled from npm (the CSP allows scripts only from this site), with only what a
// Skript editor needs: the core editor and its everyday contributions, no language services. parser-page.ts
// imports this lazily, so no other page, and not the parser page's first paint, pays for it.
//
// Skript is coloured by a Monarch tokenizer below, in the brand's code colours (keywords magenta, strings pale,
// comments and punctuation muted), read from the page's CSS tokens rather than typed again.
import * as monaco from "monaco-editor/editor/editor.api.js";
import "monaco-editor/features/codicon/register.js";
import "monaco-editor/editor/browser/coreCommands.js";
import "monaco-editor/editor/browser/widget/codeEditor/codeEditorWidget.js";
import "monaco-editor/editor/common/standaloneStrings.js";
import "monaco-editor/editor/contrib/bracketMatching/browser/bracketMatching.js";
import "monaco-editor/editor/contrib/caretOperations/browser/caretOperations.js";
import "monaco-editor/editor/contrib/clipboard/browser/clipboard.js";
import "monaco-editor/editor/contrib/comment/browser/comment.js";
import "monaco-editor/editor/contrib/contextmenu/browser/contextmenu.js";
import "monaco-editor/editor/contrib/cursorUndo/browser/cursorUndo.js";
import "monaco-editor/features/find/register.js";
import "monaco-editor/editor/contrib/folding/browser/folding.js";
import "monaco-editor/editor/contrib/fontZoom/browser/fontZoom.js";
import "monaco-editor/editor/contrib/gotoError/browser/gotoError.js";
import "monaco-editor/editor/contrib/hover/browser/hoverContribution.js";
import "monaco-editor/editor/contrib/indentation/browser/indentation.js";
import "monaco-editor/editor/contrib/lineSelection/browser/lineSelection.js";
import "monaco-editor/editor/contrib/linesOperations/browser/linesOperations.js";
import "monaco-editor/editor/contrib/multicursor/browser/multicursor.js";
import "monaco-editor/editor/contrib/smartSelect/browser/smartSelect.js";
import "monaco-editor/editor/contrib/toggleTabFocusMode/browser/toggleTabFocusMode.js";
import "monaco-editor/editor/contrib/wordHighlighter/browser/wordHighlighter.js";
import "monaco-editor/editor/contrib/wordOperations/browser/wordOperations.js";
import "monaco-editor/editor/standalone/browser/quickAccess/standaloneGotoLineQuickAccess.js";
import "monaco-editor/editor/standalone/browser/quickAccess/standaloneCommandsQuickAccess.js";
import "monaco-editor/editor/standalone/browser/quickAccess/standaloneHelpQuickAccess.js";
import EditorWorker from "monaco-editor/editor/editor.worker.js?worker";

export { monaco };

// Monaco's own worker (word ranges, diffs), from this site like everything else.
(self as unknown as { MonacoEnvironment: monaco.Environment }).MonacoEnvironment = {
  getWorker: () => new EditorWorker(),
};

const LANGUAGE = "skript";
const THEME = "neoskript";

const STRUCTURES = ["on", "command", "function", "options", "variables", "aliases", "every", "at", "local", "import"];
const CONTROL = ["if", "else", "loop", "while", "do", "return", "stop", "exit", "continue", "wait", "trigger", "parse", "any", "all"];
const KEYWORDS = [
  "set", "add", "remove", "delete", "clear", "reset", "send", "broadcast", "give", "teleport", "cancel",
  "make", "execute", "kill", "to", "from", "of", "is", "are", "not", "isn't", "aren't", "has",
  "have", "and", "or", "with", "in", "where", "by", "times", "true", "false", "yes", "no", "event",
];

monaco.languages.register({ id: LANGUAGE, extensions: [".sk"], aliases: ["Skript"] });
monaco.languages.setLanguageConfiguration(LANGUAGE, {
  comments: { lineComment: "#" },
  brackets: [["(", ")"], ["[", "]"], ["{", "}"]],
  autoClosingPairs: [
    { open: "(", close: ")" },
    { open: "[", close: "]" },
    { open: "{", close: "}" },
    { open: '"', close: '"', notIn: ["string"] },
  ],
  surroundingPairs: [
    { open: "(", close: ")" },
    { open: "{", close: "}" },
    { open: '"', close: '"' },
  ],
  // A line ending in ':' opens a block; the next line goes one level deeper.
  onEnterRules: [{ beforeText: /:\s*(#.*)?$/, action: { indentAction: monaco.languages.IndentAction.Indent } }],
  indentationRules: { increaseIndentPattern: /:\s*(#.*)?$/, decreaseIndentPattern: /^\s*else\b/ },
  wordPattern: /[A-Za-z_][\w-]*/,
  folding: { offSide: true },
});

// Strings hold %expressions%, variables hold %expressions% and can nest. Every state pops back to where it was,
// and none outlives its line: a string left open (a common mistake) mustn't colour the lines after it.
monaco.languages.setMonarchTokensProvider(LANGUAGE, {
  defaultToken: "",
  ignoreCase: true,
  structures: STRUCTURES,
  control: CONTROL,
  keywords: KEYWORDS,
  tokenizer: {
    root: [
      [/^\s*###\s*$/, { token: "comment", next: "@blockComment" }],
      [/##/, ""],
      [/#.*$/, "comment"],
      [/^(\s+)(description|usage|permission message|permission|aliases|executable by|cooldown message|cooldown bypass|cooldown storage|cooldown|prefix|return type|trigger)(:)/, ["", "keyword.entry", "delimiter"]],
      [/"/, { token: "string.quote", next: "@string" }],
      [/\{/, { token: "variable.brace", next: "@variable" }],
      [/%/, { token: "expression.delimiter", next: "@expression" }],
      [/\b\d+(\.\d+)?\b/, "number"],
      [/\b(event|loop|arg|argument)-[\w-]+/, "type.eventvalue"],
      [/[A-Za-z_][\w'-]*/, { cases: { "@structures": "keyword.structure", "@control": "keyword.control", "@keywords": "keyword", "@default": "" } }],
      [/[()[\]:,]/, "delimiter"],
    ],
    blockComment: [
      [/^\s*###\s*$/, { token: "comment", next: "@pop" }],
      [/.*$/, "comment"],
    ],
    string: [
      [/^/, { token: "", next: "@popall" }],
      [/""/, "string.escape"],
      [/%%/, "string.escape"],
      [/%/, { token: "expression.delimiter", next: "@expression" }],
      [/<[^<>"]*>/, "string.tag"],
      [/[^"%<]+/, "string"],
      [/</, "string"],
      [/"/, { token: "string.quote", next: "@pop" }],
    ],
    variable: [
      [/^/, { token: "", next: "@popall" }],
      [/\{/, { token: "variable.brace", next: "@variable" }],
      [/%/, { token: "expression.delimiter", next: "@expression" }],
      [/\}/, { token: "variable.brace", next: "@pop" }],
      [/[^{}%]+/, "variable"],
    ],
    expression: [
      [/^/, { token: "", next: "@popall" }],
      [/%/, { token: "expression.delimiter", next: "@pop" }],
      [/"/, { token: "string.quote", next: "@string" }],
      [/\{/, { token: "variable.brace", next: "@variable" }],
      [/\b(event|loop|arg|argument)-[\w-]+/, "type.eventvalue"],
      [/[^%"{]+/, "expression"],
    ],
  },
} as monaco.languages.IMonarchLanguage);

/** The page's colour tokens as #rrggbb, and sRGB mixes of them (DESIGN.md's sRGB Mix Rule). */
function palette() {
  const css = getComputedStyle(document.documentElement);
  const read = (name: string) => css.getPropertyValue(name).trim();
  const rgb = (hex: string) => [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16));
  const mix = (a: string, b: string, weight: number) =>
    "#" + rgb(a).map((v, i) => Math.round(v * weight + rgb(b)[i] * (1 - weight)).toString(16).padStart(2, "0")).join("");
  const c = {
    night: read("--neoskript-night"),
    raised: read("--neoskript-night-raised"),
    rule: read("--neoskript-night-rule"),
    text: read("--neoskript-text"),
    muted: read("--neoskript-muted"),
    magenta: read("--neoskript-magenta"),
    string: read("--neoskript-string"),
  };
  return { ...c, mix };
}

function defineTheme() {
  const { night, raised, rule, text, muted, magenta, string, mix } = palette();
  const bare = (hex: string) => hex.slice(1);
  monaco.editor.defineTheme(THEME, {
    base: "vs-dark",
    inherit: true,
    rules: [
      { token: "", foreground: bare(text) },
      { token: "comment", foreground: bare(muted), fontStyle: "italic" },
      { token: "keyword", foreground: bare(magenta) },
      { token: "keyword.structure", foreground: bare(magenta), fontStyle: "bold" },
      { token: "keyword.control", foreground: bare(magenta) },
      { token: "keyword.entry", foreground: bare(mix(magenta, text, 0.55)) },
      { token: "string", foreground: bare(string) },
      { token: "string.quote", foreground: bare(string) },
      { token: "string.escape", foreground: bare(mix(string, muted, 0.5)) },
      { token: "string.tag", foreground: bare(muted) },
      { token: "variable", foreground: bare(mix(magenta, text, 0.45)) },
      { token: "variable.brace", foreground: bare(mix(magenta, text, 0.45)) },
      { token: "expression", foreground: bare(text) },
      { token: "expression.delimiter", foreground: bare(magenta) },
      { token: "type.eventvalue", foreground: bare(mix(magenta, text, 0.45)) },
      { token: "number", foreground: bare(string) },
      { token: "delimiter", foreground: bare(muted) },
    ],
    colors: {
      "editor.background": raised,
      "editor.foreground": text,
      "editorLineNumber.foreground": mix(muted, raised, 0.7),
      "editorLineNumber.activeForeground": text,
      "editorCursor.foreground": magenta,
      "editor.selectionBackground": mix(magenta, raised, 0.35),
      "editor.inactiveSelectionBackground": mix(magenta, raised, 0.2),
      "editor.lineHighlightBackground": mix(text, raised, 0.04),
      "editor.lineHighlightBorder": "#00000000",
      "editorIndentGuide.background1": rule,
      "editorIndentGuide.activeBackground1": mix(muted, raised, 0.5),
      "editorWhitespace.foreground": rule,
      "editorGutter.background": raised,
      "editorError.foreground": magenta,
      "editorWarning.foreground": string,
      "editorOverviewRuler.errorForeground": magenta,
      "editorOverviewRuler.warningForeground": string,
      "editorOverviewRuler.border": rule,
      "minimap.background": mix(night, raised, 0.5),
      "minimap.errorHighlight": magenta,
      "minimap.warningHighlight": string,
      "scrollbarSlider.background": mix(rule, raised, 0.8),
      "scrollbarSlider.hoverBackground": rule,
      "scrollbarSlider.activeBackground": mix(muted, raised, 0.5),
      "editorWidget.background": night,
      "editorWidget.border": rule,
      "editorHoverWidget.background": night,
      "editorHoverWidget.border": rule,
      "focusBorder": magenta,
      "input.background": raised,
      "input.border": rule,
      "list.activeSelectionBackground": mix(magenta, night, 0.25),
      "list.hoverBackground": mix(magenta, night, 0.1),
      "editorBracketMatch.background": mix(magenta, raised, 0.15),
      "editorBracketMatch.border": mix(magenta, raised, 0.5),
      "editor.findMatchBackground": mix(magenta, raised, 0.45),
      "editor.findMatchHighlightBackground": mix(magenta, raised, 0.2),
      "editor.wordHighlightBackground": mix(text, raised, 0.1),
      "editor.wordHighlightStrongBackground": mix(text, raised, 0.14),
    },
  });
}

export type EditorSettings = { fontSize: number; minimap: boolean; wordWrap: boolean };

export function createEditor(container: HTMLElement, settings: EditorSettings) {
  defineTheme();
  const editor = monaco.editor.create(container, {
    language: LANGUAGE,
    theme: THEME,
    automaticLayout: true,
    fontFamily: getComputedStyle(document.documentElement).getPropertyValue("--mono").trim(),
    fontLigatures: false,
    fontSize: settings.fontSize,
    lineHeight: Math.round(settings.fontSize * 1.6),
    minimap: { enabled: settings.minimap, renderCharacters: false, showSlider: "mouseover" },
    wordWrap: settings.wordWrap ? "on" : "off",
    scrollBeyondLastLine: false,
    renderLineHighlight: "line",
    insertSpaces: false,
    detectIndentation: true,
    tabSize: 4,
    padding: { top: 12, bottom: 12 },
    overviewRulerLanes: 2,
    fixedOverflowWidgets: true,
    ariaLabel: "Script. Press Alt+F1 for the editor's accessibility help, Ctrl+M to make Tab move focus.",
    accessibilitySupport: "auto",
    // Words in a script aren't a language Monaco knows: no word-based suggestions popping up while typing.
    quickSuggestions: false,
    wordBasedSuggestions: "off",
    suggestOnTriggerCharacters: false,
    // One authored motion: none here. Smooth scrolling follows the visitor's motion preference.
    smoothScrolling: !matchMedia("(prefers-reduced-motion: reduce)").matches,
    cursorBlinking: matchMedia("(prefers-reduced-motion: reduce)").matches ? "solid" : "blink",
    stickyScroll: { enabled: false },
    unicodeHighlight: { ambiguousCharacters: false },
  });
  return editor;
}
