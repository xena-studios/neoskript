/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package ch.njol.skript.lang;

import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.lang.parser.ParserInstance;
import ch.njol.util.Kleenean;

/** An element of Skript's syntax, set up for the place a script uses it. */
public interface SyntaxElement {

    default boolean preInit() {
        return true;
    }

    boolean init(Expression<?>[] expressions, int matchedPattern, Kleenean isDelayed, ParseResult parseResult);

    default ParserInstance getParser() {
        return ParserInstance.get();
    }

    String getSyntaxTypeName();
}
