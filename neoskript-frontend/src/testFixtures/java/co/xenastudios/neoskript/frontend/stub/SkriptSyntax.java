/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.frontend.stub;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The reference Skript's syntax list, from {@code skript-syntax/<version>.json}: every structure, event,
 * section, effect, condition and expression with its patterns, plus types and functions. Generated from
 * Skript's docs export, so patterns are in documentation form.
 */
public record SkriptSyntax(Map<String, String> source, List<Element> structures, List<Element> events,
        List<Element> sections, List<Element> effects, List<Element> conditions, List<Element> expressions,
        List<Type> types, List<Function> functions) {

    public static final String VERSION = "2.17.0";

    public record Element(String id, List<String> patterns, String returns) {}

    public record Type(String id, String name, List<String> patterns) {}

    public record Function(String name, String signature, String returns) {}

    private static SkriptSyntax loaded;

    public static synchronized SkriptSyntax load() {
        if (loaded == null) {
            String resource = "/skript-syntax/" + VERSION + ".json";
            try (Reader reader = new InputStreamReader(
                    Objects.requireNonNull(SkriptSyntax.class.getResourceAsStream(resource), resource),
                    StandardCharsets.UTF_8)) {
                loaded = new Gson().fromJson(reader, SkriptSyntax.class);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return loaded;
    }

    /** Every syntax element that's matched against code, in the order Skript reports them. */
    public List<Element> allElements() {
        return java.util.stream.Stream.of(structures, events, sections, effects, conditions, expressions)
                .flatMap(List::stream)
                .toList();
    }
}
