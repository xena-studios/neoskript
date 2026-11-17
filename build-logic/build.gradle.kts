plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.spotless.gradle)
    // Reads the addon API's class files for its signature check.
    implementation(libs.asm.tree)
}
