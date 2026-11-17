plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.spotless.gradle)
    // Reads the addon API's class files for its signature check.
    implementation(libs.asm.tree)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}
