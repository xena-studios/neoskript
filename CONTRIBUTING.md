# Contributing to NeoSkript

Thanks for your interest in NeoSkript! This guide covers building the project and writing addons.

## Building

NeoSkript uses Gradle (wrapper included) and a Java 25 toolchain.

```bash
./gradlew build      # compile, run tests, produce the plugin jar
./gradlew test       # tests only
```

The distributable plugin jar lands at
`neoskript-plugin/build/libs/neoskript-plugin-<version>.jar`.

## Project layout

| Module | Responsibility |
|---|---|
| `neoskript-api` | Public SPI (`Expression`, `Effect`, `Condition`, `Type`, `SyntaxRegistry`, `NeoSkriptAddon`, factories). Keep this stable and well-documented. |
| `neoskript-core` | Pipeline (lexer, pattern engine, parser, optimizer, runtime), variables, profiler, docs generator. No hard Bukkit dependencies in code paths exercised by tests. |
| `neoskript-lang` | Built-in events/effects/conditions/expressions. |
| `neoskript-platform` | Paper + Folia adapters (scheduler, event bridge, platform detection). |
| `neoskript-plugin` | The shaded plugin: bootstrap, script loader, commands, addon discovery. |
| `neoskript-testkit` | Conformance harness and the parser benchmark. |

## Tests

- Put unit tests next to the stage they cover, in the owning module.
- Core tests stay Bukkit-free where possible; `neoskript-core` has `paper-api` only on its **test**
  classpath for the few places that touch Bukkit types.
- Run `./gradlew build` before opening a PR — CI runs the same on JDK 25.

## Writing an addon

Addons contribute syntax through the `neoskript-api` SPI. Two integration paths:

### 1. ServiceLoader (library on NeoSkript's classpath)

Implement `NeoSkriptAddon` and declare it in
`META-INF/services/co.xenastudios.neoskript.api.NeoSkriptAddon`. NeoSkript discovers it at load.

```java
public final class MyAddon implements NeoSkriptAddon {
    @Override public String name() { return "MyAddon"; }

    @Override public void registerSyntax(SyntaxRegistry registry) {
        registry.registerEffect("shout %string%", arguments -> {
            Expression<?> message = arguments.get(0);
            return ctx -> Bukkit.broadcast(Component.text(
                    String.valueOf(message.getSingle(ctx)).toUpperCase()));
        });
    }
}
```

### 2. From a dependent plugin

A separate plugin that depends on NeoSkript can register from its own `onEnable`:

```java
NeoSkriptPlugin neoskript = (NeoSkriptPlugin) getServer().getPluginManager().getPlugin("NeoSkript");
neoskript.registerAddon(new MyAddon());
```

Syntax added this way applies to scripts loaded by the next `/neoskript reload`.

## Commit style

This repo uses [Conventional Commits](https://www.conventionalcommits.org/) for subjects, with a
short `-` bullet list in the body describing what changed.

## Roadmap

See [`docs/PLAN.md`](docs/PLAN.md) for the architecture and phased roadmap.
