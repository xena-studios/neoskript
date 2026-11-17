/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package org.skriptlang.skript.lang.comparator;

/** How two values compare. */
public enum Relation {
    EQUAL("equal to"),
    NOT_EQUAL("not equal to"),
    GREATER("greater than"),
    GREATER_OR_EQUAL("greater than or equal to"),
    SMALLER("smaller than"),
    SMALLER_OR_EQUAL("smaller than or equal to");

    private final String name;

    Relation(String name) {
        this.name = name;
    }

    public static Relation get(boolean b) {
        return b ? EQUAL : NOT_EQUAL;
    }

    public static Relation get(int i) {
        return i == 0 ? EQUAL : i > 0 ? GREATER : SMALLER;
    }

    public static Relation get(double d) {
        return d == 0 ? EQUAL : d > 0 ? GREATER : SMALLER;
    }

    public boolean isImpliedBy(Relation other) {
        if (other == this) {
            return true;
        }
        return switch (this) {
            case EQUAL, GREATER, SMALLER -> false;
            case NOT_EQUAL -> other == SMALLER || other == GREATER;
            case GREATER_OR_EQUAL -> other == GREATER || other == EQUAL;
            case SMALLER_OR_EQUAL -> other == SMALLER || other == EQUAL;
        };
    }

    public boolean isImpliedBy(Relation... others) {
        for (Relation other : others) {
            if (isImpliedBy(other)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return name;
    }

    public Relation getInverse() {
        return switch (this) {
            case EQUAL -> NOT_EQUAL;
            case NOT_EQUAL -> EQUAL;
            case GREATER -> SMALLER_OR_EQUAL;
            case GREATER_OR_EQUAL -> SMALLER;
            case SMALLER -> GREATER_OR_EQUAL;
            case SMALLER_OR_EQUAL -> GREATER;
        };
    }

    public Relation getSwitched() {
        return switch (this) {
            case EQUAL, NOT_EQUAL -> this;
            case GREATER -> SMALLER;
            case GREATER_OR_EQUAL -> SMALLER_OR_EQUAL;
            case SMALLER -> GREATER;
            case SMALLER_OR_EQUAL -> GREATER_OR_EQUAL;
        };
    }

    public int getRelation() {
        return switch (this) {
            case EQUAL, NOT_EQUAL -> 0;
            case GREATER, GREATER_OR_EQUAL -> 1;
            case SMALLER, SMALLER_OR_EQUAL -> -1;
        };
    }
}
