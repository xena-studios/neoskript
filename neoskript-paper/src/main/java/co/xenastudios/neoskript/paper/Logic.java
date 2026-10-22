/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.paper;

import co.xenastudios.neoskript.api.registration.Registrar;
import co.xenastudios.neoskript.runtime.Comparison;

/** Comparisons, whether something is set, and counting. */
final class Logic {

    private enum Relation {
        EQUAL,
        NOT_EQUAL,
        GREATER,
        GREATER_OR_EQUAL,
        LESS,
        LESS_OR_EQUAL
    }

    private Logic() {}

    static void register(Registrar registrar) {
        registrar.condition("CondCompare", info -> {
            Relation relation = relation(info.tags());
            boolean negated = info.hasTag("not");
            return (context, args) -> negated != holds((Object[]) args[0], (Object[]) args[1], relation);
        }, "%objects% (is|are)[(not:n't|not: not)] (ge:(greater|more|higher|bigger|larger) than or (equal to|the same as)"
                + "|gt:(greater|more|higher|bigger|larger) than|gt:above"
                + "|le:(less|smaller|lower) than or (equal to|the same as)|lt:(less|smaller|lower) than|lt:below"
                + "|eq:[equal to|the same as]) %objects%",
                "%objects% (ge:\\>=|gt:\\>|le:\\<=|ne:\\<\\>|lt:\\<|eq:=|ne:!=) %objects%");

        registrar.condition("CondIsSet", info -> {
            boolean negated = info.hasTag("not");
            return (context, args) -> negated == (((Object[]) args[0]).length == 0);
        }, "%~objects% (exist[s]|(is|are) set)",
                "%~objects% (not:do[es](n't| not) exist|not:(is|are)(n't| not) set)");

        registrar.expression("ExprTimes", "integer", info -> (context, args) -> {
            long count = Math.max(0, ((Number) args[0]).longValue());
            Object[] numbers = new Object[(int) Math.min(count, Integer.MAX_VALUE)];
            for (int i = 0; i < numbers.length; i++) {
                numbers[i] = (long) i + 1;
            }
            return numbers;
        }, "%number% times");

        registrar.expression("ExprAmount", "integer", info -> (context, args) -> (long) ((Object[]) args[0]).length,
                "[the] (amount|number|size) of %objects%");
    }

    private static Relation relation(java.util.Set<String> tags) {
        if (tags.contains("ge")) {
            return Relation.GREATER_OR_EQUAL;
        } else if (tags.contains("gt")) {
            return Relation.GREATER;
        } else if (tags.contains("le")) {
            return Relation.LESS_OR_EQUAL;
        } else if (tags.contains("lt")) {
            return Relation.LESS;
        } else if (tags.contains("ne")) {
            return Relation.NOT_EQUAL;
        }
        return Relation.EQUAL;
    }

    /** Every value on the left relates to at least one on the right. Nothing on either side is false. */
    private static boolean holds(Object[] left, Object[] right, Relation relation) {
        if (left.length == 0 || right.length == 0) {
            return false;
        }
        for (Object a : left) {
            boolean any = false;
            for (Object b : right) {
                if (holds(a, b, relation)) {
                    any = true;
                    break;
                }
            }
            if (!any) {
                return false;
            }
        }
        return true;
    }

    private static boolean holds(Object a, Object b, Relation relation) {
        if (relation == Relation.EQUAL) {
            return Comparison.equal(a, b);
        }
        if (relation == Relation.NOT_EQUAL) {
            return !Comparison.equal(a, b);
        }
        Integer order = Comparison.order(a, b);
        if (order == null) {
            return false;
        }
        return switch (relation) {
            case GREATER -> order > 0;
            case GREATER_OR_EQUAL -> order >= 0;
            case LESS -> order < 0;
            default -> order <= 0;
        };
    }
}
