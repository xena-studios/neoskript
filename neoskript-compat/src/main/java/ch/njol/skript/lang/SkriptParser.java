/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package ch.njol.skript.lang;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.MatchResult;

/** Skript's parser. NeoSkript parses scripts itself; addons see how their syntax matched. */
public final class SkriptParser {

    public static final int PARSE_EXPRESSIONS = 1;
    public static final int PARSE_LITERALS = 2;
    public static final int ALL_FLAGS = PARSE_EXPRESSIONS | PARSE_LITERALS;

    private SkriptParser() {}

    /** How a pattern matched: its marks, tags, regexes and the text matched. */
    public static class ParseResult {

        public Expression<?>[] exprs;
        public List<MatchResult> regexes = new ArrayList<>(1);
        public String expr;
        public int mark = 0;
        public List<String> tags = new ArrayList<>();

        public ParseResult(String expr, Expression<?>[] expressions) {
            this.expr = expr;
            this.exprs = expressions;
        }

        public boolean hasTag(String tag) {
            return tags.contains(tag);
        }
    }
}
