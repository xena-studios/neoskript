plugins {
    id("neoskript.java-conventions")
}

dependencies {
    api(project(":neoskript-api"))
    api(project(":neoskript-runtime"))
    compileOnly(libs.paper.api)
    // Language tests run the real standard library through the compiler, without a server.
    testImplementation(project(":neoskript-compiler"))
    testImplementation(libs.paper.api)
}
