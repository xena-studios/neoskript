plugins {
    alias(libs.plugins.shadow)
}

dependencies {
    implementation(project(":neoskript-api"))
    implementation(project(":neoskript-core"))
    implementation(project(":neoskript-lang"))
    implementation(project(":neoskript-platform"))
    compileOnly(libs.paper.api)
}

tasks.named<ProcessResources>("processResources") {
    val props = mapOf("version" to project.version.toString())
    inputs.properties(props)
    filesMatching("paper-plugin.yml") {
        expand(props)
    }
}

// The shaded jar is the distributable artifact; disable the thin jar to avoid a name collision.
tasks.named<Jar>("jar") {
    enabled = false
}

tasks.named<Jar>("shadowJar") {
    archiveClassifier.set("")
}

tasks.named("build") {
    dependsOn("shadowJar")
}
