plugins {
    id("neoskript.java-conventions")
}

dependencies {
    api(project(":neoskript-api"))
    compileOnly(libs.paper.api)
}

tasks.test {
    useJUnitPlatform {
        excludeTags("large-file")
    }
}

// Loading a variables.csv of more than 300 MB with a heap of 512 MB: the file is read a line at a time.
val largeFileTest by tasks.registering(Test::class) {
    description = "Loads a generated variables.csv of more than 300 MB with a 512 MB heap."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform {
        includeTags("large-file")
    }
    maxHeapSize = "512m"
}

tasks.check {
    dependsOn(largeFileTest)
}
