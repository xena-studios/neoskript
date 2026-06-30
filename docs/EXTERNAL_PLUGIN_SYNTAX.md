# External-Plugin Syntax (out of scope for NeoSkript core)

These Skript syntaxes only work when a **third-party plugin** is installed — they're implemented in
Skript via optional "hooks" (`ch.njol.skript.hooks.*`) that no-op when the dependency is absent.
NeoSkript core deliberately **does not implement them**: they need an external runtime dependency we
can't assume, and they belong in an addon (against the `neoskript-api`) rather than the core jar.

They are **excluded from the conformance denominator** — implementing them is not part of reaching
"100% of the documented core." If addon support is desired later, each maps cleanly onto an addon
that registers the same syntax once the relevant plugin is present.

_Reference: Skript 2.15.3. Last updated: 2026-06-30._

## Vault — economy

| Entry | Kind | Requires |
|---|---|---|
| Money (balance of a player) | expression | Vault + an economy plugin |

## Vault — permissions / groups

| Entry | Kind | Requires |
|---|---|---|
| Group (a player's primary permission group) | expression | Vault + a permissions plugin |
| All Groups (`all groups`) | expression | Vault + a permissions plugin |

> Note: basic `has permission %string%` (no group/Vault dependency) **is** implemented in core — only
> the Vault-backed *group* expressions are excluded.

## Vault — chat

| Entry | Kind | Requires |
|---|---|---|
| Prefix / Suffix (a player's chat prefix/suffix) | expression | Vault + a chat plugin |

## Region plugins (WorldGuard, GriefPrevention, Residence, PreciousStones, …)

| Entry | Kind | Requires |
|---|---|---|
| Region | expression | a supported region plugin |
| Regions At (regions at a location) | expression | a supported region plugin |
| Blocks in Region | expression | a supported region plugin |
| Region Members & Owners | expression | a supported region plugin |
| Is Member / Owner of Region | condition | a supported region plugin |
| Region Contains | condition | a supported region plugin |
| Can Build (region-aware build check) | condition | a supported region plugin |

> Note: NeoSkript core implements a **non-region** `can build` condition (a game-mode check:
> survival/creative can build, adventure/spectator cannot). The full region-aware `can build` above —
> which consults a region plugin's build flags — is out of scope and lives here.

## Total

**11 entries excluded** (1 economy + 2 permission + 1 chat + 7 regions). These are subtracted from the
documented total when computing conformance coverage.
