plugins {
    id("neoskript.java-conventions")
}

dependencies {
    api(project(":neoskript-api"))
    api(project(":neoskript-runtime"))
    // Alias names are written as in scripts: 'boot¦s', '[any] potion'.
    implementation(project(":neoskript-frontend"))
    compileOnly(libs.paper.api)
    // Plugins hooked into when they're installed, as Skript's hooks. Only their APIs, and never shaded.
    compileOnly(libs.vault.api) { isTransitive = false }
    for (api in listOf(libs.worldguard.bukkit, libs.worldguard.core, libs.worldedit.core, libs.worldedit.bukkit)) {
        compileOnly(api) { isTransitive = false }
    }
    compileOnly(libs.griefprevention) { isTransitive = false }
    // Language tests run the real standard library through the compiler, without a server.
    testImplementation(project(":neoskript-compiler"))
    testImplementation(libs.paper.api)
    testImplementation(libs.vault.api) { isTransitive = false }
}
