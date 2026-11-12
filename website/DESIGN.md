---
name: NeoSkript website
description: neoskript.dev, NeoSkript's home. Neo Magenta on night, Geist for words, the same script on two servers across one staff.
colors:
  magenta: "#FF4D9D"
  magenta-daylight: "#D61F78"
  string: "#FFC2DD"
  night: "#110B13"
  night-raised: "#1B1320"
  night-rule: "#30243A"
  text: "#F7F1F6"
  muted: "#A898A9"
  ink: "#1A1219"
typography:
  display:
    fontFamily: "Geist Variable, system-ui, sans-serif"
    fontSize: "clamp(2.5rem, 1.2rem + 5.2vw, 5.25rem)"
    fontWeight: 700
    lineHeight: 1
    letterSpacing: "-0.035em"
  page-title:
    fontFamily: "Geist Variable, system-ui, sans-serif"
    fontSize: "clamp(2.5rem, 1.6rem + 3.6vw, 4.5rem)"
    fontWeight: 700
    lineHeight: 1
    letterSpacing: "-0.035em"
  category-title:
    fontFamily: "Geist Variable, system-ui, sans-serif"
    fontSize: "clamp(2.25rem, 1.6rem + 2.6vw, 3.5rem)"
    fontWeight: 700
    lineHeight: 1.05
    letterSpacing: "-0.03em"
  headline:
    fontFamily: "Geist Variable, system-ui, sans-serif"
    fontSize: "clamp(1.75rem, 1.3rem + 1.9vw, 2.75rem)"
    fontWeight: 700
    lineHeight: 1.1
    letterSpacing: "-0.03em"
  title:
    fontFamily: "Geist Variable, system-ui, sans-serif"
    fontSize: "1.5rem"
    fontWeight: 700
    lineHeight: 1.05
    letterSpacing: "-0.02em"
  lead:
    fontFamily: "Geist Variable, system-ui, sans-serif"
    fontSize: "clamp(1.0625rem, 1rem + 0.35vw, 1.25rem)"
    fontWeight: 400
    lineHeight: 1.7
  body:
    fontFamily: "Geist Variable, system-ui, sans-serif"
    fontSize: "1rem"
    fontWeight: 400
    lineHeight: 1.6
  numeral:
    fontFamily: "Geist Variable, system-ui, sans-serif"
    fontSize: "clamp(1.125rem, 0.9rem + 0.9vw, 1.625rem)"
    fontWeight: 500
    letterSpacing: "-0.02em"
    fontFeature: "tnum"
  figure:
    fontFamily: "Geist Variable, system-ui, sans-serif"
    fontSize: "clamp(1.5rem, 1.2rem + 1.2vw, 2.25rem)"
    fontWeight: 700
    letterSpacing: "-0.03em"
    fontFeature: "tnum"
  ui:
    fontFamily: "Geist Variable, system-ui, sans-serif"
    fontSize: "0.9375rem"
    fontWeight: 500
  mono:
    fontFamily: "Geist Mono Variable, ui-monospace, monospace"
    fontSize: "0.875rem"
    fontWeight: 400
    fontFeature: "liga 0, calt 0"
  mono-small:
    fontFamily: "Geist Mono Variable, ui-monospace, monospace"
    fontSize: "0.8125rem"
    fontWeight: 400
  label:
    fontFamily: "JetBrains Mono Variable, ui-monospace, monospace"
    fontSize: "0.75rem"
    fontWeight: 500
    letterSpacing: "0.14em"
  label-small:
    fontFamily: "JetBrains Mono Variable, ui-monospace, monospace"
    fontSize: "0.6875rem"
    fontWeight: 500
    letterSpacing: "0.14em"
rounded:
  focus: "4px"
  code-block: "12px"
  pill: "999px"
spacing:
  gutter: "clamp(1rem, 0.4rem + 3vw, 4rem)"
  max: "90rem"
  action-gap: "0.75rem"
  row-block: "0.7rem"
  row-inline: "1.25rem"
  section: "clamp(4rem, 2.5rem + 5vw, 7.5rem)"
components:
  button-primary:
    backgroundColor: "{colors.magenta}"
    textColor: "{colors.night}"
    rounded: "{rounded.pill}"
    padding: "0 1.375rem"
    height: "44px"
  button-primary-hover:
    backgroundColor: "color-mix(in srgb, #FF4D9D 85%, white)"
    textColor: "{colors.night}"
  button-secondary:
    backgroundColor: "transparent"
    textColor: "{colors.text}"
    rounded: "{rounded.pill}"
    padding: "0 1.375rem"
    height: "44px"
  staff-script-cell:
    backgroundColor: "{colors.night-raised}"
    textColor: "{colors.text}"
    typography: "{typography.mono}"
    padding: "0.7rem 1.25rem"
    width: "36rem"
  skip-link:
    backgroundColor: "{colors.magenta}"
    textColor: "{colors.night}"
    rounded: "{rounded.pill}"
    padding: "0.5rem 1rem"
---

# Design System: NeoSkript website

## Overview

**Creative North Star: "Two servers, one staff"**

neoskript.dev is NeoSkript's own brand on night, laid out with xenastudios.co's grammar: hairlines, pills, one column of ideas, no cards. Every colour, logo and scene comes from [xena-studios/brand](https://github.com/xena-studios/brand) (`tokens/neoskript.*`, synced into `src/brand/` by `pnpm brand:sync`); the site composes them with exact type, hairline rules and space. Xena frames the product with the planet horizon and the XENA endorsement in the footer, and nothing else.

The signature is the staff: the same script (or the same measured job) runs down a raised centre column between two magenta rails, with Skript's result mirrored on its left and NeoSkript's on its right, one grid row per line so the two sides always line up. The landing page leads with the promise and three headline numbers, then Skript and NeoSkript side by side in a comparison table, then the performance staff (bars share a scale per group, so a bar's length is the time it took) and the differential staff that prints what each server output for the same probe, then the switch, questions people ask before switching, and the call to try it. The rest of the site is hairline rows, plain claims and links to source.

Two surfaces share one palette. The custom pages (landing, download, the syntax reference and the parser) are dark only (`color-scheme: dark`) and styled by `src/styles/site.css`, with `src/styles/syntax.css` for the reference. The guides are Starlight, mapped onto the same tokens in `src/styles/theme.css`. They follow the visitor's system theme (Starlight's Auto), with a light theme in ink and magenta-daylight: going from the dark landing to light guides is deliberate, for people who read docs in light mode. Both surfaces share the header links and the footer (`src/lib/nav.ts`, `SiteFooter.astro`), so they read as one site.

**Key Characteristics:**
- Neo Magenta on near-black, aubergine-tinted night; magenta marks meaning, never decoration.
- Geist Bold headlines at tight negative tracking; Geist Mono for script and output; JetBrains Mono uppercase labels for metadata.
- Hairline rules instead of cards; flat, no shadows anywhere.
- 44px pills for actions; one authored SVG arrow is the only icon on the content pages, with the landing questions' chevron in the same style. The two tools (the parser and the syntax reference) add a small authored icon set.
- The tools are laid out as tools, like the community tools people already use: a parser in a full-window app, a reference with a sidebar and one flat card per entry. Cards are theirs alone.
- One authored motion moment (bars draw out, rows settle once); reduced motion shows everything at once.

## Colors

A restrained palette: tinted night neutrals carry almost everything, and Neo Magenta appears only where it means "NeoSkript" or "act here".

### Primary
- **Neo Magenta** (`magenta`): the primary pill, the skip link, the second line of the hero headline, the staff's two rails, NeoSkript's bars, the "N× faster" and "Same" marks, the step numerals, link-underline hover, focus rings and text selection (35% mix). Also the Starlight dark-theme accent. 6.3:1 on night; night text on it is 6.3:1.
- **Magenta Daylight** (`magenta-daylight`): the Starlight light-theme accent only, chosen for contrast on white (4.85:1).

### Secondary
- **String Pink** (`string`): string literals in highlighted Skript (the brand's code colouring: keywords magenta, strings pale, punctuation muted), and Starlight's dark `accent-high`.

### Neutral
- **Night** (`night`): the page ground, the header and footer, and text on magenta. Also the `theme-color` meta.
- **Raised Night** (`night-raised`): the staff's centre column, and Starlight's `gray-6`. The only raised tone.
- **Night Rule** (`night-rule`): every hairline (header and footer borders, section tops, staff and row rules, download rows), the bar track, the secondary pill's border and the scrollbar thumb.
- **Text** (`text`): headings, body emphasis, times and NeoSkript's readings. 17.5:1 on night.
- **Muted** (`muted`): lead paragraphs, nav links at rest, Skript's times and bars, labels, captions, definitions. 7.2:1 on night, 6.7:1 on raised night.
- **Ink** (`ink`): text in the guides' light theme; its light greys are ink mixed with white.

### Named Rules
**The Brand Repo Rule.** Colours are the synced `--neoskript-*` custom properties, aliased as `--night`, `--raised`, `--rule`, `--text`, `--muted`, `--magenta`, `--string`. Never type a new hex; resync from the brand repo.

**The sRGB Mix Rule.** Every translucent or derived colour is `color-mix(in srgb, ...)`: Starlight's greys from text and night, the hover tints, the selection. oklch mixing drifts the hue.

**The Two Sides Rule.** On a staff, NeoSkript's side is magenta and Skript's is muted. Skript is never shown in magenta, and NeoSkript never in muted.

## Typography

**Display Font:** Geist (variable, self-hosted via Fontsource, with system-ui)
**Body Font:** Geist
**Code Font:** Geist Mono (variable, with ui-monospace), ligatures off
**Label Font:** JetBrains Mono (variable, with ui-monospace)

**Character:** A crisp grotesk doing the talking in bold, tightly tracked headlines, with two monos split by job: Geist Mono for anything that is script or output, JetBrains Mono (Xena's family) only for small uppercase metadata.

### Hierarchy
- **Display** (`display`): the landing hero, capped at 16ch, balanced.
- **Page title** (`page-title`): the h1 of download, the syntax index and the parser: display's weight and tracking at a smaller clamp. The syntax categories' h1 is a step down (`category-title`).
- **Headline** (`headline`): section headings, capped at 22ch, balanced.
- **Title** (`title`): the download page's "Development build" heading. The staff's measure names sit near this level at 1.0625rem, tracked -0.01em.
- **Lead** (`lead`): the paragraph under a heading, in muted, capped at 62ch (52ch in the hero).
- **Body** (`body`): prose, `text-wrap: pretty`; download definitions cap at 66ch.
- **Numeral** (`numeral`): benchmark times, always tabular. The facts row's figures are `figure`.
- **UI** (`ui`): nav links, buttons, the footer and chips, at 0.9375rem.
- **Mono** (`mono`): staff rows, inline code, the SHA-256. `mono-small` (0.8125rem) for the measured-job descriptions, examples and patterns in lists.
- **Label** (`label`): uppercase. Staff column heads, the "N× faster" and "Same" marks, the footer licence line, and, at `label-small` (0.6875rem), the per-cell server names on narrow screens and the syntax reference's term labels.

### Named Rules
**The Label Is Metadata Rule.** Uppercase JetBrains Mono labels name columns, servers, marks and licences. They never sit above a heading as an eyebrow.

**The Mono Means Code Rule.** Geist Mono is for script lines, server output, file names, hashes and commands. Words and numbers people read are Geist.

## Layout

One centred column of ideas inside a wide container (`max`, 90rem) with a fluid side gutter (`gutter`). Sections stack full-width, each opened by a hairline top rule and generous fluid block padding (`section`); the facts row is tighter (2.5rem to 3.5rem). Hero and closing copy are centred; everything else is left-aligned.

The staff is a three-column grid (`minmax(0, 1fr) | minmax(0, 36rem) | minmax(0, 1fr)`) whose rows use `subgrid`, so every row's three cells share the columns. Benchmark bars use one fixed track width on both sides (`clamp(6rem, 1rem + 12vw, 13rem)`), so lengths compare across the staff and down it.

Breakpoints, as built:
- **Under 60rem** the staff becomes one column: the script line first, then each server's output under it, each cell a small grid of server name, value and mark. The magenta rails move to the staff's outer edges and the column heads hide.
- **Under 52rem** the facts row, the three steps and the footer collapse to one column.
- **Under 44rem** download rows and build facts stack their term over their definition.
- **Under 30rem** the header tightens: wordmark 1.375rem, nav at 0.875rem with no wrap, and Docs and GitHub leave the header (both are in the footer, and the landing links the guides).
- **Under 22rem** the `.sk` tile stands in for the wordmark.

Actions sit in a wrapping row with a 0.75rem gap. The header is 4.5rem tall.

## Elevation & Depth

Flat. There are no shadows anywhere; Starlight's `--sl-shadow-sm/md/lg` are set to `none`. Depth comes only from tone (night to raised night) and hairline rules. The only atmospheric element is the Xena horizon at the foot of the landing page, an inline SVG masked to fade into the page.

### Named Rules
**The Hairline Rule.** Separation is a 1px `night-rule` line, never a box. Rows, sections, the download build block and the steps are ruled, not carded.

## Shapes

Shapes are straight. Rows, sections and the staff are square-edged and bounded by hairlines. The only rounding is the fully rounded pill (`pill`) on buttons and the skip link, a small 4px radius on focus outlines (`focus`), and 12px on the guides' code blocks (`code-block`, Expressive Code). Benchmark bars are flat rectangles on a `night-rule` track.

## Components

### Buttons
Pills, quiet and certain.
- **Shape:** fully rounded (999px), at least 44px tall, 1.375rem inline padding, 0.9375rem Geist 500, never wrapping.
- **Primary:** solid Neo Magenta with night text. One per action row ("Download the beta", the jar download).
- **Hover / Focus:** primary lightens to an 85% magenta / white mix; secondary's border warms to a 60% magenta mix. 180ms on the shared ease. Focus is the global 2px magenta outline at 3px offset.
- **Secondary:** transparent with a `night-rule` hairline and text colour ("See how it compares", "Read the docs", "On Modrinth").

### Navigation
- **Header:** the NeoSkript logo (1.75rem tall) left; Docs, Syntax, Parser, Download and GitHub right in muted 0.9375rem, turning text on hover and when `aria-current="page"`. A hairline under the bar. The links come from `src/lib/nav.ts`; the guides show the same links in Starlight's header and mobile menu (`components/starlight/SocialIcons.astro`), with search and the theme select beside them. The guides' header is set to match (`theme.css`): the same 4.5rem bar over a `night-rule` hairline, the site's gutter, the wordmark at the same 1.75rem (1.375rem under 30rem) with its clear space cropped, and the links at 1.75rem gaps, closing to 1rem beside the search under 72rem; between 50rem and 60rem Docs and GitHub leave it, as on a phone. Every structural rule in the guides (header, sidebar, mobile contents) is a hairline: `--sl-color-hairline-shade` is `night-rule`, never night.
- **Footer:** `SiteFooter.astro`, on every page, the guides included (after Starlight's edit link and previous/next), laid out as on Xena's other sites (raptorpanel.net, xenastudios.co): the XENA mark in text colour beside a label-styled "© <year> Xena Studios", a muted line on the licence and affiliation, then the site's links plus Modrinth, Source and "Report a bug", stacked and left-aligned. A hairline above.
- **Guides:** the current guide in the sidebar is text colour with a 1px magenta rail, not a filled pill; asides are hairline-ruled notes with no coloured stripe or fill; previous/next is a hairline row, not boxes.
- **Skip link:** a magenta pill that drops in at the top-left gutter on focus.

### Links
Inherit their colour, underlined at 0.25em offset in a 35% text mix; the underline turns magenta on hover. Every link to another site carries the External arrow.

### External arrow
The content pages' only icon besides the landing questions' chevron (`src/components/External.astro`): an authored 10x10 SVG up-right arrow, 1.4 stroke with round caps, in `currentColor`, sized 0.62em. It nudges 1px up-right on hover and focus. No Unicode arrows, no icon fonts.

### Tool icons
The parser and the syntax reference have a small authored set in the arrow's style (`src/lib/icons.ts`, `components/Icon.astro`): 16x16 strokes, 1.5 wide, round caps, `currentColor`, no fills except the Parse button's. Save, settings, new, close, chevron, parse, link, search, filter, files, one per syntax category, guides and parser. They're decorative: every icon-only control has an `aria-label`. Monaco's own codicons stay inside the editor.

### The staff (signature)
- **Centre column:** raised night between 1px magenta inline rails, Geist Mono 0.875rem, 0.7rem by 1.25rem cells, every cell ruled underneath. In the performance staff the centre holds the measure name in Geist and what was measured in muted mono.
- **Sides:** Skript's output right-aligned on the left in muted; NeoSkript's on the right in text, with a magenta "Same" or "N× faster" label.
- **Bars:** SVG, one scale across both rows and sides. NeoSkript's fill magenta; Skript's muted and mirrored to grow away from the staff; both on a `night-rule` track.
- **Hover:** pointing at a row tints it across both servers (7% magenta mix; 10% over raised night in the centre).
- **Motion:** rows settle in once (700ms, 6px rise, staggered 60ms from 120ms); in the performance staff, bars draw out from the staff instead (1100ms scale from zero).
- **Caption:** centred, muted 0.875rem, at most 72ch, naming the machine, server, date and a source link.

### Headline numbers
The hero's row of three, under a hairline: each a large magenta figure ("2.3×", tabular, 700) over its claim in text and one muted sentence with both servers' times. A muted note under the row names the reference and links to the numbers. Not cards.

### Comparison table
A real `<table>`, hairline rows: the point in bold text, Skript's answer in muted, NeoSkript's in text beside a 1px magenta rail (the column head is a magenta label). It includes where NeoSkript is behind (Skript addons), stated plainly. Under 44rem each row stacks: the point, then a Skript line and a NeoSkript line, each led by its label-small server name.

### Questions
`<details>` rows between hairlines: the question in bold text (at least 44px tall) with the authored chevron, which turns on open; the answer in muted, capped at 66ch. A two-column section above 52rem: the heading left, the rows right.

### Facts row and steps
Hairline-topped rows of three, not cards. Facts lead with a large tabular figure in text, a muted sentence, and a source link. Steps number themselves with a magenta JetBrains Mono counter above a bold first sentence.

### The parser (`/parser`)
A full-window tool under the site header (Base's `app` prop: no footer, and the body doesn't scroll above 60rem). A night menu bar: save and settings icons, then the File, Insert, Sharing and Help menus (WAI-ARIA menu buttons: arrows move, Left and Right switch menus, Esc closes); on the right the open file as a raised tab with a magenta underline, new file, the open-files list and the magenta Parse pill. Below it, Monaco (bundled from npm, loaded lazily, `src/scripts/parser-editor.ts`) on raised night takes two thirds and the results panel on night one third. A status bar across the foot in uppercase JetBrains Mono: STATUS and its word (magenta once parsed), then counts with the number in text and the word in magenta, each a toggle that lists its items, and CLICK TO PARSE at the end. Under 60rem the editor, results and status bar stack and the page scrolls, with the status bar sticky at the foot.

The editor's theme is built from the brand tokens at runtime (no typed hex): keywords magenta, structure keywords bold magenta, strings and numbers String Pink, variables and event values a 45% magenta / text mix, comments and punctuation muted, line numbers an 80% muted mix (4.7:1).

Monaco's overlays (the context menu, the command palette and go to line, find and replace, hovers, problem peeks) are night on raised night with a `night-rule` hairline, 4px corners and no shadow; the focused item is a 25% magenta mix with text colour. The theme names every widget colour Monaco reads (`menu.*`, `quickInput.*`, `list.*`, `input.*`, `editorWidget.*`, `widget.shadow`), because `vs-dark`'s defaults are grey. Two things make them reach the page: the editor's container is a `.monaco-component`, so the context menu, which Monaco draws in a shadow root beside the editor, inherits the theme's `--vscode-*` colours; and `.editor` defines the VS Code size tokens (`--vscode-strokeThickness`, `cornerRadius-*`, `spacing-*`, `shadow-*`) the standalone editor's CSS uses but never sets.

### Parser diagnostics
The one place magenta means "error": an error's label, its card's left rail, its squiggle and its overview-ruler mark are magenta, a warning's are String Pink with a dashed rail. That keeps to magenta's rule (it marks where to act), and colour is never alone: every problem card says Error or Warning on Line N, then the message and the line's code, and has a Go to line button.

### Syntax reference cards
`/syntax/<category>/` has a 15rem sticky sidebar (categories with icons and counts, Guides and Parser, then every entry's name; the current category and the entry in view get a 3px magenta bar) and a main column: a 52px search pill with a Filters toggle (category, Added in, and per page with examples, hide deprecated, cancellable only), a Clear filters pill, then one flat card per entry: a magenta header with night text (6.3:1) holding the link icon, the name and night badges (category, Skript version), the patterns on a night band, the description and the registry's facts on raised night, and full-width 44px rows that open (View examples, View event values) with a chevron. 4px corners, hairline borders, no shadows. Under 60rem the sidebar becomes a scrolling row of categories and the names list goes.

### Definition rows (download)
A `<dl>` of hairline-separated rows: bold term in a 14rem column, muted definition in the rest. The build block above them is a hairline-bounded band with its own small term/definition grid, not a card.

### Logo and Horizon
`Logo.astro` inlines the brand SVGs and crops their 50-unit clear space; the XENA mark takes `currentColor`, NeoSkript's own colours are never changed. `Horizon.astro` inlines the brand's recoloured horizon (night sky, magenta limb), absolutely positioned at the bottom of an `isolation: isolate` section, `clamp(14rem, 30vw, 26rem)` tall, masked from 55% to transparent.

## Do's and Don'ts

### Do:
- **Do** take every colour, logo and scene from the brand repo (`pnpm brand:sync`), and use the aliased tokens.
- **Do** separate with 1px `night-rule` hairlines and tone; keep everything flat.
- **Do** put the same thing on both sides of a staff: Skript muted on the left, NeoSkript magenta on the right, one row per line, one scale for every bar.
- **Do** carry the authored External arrow on every link to another site.
- **Do** keep pills at 44px, one primary per action row.
- **Do** honour `prefers-reduced-motion: reduce`: every animation and transition collapses to its end state, and nothing depends on an animation to become visible.
- **Do** emit styles as files: authored code has no `<style>` inlined into the page and no `style` attributes (`inlineStylesheets: "never"`, no inlined assets), and scripts run only from files or hashed inline blocks, per the CSP in `public/_headers`. Its `style-src` allows `'unsafe-inline'` only for the `style` attributes Starlight writes.

### Don't:
- **Don't** use cards or card grids on the content pages; facts, steps and rows are hairline rows. The tools' result and entry cards are the exception.
- **Don't** put an uppercase label above a heading as an eyebrow.
- **Don't** use Unicode or emoji arrows or icons; the SVG arrow, the questions' chevron, and in the tools their authored icon set, are the only icons.
- **Don't** add shadows, glows or gradient text, or any atmosphere beyond the Xena horizon.
- **Don't** use magenta decoratively, or Xena's Ion Blue anywhere.
- **Don't** recolour the NeoSkript logo.
- **Don't** mix colours in oklch.
