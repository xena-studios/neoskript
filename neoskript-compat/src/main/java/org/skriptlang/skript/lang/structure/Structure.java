/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package org.skriptlang.skript.lang.structure;

import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.Literal;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.util.Kleenean;
import org.skriptlang.skript.lang.entry.EntryContainer;

/** A structure: a top-level section, as {@code on load:}. NeoSkriptCompat doesn't run addons' structures yet. */
public abstract class Structure implements ch.njol.skript.lang.SyntaxElement, ch.njol.skript.lang.Debuggable {

    public static final Priority DEFAULT_PRIORITY = new Priority(1000);

    public Structure() {}

    @Override
    public final boolean init(Expression<?>[] expressions, int matchedPattern, Kleenean isDelayed,
            ParseResult parseResult) {
        throw new UnsupportedOperationException("NeoSkriptCompat doesn't run addons' structures yet");
    }

    public abstract boolean init(Literal<?>[] arguments, int matchedPattern, ParseResult parseResult,
            EntryContainer entryContainer);

    public boolean preLoad() {
        return true;
    }

    public abstract boolean load();

    public boolean postLoad() {
        return true;
    }

    public void unload() {}

    public void postUnload() {}

    public Priority getPriority() {
        return DEFAULT_PRIORITY;
    }

    @Override
    public String toString() {
        return toString(null, false);
    }

    @Override
    public String getSyntaxTypeName() {
        return "structure";
    }

    /** When a structure loads, before or after others: lower first. */
    public static class Priority implements Comparable<Priority> {

        private final int priority;

        public Priority(int priority) {
            this.priority = priority;
        }

        public int getPriority() {
            return priority;
        }

        @Override
        public int compareTo(Priority other) {
            return Integer.compare(priority, other.priority);
        }
    }
}
