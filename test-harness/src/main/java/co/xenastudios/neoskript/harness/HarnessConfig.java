/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.harness;

import java.nio.file.Files;
import java.nio.file.Path;

/** Paths and toolchains the Gradle task passes to the harness as system properties. */
public record HarnessConfig(
        Path java21Home,
        Path java25Home,
        Path neoskriptJar,
        Path environmentsDir,
        Path scriptsDir,
        Path referenceSkriptFile,
        Path skriptCorpus,
        Path realWorldCorpus,
        Path outputDir,
        Path cacheDir,
        boolean echo) {

    public static HarnessConfig fromSystemProperties() {
        return new HarnessConfig(
                path("harness.java.21"),
                path("harness.java.25"),
                path("harness.neoskriptJar"),
                path("harness.environments"),
                path("harness.scripts"),
                path("harness.referenceSkript"),
                path("harness.corpus.skript"),
                path("harness.corpus.realWorld"),
                path("harness.output"),
                path("harness.cache"),
                Boolean.parseBoolean(System.getProperty("harness.echo", "false")));
    }

    private static Path path(String property) {
        String value = System.getProperty(property);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Missing system property '" + property + "'. Run the harness with ./gradlew :test-harness:harness");
        }
        return Path.of(value);
    }

    public Path javaExecutable(int javaVersion) {
        Path home = switch (javaVersion) {
            case 21 -> java21Home;
            case 25 -> java25Home;
            default -> throw new IllegalArgumentException("No toolchain configured for Java " + javaVersion);
        };
        Path java = home.resolve("bin").resolve("java");
        return Files.exists(java) ? java : home.resolve("bin").resolve("java.exe");
    }

    /** Skript's own test scripts, inside {@link #skriptCorpus()} at the same path as in Skript's repo. */
    public Path skriptTests() {
        return skriptCorpus.resolve("src/test/skript/tests");
    }

    public Path runsDir() {
        return outputDir.resolve("runs");
    }

    public Path reportsDir() {
        return outputDir.resolve("reports");
    }
}
