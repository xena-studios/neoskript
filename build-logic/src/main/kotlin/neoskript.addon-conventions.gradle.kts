// An official NeoSkript addon in addons/<name>: a Paper plugin built against neoskript-api, versioned and released
// with NeoSkript. It ships one jar with its libraries shaded and relocated into its own package, and plugin.yml is
// made from `neoskriptAddon { ... }`. See addons/README.md.

import co.xenastudios.neoskript.gradle.CheckShadedPackages
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
    // NeoSkript's own oldest version, so the two never disagree.
    apiVersion = providers.fileContents(rootProject.layout.projectDirectory.file("neoskript-plugin/src/main/resources/plugin.yml"))
        .asText.map { yml ->
            Regex("(?m)^api-version:\\s*['\"]?([^'\"\\s]+)").find(yml)?.groupValues?.get(1)
                ?: error("NeoSkript's plugin.yml has no api-version")
        }
    replaces = addon.replaces
    // A community addon it replaces loads first if it can load at all, so the check when this one starts sees it.
    softDepend = addon.softDepend.zip(addon.replaces) { soft, replaced -> (soft + replaced).distinct() }
    syntaxExport = addon.syntaxExport
    output = generated
}

the<SourceSetContainer>().named("main") {
    resources.srcDir(generateAddonResources)
}

// Each addon's classes are in co.xenastudios.neoskript.addons.<name>, and what it ships is relocated into .libs there.
val addonPackage = "co.xenastudios.neoskript.addons." + project.name.replace(Regex("[^A-Za-z0-9]"), "")
val relocationBase = "$addonPackage.libs"
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
        // Libraries find their parts through META-INF/services (JDBC drivers, MariaDB's codecs): the files are
        // merged, and renamed and rewritten with the relocations, so they still find them.
        "mergeServiceFiles"()
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

// Nothing outside the addon's own package: a library shaded but not relocated fails the build.
val checkShadedPackages = tasks.register<CheckShadedPackages>("checkShadedPackages") {
    group = "verification"
    description = "Checks that every class in the addon's jar is in its own package."
    jar.set(project.tasks.named<Jar>("shadowJar").flatMap { it.archiveFile })
    ownPackage = addonPackage.replace('.', '/') + "/"
    marker = layout.buildDirectory.file("checkShadedPackages/ok")
}

tasks.named("check") {
    dependsOn(checkShadedPackages)
}
