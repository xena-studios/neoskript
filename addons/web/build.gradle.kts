// NeoSkriptWeb: HTTP requests and JSON from scripts, with the syntax of Reqn and skJson's HTTP and JSON parts.
plugins {
    id("neoskript.addon-conventions")
}

description = "HTTP requests, webhooks and JSON for NeoSkript: scripts written for Reqn or skJson's requests and JSON run unchanged."

neoskriptAddon {
    pluginName = "NeoSkriptWeb"
    mainClass = "co.xenastudios.neoskript.addons.web.WebAddon"
    // Reqn, and skJson's plugin name.
    replaces = listOf("Reqn", "SkJson")
}

dependencies {
    // Paper ships Gson, the JSON library skJson is built on, so JSON reads and prints exactly as skJson's does.
    compileOnly(libs.gson)
    testImplementation(libs.gson)
}

dependencies {
    // Scripts run through NeoSkript's real compiler and standard library in the tests, without a server.
    testImplementation(project(":neoskript-paper"))
    testImplementation(project(":neoskript-compiler"))
    testImplementation(project(":neoskript-runtime"))
    testImplementation(project(":neoskript-frontend"))
}
