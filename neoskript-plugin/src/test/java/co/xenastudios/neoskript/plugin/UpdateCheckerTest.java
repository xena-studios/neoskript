/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class UpdateCheckerTest {

    private static String versions(String... numberAndType) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < numberAndType.length; i += 2) {
            if (i > 0) {
                json.append(',');
            }
            json.append("{\"version_number\":\"").append(numberAndType[i]).append("\",\"version_type\":\"")
                    .append(numberAndType[i + 1]).append("\",\"loaders\":[\"paper\",\"folia\"]}");
        }
        return json.append(']').toString();
    }

    private static Optional<String> newest(String json, String current) {
        return UpdateChecker.newest(json, current).map(UpdateChecker.Update::version);
    }

    @Test
    void developmentBuildsDontCheck() {
        assertFalse(UpdateChecker.checks("0.1.0-SNAPSHOT"));
        assertFalse(UpdateChecker.checks("not a version"));
        assertTrue(UpdateChecker.checks("0.1.0-beta.1"));
        assertTrue(UpdateChecker.checks("1.0.0"));
    }

    @Test
    void picksTheNewestOnTheChannel() {
        String json = versions("1.1.0-beta.1", "beta", "1.0.1", "release", "1.0.0", "release", "0.9.0-beta.2", "beta");
        // A release hears only of releases.
        assertEquals(Optional.of("1.0.1"), newest(json, "1.0.0"));
        // A beta also hears of betas.
        assertEquals(Optional.of("1.1.0-beta.1"), newest(json, "0.9.0-beta.2"));
        assertEquals(Optional.empty(), newest(json, "1.1.0-beta.1"));
        assertEquals(Optional.empty(), newest(json, "1.0.1"));
    }

    @Test
    void theUpdateLinksToItsModrinthPage() {
        assertEquals("https://modrinth.com/plugin/neoskript/version/1.0.1",
                UpdateChecker.newest(versions("1.0.1", "release"), "1.0.0").orElseThrow().url());
    }

    @Test
    void skipsAlphasAndSnapshots() {
        assertEquals(Optional.empty(), newest(versions("2.0.0", "alpha", "1.1.0-SNAPSHOT", "beta"), "1.0.0-beta.1"));
    }

    @Test
    void semanticVersionOrder() {
        String[] ascending = {"0.1.0-alpha", "0.1.0-alpha.1", "0.1.0-alpha.beta", "0.1.0-beta", "0.1.0-beta.2",
            "0.1.0-beta.11", "0.1.0-rc.1", "0.1.0", "0.1.1", "0.2.0", "1.0.0"};
        for (int i = 0; i + 1 < ascending.length; i++) {
            UpdateChecker.Version lower = UpdateChecker.Version.parse(ascending[i]);
            UpdateChecker.Version higher = UpdateChecker.Version.parse(ascending[i + 1]);
            assertTrue(lower.compareTo(higher) < 0, ascending[i] + " < " + ascending[i + 1]);
            assertTrue(higher.compareTo(lower) > 0, ascending[i + 1] + " > " + ascending[i]);
        }
        assertEquals(0, UpdateChecker.Version.parse("v1.2").compareTo(UpdateChecker.Version.parse("1.2.0+build.5")));
    }
}
