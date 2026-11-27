// NeoSkriptCompat (D22): Skript's classes for Skript addons, backed by NeoSkript. See docs/SKRIPT-ADDONS.md.
import co.xenastudios.neoskript.gradle.GenerateSkriptStubs

plugins {
    id("neoskript.java-conventions")
    alias(libs.plugins.shadow)
}

description = "Lets Skript addons such as SkBee run on NeoSkript, by standing in for Skript."

dependencies {
    // NeoSkript's own classes come from its jar at run time (depend: [NeoSkript]); the layer uses its internals.
    compileOnly(project(":neoskript-api"))
    compileOnly(project(":neoskript-frontend"))
    compileOnly(project(":neoskript-compiler"))
    compileOnly(project(":neoskript-runtime"))
    compileOnly(project(":neoskript-paper"))
    compileOnly(project(":neoskript-plugin"))
    compileOnly(libs.paper.api)
    // Finding Skript's own jar in plugins/ (D22), shaded and relocated.
    implementation(project(":addons:support"))
    testImplementation(project(":neoskript-api"))
    testImplementation(project(":neoskript-frontend"))
    testImplementation(project(":neoskript-compiler"))
    testImplementation(project(":neoskript-runtime"))
    testImplementation(libs.paper.api)
}

// Skript's classes the layer doesn't implement yet, from Skript's API listing: each throws, saying so.
val stubs = tasks.register<GenerateSkriptStubs>("generateSkriptStubs") {
    listing = layout.projectDirectory.file("src/stubs/skript-2.17.0.api")
    sources.from(layout.projectDirectory.dir("src/main/java"))
    output = layout.buildDirectory.dir("generated/skript-stubs")
}
val stubClasses = files(stubs.map { it.output })

dependencies {
    compileOnly(stubClasses)
    testImplementation(stubClasses)
}

tasks.processResources {
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filesMatching("plugin.yml") {
        expand(props)
    }
}

// The hand-written classes of Skript's, compiled and completed with the members of Skript's they don't declare.
val completed = tasks.register<GenerateSkriptStubs>("completeSkriptClasses") {
    listing = layout.projectDirectory.file("src/stubs/skript-2.17.0.api")
    sources.from(layout.projectDirectory.dir("src/main/java"))
    compiled = tasks.compileJava.flatMap { it.destinationDirectory }
    output = layout.buildDirectory.dir("generated/skript-completed")
}

tasks.jar {
    archiveClassifier = "plain"
}

tasks.shadowJar {
    archiveBaseName = "NeoSkriptCompat"
    archiveClassifier = ""
    // Skript's classes: the completed hand-written ones in place of their compiled originals, and the stubs.
    val skriptClasses = provider {
        val dir = completed.get().output.get().asFile
        dir.walkTopDown().filter { it.isFile }.map { it.relativeTo(dir).path.replace(File.separatorChar, '/') }.toSet()
    }
    exclude { element -> !element.isDirectory && element.relativePath.pathString in skriptClasses.get() &&
            element.file.path.contains("classes${File.separator}java${File.separator}main") }
    from(completed.map { it.output })
    from(stubs.map { it.output })
    from(rootProject.layout.projectDirectory.file("LICENSE")) { into("META-INF") }
    relocate("co.xenastudios.neoskript.addon.support", "co.xenastudios.neoskript.compat.libs.support")
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}
