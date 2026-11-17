// The template for an official addon: copy this folder to addons/<name> and change what's below.
plugins {
    id("neoskript.addon-conventions")
}

description = "The template for official NeoSkript addons. Not released."

neoskriptAddon {
    // The plugin's name, as Paper and /sk info show it.
    pluginName = "NeoSkriptTemplate"
    mainClass = "co.xenastudios.neoskript.addons.template.TemplateAddon"
    // The community addons it replaces, by their plugin names: it says to remove their jars, and won't load next
    // to one that's running.
    replaces = listOf("skript-template")
    // The template's syntax isn't for neoskript.dev or the editors. A real addon leaves this out.
    syntaxExport = false
    // Libraries it ships, relocated into its own package:
    // relocate = listOf("com.zaxxer.hikari")
}

dependencies {
    // Libraries it ships go in implementation, and their packages in relocate above:
    // implementation("com.zaxxer:HikariCP:6.3.0")
}
