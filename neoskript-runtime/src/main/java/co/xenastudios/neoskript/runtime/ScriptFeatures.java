/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.runtime;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** The experimental features each script turns on with {@code using X}, like {@code using script reflection}. */
public final class ScriptFeatures {

    private static final Map<String, Set<String>> FEATURES = new ConcurrentHashMap<>();

    /**
     * Skript's experiments, by code name, with what a script may write for each ({@code using script
     * reflection} is {@code reflection}). The last three are Skript's test features.
     */
    private static final Map<String, java.util.regex.Pattern> EXPERIMENTS = Map.ofEntries(
            Map.entry("examples", pattern("examples")),
            Map.entry("queues", pattern("queues")),
            Map.entry("for loop", pattern("for( each)? loops?")),
            Map.entry("reflection", pattern("(script )?reflection")),
            Map.entry("catch runtime errors", pattern("error catching( section)?")),
            Map.entry("type hints", pattern("(local variable )?type hints")),
            Map.entry("damage source", pattern("damage sources?")),
            Map.entry("equippable components", pattern("equippable components")),
            Map.entry("example feature", pattern("example feature")),
            Map.entry("deprecated feature", pattern("deprecated feature")),
            Map.entry("test", pattern("test(ing)?|fizz ?buzz")));

    private static java.util.regex.Pattern pattern(String regex) {
        return java.util.regex.Pattern.compile(regex);
    }

    /** The script being compiled on this thread, for syntax that only exists with an experiment. */
    private static final ThreadLocal<String> COMPILING = new ThreadLocal<>();

    private ScriptFeatures() {}

    /** Marks {@code script} as being compiled on this thread, or none if {@code null}. */
    public static void compiling(String script) {
        if (script == null) {
            COMPILING.remove();
        } else {
            COMPILING.set(script);
        }
    }

    /** Whether the script being compiled on this thread uses {@code feature}. */
    public static boolean compilingUses(String feature) {
        String script = COMPILING.get();
        return script != null && uses(script, feature);
    }

    /** Records what {@code script} uses ({@code using ...}), replacing what it used before. */
    public static void set(String script, Set<String> features) {
        if (features.isEmpty()) {
            FEATURES.remove(script);
        } else {
            FEATURES.put(script, features.stream().map(ScriptFeatures::codeName).collect(java.util.stream.Collectors.toUnmodifiableSet()));
        }
    }

    /** Whether {@code script} uses {@code feature}, by any of its names. */
    public static boolean uses(String script, String feature) {
        Set<String> features = FEATURES.get(script);
        return features != null && features.contains(codeName(feature));
    }

    /** The experiment {@code name} is written for, by its code name, or the name itself if it isn't one. */
    private static String codeName(String name) {
        String normal = name.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        if (EXPERIMENTS.containsKey(normal)) {
            return normal;
        }
        for (Map.Entry<String, java.util.regex.Pattern> experiment : EXPERIMENTS.entrySet()) {
            if (experiment.getValue().matcher(normal).matches()) {
                return experiment.getKey();
            }
        }
        return normal;
    }
}
