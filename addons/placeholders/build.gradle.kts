// NeoSkriptPlaceholders: PlaceholderAPI from scripts, with skript-placeholders' syntax. See addons/README.md.
plugins {
    id("neoskript.addon-conventions")
}

description = "PlaceholderAPI from scripts: register placeholders and read them, with skript-placeholders' syntax."

neoskriptAddon {
    pluginName = "NeoSkriptPlaceholders"
    mainClass = "co.xenastudios.neoskript.addons.placeholders.PlaceholdersAddon"
    replaces = listOf("skript-placeholders")
    // Enables after PlaceholderAPI when it's installed, so its expansions can be registered.
    softDepend = listOf("PlaceholderAPI")
}

dependencies {
    // The server has PlaceholderAPI, if it has it at all: never shaded.
    compileOnly(libs.placeholderapi)
    testImplementation(libs.placeholderapi)
}
