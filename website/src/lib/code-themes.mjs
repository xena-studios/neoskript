// The guides' code colours (Expressive Code), in the brand's code style:
// keywords magenta (structures bold), strings pale, variables and entries a
// magenta tint of the text, comments and punctuation muted, the rest text.
// One theme per site theme, on the same raised surface as the pages
// (tokens.css), so Expressive Code's contrast check reads the real background.
// Skript comes from the VS Code extension's grammar (astro.config.mjs).
const theme = (type, c) => ({
  name: `neoskript-${type}`,
  type,
  colors: {
    "editor.background": c.raised,
    "editor.foreground": c.text,
    "terminal.background": c.raised,
    "terminal.foreground": c.text,
  },
  tokenColors: [
    { settings: { foreground: c.text } },
    { scope: ["comment", "punctuation.definition.comment"], settings: { foreground: c.muted, fontStyle: "italic" } },
    { scope: ["keyword", "storage", "keyword.operator.word"], settings: { foreground: c.keyword } },
    {
      scope: ["keyword.declaration", "storage.type.class", "storage.type.function"],
      settings: { foreground: c.keyword, fontStyle: "bold" },
    },
    { scope: ["string", "constant.numeric", "constant.language"], settings: { foreground: c.string } },
    { scope: ["constant.character.escape", "constant.other.color"], settings: { foreground: c.muted } },
    {
      scope: ["variable.other.skript", "variable.language", "variable.other.constant", "variable.parameter", "entity.name.tag"],
      settings: { foreground: c.variable },
    },
    { scope: ["support.type.property-name", "entity.other.attribute-name"], settings: { foreground: c.entry } },
    { scope: ["entity.name.function", "entity.name.type", "support.function"], settings: { foreground: c.text, fontStyle: "bold" } },
    { scope: ["punctuation.section.embedded"], settings: { foreground: c.keyword } },
    { scope: ["meta.embedded.expression"], settings: { foreground: c.text } },
    { scope: ["punctuation", "keyword.operator", "meta.brace"], settings: { foreground: c.muted } },
    { scope: ["markup.inserted"], settings: { foreground: c.keyword } },
    { scope: ["markup.deleted"], settings: { foreground: c.muted } },
  ],
});

export const dark = theme("dark", {
  raised: "#1B1320",
  text: "#F7F1F6",
  muted: "#A898A9",
  keyword: "#FF4D9D",
  string: "#FFC2DD",
  variable: "#FBA7CE",
  entry: "#FB97C5",
});

export const light = theme("light", {
  raised: "#F3EDF1",
  text: "#1A1219",
  muted: "#6B5F69",
  keyword: "#C0176A",
  string: "#8A2F6A",
  variable: "#6A2A55",
  entry: "#A0245F",
});
