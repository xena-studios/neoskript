---
title: Writing scripts with AI
description: A skill that teaches Claude Code, Codex and other coding agents to write Skript that loads, for Skript and NeoSkript.
---

Coding agents write plausible English that isn't Skript, and Skript only accepts its registered patterns. The NeoSkript skill gives an agent the rules that matter: how scripts are shaped, how variables, commands, functions and events work, what keeps scripts fast, and that syntax it isn't sure of has to be looked up, not guessed. It points the agent at the [syntax reference](/syntax/) and at this site's [`llms.txt`](/llms.txt).

The skill is one file, [`SKILL.md`](https://github.com/xena-studios/neoskript/blob/main/plugins/skript/skills/skript/SKILL.md). It targets Skript 2.17 syntax with no addons, so what it writes runs on Skript and on NeoSkript.

## Claude Code

```sh
/plugin install skript --marketplace xena-studios/neoskript
```

On versions of Claude Code before 2.1.275, add the marketplace first, then install:

```sh
/plugin marketplace add xena-studios/neoskript
/plugin install skript@neoskript
```

## Codex

Codex reads skills from `~/.agents/skills/`:

```sh
mkdir -p ~/.agents/skills/skript
curl -fsSL https://neoskript.dev/skill/SKILL.md -o ~/.agents/skills/skript/SKILL.md
```

Restart Codex if the skill doesn't show up.

## Other agents

Any agent that reads Agent Skills (a folder with a `SKILL.md`) can use the same file from `https://neoskript.dev/skill/SKILL.md`. For one that doesn't, paste the file into its instructions.

## Checking what it writes

Every example in the skill is loaded on real Skript and NeoSkript servers by NeoSkript's test harness (the `skill-examples` scenario), so the skill's own scripts load without errors on both. What an agent writes for you still needs checking: reload it on a test server with `/sk reload <script>` and read the errors, or paste it into the [parser](/parser/).
