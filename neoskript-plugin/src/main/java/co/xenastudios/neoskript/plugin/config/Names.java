/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.plugin.config;

import java.lang.reflect.RecordComponent;

/** Setting names: a record component {@code saveInterval} is the key {@code save-interval}. */
final class Names {

    private Names() {}

    /** A component's key: its {@link Key}, or its name in kebab case. */
    static String key(RecordComponent component) {
        Key key = component.getAnnotation(Key.class);
        return key != null ? key.value() : key(component.getName());
    }

    static String key(String componentName) {
        StringBuilder key = new StringBuilder(componentName.length() + 4);
        for (int i = 0; i < componentName.length(); i++) {
            char c = componentName.charAt(i);
            if (Character.isUpperCase(c)) {
                key.append('-').append(Character.toLowerCase(c));
            } else {
                key.append(c);
            }
        }
        return key.toString();
    }
}
