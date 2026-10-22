import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties

plugins {
    id("neoskript.java-conventions")
}

val neoskriptPlugin = configurations.create("neoskriptPlugin") {
    isCanBeConsumed = false
    isTransitive = false
}

dependencies {
    implementation(libs.gson)
    neoskriptPlugin(project(path = ":neoskript-plugin", configuration = "shadowRuntimeElements"))
}

val referenceSkript = Properties().apply {
    file("reference-skript.properties").reader().use { load(it) }
}
val skriptVersion: String = referenceSkript.getProperty("version")
val skriptSourceArchive = layout.buildDirectory.file("downloads/skript-$skriptVersion-src.tar.gz")

val downloadSkriptSource = tasks.register("downloadSkriptSource") {
    description = "Downloads the reference Skript's source archive."
    val url = referenceSkript.getProperty("source.url")
    val archive = skriptSourceArchive
    inputs.property("url", url)
    outputs.file(archive)
    doLast {
        val target = archive.get().asFile.toPath()
        Files.createDirectories(target.parent)
        URI(url).toURL().openStream().use { Files.copy(it, target, StandardCopyOption.REPLACE_EXISTING) }
    }
}

val fetchSkriptTests = tasks.register<Sync>("fetchSkriptTests") {
    group = "verification"
    description = "Extracts the reference Skript's own test scripts into the compatibility corpus."
    dependsOn(downloadSkriptSource)
    from(skriptSourceArchive.map { tarTree(it.asFile) }) {
        include("*/src/test/skript/tests/**")
        // Strip "Skript-<version>/" but keep Skript's layout: some of its tests check script paths
        // relative to a server running in build/test_runners/<env>.
        eachFile { relativePath = RelativePath(true, *relativePath.segments.drop(1).toTypedArray()) }
        includeEmptyDirs = false
    }
    into(layout.buildDirectory.dir("corpus/skript"))
}

tasks.register<JavaExec>("harness") {
    group = "verification"
    description = "Boots real Paper/Folia servers and runs harness scenarios. " +
        "Options: -Pharness.env=<name|all> -Pharness.group=<smoke|baseline|all> -Pharness.scenario=<name>"
    dependsOn(fetchSkriptTests)
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "co.xenastudios.neoskript.harness.HarnessMain"
    javaLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(21) }

    val pluginJar: FileCollection = neoskriptPlugin
    inputs.files(pluginJar)
    // Servers on 1.21.x run on Java 21, and 26.x needs Java 25.
    val java21 = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(21) }
    val java25 = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(25) }
    // -Pharness.echo=true streams server consoles to the build output.
    val echo = providers.gradleProperty("harness.echo").orElse("false")
    val projectDir = layout.projectDirectory
    val buildDir = layout.buildDirectory
    val cacheDir = layout.settingsDirectory.dir(".harness-cache")
    jvmArgumentProviders.add(CommandLineArgumentProvider {
        listOf(
            "-Dharness.java.21=" + java21.get().metadata.installationPath.asFile.absolutePath,
            "-Dharness.java.25=" + java25.get().metadata.installationPath.asFile.absolutePath,
            "-Dharness.neoskriptJar=" + pluginJar.singleFile.absolutePath,
            "-Dharness.environments=" + projectDir.dir("environments").asFile.absolutePath,
            "-Dharness.scripts=" + projectDir.dir("scripts").asFile.absolutePath,
            "-Dharness.referenceSkript=" + projectDir.file("reference-skript.properties").asFile.absolutePath,
            "-Dharness.corpus.skript=" + buildDir.dir("corpus/skript").get().asFile.absolutePath,
            "-Dharness.corpus.realWorld=" + projectDir.dir("corpus/real-world").asFile.absolutePath,
            "-Dharness.output=" + buildDir.dir("harness").get().asFile.absolutePath,
            "-Dharness.cache=" + cacheDir.asFile.absolutePath,
            "-Dharness.echo=" + echo.get(),
        )
    })

    val env = providers.gradleProperty("harness.env").orElse("all")
    val scenarioGroup = providers.gradleProperty("harness.group").orElse("smoke")
    val scenario = providers.gradleProperty("harness.scenario").orElse("")
    argumentProviders.add(CommandLineArgumentProvider {
        listOf("--env", env.get(), "--group", scenarioGroup.get(), "--scenario", scenario.get())
    })
}
