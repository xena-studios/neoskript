plugins {
    id("neoskript.java-conventions")
    alias(libs.plugins.shadow)
    alias(libs.plugins.run.paper)
}

dependencies {
    implementation(project(":neoskript-compiler"))
    implementation(project(":neoskript-paper"))
    implementation(libs.bstats.bukkit)
    compileOnly(libs.paper.api)
}

tasks.processResources {
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filesMatching("plugin.yml") {
        expand(props)
    }
}

tasks.shadowJar {
    archiveBaseName = "NeoSkript"
    archiveClassifier = ""
    // bStats requires relocation so each plugin reports through its own copy.
    relocate("org.bstats", "co.xenastudios.neoskript.libs.bstats")
    // Our own copy of ASM, so another plugin's version can't clash with it.
    relocate("org.objectweb.asm", "co.xenastudios.neoskript.libs.asm")
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}

// Dev servers: ./gradlew runServer (Paper) and ./gradlew runFolia (Folia), on the oldest supported version.
tasks.runServer {
    minecraftVersion("1.21.4")
}

runPaper.folia.registerTask()
