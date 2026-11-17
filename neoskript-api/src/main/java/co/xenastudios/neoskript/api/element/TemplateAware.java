/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.api.element;

import co.xenastudios.neoskript.api.annotation.Experimental;

/**
 * An element that wants some of its text arguments with the expressions in them kept apart, as a
 * {@link TextTemplate}, rather than joined into one string: skript-db's {@code execute} binds each one as a
 * parameter, so a player's name can't change the query. Implement it on the object the factory returns.
 *
 * <p>An argument it asks for is passed as a {@link TextTemplate} only where the script writes a text in quotes
 * there ({@link ElementInfo#argumentText} isn't {@code null}); anything else, like a variable, is passed as
 * usual. A text with no expressions in it is a template with one piece and no holes.
 *
 * <p>Experimental until a second addon uses it.
 */
@Experimental
public interface TemplateAware {

    /**
     * Whether argument {@code index}, where it's a text in quotes, is passed as a {@link TextTemplate}.
     *
     * @param index the argument's index
     * @return whether it is
     */
    boolean template(int index);
}
