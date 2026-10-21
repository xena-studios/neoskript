/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.harness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Checks the pinned environments in {@code test-harness/environments}. */
class EnvironmentTest {

    private static final List<Environment> ENVIRONMENTS = load();

    private static List<Environment> load() {
        try {
            return Environment.loadAll(Path.of("environments"));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void coversTheOldestAndNewestVersionOnBothPlatforms() {
        List<String> names = ENVIRONMENTS.stream().map(Environment::name).toList();
        assertTrue(names.containsAll(List.of("paper-1.21.4", "folia-1.21.4")), names.toString());
        assertTrue(names.stream().anyMatch(n -> n.startsWith("paper-26.")), names.toString());
        assertTrue(names.stream().anyMatch(n -> n.startsWith("folia-26.")), names.toString());
    }

    @Test
    void everyEnvironmentIsPinnedToAnExactBuild() {
        for (Environment environment : ENVIRONMENTS) {
            String sha = environment.download().sha256();
            assertTrue(sha.matches("[0-9a-f]{64}"), environment.name() + " has a bad sha256");
            assertTrue(environment.download().url().contains(sha), environment.name() + " url doesn't match its sha256");
            assertTrue(
                    environment.download().url().endsWith("-" + environment.minecraftVersion() + "-" + environment.build() + ".jar"),
                    environment.name() + " url doesn't match its version and build");
            assertTrue(environment.name().equals(environment.platform().name().toLowerCase() + "-" + environment.minecraftVersion()));
        }
    }

    @Test
    void javaVersionMatchesTheMinecraftVersion() {
        for (Environment environment : ENVIRONMENTS) {
            int expected = environment.minecraftVersion().startsWith("1.") ? 21 : 25;
            assertEquals(expected, environment.javaVersion(), environment.name());
        }
    }

    @Test
    void skriptOnlyRunsOnPaper() {
        // Skript doesn't support Folia, so Folia can't have a Skript baseline.
        for (Environment environment : ENVIRONMENTS) {
            if (environment.platform() == Environment.Platform.FOLIA) {
                assertFalse(environment.skriptReference(), environment.name());
            }
        }
        assertTrue(ENVIRONMENTS.stream().anyMatch(Environment::skriptReference));
    }
}
