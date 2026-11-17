/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.api;

/**
 * Values as scripts have them: their types, their text, and how they're saved. For an addon that writes values
 * somewhere else, as a file or a database, the way Skript would.
 * <p>Each method can be called from any thread, once NeoSkript has registered its syntax (from
 * {@link NeoSkript#whenReady}, or in the elements an addon registers). Before that they know no types.
 * <p>Addons call this; they don't implement it.
 */
public interface Values {

    /**
     * The id of {@code value}'s type, as patterns name it: {@code timespan}, {@code itemtype}, {@code player}. The
     * most specific type its class has, or {@code null} for a value of no type.
     * @param value a value
     * @return its type's id, or {@code null}
     */
    String type(Object value);

    /**
     * {@code value} as text, as {@code "%value%"} writes it in a script.
     * @param value a value
     * @return its text
     */
    String text(Object value);

    /**
     * Text as a value of type {@code typeId}, as a command argument of that type is read: {@code "5 minutes"} as a
     * timespan.
     * @param text the text
     * @param typeId the type's id
     * @return the value, or {@code null} if the text isn't one or the type can't be read from text
     */
    Object parse(String text, String typeId);

    /**
     * {@code value} as an instance of {@code to}, through the converters scripts use: an item type as an
     * {@code ItemStack}, a slot as its item.
     * @param value a value
     * @param to the class wanted
     * @return the value converted, {@code value} itself if it already is one, or {@code null} if it can't be
     */
    Object convert(Object value, Class<?> to);

    /**
     * A script value as plain Java has it, where there's a match: a date as a {@code java.time.Instant}, a timespan
     * as a {@code java.time.Duration}. Other values are given back as they are.
     * @param value a value
     * @return it as plain Java has it
     */
    Object toJava(Object value);

    /**
     * A plain Java value as scripts have it: an {@code Instant} (or a {@code java.util.Date}) as a date, a
     * {@code Duration} as a timespan, and whole numbers as {@code Long}s and decimals as {@code Double}s, as script
     * numbers are. Other values are given back as they are.
     * @param value a value
     * @return it as scripts have it
     */
    Object fromJava(Object value);

    /**
     * {@code value} as {@code variables.csv} saves it: Skript's name for its type and its bytes, the same as
     * Skript's {@code Classes.serialize} gives.
     * @param value a value
     * @return how it's saved, or {@code null} if values of its type can't be
     */
    Saved save(Object value);

    /**
     * A value saved by {@link #save}, or by Skript.
     * @param type Skript's name for its type
     * @param data its bytes
     * @return the value, or {@code null} if it can't be read
     */
    Object load(String type, byte[] data);

    /**
     * A value as {@code variables.csv} saves it.
     * @param type Skript's name for its type, as {@code timespan}
     * @param data its bytes
     */
    record Saved(String type, byte[] data) {

        /**
         * A saved value; the bytes are copied.
         * @param type Skript's name for its type
         * @param data its bytes
         */
        public Saved {
            data = data.clone();
        }

        /**
         * Its bytes.
         * @return a copy of them
         */
        @Override
        public byte[] data() {
            return data.clone();
        }
    }
}
