/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.addons.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/** Every setting with a value that isn't valid is its default, with a warning; nothing in config.yml can break it. */
class WebConfigTest {

    private static WebConfig read(String yaml, List<String> warnings) throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString(yaml);
        return WebConfig.read(config, warnings::add);
    }

    @Test
    void theShippedConfigIsTheDefaults() throws Exception {
        List<String> warnings = new ArrayList<>();
        String shipped = new String(WebConfigTest.class.getResourceAsStream("/config.yml").readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(WebConfig.DEFAULTS, read(shipped, warnings));
        assertEquals(List.of(), warnings);
        assertEquals(WebConfig.DEFAULTS, read("", warnings));
    }

    @Test
    void valuesAreRead() throws Exception {
        List<String> warnings = new ArrayList<>();
        WebConfig config = read("""
                timeout-seconds: 5
                connect-timeout-seconds: 2
                max-response-kilobytes: 64
                max-redirects: 0
                max-requests-in-flight: 1
                max-requests-per-minute: 10
                block-private-addresses: true
                """, warnings);
        assertEquals(new WebConfig(Duration.ofSeconds(5), Duration.ofSeconds(2), 64 * 1024, 0, 1, 10, true), config);
        assertEquals(List.of(), warnings);
    }

    @Test
    void badValuesAreTheDefaultsWithAWarningEach() throws Exception {
        List<String> warnings = new ArrayList<>();
        WebConfig config = read("""
                timeout-seconds: -1
                connect-timeout-seconds: lots
                max-response-kilobytes: 99999999999
                max-redirects: 2.5
                max-requests-in-flight: [1, 2]
                max-requests-per-minute: 0
                block-private-addresses: maybe
                """, warnings);
        assertEquals(WebConfig.DEFAULTS, config);
        assertEquals(7, warnings.size(), warnings.toString());
        assertTrue(warnings.getFirst().startsWith("config.yml: timeout-seconds must be a whole number from 1 to 600"),
                warnings.getFirst());
    }
}
