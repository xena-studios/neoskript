# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Stack

Astro with Starlight for the guides, in `website/` inside the neoskript repo. Built as static files and served by a Cloudflare Worker with static assets only, like xenastudios.co and Raptor's sites. The user named Starlight; the rest follows the other Xena sites.

## Users

- **Skript server owners** (primary). They run a Minecraft server on Paper or Folia with Skript scripts, often many, written by themselves or copied from forums. Their job: decide whether NeoSkript can replace Skript without breaking anything, then switch. They arrive from GitHub, Modrinth, Skript communities, or a link someone shared. They are skeptical: they've been burned by plugins that promise speed and break scripts.
- **Script writers.** People writing `.sk` day to day. Their job: look up a piece of syntax (its patterns, what it returns, an example), or check why a script won't parse. They use the syntax reference and the parser as tools, repeatedly.
- **Addon developers** (later, smaller). Java developers who want to add syntax through NeoSkript's addon API.

## Product Purpose

neoskript.dev is NeoSkript's home: it explains what NeoSkript is, proves the drop-in claim with checkable evidence, and carries the tools people need once they use it. It has five parts: the landing page, guides (`/docs`), a syntax reference generated from NeoSkript's own registry (`/syntax`), an in-browser parser that runs NeoSkript's real frontend (`/parser`), and downloads (`/download`).

Success: a server owner can tell in under a minute what NeoSkript is, whether it's released, and how to try it; a script writer finds any element's syntax in a few keystrokes and trusts that it matches what NeoSkript accepts.

## Positioning

NeoSkript is a clean-room, drop-in replacement for Skript: existing `.sk` scripts run unchanged, compiled to JVM bytecode, on Paper and Folia. The claim is backed by machinery others don't have: Skript's whole test suite passes on NeoSkript, and a differential harness runs the same scripts on real Skript and NeoSkript servers and compares the results. The site's reference and parser come from NeoSkript's own code, so they can't drift from what it actually accepts.

## Operating Context

- Server owners compare against Skript's own site (skriptlang.org docs) and community docs (skUnity, SkriptHub). The syntax reference and parser are measured against those.
- Script writers keep the reference open while editing scripts, and paste scripts into a parser to find errors. Shareable links to a parse result are common in support threads.
- Visitors arrive on desktop and phone alike; support questions often happen on phones.

## Capabilities and Constraints

- **Status: in development, nothing released.** The site must say so plainly. Before 1.0, `/download` offers development builds from CI, clearly labelled as such, with the status; Modrinth and GitHub Releases come with the public beta (M6).
- **Scope of syntax: NeoSkript's core library only for now.** Addon syntax (skUnity-style) comes later; the structure should leave room for it.
- Syntax descriptions and examples may reuse Skript's documentation with attribution: Skript is GPL-3.0-or-later and NeoSkript is GPL-3.0-only, so it's used under version 3.
- Requirements to state: Paper or Folia, Minecraft 1.21.4+, Java 21. NeoSkript refuses to load alongside Skript, and on first start copies `scripts/` and `variables.csv` from `plugins/Skript` (copy, not move).
- Community: GitHub only for now (issues, discussions). No Discord.
- Strict CSP like the other Xena sites: inline scripts only by hash, stylesheets as files. Styles allow `'unsafe-inline'` only because Starlight writes `style` attributes; our own code adds none.
- Terminology: **NeoSkript** (one word, capital N and S; never "Neoskript", "Neo Skript" or "NS"), **Skript**, **script** (a `.sk` file), **addon**, **syntax element** (event, effect, condition, expression, type, section, structure, function).

## Brand Commitments

- NeoSkript is a product brand under Xena Studios, with its own identity from xena-studios/brand (`neoskript/`, `tokens/neoskript.*`): the `.sk` tile mark, the NeoSkript wordmark, Neo Magenta on night. Xena supplies only the planet horizon and the XENA STUDIOS endorsement.
- Headline: **Keep your scripts. Lose the lag.** Modrinth summary: "A fast, modern drop-in replacement for Skript. Your .sk scripts run unchanged, only faster. For Paper and Folia."
- Voice, as across Xena: technical, honest, open. Plain claims, real specifics, links to the source instead of adjectives. No hype.
- The brand repo is the design source: logos, colours and scenes are synced from it, not redrawn.

## Evidence on Hand

- The README banner and headline (`.github/assets/banner.png`), and brand files in xena-studios/brand.
- Progress per milestone in `docs/PROGRESS.md`; the plan in `docs/PLAN.md`.
- Skript's test suite passing on NeoSkript; the differential test harness (`test-harness/`) and its known-differences list.
- The reference Skript docs export (`neoskript-frontend/src/testFixtures/resources/skript-syntax/2.17.0.json`).
- **Not on hand, must not be invented:** benchmark numbers (published at M6), user counts, testimonials, server counts, a release date.

## Product Principles

1. **Prove the drop-in claim.** Every compatibility or speed claim links to something checkable: the test suite, the differential harness, published benchmarks, the source.
2. **Generated from the code, never hand-copied.** The syntax reference and parser come from NeoSkript's registry and frontend, so they match what NeoSkript accepts.
3. **Honest status.** Unreleased means unreleased; development builds are labelled; no "coming soon" banners, no placeholder sections.
4. **Tools first for returning visitors.** Search, the reference and the parser are one keystroke or click away on every page.
5. **NeoSkript keeps its identity.** Xena frames it with the horizon and endorsement; it never takes Xena's blue.

## Accessibility & Inclusion

WCAG 2.2 AA, as across Xena: text contrast at least 4.5:1, visible focus everywhere, full keyboard use (including the parser's editor and the reference filters), semantic landmarks, `prefers-reduced-motion` respected, and colour never the only carrier of meaning (diagnostics carry an icon or label as well as a colour).
