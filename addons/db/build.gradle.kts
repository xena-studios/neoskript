// neoskript-db: SQL from scripts, with skript-db's syntax. See addons/README.md and the addon's page on neoskript.dev.
plugins {
    id("neoskript.addon-conventions")
}

description = "SQL queries from scripts, with skript-db's syntax. Queries never block the server."

neoskriptAddon {
    pluginName = "NeoSkriptDb"
    mainClass = "co.xenastudios.neoskript.addons.db.NeoSkriptDb"
    replaces = listOf("skript-db")
    // The pool and the drivers Paper doesn't ship. MySQL's and SQLite's come with Paper, and slf4j too.
    relocate = listOf("com.zaxxer.hikari", "org.mariadb.jdbc", "org.postgresql")
}

dependencies {
    implementation(libs.hikaricp) {
        // Paper has slf4j: HikariCP logs to the server's console through it.
        exclude(group = "org.slf4j")
    }
    implementation(libs.mariadb.java.client)
    implementation(libs.postgresql) {
        // Annotations only, for the compiler.
        exclude(group = "org.checkerframework")
    }
    testImplementation(libs.sqlite.jdbc)
    // NeoSkript's own values (keyed lists, dates), which the addon reads by their accessors.
    testImplementation(project(":neoskript-runtime"))
    // HikariCP's and the drivers' logging, through java.util.logging, for the test that no password reaches it.
    testRuntimeOnly("org.slf4j:slf4j-jdk14:2.0.17")
}
