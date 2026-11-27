/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.addons.reflect;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What a call names, as skript-reflect's {@code Descriptor}: {@code [ArrayList]fastRemove}, {@code println[Object]}
 * or plain {@code getName}. The class in front says where to look for a non-public member; the types after pick an
 * overload.
 *
 * @param javaClass the class named in front, or {@code null} for the target's
 * @param name the member's name
 * @param parameterTypes the overload's parameter types, or {@code null} to pick one by the arguments
 */
record Descriptor(Class<?> javaClass, String name, Class<?>[] parameterTypes) {

    private static final String PACKAGE_ARRAY = Imports.PACKAGE + "(?:\\[])*";
    private static final Pattern DESCRIPTOR = Pattern.compile("(?:\\[(" + Imports.PACKAGE + ")])?(" + Imports.IDENTIFIER
            + ")(?:\\[((?:" + PACKAGE_ARRAY + "\\s*,\\s*)*(?:" + PACKAGE_ARRAY + "))])?");

    /** The descriptor of a constructor. */
    static final Descriptor CONSTRUCTOR = new Descriptor(null, "<init>", null);

    /** A class named in a descriptor that the script hasn't imported. */
    static final class NotFound extends Exception {
        final String name;

        NotFound(String name) {
            super(name);
            this.name = name;
        }
    }

    /** Parses {@code text} with a script's imports, by name, or {@code null} if it isn't a descriptor. */
    static Descriptor parse(String text, java.util.Map<String, JavaType> imports) throws NotFound {
        Matcher matcher = DESCRIPTOR.matcher(text);
        if (!matcher.matches()) {
            return null;
        }
        Class<?> javaClass = matcher.group(1) == null ? null : lookup(matcher.group(1), imports);
        Class<?>[] parameters = null;
        if (matcher.group(3) != null) {
            String[] raw = matcher.group(3).split(",");
            parameters = new Class<?>[raw.length];
            for (int i = 0; i < raw.length; i++) {
                String type = raw[i].strip();
                int depth = 0;
                while (type.endsWith("[]")) {
                    type = type.substring(0, type.length() - 2);
                    depth++;
                }
                Class<?> base = Coercion.PRIMITIVES.get(type);
                parameters[i] = Coercion.arrayOf(base != null ? base : lookup(type, imports), depth);
            }
        }
        return new Descriptor(javaClass, matcher.group(2), parameters);
    }

    private static Class<?> lookup(String name, java.util.Map<String, JavaType> imports) throws NotFound {
        JavaType imported = imports.get(name);
        if (imported == null) {
            throw new NotFound(name);
        }
        return imported.javaClass();
    }

    /** This, with {@code type} as its class if it names none. */
    Descriptor orClass(Class<?> type) {
        return javaClass != null ? this : new Descriptor(type, name, parameterTypes);
    }

    /** As skript-reflect writes it in messages: {@code Player#getName} on an object, {@code Math.max} static. */
    String text(boolean isStatic) {
        return javaClass == null ? "(unspecified)" : Coercion.debugName(javaClass) + (isStatic ? "." : "#") + name;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Descriptor that && java.util.Objects.equals(javaClass, that.javaClass)
                && name.equals(that.name) && java.util.Arrays.equals(parameterTypes, that.parameterTypes);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(javaClass, name) * 31 + java.util.Arrays.hashCode(parameterTypes);
    }
}
