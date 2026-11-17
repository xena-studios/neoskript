package co.xenastudios.neoskript.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import java.util.zip.ZipFile
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

/**
 * An official addon, as its build file describes it (`neoskriptAddon { ... }`), for the
 * `neoskript.addon-conventions` plugin.
 */
abstract class OfficialAddonExtension {
    /** The plugin's name, as Paper and `/sk info` show it: `NeoSkriptDb`. */
    abstract val pluginName: Property<String>

    /** The plugin's main class. */
    abstract val mainClass: Property<String>

    /** One sentence for plugin.yml. */
    abstract val description: Property<String>

    /**
     * The plugin names of the community addons it replaces (`skript-db`), every name each has gone by
     * (`skript-reflect` and `skript-mirror`), which it looks for when it starts (see the addon support library's
     * `OfficialAddon.start`). They're soft dependencies too, so one that can load loads first.
     */
    abstract val replaces: ListProperty<String>

    /** Plugins it can use when they're there, for plugin.yml's `softdepend`. */
    abstract val softDepend: ListProperty<String>

    /**
     * Packages of the libraries it ships, relocated into the addon's own package so another plugin's copy can't
     * clash: `com.zaxxer.hikari`.
     */
    abstract val relocate: ListProperty<String>

    /** Whether its syntax goes into the syntax export for neoskript.dev and the editors. */
    abstract val syntaxExport: Property<Boolean>
}

/** Writes an official addon's plugin.yml and its `neoskript-addon.properties`, from its build file. */
abstract class GenerateAddonResources : DefaultTask() {
    @get:Input abstract val pluginName: Property<String>
    @get:Input abstract val mainClass: Property<String>
    @get:Input abstract val pluginDescription: Property<String>
    @get:Input abstract val pluginVersion: Property<String>
    /** The oldest Minecraft API version, NeoSkript's own (its plugin.yml's `api-version`). */
    @get:Input abstract val apiVersion: Property<String>
    @get:Input abstract val replaces: ListProperty<String>
    @get:Input abstract val softDepend: ListProperty<String>
    @get:Input abstract val syntaxExport: Property<Boolean>
    @get:OutputDirectory abstract val output: DirectoryProperty

    @TaskAction
    fun generate() {
        val dir = output.get().asFile
        dir.mkdirs()
        val name = pluginName.get()
        require(Regex("[A-Za-z0-9_-]+").matches(name)) { "A plugin's name can only have letters, digits, _ and -: $name" }
        val soft = softDepend.get()
        dir.resolve("plugin.yml").writeText(buildString {
            appendLine("# Made by the build from neoskriptAddon { ... } in build.gradle.kts.")
            appendLine("name: $name")
            appendLine("version: ${quote(pluginVersion.get())}")
            appendLine("main: ${mainClass.get()}")
            appendLine("api-version: ${quote(apiVersion.get())}")
            appendLine("folia-supported: true")
            // An addon depends on NeoSkript, so it enables after it, and NeoSkript waits for its syntax.
            appendLine("depend: [NeoSkript]")
            if (soft.isNotEmpty()) appendLine("softdepend: [${soft.joinToString(", ")}]")
            appendLine("description: ${quote(pluginDescription.get())}")
            appendLine("website: https://neoskript.dev")
            appendLine("authors: [Xena Studios]")
        })
        dir.resolve("neoskript-addon.properties").writeText(buildString {
            appendLine("# Made by the build: what the addon support library and the harness read.")
            appendLine("name=$name")
            appendLine("neoskript=${pluginVersion.get()}")
            appendLine("replaces=${replaces.get().joinToString(",")}")
            appendLine("syntaxExport=${syntaxExport.get()}")
        })
    }

    private fun quote(text: String) = "'" + text.replace("'", "''") + "'"
}

/**
 * Fails if an addon's jar has a class outside its own package, `co.xenastudios.neoskript.addons.<name>` (its
 * relocated libraries are in `.libs` under it): a library shaded without being relocated would clash with another
 * plugin's copy of it.
 */
abstract class CheckShadedPackages : DefaultTask() {
    @get:InputFile @get:PathSensitive(PathSensitivity.NONE) abstract val jar: RegularFileProperty
    /** The addon's package, as a path: `co/xenastudios/neoskript/addons/db/`. */
    @get:Input abstract val ownPackage: Property<String>
    @get:OutputFile abstract val marker: RegularFileProperty

    @TaskAction
    fun check() {
        val own = ownPackage.get()
        val stray = ZipFile(jar.get().asFile).use { zip ->
            zip.entries().asSequence().map { it.name }
                .filter { it.endsWith(".class") }
                // A multi-release jar's classes are under META-INF/versions/<n>/.
                .map { it.replace(Regex("^META-INF/versions/\\d+/"), "") }
                .filter { it != "module-info.class" && !it.startsWith(own) }
                .sorted().toList()
        }
        if (stray.isNotEmpty()) {
            throw GradleException("${jar.get().asFile.name} has classes outside $own, from libraries shaded but not"
                    + " relocated: add their packages to neoskriptAddon { relocate = ... }, or make them compileOnly."
                    + " The first: ${stray.take(10).joinToString(", ")}")
        }
        marker.get().asFile.apply { parentFile.mkdirs() }.writeText("ok\n")
    }
}
