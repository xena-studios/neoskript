plugins {
    alias(libs.plugins.shadow)
}

dependencies {
    implementation(project(":neoskript-api"))
    implementation(project(":neoskript-core"))
    implementation(project(":neoskript-lang"))
    implementation(project(":neoskript-platform"))
    implementation(libs.sqlite.jdbc)
    compileOnly(libs.paper.api)

    testImplementation(libs.paper.api.test)
    testImplementation(libs.mockbukkit)
    testImplementation(libs.h2)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.named<ProcessResources>("processResources") {
    val props = mapOf(
        "version" to project.version.toString(),
        "apiVersion" to (project.findProperty("paperApiVersion") as String? ?: "1.21"),
    )
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
