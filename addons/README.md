# Official addons

NeoSkript's own addons, built on [`neoskript-api`](../neoskript-api) and released with NeoSkript at the same version. Each replaces a community Skript addon (skript-db, skript-reflect...) and takes its syntax, so scripts written for it run unchanged. Which addons are planned, and why, is in [issue #12](https://github.com/xena-studios/neoskript/issues/12). This page is how they're laid out and built.

## Layout

```
addons/
  support/      shared code, shaded into each addon: finding the community addon it replaces, NeoSkript's version
  template/     a minimal addon to copy; built and tested, never released
  <name>/       one folder per addon, e.g. db/ for NeoSkriptDb
    build.gradle.kts
    syntax-docs.json            docs for its syntax on neoskript.dev and in the editors
    src/main/java/co/xenastudios/neoskript/addons/<name>/...
    src/test/java/...
```

Every folder in `addons/` with a `build.gradle.kts` is part of the build (`settings.gradle.kts` finds them), so `./gradlew build` builds and tests them, and CI does too.

## Starting one

1. Copy `addons/template` to `addons/<name>`, and rename its package to `co.xenastudios.neoskript.addons.<name>`.
2. In `build.gradle.kts`, fill in `neoskriptAddon { ... }`: the plugin's name (`NeoSkriptDb`), its main class, the community addons it replaces by every plugin name they've gone by (`skript-reflect` and `skript-mirror`), and the packages of any libraries it ships. Drop `syntaxExport = false`.
3. Start `onEnable` with `OfficialAddon.start(this)`, then register the syntax with `NeoSkript.get().addon(...)`, as the template does. The [Addon API guide](https://neoskript.dev/docs/addon-api/) covers the API; for anything slow (a database, the web), use pausing elements, never block a server thread.
4. Start element ids with the plugin's name (`NeoSkriptDbEffExecute`), so they can't clash.
5. Write `syntax-docs.json` (see below), a smoke test in the harness, and a scenario comparing it with the original on Skript (issue #12's principles).

## What the convention plugin does

`id("neoskript.addon-conventions")` (in `build-logic`):

- **Dependencies:** `neoskript-api` and Paper's API, compile-only (NeoSkript and the server provide them at run time; an addon with its own copy of the API can't reach NeoSkript), and `addons/support`, shaded in.
- **plugin.yml** is made from `neoskriptAddon { ... }`: name, the version, main class, NeoSkript's own `api-version` (read from its plugin.yml), `folia-supported: true`, `depend: [NeoSkript]` (so it enables after NeoSkript, which waits for its syntax), `softdepend` with the addons it replaces (so one that can load loads first, and the check below sees it whatever the order), description, website and authors. Don't write one by hand.
- **neoskript-addon.properties**, next to it, records the NeoSkript version it was built with, the addons it replaces and whether its syntax is exported; the support code and the harness read it.
- **Version:** the addon's version is NeoSkript's (`gradle.properties`). They're released together.
- **One jar:** `build/libs/<PluginName>-<version>.jar`, with its libraries shaded and relocated into `co.xenastudios.neoskript.addons.<name>.libs`, so another plugin's copy of HikariCP or Jackson can't clash. The jar without them is `-plain`, and isn't for servers.
- **Nothing outside its package:** `checkShadedPackages`, part of `check` and so of `./gradlew build`, fails if the jar has a class outside `co.xenastudios.neoskript.addons.<name>`. A library pulls in others (HikariCP brings slf4j-api): relocate each package it names, or make the dependency `compileOnly` when the server already has it.
- **Licence:** the SPDX GPL-3.0-only header on every source file, checked by Spotless as everywhere else, and `LICENSE` in the jar's `META-INF`.

## The community addon it replaces

NeoSkript doesn't run next to Skript ([D10](../docs/PLAN.md#2-locked-decisions)), and a community addon like skript-db needs Skript, so today it can't load with NeoSkript at all: Paper refuses it. What happens when both jars are in `plugins/` is [D21](../docs/PLAN.md#2-locked-decisions):

- **Only the original's jar is there** (it can't run): ours loads, and the console says that jar is the original, that ours replaces it and scripts written for it should work unchanged (with the addon's docs listing any differences), and to remove the jar.
- **The original is loaded** (only possible once Skript's addon compatibility layer exists): ours doesn't load, so the two never register the same syntax, and the console says to remove one of them. It's a soft dependency of ours, so it loads first if it loads at all, and ours checks whether it's loaded rather than enabled, so load order can't change the outcome.

`OfficialAddon.start` does both. It asks the server for each original by name first, and only if none is loaded reads the jars in `plugins/`, finding the original by the `name:` in its plugin.yml, whatever the file is called. When the compatibility layer comes, it should skip loading a community addon that an installed official one replaces, so ours wins by default; then the second case only happens if someone asks for the original.

## Syntax docs and the editors

The `syntax-export` harness scenario installs every official addon whose build doesn't set `syntaxExport = false`, so their syntax is in `syntax-export.json`, each element, type and function with its addon's plugin name. `pnpm syntax:export` in `website/` turns that into neoskript.dev's syntax reference, where addon syntax carries a badge with the addon's name, and the editors' hover and completion, which say "From the ... addon".

Descriptions and examples come from the addon's `syntax-docs.json`, written as `website/scripts/syntax-extra.json`'s `written` entries, by element id:

```json
{
  "elements": {
    "NeoSkriptDbEffExecute": {
      "name": "Execute",
      "description": ["Runs SQL on a database, and waits for it without blocking the server."],
      "examples": ["execute \"SELECT * FROM homes WHERE owner = %player's uuid%\" in {db} and store the result in {_homes::*}"],
      "since": ["1.0.0"]
    }
  },
  "types": {},
  "functions": {}
}
```

## The addons

| Folder | Plugin | Replaces | Docs |
|---|---|---|---|
| `yaml/` | NeoSkriptYaml | skript-yaml 1.7.2 | [neoskript.dev/docs/addons/yaml](https://neoskript.dev/docs/addons/yaml/) |

## Releasing

An addon is released with NeoSkript, at the same version, from the same tag: its jar from `./gradlew :addons:<name>:build` goes on the GitHub release next to NeoSkript's.

Each addon gets its own Modrinth project, which only the owner can create. For each, they need:

- the project: name (`NeoSkript DB`), slug (`neoskript-db`), summary, description (what it replaces, that scripts run unchanged, that it needs NeoSkript), icon, licence GPL-3.0-only, the source link to this repository, Paper and Folia as loaders, the Minecraft versions NeoSkript supports;
- a **required dependency** on the NeoSkript project (`FuVeJ2X5`), and an **incompatible** one on the community addon's project, if it's on Modrinth;
- the project id added to the release steps, so each release uploads the addon's jar to it with the same version and changelog as NeoSkript's.

### What each project needs

**NeoSkriptYaml** (`addons/yaml`):

- name `NeoSkript YAML`, slug `neoskript-yaml`;
- summary: "YAML files from scripts, with skript-yaml's syntax: scripts written for skript-yaml run unchanged on NeoSkript.";
- description: what it replaces (skript-yaml), that scripts and files carry over unchanged, that saves are written off the server's threads and never half written, that it runs on Folia, that it needs NeoSkript at the same version, and a link to [the docs](https://neoskript.dev/docs/addons/yaml/) with the list of differences;
- icon, licence GPL-3.0-only, source link to this repository (`addons/yaml`), loaders Paper and Folia, NeoSkript's Minecraft versions;
- a required dependency on NeoSkript (`FuVeJ2X5`); skript-yaml isn't on Modrinth (it's on GitHub and SpigotMC), so there's no incompatible dependency to add;
- the jar to upload is `addons/yaml/build/libs/NeoSkriptYaml-<version>.jar`, and the project id goes into the release steps next to NeoSkript's.
