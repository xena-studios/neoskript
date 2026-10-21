// Shared setup for every NeoSkript module: Java 21, UTF-8, lint, JUnit, and GPL headers.

plugins {
    `java-library`
    id("com.diffplug.spotless")
}

val libs = the<VersionCatalogsExtension>().named("libs")

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 21
    options.compilerArgs.addAll(listOf("-Xlint:all,-processing,-serial", "-parameters"))
}

dependencies {
    testImplementation(platform(libs.findLibrary("junit-bom").get()))
    testImplementation(libs.findLibrary("junit-jupiter").get())
    testRuntimeOnly(libs.findLibrary("junit-platform-launcher").get())
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

spotless {
    java {
        licenseHeader(
            """
            /*
             * Copyright (c) ${'$'}YEAR Xena Studios
             * SPDX-License-Identifier: GPL-3.0-or-later
             */
            """.trimIndent() + "\n"
        )
        trimTrailingWhitespace()
        endWithNewline()
    }
}
