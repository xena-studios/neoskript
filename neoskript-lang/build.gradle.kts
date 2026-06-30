dependencies {
    api(project(":neoskript-api"))
    implementation(project(":neoskript-core"))
    compileOnly(libs.paper.api)

    testImplementation(libs.paper.api.test)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
