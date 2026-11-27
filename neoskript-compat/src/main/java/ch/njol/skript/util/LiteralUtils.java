/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package ch.njol.skript.util;

import ch.njol.skript.lang.Expression;

/**
 * Skript's helpers for words it couldn't read as any one type when it parsed the line. NeoSkript reads every argument
 * as a value of a type, so there are none of those: arguments are kept as they are.
 */
public class LiteralUtils {

    @SuppressWarnings("unchecked")
    public static <T> Expression<T> defendExpression(Expression<?> expr) {
        return (Expression<T>) expr;
    }

    public static boolean hasUnparsedLiteral(Expression<?> expr) {
        return false;
    }

    public static boolean canInitSafely(Expression<?>... expressions) {
        for (Expression<?> expression : expressions) {
            if (expression == null) {
                return false;
            }
        }
        return true;
    }
}
