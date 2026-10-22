/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.api.registration;

import co.xenastudios.neoskript.api.element.Condition;
import co.xenastudios.neoskript.api.element.Effect;
import co.xenastudios.neoskript.api.element.ElementFactory;
import co.xenastudios.neoskript.api.element.Expression;

/**
 * Registers types and syntax. NeoSkript's standard library registers through this, as addons will.
 *
 * <p>Patterns use Skript's pattern language. Type names in patterns are a type's id or name, singular or
 * plural. Elements are tried in registration order.
 */
public interface Registrar {

    /**
     * Registers a type.
     *
     * @param id the code name used in patterns, e.g. {@code commandsender}
     * @param name the display name, e.g. {@code command sender}; also usable in patterns
     * @param javaClass the class values of this type have
     */
    void type(String id, String name, Class<?> javaClass);

    /** Values of type {@code sub} can be used where {@code supertype} is expected. */
    void subtype(String sub, String supertype);

    void effect(String id, ElementFactory<Effect> factory, String... patterns);

    void condition(String id, ElementFactory<Condition> factory, String... patterns);

    /** @param returnType the id of the type the expression returns */
    void expression(String id, String returnType, ElementFactory<Expression> factory, String... patterns);

    /** An event NeoSkript fires itself, like a script loading. */
    void event(String id, ScriptEvent event, String... patterns);
}
