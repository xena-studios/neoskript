// NeoSkript's frontend compiled to JavaScript with TeaVM, for neoskript.dev's in-browser parser.
// `./gradlew :neoskript-web:website` builds it and copies it into website/src/parser/.

import org.teavm.gradle.api.OptimizationLevel
import org.teavm.gradle.api.JSModuleType

plugins {
    id("neoskript.java-conventions")
    alias(libs.plugins.teavm)
}

dependencies {
    implementation(project(":neoskript-frontend"))
    // Interim: the registry the corpus tests parse against, built from Skript 2.17.0's docs export.
    // See RegistrySource. TeaVM only compiles what's reachable, so the fixtures' Gson loader stays out.
    implementation(testFixtures(project(":neoskript-frontend")))
    implementation(teavm.libs.jsoApis)
}

teavm {
    js {
        mainClass = "co.xenastudios.neoskript.web.Main"
        moduleType = JSModuleType.ES2015
        obfuscated = !providers.gradleProperty("teavmDebug").isPresent
        optimization = OptimizationLevel.AGGRESSIVE
        targetFileName = "neoskript-parser.js"
    }
}

/** The Skript docs export the interim registry is built from, minified. */
val syntaxJson = layout.projectDirectory.file(
    "../neoskript-frontend/src/testFixtures/resources/skript-syntax/2.17.0.json")

tasks.register<Copy>("website") {
    group = "build"
    description = "Builds the in-browser parser and copies it into website/src/parser/."
    dependsOn("generateJavaScript")
    from(layout.buildDirectory.dir("generated/teavm/js")) { include("neoskript-parser.js") }
    from(syntaxJson) {
        rename { "syntax.json" }
        filter { line -> line.trim() }
    }
    into(rootProject.layout.projectDirectory.dir("website/src/parser"))
}
