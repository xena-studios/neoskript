plugins {
    java
}

allprojects {
    group = "co.xenastudios"
    version = "1.0.0-SNAPSHOT"
}

subprojects {
    apply(plugin = "java-library")

    configure<JavaPluginExtension> {
        toolchain {
            languageVersion = JavaLanguageVersion.of(25)
        }
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release = 25
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
}
