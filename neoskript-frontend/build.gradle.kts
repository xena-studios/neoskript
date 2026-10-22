plugins {
    id("neoskript.java-conventions")
    `java-test-fixtures`
}

dependencies {
    api(project(":neoskript-api"))
    // The stub registry built from the reference Skript's docs export, shared with the test harness.
    testFixturesImplementation(libs.gson)
}
