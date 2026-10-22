/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.harness;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** The pinned Skript release used as the behavioral spec, from {@code reference-skript.properties}. */
public record ReferenceSkript(String version, String jarUrl, String jarSha256) {

    public static ReferenceSkript load(Path file) throws IOException {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file)) {
            properties.load(reader);
        }
        return new ReferenceSkript(
                require(properties, "version"), require(properties, "jar.url"), require(properties, "jar.sha256"));
    }

    private static String require(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("reference-skript.properties is missing '" + key + "'");
        }
        return value.trim();
    }
}
