plugins {
    id("neoskript.java-conventions")
    alias(libs.plugins.shadow)
}

val neoskriptPlugin = configurations.create("neoskriptPlugin") {
    isCanBeConsumed = false
    isTransitive = false
}

dependencies {
    implementation(libs.gson)
    // The plugin the check server runs, inside the CLI's jar.
    neoskriptPlugin(project(path = ":neoskript-plugin", configuration = "shadowRuntimeElements"))
}

// Run as `java -jar neoskript-cli.jar check <scripts>`; the shadow jar is the one to ship.
tasks.jar {
    archiveClassifier = "plain"
}

// The plugin, and the Paper builds the check runs on, pinned as the test harness pins them.
tasks.processResources {
    from(neoskriptPlugin) {
        rename { "neoskript-plugin.jar" }
    }
    from(rootProject.layout.projectDirectory.dir("test-harness/environments")) {
        include("paper-*.json")
        into("servers")
    }
}

tasks.shadowJar {
    archiveBaseName = "neoskript-cli"
    archiveClassifier = ""
    manifest {
        attributes["Main-Class"] = "co.xenastudios.neoskript.cli.NeoSkriptCli"
    }
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}
