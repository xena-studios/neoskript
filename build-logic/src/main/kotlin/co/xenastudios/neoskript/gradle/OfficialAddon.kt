package co.xenastudios.neoskript.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
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
     * The plugin names of the community addons it replaces (`skript-db`), whose jars it looks for in `plugins/`
     * when it starts (see the addon support library's `OfficialAddon.start`).
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
            appendLine("api-version: '1.21.4'")
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
