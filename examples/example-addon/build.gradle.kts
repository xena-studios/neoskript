// An example NeoSkript addon. CI builds it, so it keeps compiling against the API, and the harness's
// neoskript-addon scenario runs it on a real server. It's never shipped.
plugins {
    id("neoskript.java-conventions")
}

dependencies {
    // An addon outside this repository uses compileOnly("co.xenastudios.neoskript:neoskript-api:<version>").
    compileOnly(project(":neoskript-api"))
    compileOnly(libs.paper.api)
}

tasks.processResources {
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filesMatching("plugin.yml") {
        expand(props)
    }
}

tasks.jar {
    archiveBaseName = "NeoSkriptExampleAddon"
}
