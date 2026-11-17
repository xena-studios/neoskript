// What every official addon shares, shaded and relocated into each: finding the community addon it replaces in
// plugins/, and checking NeoSkript's version. Not a plugin of its own.
plugins {
    id("neoskript.java-conventions")
}

dependencies {
    compileOnly(project(":neoskript-api"))
    compileOnly(libs.paper.api)
    testImplementation(libs.paper.api)
}
