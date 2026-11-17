// neoskript-proxy: messages and proxy actions across a BungeeCord or Velocity network, over plugin messaging and
// Redis. See addons/README.md and the addon's page on neoskript.dev.
plugins {
    id("neoskript.addon-conventions")
}

description = "Messages and proxy actions across a network, over plugin messaging and Redis."

neoskriptAddon {
    pluginName = "NeoSkriptProxy"
    mainClass = "co.xenastudios.neoskript.addons.proxy.NeoSkriptProxy"
    // The community addons whose syntax it takes.
    replaces = listOf("SkProxy", "Skelocity", "SkRedis")
}

dependencies {
    // NeoSkript's own values (keyed lists), which tests read.
    testImplementation(project(":neoskript-runtime"))
}
