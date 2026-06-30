plugins {
    java
}

allprojects {
    group = "co.xenastudios"
    // Overridable for releases/nightlies via -PneoskriptVersion=...; otherwise the version tracked in
    // version.txt (maintained by release-please), falling back to a local snapshot.
    version = (findProperty("neoskriptVersion") as String?)
        ?: runCatching { rootDir.resolve("version.txt").readText().trim() }.getOrNull()?.takeIf { it.isNotEmpty() }
        ?: "1.0.0-SNAPSHOT"
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
