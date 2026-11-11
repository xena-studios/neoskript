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

/**
 * NeoSkript's syntax export, taken on a real server and committed; /syntax is made from the same file. See
 * website/scripts/syntax.mjs for how to remake it.
 */
val syntaxExport = rootProject.layout.projectDirectory.file("website/src/data/syntax-export.json")

/**
 * The parser's data: the export without the parts only /syntax shows (event names and values), on one line, as
 * the page downloads it.
 */
val parserData = tasks.register("parserData") {
    description = "Writes the in-browser parser's syntax data from the committed syntax export."
    val input = syntaxExport
    val output = layout.buildDirectory.file("parser/syntax.json")
    inputs.file(input)
    outputs.file(output)
    doLast {
        @Suppress("UNCHECKED_CAST")
        val root = groovy.json.JsonSlurper().parse(input.asFile) as MutableMap<String, Any?>
        @Suppress("UNCHECKED_CAST")
        for (element in root["elements"] as List<MutableMap<String, Any?>>) {
            element.remove("eventName")
            element.remove("eventValues")
        }
        output.get().asFile.writeText(groovy.json.JsonOutput.toJson(root))
    }
}

tasks.test {
    // The tests parse with the committed export.
    inputs.file(syntaxExport)
    systemProperty("neoskript.syntaxExport", syntaxExport.asFile.absolutePath)
}

tasks.register<Copy>("website") {
    group = "build"
    description = "Builds the in-browser parser and copies it into website/src/parser/."
    dependsOn("generateJavaScript")
    from(layout.buildDirectory.dir("generated/teavm/js")) { include("neoskript-parser.js") }
    from(parserData)
    into(rootProject.layout.projectDirectory.dir("website/src/parser"))
}
