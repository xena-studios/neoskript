/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.plugin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * D15: asks Modrinth whether a newer NeoSkript is out. Turned off with {@code update-checker: false}.
 *
 * <p>Only versions on the server's channel count: a release only hears of newer releases, a beta also of newer
 * betas. Development builds (SNAPSHOT versions) don't check at all; they update from the dev build.
 */
final class UpdateChecker {

    /** NeoSkript's project on Modrinth. */
    static final String PROJECT = "neoskript";

    static final String PAGE = "https://modrinth.com/plugin/" + PROJECT + "/versions";

    private static final URI VERSIONS = URI.create("https://api.modrinth.com/v2/project/" + PROJECT + "/version?loaders="
            + URLEncoder.encode("[\"paper\",\"folia\"]", StandardCharsets.UTF_8));

    private UpdateChecker() {}

    /** A newer version, by its number and its Modrinth page. */
    record Update(String version, String url) {}

    /**
     * Whether {@code current} checks for updates: release and beta versions do, development builds don't.
     */
    static boolean checks(String current) {
        Version version = Version.parse(current);
        return version != null && !version.snapshot();
    }

    /**
     * Fetches NeoSkript's versions from Modrinth and returns the newest one past {@code current}, if any. Blocks, so
     * it's called off the main thread.
     *
     * @throws Exception when Modrinth can't be reached or answers with something else than the list
     */
    static Optional<Update> fetch(String current) throws Exception {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        HttpRequest request = HttpRequest.newBuilder(VERSIONS)
                .timeout(Duration.ofSeconds(10))
                // Modrinth asks API clients to name themselves.
                .header("User-Agent", "xena-studios/neoskript/" + current + " (update checker; neoskript.dev)")
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 404) {
            // Not on Modrinth yet: nothing newer.
            return Optional.empty();
        }
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Modrinth answered " + response.statusCode());
        }
        return newest(response.body(), current);
    }

    /**
     * The newest version in Modrinth's version list JSON that's past {@code current} and on its channel.
     */
    static Optional<Update> newest(String json, String current) {
        Version running = Version.parse(current);
        if (running == null) {
            return Optional.empty();
        }
        JsonElement root = JsonParser.parseString(json);
        if (!root.isJsonArray()) {
            throw new IllegalArgumentException("not a version list");
        }
        Version best = null;
        String bestNumber = null;
        for (JsonElement element : (JsonArray) root) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject entry = element.getAsJsonObject();
            String number = string(entry, "version_number");
            String type = string(entry, "version_type");
            Version version = number == null ? null : Version.parse(number);
            if (version == null || version.snapshot() || "alpha".equals(type)) {
                continue;
            }
            // A release server only hears of releases.
            if (running.prerelease().isEmpty() && (!"release".equals(type) || !version.prerelease().isEmpty())) {
                continue;
            }
            if (version.compareTo(running) > 0 && (best == null || version.compareTo(best) > 0)) {
                best = version;
                bestNumber = number;
            }
        }
        return bestNumber == null
                ? Optional.empty()
                : Optional.of(new Update(bestNumber, "https://modrinth.com/plugin/" + PROJECT + "/version/" + bestNumber));
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
    }

    /** A semantic version: {@code major.minor.patch}, maybe with {@code -prerelease} and {@code +build}. */
    record Version(int major, int minor, int patch, List<String> prerelease) implements Comparable<Version> {

        private static final Pattern FORM =
                Pattern.compile("v?(\\d+)\\.(\\d+)(?:\\.(\\d+))?(?:-([0-9A-Za-z.-]+))?(?:\\+[0-9A-Za-z.-]+)?");

        static Version parse(String text) {
            Matcher matcher = FORM.matcher(text.strip());
            if (!matcher.matches()) {
                return null;
            }
            try {
                List<String> prerelease = matcher.group(4) == null ? List.of() : List.of(matcher.group(4).split("\\."));
                return new Version(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)),
                        matcher.group(3) == null ? 0 : Integer.parseInt(matcher.group(3)), prerelease);
            } catch (NumberFormatException tooBig) {
                return null;
            }
        }

        boolean snapshot() {
            return prerelease.stream().anyMatch(part -> part.equalsIgnoreCase("SNAPSHOT"));
        }

        /** Semantic versioning's precedence: a prerelease comes before its release. */
        @Override
        public int compareTo(Version other) {
            int by = Integer.compare(major, other.major);
            if (by == 0) {
                by = Integer.compare(minor, other.minor);
            }
            if (by == 0) {
                by = Integer.compare(patch, other.patch);
            }
            if (by != 0) {
                return by;
            }
            if (prerelease.isEmpty() || other.prerelease.isEmpty()) {
                return Boolean.compare(prerelease.isEmpty(), other.prerelease.isEmpty());
            }
            for (int i = 0; i < Math.min(prerelease.size(), other.prerelease.size()); i++) {
                String a = prerelease.get(i);
                String b = other.prerelease.get(i);
                boolean aNumber = a.chars().allMatch(Character::isDigit);
                boolean bNumber = b.chars().allMatch(Character::isDigit);
                int part;
                if (aNumber && bNumber) {
                    part = new java.math.BigInteger(a).compareTo(new java.math.BigInteger(b));
                } else if (aNumber != bNumber) {
                    part = aNumber ? -1 : 1;
                } else {
                    part = a.compareTo(b);
                }
                if (part != 0) {
                    return part;
                }
            }
            return Integer.compare(prerelease.size(), other.prerelease.size());
        }
    }
}
