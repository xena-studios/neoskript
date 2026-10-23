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

    private ScriptFeatures() {}

    /** Records what {@code script} uses, replacing what it used before. */
    public static void set(String script, Set<String> features) {
        if (features.isEmpty()) {
            FEATURES.remove(script);
        } else {
            FEATURES.put(script, Set.copyOf(features));
        }
    }

    /** Whether {@code script} uses {@code feature}. */
    public static boolean uses(String script, String feature) {
        Set<String> features = FEATURES.get(script);
        return features != null && features.contains(feature.toLowerCase(Locale.ROOT));
    }
}
