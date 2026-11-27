/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package ch.njol.skript.lang.util;

import ch.njol.skript.lang.Literal;
import ch.njol.skript.lang.SkriptEvent;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import org.bukkit.event.Event;

/** An event with nothing to check: each event of its classes is one. */
public class SimpleEvent extends SkriptEvent {

    private final String description;

    public SimpleEvent() {
        this(null);
    }

    public SimpleEvent(String description) {
        this.description = description;
    }

    @Override
    public boolean check(Event event) {
        return true;
    }

    @Override
    public boolean init(Literal<?>[] args, int matchedPattern, ParseResult parseResult) {
        if (args.length != 0) {
            throw new ch.njol.skript.SkriptAPIException("Invalid use of SimpleEvent");
        }
        return true;
    }

    @Override
    public String toString(Event event, boolean debug) {
        return description != null ? description : "simple event";
    }
}
