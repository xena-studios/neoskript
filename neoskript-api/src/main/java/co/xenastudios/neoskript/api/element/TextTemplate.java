/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.api.element;

import java.util.List;
import java.util.Objects;

/**
 * A text written in quotes in a script, with the expressions in it ({@code %...%}) kept apart from the text
 * around them, for an element that treats them differently: skript-db's {@code execute} sends each one to the
 * database as a parameter rather than as part of the query. An element gets one for an argument it asks for with
 * {@link TemplateAware}, and {@link ElementInfo#argumentText} describes it while the script loads.
 *
 * @param pieces the text around the expressions, unescaped ({@code %%} as {@code %}): one more than there are
 *     holes, the first before the first hole and the last after the last, any of them empty
 * @param holes the expressions, in order
 */
public record TextTemplate(List<String> pieces, List<Hole> holes) {

    public TextTemplate {
        pieces = List.copyOf(pieces);
        holes = List.copyOf(holes);
        if (pieces.size() != holes.size() + 1) {
            throw new IllegalArgumentException("A template has one more piece than holes: " + pieces.size()
                    + " pieces, " + holes.size() + " holes");
        }
    }

    /**
     * An expression in the text.
     *
     * @param element the id of the registered element the expression is, without its variant, as the addon that
     *     registered it named it ({@code NeoSkriptDbExprUnsafe}), or {@code null} for anything else: a
     *     variable, a function call, a literal
     * @param written the expression as written, between its {@code %} signs
     * @param values its values when the trigger ran, none while the script loads
     * @param texts each value as text, as the text would show it on its own ({@code "%value%"}), none while the
     *     script loads
     */
    public record Hole(String element, String written, List<Object> values, List<String> texts) {

        public Hole {
            Objects.requireNonNull(written, "written");
            values = List.copyOf(values);
            texts = List.copyOf(texts);
        }
    }
}
