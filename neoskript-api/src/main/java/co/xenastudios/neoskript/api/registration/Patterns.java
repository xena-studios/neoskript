/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.api.registration;

/** The patterns Skript's property expressions and conditions use, so addons phrase them the same way. */
public final class Patterns {

    private Patterns() {}

    /**
     * {@code [the] <property> of %owners%} and {@code %owners%'[s] <property>}.
     *
     * @param owners the owners' types, plural, as in a placeholder: {@code offlineplayers}
     */
    public static String[] property(String property, String owners) {
        return new String[] {"[the] " + property + " of %" + owners + "%", "%" + owners + "%'[s] " + property};
    }

    /**
     * {@code %owners% (is|are) <property>}, then the negated pattern {@code %owners% (isn't|is not|aren't|are
     * not) <property>}: pattern 1 is the negated one.
     */
    public static String[] isProperty(String property, String owners) {
        return new String[] {"%" + owners + "% (is|are) " + property,
                "%" + owners + "% (isn't|is not|aren't|are not) " + property};
    }

    /**
     * {@code %owners% (has|have) <property>}, then the negated pattern {@code %owners% (doesn't|does not|do
     * not|don't) have <property>}: pattern 1 is the negated one.
     */
    public static String[] hasProperty(String property, String owners) {
        return new String[] {"%" + owners + "% (has|have) " + property,
                "%" + owners + "% (doesn't|does not|do not|don't) have " + property};
    }
}
