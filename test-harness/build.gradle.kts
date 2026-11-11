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
val neoskriptCli = configurations.create("neoskriptCli") {
    isCanBeConsumed = false
    isTransitive = false
}

dependencies {
    implementation(libs.gson)
    // The frontend with the stub registry from the reference Skript's docs, for parse checks without a server.
    implementation(testFixtures(project(":neoskript-frontend")))
    neoskriptPlugin(project(path = ":neoskript-plugin", configuration = "shadowRuntimeElements"))
    neoskriptCli(project(path = ":neoskript-cli", configuration = "shadowRuntimeElements"))
}

// The harness bot: a plugin the harness installs next to NeoSkript or Skript, whose console commands
// make players join, chat and run commands on a headless server. It compiles against a real Paper
// server's classes, which the harness unpacks into .harness-cache, so it's built only for the harness
// and never part of NeoSkript's jar.
val bot: SourceSet = sourceSets.create("bot")
val botEnvironment = "paper-26.3"
val botServerDir = layout.settingsDirectory.dir(".harness-cache/bundler/$botEnvironment")

val prepareBotServer = tasks.register<JavaExec>("prepareBotServer") {
    description = "Unpacks the Paper server the harness bot compiles against into .harness-cache."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "co.xenastudios.neoskript.harness.ServerClasses"
    javaLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(21) }
    val environmentFile = layout.projectDirectory.file("environments/$botEnvironment.json")
    inputs.file(environmentFile)
    // Paperclip runs on the server's own Java.
    val java25 = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(25) }
    val (platform, version) = botEnvironment.split("-", limit = 2)
    outputs.file(botServerDir.file("versions/$version/$platform-$version.jar"))
    val cacheDir = layout.settingsDirectory.dir(".harness-cache")
    argumentProviders.add(CommandLineArgumentProvider {
        listOf(
            environmentFile.asFile.absolutePath,
            cacheDir.asFile.absolutePath,
            java25.get().executablePath.asFile.absolutePath,
        )
    })
}

dependencies {
    "botCompileOnly"(
        fileTree(botServerDir) { include("versions/**/*.jar", "libraries/**/*.jar") }.builtBy(prepareBotServer)
    )
}

tasks.named<JavaCompile>(bot.compileJavaTaskName) {
    // The server's classes are Java 25 class files. The bot itself stays Java 21, so it loads on
    // 1.21.x too and says there that it isn't supported.
    javaCompiler = javaToolchains.compilerFor { languageVersion = JavaLanguageVersion.of(25) }
    // The server's libraries have annotations whose classes aren't on the classpath.
    options.compilerArgs.add("-Xlint:-classfile")
}

val botJar = tasks.register<Jar>("botJar") {
    description = "Builds the harness bot plugin."
    archiveBaseName = "NeoSkriptHarnessBot"
    from(bot.output)
    // Compiled against Mojang's names, so Paper mustn't remap it as a Spigot-mapped plugin.
    manifest { attributes("paperweight-mappings-namespace" to "mojang") }
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
    description = "Extracts the reference Skript's own test scripts and sources into the compatibility corpus."
    dependsOn(downloadSkriptSource)
    from(skriptSourceArchive.map { tarTree(it.asFile) }) {
        include("*/src/test/skript/tests/**")
        // Skript's sources, for the documentation examples the differential scenario loads.
        include("*/src/main/java/**/*.java")
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
    val cliJar: FileCollection = neoskriptCli
    inputs.files(cliJar)
    val botJarFile = botJar.flatMap { it.archiveFile }
    inputs.file(botJarFile)
    // Servers on 1.21.x run on Java 21, and 26.x needs Java 25.
    val java21 = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(21) }
    val java25 = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(25) }
    // -Pharness.echo=true streams server consoles to the build output.
    val echo = providers.gradleProperty("harness.echo").orElse("false")
    // -Pharness.differential.skriptRuns=N runs the reference Skript N times for the differential (each run cached).
    val skriptRuns = providers.gradleProperty("harness.differential.skriptRuns").orElse("3")
    val projectDir = layout.projectDirectory
    val buildDir = layout.buildDirectory
    val cacheDir = layout.settingsDirectory.dir(".harness-cache")
    val skillDir = layout.settingsDirectory.dir("plugins/skript/skills/skript")
    jvmArgumentProviders.add(CommandLineArgumentProvider {
        listOf(
            "-Dharness.java.21=" + java21.get().metadata.installationPath.asFile.absolutePath,
            "-Dharness.java.25=" + java25.get().metadata.installationPath.asFile.absolutePath,
            "-Dharness.neoskriptJar=" + pluginJar.singleFile.absolutePath,
            "-Dharness.cliJar=" + cliJar.singleFile.absolutePath,
            "-Dharness.botJar=" + botJarFile.get().asFile.absolutePath,
            "-Dharness.environments=" + projectDir.dir("environments").asFile.absolutePath,
            "-Dharness.scripts=" + projectDir.dir("scripts").asFile.absolutePath,
            "-Dharness.referenceSkript=" + projectDir.file("reference-skript.properties").asFile.absolutePath,
            "-Dharness.corpus.skript=" + buildDir.dir("corpus/skript").get().asFile.absolutePath,
            "-Dharness.corpus.realWorld=" + projectDir.dir("corpus/real-world").asFile.absolutePath,
            // The AI skill (plugins/skript), whose example scripts the skill-examples scenario loads.
            "-Dharness.skill=" + skillDir.asFile.absolutePath,
            "-Dharness.output=" + buildDir.dir("harness").get().asFile.absolutePath,
            "-Dharness.cache=" + cacheDir.asFile.absolutePath,
            "-Dharness.echo=" + echo.get(),
            "-Dharness.differential.skriptRuns=" + skriptRuns.get(),
        )
    })

    val env = providers.gradleProperty("harness.env").orElse("all")
    val scenarioGroup = providers.gradleProperty("harness.group").orElse("smoke")
    val scenario = providers.gradleProperty("harness.scenario").orElse("")
    argumentProviders.add(CommandLineArgumentProvider {
        listOf("--env", env.get(), "--group", scenarioGroup.get(), "--scenario", scenario.get())
    })
}
