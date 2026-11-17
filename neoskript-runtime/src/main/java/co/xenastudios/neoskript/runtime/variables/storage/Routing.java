/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.runtime.variables.storage;

import co.xenastudios.neoskript.runtime.variables.VariableStore;
import java.util.function.Predicate;

/**
 * How one database among several ({@link Databases}) fits in: which variables it saves, and where loading puts
 * what it reads.
 *
 * @param owns whether a variable, by its name, is saved in this database
 * @param movedAway whether a variable this database doesn't own has been saved in the database that does, so this
 *     one can let go of its copy. Only an SQL database asks: it keeps rows no database owns, as Skript does
 * @param loadedElsewhere whether another database loaded a variable this one owns when the server started: a database
 *     that loads later leaves its value alone, as startup's order rule kept it
 * @param loadInto where loading puts the variables, or {@code null} for the store saves read from
 */
public record Routing(Predicate<String> owns, Predicate<String> movedAway, Predicate<String> loadedElsewhere,
        VariableStore loadInto) {

    /** The only database: every variable, loaded straight into the store. */
    public static final Routing ALL = new Routing(name -> true, name -> false, name -> false, null);
}
