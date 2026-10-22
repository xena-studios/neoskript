plugins {
    id("neoskript.java-conventions")
}

dependencies {
    api(project(":neoskript-frontend"))
    api(project(":neoskript-runtime"))
    implementation(libs.asm)
    testImplementation(testFixtures(project(":neoskript-frontend")))
}
