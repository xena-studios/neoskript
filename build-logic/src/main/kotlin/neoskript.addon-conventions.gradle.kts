// An official NeoSkript addon in addons/<name>: a Paper plugin built against neoskript-api, versioned and released
// with NeoSkript. It ships one jar with its libraries shaded and relocated into its own package, and plugin.yml is
// made from `neoskriptAddon { ... }`. See addons/README.md.

import co.xenastudios.neoskript.gradle.GenerateAddonResources
import co.xenastudios.neoskript.gradle.OfficialAddonExtension

plugins {
    id("neoskript.java-conventions")
}

// The Shadow plugin is on the build's classpath through the root build file; applied by id here.
pluginManager.apply("com.gradleup.shadow")

val libs = the<VersionCatalogsExtension>().named("libs")
val addon = extensions.create<OfficialAddonExtension>("neoskriptAddon")
addon.syntaxExport.convention(true)
addon.description.convention(provider { project.description ?: "An official NeoSkript addon." })

dependencies {
    // NeoSkript has the API at run time: an addon with its own copy can't reach it.
    "compileOnly"(project(":neoskript-api"))
    "compileOnly"(libs.findLibrary("paper-api").get())
    // Finding the original addon, checking NeoSkript's version: shaded and relocated into each addon.
    "implementation"(project(":addons:support"))
    "testImplementation"(project(":neoskript-api"))
    "testImplementation"(libs.findLibrary("paper-api").get())
}

val generated = layout.buildDirectory.dir("generated/addon-resources")
val generateAddonResources = tasks.register<GenerateAddonResources>("generateAddonResources") {
    pluginName = addon.pluginName
    mainClass = addon.mainClass
    pluginDescription = addon.description
    // The same version as NeoSkript: they're released together.
    pluginVersion = provider { project.version.toString() }
    replaces = addon.replaces
    softDepend = addon.softDepend
    syntaxExport = addon.syntaxExport
    output = generated
}

the<SourceSetContainer>().named("main") {
    resources.srcDir(generateAddonResources)
}

// Each addon relocates what it ships into co.xenastudios.neoskript.addons.<name>.libs.
val relocationBase = "co.xenastudios.neoskript.addons." + project.name.replace(Regex("[^A-Za-z0-9]"), "") + ".libs"
val licence = rootProject.layout.projectDirectory.file("LICENSE")

tasks.named<Jar>("jar") {
    // The jar without its libraries isn't the one to install.
    archiveClassifier = "plain"
}

tasks.named<Jar>("shadowJar") {
    archiveBaseName = addon.pluginName
    archiveClassifier = ""
    from(licence) { into("META-INF") }
    withGroovyBuilder {
        "relocate"("co.xenastudios.neoskript.addon.support", "$relocationBase.support")
    }
}

// The build file's relocations, once it's been evaluated.
afterEvaluate {
    val relocations = addon.relocate.get()
    require(relocations.none { it.startsWith("co.xenastudios.neoskript") }) {
        "An addon relocates the libraries it ships, not NeoSkript's packages: $relocations"
    }
    require(addon.pluginName.isPresent && addon.mainClass.isPresent) {
        "neoskriptAddon { pluginName = ...; mainClass = ... } is missing in ${project.path}"
    }
    tasks.named("shadowJar") {
        for (pkg in relocations) {
            withGroovyBuilder { "relocate"(pkg, "$relocationBase.$pkg") }
        }
    }
}

tasks.named("assemble") {
    dependsOn("shadowJar")
}
