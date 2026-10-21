plugins {
    id("neoskript.java-conventions")
}

dependencies {
    api(project(":neoskript-api"))
    api(project(":neoskript-runtime"))
    compileOnly(libs.paper.api)
}
