---
title: What NeoSkript is
description: A fast, modern drop-in replacement for Skript. Your .sk scripts run unchanged, only faster, on Paper and Folia.
---

NeoSkript runs [Skript](https://github.com/SkriptLang/Skript) scripts. It isn't a fork: it's a clean-room rewrite that treats Skript's behaviour as the spec, so the `.sk` files you have today load and behave the same way. What changes is underneath. Each trigger is compiled to JVM bytecode, so the JIT can optimise across a whole script instead of stepping through syntax one element at a time, and the threading model is built for Folia from the start.

:::caution[In development]
Nothing is released yet. [Development builds](/download) are for testing on a server you can afford to break. Where each milestone stands is in [the progress log](https://github.com/xena-studios/neoskript/blob/main/docs/PROGRESS.md).
:::

## How we check it's a drop-in

- **Skript's own test suite.** Every test in Skript 2.17's suite runs on NeoSkript, and all 862 pass.
- **Side by side with real Skript.** A [differential test harness](https://github.com/xena-studios/neoskript/tree/main/test-harness) runs the same scripts on a real Skript server and a NeoSkript server and compares what they do, message by message.

## What it runs on

| | |
|---|---|
| Server | Paper or Folia |
| Minecraft | 1.21.4 or newer |
| Java | 21 or newer |

Skript addons don't load on NeoSkript yet: an addon compatibility layer is planned after 1.0. Addons written against NeoSkript's own API work from the start.
