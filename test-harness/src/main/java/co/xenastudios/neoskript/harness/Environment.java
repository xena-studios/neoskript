/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.harness;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * A pinned server to test on, loaded from {@code environments/*.json}.
 *
 * @param skriptReference whether the reference Skript supports this server, so Skript scenarios can
 *     run on it
 */
public record Environment(
        String name,
        Platform platform,
        String minecraftVersion,
        int build,
        int javaVersion,
        Download download,
        boolean skriptReference) {

    public enum Platform {
        @com.google.gson.annotations.SerializedName("paper")
        PAPER,
        @com.google.gson.annotations.SerializedName("folia")
        FOLIA;

        public String displayName() {
            return this == PAPER ? "Paper" : "Folia";
        }
    }

    public record Download(String url, String sha256) {}

    public Environment {
        if (name == null || platform == null || minecraftVersion == null || download == null) {
            throw new IllegalArgumentException("Environment is missing required fields: " + name);
        }
    }

    public static Environment parse(Reader json) {
        return new Gson().fromJson(json, Environment.class);
    }

    public static List<Environment> loadAll(Path directory) throws IOException {
        List<Environment> environments = new ArrayList<>();
        try (Stream<Path> files = Files.list(directory)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".json")).sorted().toList()) {
                try (Reader reader = Files.newBufferedReader(file)) {
                    Environment environment = parse(reader);
                    String expected = file.getFileName().toString().replace(".json", "");
                    if (!environment.name().equals(expected)) {
                        throw new IllegalArgumentException(
                                file + " has name '" + environment.name() + "', expected '" + expected + "'");
                    }
                    environments.add(environment);
                }
            }
        }
        environments.sort(Comparator.comparing(Environment::name));
        return environments;
    }
}
