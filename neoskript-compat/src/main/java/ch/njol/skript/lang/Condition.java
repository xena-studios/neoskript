/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package ch.njol.skript.lang;

import ch.njol.util.Kleenean;
import org.bukkit.event.Event;

/** A line that checks something: false stops the trigger, or picks the branch. */
public abstract class Condition extends Statement implements org.skriptlang.skript.lang.condition.Conditional<Event>,
        org.skriptlang.skript.log.runtime.SyntaxRuntimeErrorProducer,
        ch.njol.skript.lang.simplification.Simplifiable<Condition> {

    private boolean negated;

    protected Condition() {}

    public abstract boolean check(Event event);

    @Override
    public Kleenean evaluate(Event event) {
        return Kleenean.get(check(event));
    }

    @Override
    public final boolean run(Event event) {
        return check(event);
    }

    protected final void setNegated(boolean invert) {
        negated = invert;
    }

    public final boolean isNegated() {
        return negated;
    }

    @Override
    public ch.njol.skript.config.Node getNode() {
        return null;
    }

    @Override
    public String getSyntaxTypeName() {
        return "condition";
    }

    @Override
    public Condition simplify() {
        return this;
    }
}
