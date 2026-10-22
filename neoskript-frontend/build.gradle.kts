plugins {
    id("neoskript.java-conventions")
}

dependencies {
    api(project(":neoskript-api"))
    testImplementation(libs.gson)
}
