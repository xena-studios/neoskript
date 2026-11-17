/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.addon.support;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Finds the jars of community addons in a plugins folder by the plugin name in their {@code plugin.yml} (or
 * {@code paper-plugin.yml}), whatever the file is called. No server needed: it only reads files, and a jar it can't
 * read is passed over.
 */
public final class Originals {

    /** A top-level {@code name:} in plugin.yml, quoted or not. */
    private static final Pattern NAME = Pattern.compile("^name\\s*:\\s*['\"]?([^'\"#]+?)['\"]?\\s*(#.*)?$");
    /** The most of plugin.yml read, against a huge or broken file. */
    private static final int MAX_LINES = 10_000;

    private Originals() {}

    /**
     * The jars in {@code plugins} whose plugin is one of {@code names} (compared ignoring case), by the name as
     * given, sorted by name. A folder that's missing or can't be read gives none.
     *
     * @param plugins the plugins folder
     * @param names the plugin names looked for
     * @return each name found, with its jar
     */
    public static Map<String, Path> find(Path plugins, Collection<String> names) {
        Map<String, String> wanted = names.stream().filter(name -> name != null && !name.isBlank())
                .collect(Collectors.toMap(name -> name.strip().toLowerCase(Locale.ROOT), String::strip, (a, b) -> a));
        Map<String, Path> found = new TreeMap<>();
        if (wanted.isEmpty() || plugins == null || !Files.isDirectory(plugins)) {
            return found;
        }
        try (DirectoryStream<Path> jars = Files.newDirectoryStream(plugins, "*.{jar,JAR}")) {
            for (Path jar : jars) {
                String name = pluginName(jar);
                String original = name == null ? null : wanted.get(name.toLowerCase(Locale.ROOT));
                if (original != null) {
                    found.putIfAbsent(original, jar);
                }
            }
        } catch (IOException | RuntimeException unreadable) {
            // A folder that can't be listed has nothing to find.
        }
        return found;
    }

    /**
     * The plugin name in a jar's {@code plugin.yml} or {@code paper-plugin.yml}, or {@code null} if it has neither
     * or can't be read.
     *
     * @param jar the jar
     * @return its plugin's name, or {@code null}
     */
    public static String pluginName(Path jar) {
        if (!Files.isRegularFile(jar)) {
            return null;
        }
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            for (String file : java.util.List.of("paper-plugin.yml", "plugin.yml")) {
                ZipEntry entry = zip.getEntry(file);
                if (entry != null) {
                    String name = nameIn(zip.getInputStream(entry));
                    if (name != null) {
                        return name;
                    }
                }
            }
        } catch (IOException | RuntimeException unreadable) {
            // Not a jar, or a broken one: not the original.
        }
        return null;
    }

    private static String nameIn(InputStream yaml) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(yaml, StandardCharsets.UTF_8))) {
            String line;
            for (int i = 0; i < MAX_LINES && (line = reader.readLine()) != null; i++) {
                Matcher name = NAME.matcher(line.replace("﻿", ""));
                if (name.matches()) {
                    return name.group(1).strip();
                }
            }
        }
        return null;
    }
}
