---
name: NeoSkript website
description: neoskript.dev, NeoSkript's home. Neo Magenta on night (or ink on paper), Geist for reading, and a server console's grammar where it means something.
colors:
  magenta: "#FF4D9D"
  magenta-daylight: "#D61F78"
  magenta-text-light: "#C8196E"
  string: "#FFC2DD"
  string-light: "#8A2F6A"
  night: "#110B13"
  night-raised: "#1B1320"
  night-rule: "#30243A"
  text: "#F7F1F6"
  muted: "#A898A9"
  paper: "#FBF8FA"
  paper-raised: "#F3EDF1"
  paper-rule: "#E3D8E0"
  ink: "#1A1219"
  muted-light: "#6B5F69"
typography:
  display:
    fontFamily: "Geist Variable, system-ui, sans-serif"
    fontSize: "clamp(2.75rem, 1.2rem + 5.6vw, 6rem)"
    fontWeight: 700
    lineHeight: 0.98
    letterSpacing: "-0.04em"
  page-title:
    fontFamily: "Geist Variable, system-ui, sans-serif"
    fontSize: "clamp(2.5rem, 1.6rem + 3.6vw, 4.5rem)"
    fontWeight: 700
    lineHeight: 1
    letterSpacing: "-0.04em"
  docs-title:
    fontFamily: "Geist Variable, system-ui, sans-serif"
    fontSize: "clamp(2.25rem, 1.7rem + 2.2vw, 3.25rem)"
    fontWeight: 700
    lineHeight: 1.02
    letterSpacing: "-0.04em"
  headline:
    fontFamily: "Geist Variable, system-ui, sans-serif"
    fontSize: "clamp(1.875rem, 1.3rem + 2.2vw, 3rem)"
    fontWeight: 700
    lineHeight: 1.05
    letterSpacing: "-0.03em"
  subhead:
    fontFamily: "Geist Variable, system-ui, sans-serif"
    fontSize: "clamp(1.5rem, 1.2rem + 1.2vw, 2rem)"
    fontWeight: 700
    letterSpacing: "-0.03em"
  lead:
    fontFamily: "Geist Variable, system-ui, sans-serif"
    fontSize: "clamp(1.0625rem, 1rem + 0.35vw, 1.25rem)"
    fontWeight: 400
    lineHeight: 1.65
  body:
    fontFamily: "Geist Variable, system-ui, sans-serif"
    fontSize: "1rem"
    fontWeight: 400
    lineHeight: 1.6
  docs-body:
    fontFamily: "Geist Variable, system-ui, sans-serif"
    fontSize: "1.0625rem"
    fontWeight: 400
    lineHeight: 1.72
  ui:
    fontFamily: "Geist Variable, system-ui, sans-serif"
    fontSize: "0.9375rem"
    fontWeight: 500
  log:
    fontFamily: "Geist Mono Variable, ui-monospace, monospace"
    fontSize: "0.8125rem"
    fontWeight: 400
    lineHeight: 1.75
    fontFeature: "liga 0, calt 0, tnum"
  code:
    fontFamily: "Geist Mono Variable, ui-monospace, monospace"
    fontSize: "0.875rem"
    fontWeight: 400
    lineHeight: 1.7
  label:
    fontFamily: "JetBrains Mono Variable, ui-monospace, monospace"
    fontSize: "0.75rem"
    fontWeight: 500
    letterSpacing: "0.14em"
  level:
    fontFamily: "JetBrains Mono Variable, ui-monospace, monospace"
    fontSize: "0.6875rem"
    fontWeight: 600
    letterSpacing: "0.08em"
rounded:
  focus: "4px"
  control: "6px"
  inset: "8px"
  pane: "12px"
  pill: "999px"
spacing:
  gutter: "clamp(1rem, 0.4rem + 3vw, 4rem)"
  max: "90rem"
  page-column: "64rem"
  docs-measure: "44rem"
  section: "clamp(4rem, 2.5rem + 5vw, 7.5rem)"
  pane-cell: "0.875rem 1rem"
  action-gap: "0.75rem"
components:
  button-primary:
    backgroundColor: "{colors.magenta}"
    textColor: "{colors.night}"
    rounded: "{rounded.pill}"
    padding: "0 1.375rem"
    height: "44px"
  button-primary-light:
    backgroundColor: "{colors.magenta-daylight}"
    textColor: "#FFFFFF"
    rounded: "{rounded.pill}"
    height: "44px"
  button-secondary:
    backgroundColor: "transparent"
    textColor: "{colors.text}"
    rounded: "{rounded.pill}"
    padding: "0 1.375rem"
    height: "44px"
  pane:
    backgroundColor: "{colors.night-raised}"
    textColor: "{colors.text}"
    rounded: "{rounded.pane}"
  pane-bar:
    backgroundColor: "{colors.night}"
    textColor: "{colors.muted}"
    typography: "{typography.label}"
    padding: "0.5rem 1rem"
  log-line:
    textColor: "{colors.text}"
    typography: "{typography.log}"
  aside:
    backgroundColor: "{colors.night-raised}"
    textColor: "{colors.text}"
    rounded: "{rounded.pane}"
    padding: "1rem 1.25rem"
---

# Design System: NeoSkript website

## Overview

**Creative North Star: "The server log"**

neoskript.dev reads like a Paper server's console where that is the honest form: two servers' logs side by side, elapsed-time stamps, level tags, a title strip naming what runs in each pane. It is not a terminal costume. Words are set in Geist and read like a good product site and good docs; mono appears only where the text is code, a log line or a measurement; magenta marks NeoSkript and the action. The world sits in the Xena family (xenastudios.co, plugins.xenastudios.co): hairline rows, 44px pills, a frosted sticky header, JetBrains Mono uppercase labels for metadata, the XENA footer, and the planet horizon once, at the close.

The landing page proves the claim in its first viewport. Two console panes, one per server, replay the benchmark's startup and `/sk reload` at the speed each server took (`src/scripts/replay.ts`, from `src/data/benchmark.json`), each pane's clock stopping at its own time, NeoSkript's ratios as magenta marks. Below it: the comparison table, the numbers on one left-to-right axis per labelled group, the output check (the script's lines against what each server printed, a row lighting across both servers on hover), the switch, questions and the close. The tools (parser, syntax reference) are panes too. The guides are Starlight, themed into the same world, with asides as INFO / TIP / WARN / ERROR notes.

Every page works in dark and light. Dark is the brand as the brand repo sets it (night and Neo Magenta). Light is ink and magenta-daylight on a paper faintly tinted toward magenta. The theme is the one Starlight's picker stored (`localStorage["starlight-theme"]`), else the system's: Starlight sets `data-theme` on the guides, and `Base.astro`'s hashed head script sets it on the custom pages before the first paint.

**Key Characteristics:**
- One token set (`src/styles/tokens.css`) for every page, the guides included, in both themes.
- Console panes with a title strip and a hairline frame, 12px corners; everything else is hairline rows on the ground.
- Geist for reading, Geist Mono for code, logs and measurements, JetBrains Mono uppercase for labels and level tags.
- Magenta means NeoSkript, the action, or an error. Skript's side is always muted.
- Flat: depth comes from tone (ground and raised) and hairlines, never shadows.
- One authored moment on the landing (the replay); the numbers' bars draw out once; reduced motion shows the finished state.

## Colors

A restrained palette: tinted neutrals carry almost everything, and magenta appears only where it means something.

### Primary
- **Neo Magenta** (`magenta`, dark) and **Magenta Daylight** (`magenta-daylight`, light): `--accent-fill`, the fill of primary pills, the Parse button, the skip link and copied states, with `--on-accent` text (night in dark, 6.3:1; white in light, 4.85:1).
- **Accent text** (`--accent`): magenta in dark (6.3:1 on night, 5.9:1 on raised), `magenta-text-light` in light (5.2:1 on paper, 4.8:1 on paper-raised). The second line of the hero, NeoSkript's pane title and marks, the "Same" and "N× faster" marks, step numerals, keywords in code, current-page rails, focus rings, link-underline hover.

### Secondary
- **String** (`string` dark, `string-light` light): string literals and numbers in code, and the WARN level (caution asides, parser warnings).

### Neutral
- **Ground** (`night` / `paper`): the page, pane title strips, the header (at 88% under a blur).
- **Raised** (`night-raised` / `paper-raised`): pane bodies, cards, asides, inline code, code blocks.
- **Rule** (`night-rule` / `paper-rule`): every hairline and pane frame, bar tracks.
- **Text** (`text` / `ink`): headings, values, NeoSkript's readings. Docs prose is text mixed 12% toward muted for long reading.
- **Muted** (`muted` / `muted-light`): leads, labels, timestamps, Skript's side, captions. 7.2:1 on night, 5.7:1 on paper.

### Named Rules
**The Token Rule.** Pages use the tokens (`--ground`, `--raised`, `--rule`, `--text`, `--muted`, `--accent`, `--accent-fill`, `--on-accent`, `--string`); `--night` and `--magenta` remain as aliases for the tools. Dark values come from the brand repo (`pnpm brand:sync`). Light neutrals are fixed hex in `tokens.css` so the parser's editor can read them; change them there and nowhere else.

**The sRGB Mix Rule.** Every translucent or derived colour is `color-mix(in srgb, ...)`. oklch mixing drifts the hue.

**The Two Sides Rule.** Skript is muted, NeoSkript is text with magenta marks. Skript is never shown in magenta.

## Typography

**Reading:** Geist (variable, self-hosted via Fontsource)
**Code, logs, measurements:** Geist Mono, ligatures off
**Labels and levels:** JetBrains Mono, uppercase (Xena's shared face)

### Hierarchy
- **Display** (`display`): the landing h1 only, two lines, the second in the accent, capped at 11ch.
- **Page title** (`page-title`): download, addons, the syntax index and categories. **Docs title** (`docs-title`) in Starlight, followed by the page's description as a lead (`components/starlight/PageTitle.astro`).
- **Headline** (`headline`): landing section headings, capped at 20ch. **Subhead** (`subhead`): section headings on download and addons, over a hairline.
- **Lead** (`lead`): the muted paragraph under a heading, capped at 60ch.
- **Body** (`body`) on the custom pages; **docs body** (`docs-body`) in the guides, on a 44rem measure (about 72 characters).
- **Log** (`log`): console lines, timestamps and clocks, tabular. **Code** (`code`): code blocks and patterns.
- **Label** (`label`): column heads, pane titles, server names, the footer's copyright, docs sidebar groups, table heads. **Level** (`level`): INFO, TIP, WARN, ERROR, "Same", "N× faster".

### Named Rules
**The Mono Means Code Rule.** Mono is for script, output, file names, commands, hashes, timestamps and measured values. Descriptions people read are Geist, even inside a pane.

**The Label Is Metadata Rule.** Uppercase labels name columns, servers, levels and groups. They never sit above a heading as an eyebrow. Never put a unit like µs in an uppercase label: it reads as MS.

## Layout

A wide container (`max`, 90rem) with a fluid gutter. Landing sections stack full width, each opened by a hairline and `section` block padding. The hero is a two-column text row (headline left, lead and actions right, aligned to the bottom) over the full-width pair of consoles; under 60rem the text stacks, under 52rem the consoles stack. Download and addons sit in a 64rem column aligned to the container's left edge.

- **The numbers:** a two-column row per measure (name and description, then two runs) under a labelled group head that names its scale ("Bars from 0 to 1.97 s"). Each run is server label, bar, value. Under 60rem the columns stack; under 30rem the bar takes its own line.
- **The output check:** a subgrid of script line, Skript's output, NeoSkript's output; under 52rem each line is followed by both outputs, each led by a server label.
- **The guides:** Starlight's three columns, the header matched to the custom pages (4.5rem, the site's gutter, wordmark at 1.75rem).
- **Header on a phone:** the wordmark and Docs, Syntax, Download; Parser, Addons and GitHub move to the footer. Under 22rem the `.sk` tile replaces the wordmark.

## Elevation & Depth

Flat. No shadows anywhere (Starlight's `--sl-shadow-*` are `none`). Depth is ground versus raised, and hairlines. The sticky header is the ground at 88% under a 12px blur, as on plugins.xenastudios.co. The only atmosphere is the Xena horizon at the foot of the landing page; in the light theme its planet is drawn in paper tones with the magenta rim.

## Shapes

Panes, cards, asides, code blocks, link cards, file trees and pagination are 12px (`pane`). Inline insets (examples, a highlighted line) are 8px; small controls 6px; focus outlines 4px. Pills are fully round: buttons, the search field, badges, level chips. Bars are 2px-rounded rectangles on a track. A coloured edge is always a full 1px frame or a 1px rail, never a thick side stripe.

## Components

### Buttons
44px pills, 0.9375rem Geist. Primary: `--accent-fill` with `--on-accent`, 600, one per action row; hover mixes 14% text in. Secondary: transparent with a rule frame; hover warms the frame to a 60% accent mix. Download's main pills are 52px.

### Console pane (signature)
`.pane` / `.pane-bar` / `.pane-body` in `site.css`. A rule frame, 12px corners, a ground-coloured title strip with labels (server and version left, a clock or date right), a raised body. Log lines (`.log`) are a grid of stamp, level and message; a command line uses `>` as its level and a muted message. NeoSkript's pane has an accent-tinted frame and an accent title. The landing's pane footer reads the tick figure. Used by the hero, the output check, download's release and terminal panes; Starlight's terminal frames match it (no window dots, a TERMINAL label).

### The replay
Both panes start at 0; each line appears at its measured time, the clocks count and stop at each server's end; a Replay button restarts it. Runs once when the consoles are 40% in view. The page is rendered finished, so nothing depends on the script; with reduced motion it never runs and the button stays hidden.

### Comparison table
A real `<table>`: the point in bold, Skript muted, NeoSkript in text with a "Same" level tag where nothing changes. Under 44rem rows stack with server labels.

### Docs (Starlight)
- **Sidebar:** group names as labels; links muted; the current guide in text, 600, on a 10% accent wash with a 1px accent rail on the tree line.
- **Page header:** the title, then the description as a muted lead, over a hairline.
- **Reading:** h2 opens a section with a hairline above and more space above than below; links in text with a 35% underline that turns magenta; inline code raised in a rule frame; keys with a 2px bottom edge; tables with label heads and hairline rows; blockquotes with a 1px accent rail.
- **Code blocks:** Expressive Code with the brand's themes (`src/lib/code-themes.mjs`) and Skript from the VS Code extension's grammar (`editors/vscode/syntaxes/`), as ```` ```sk ```` or ```` ```skript ````. Keywords magenta (structures bold), strings and numbers in string, variables a magenta tint, comments muted italic. Editor frames show the file name in mono on a tab with a 2px accent underline.
- **Asides:** raised panes led by a level tag: note INFO (muted), tip TIP (accent), caution WARN (string, string-tinted frame), danger ERROR (accent, accent-tinted frame). The icon is hidden; the level says it in words.
- **Tabs:** Geist labels, the chosen one in text over a 2px accent underline on a hairline.
- **Cards / CardGrid:** raised panes, the icon in the accent with no tile, muted body. **LinkCard:** the same pane, the arrow turns accent and nudges 3px on hover.
- **Steps:** mono numerals in the accent in a ruled circle on the ground, joined by a hairline.
- **FileTree:** a raised pane in mono 0.8125rem, hairline guides, highlighted names in the accent.
- **Badge:** pill-shaped level chips, 12% tinted fill and a full frame in the variant's colour (default and tip, success and danger in the accent; caution in string; note muted).
- **Previous / next:** two ruled panes with a label for the direction and the title in Geist.

### Syntax reference
Each entry is a pane: a title strip with the copy-link icon in the accent, the name, and outlined label pills (category, Skript version; flags in string), the patterns on the ground (`%type%` slots in the accent, regex in string), the description and facts on raised, then full-width 44px rows that open. The sidebar marks the current category and entry with a wash and a 1px rail.

### Parser
A full-window tool under the header. Monaco's theme is built at runtime from the tokens, `vs` or `vs-dark` by theme, so it follows light and dark. Problem cards are panes framed in their level's colour (a dashed string frame for a warning) and say Error or Warning on Line N in words.

### External arrow and icons
The authored 10×10 up-right arrow (`External.astro`) on every link to another site; the tools' 16×16 stroke icons (`src/lib/icons.ts`). No Unicode or emoji icons.

## Do's and Don'ts

### Do:
- **Do** use the tokens and both themes; check new colours for 4.5:1 in each.
- **Do** put a console pane only where the content is a log, a command, code, or a tool's output.
- **Do** keep Skript muted and NeoSkript in text with magenta marks, on one axis per labelled group.
- **Do** take numbers from `src/data/*.json` and `docs/PROGRESS.md`; never type one into a page.
- **Do** keep pills at 44px, one primary per action row.
- **Do** honour `prefers-reduced-motion`: everything is visible without animation.
- **Do** emit styles as files and scripts from files or hashed inline blocks (the CSP in `public/_headers`).

### Don't:
- **Don't** dress words in mono or the page in green-on-black terminal styling.
- **Don't** add shadows, glows, gradient text, or atmosphere beyond the horizon.
- **Don't** put an eyebrow label above a heading, or a thick coloured stripe on the side of a card or note.
- **Don't** use magenta decoratively, or Xena's Ion Blue anywhere.
- **Don't** recolour the NeoSkript logo; the light theme uses the brand's light logo.
- **Don't** mix colours in oklch.
