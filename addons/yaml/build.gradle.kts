// NeoSkriptYaml: YAML files from scripts, with skript-yaml's syntax. See addons/README.md and the addon's page on
// neoskript.dev (website/src/content/docs/docs/addons/yaml.md).
plugins {
    id("neoskript.addon-conventions")
}

description = "YAML files from scripts, with skript-yaml's syntax: load, read, change and save them safely on" +
    " Paper and Folia."

neoskriptAddon {
    pluginName = "NeoSkriptYaml"
    mainClass = "co.xenastudios.neoskript.addons.yaml.YamlAddon"
    replaces = listOf("skript-yaml")
}

// SnakeYAML comes with Paper (its paper-api dependency), the same library skript-yaml uses, so files are written
// the same way. Nothing to ship.
