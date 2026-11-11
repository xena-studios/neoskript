/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.api.element;

/**
 * An {@link Expression} that {@code set}, {@code add}, {@code remove}, {@code delete} and {@code reset}
 * can change, like {@code the name of %player%}. Expressions opt in by implementing this as well.
 */
public interface Changeable {

    /** How a value is changed. */
    enum Mode {
        /** {@code set X to Y} */
        SET,
        /** {@code add Y to X}, {@code increase X by Y} */
        ADD,
        /** {@code remove Y from X}, {@code reduce X by Y} */
        REMOVE,
        /** {@code remove all Y from X} */
        REMOVE_ALL,
        /** {@code delete X}, {@code clear X} */
        DELETE,
        /** {@code reset X} */
        RESET
    }

    /**
     * The classes of values this expression can be changed with in {@code mode}, or {@code null} if it
     * can't be changed that way. {@link Mode#DELETE} and {@link Mode#RESET} take no values: return an
     * empty array to support them.
     */
    Class<?>[] acceptChange(Mode mode);

    /**
     * Whether {@code mode} takes only one value, as Skript's expressions that accept a class rather than an
     * array of it: {@code add all players to whitelist} is then an error. Most take several.
     */
    default boolean takesOne(Mode mode) {
        return false;
    }

    /**
     * Whether, in {@code mode}, which this doesn't take itself, its values are changed through their type
     * instead, as Skript's expressions that leave other modes to {@code super.acceptChange}: {@code delete the
     * vehicle of player} kills it, though only setting the vehicle is the expression's own.
     */
    default boolean typeChanges(Mode mode) {
        return false;
    }

    /**
     * Changes the expression.
     *
     * @param arguments the expression's own arguments, as for {@link Expression#get}
     * @param delta the values to change it with, already narrowed to the accepted classes; {@code null}
     *     for {@link Mode#DELETE} and {@link Mode#RESET}
     */
    void change(TriggerContext context, Object[] arguments, Object[] delta, Mode mode);
}
