/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package ch.njol.skript.lang;

import org.bukkit.event.Event;

/** A line that does something. */
public abstract class Effect extends Statement implements org.skriptlang.skript.log.runtime.SyntaxRuntimeErrorProducer {

    protected Effect() {}

    protected abstract void execute(Event event);

    @Override
    public final boolean run(Event event) {
        execute(event);
        return true;
    }

    @Override
    public ch.njol.skript.config.Node getNode() {
        return null;
    }

    @Override
    public String getSyntaxTypeName() {
        return "effect";
    }
}
