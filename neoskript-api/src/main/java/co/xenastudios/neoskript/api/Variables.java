/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.api;

import java.util.Map;

/**
 * Scripts' global variables. A name is written as in a script without its braces, with anything in
 * {@code %...%} already filled in: {@code money::069a79f4-44e9-4726-a5be-fca90e38aaf5} for
 * {@code {money::%player%}}, as players are their UUIDs in variable names by default. Values are as scripts have
 * them: a {@code Long} or {@code Double} for a number, a {@code String} for a text.
 *
 * <p>Each call is atomic and can be made from any thread, as each operation in a script is; a sequence of calls
 * isn't. Variables changed here are saved with the rest.
 *
 * <p>Addons call this; they don't implement it.
 */
public interface Variables {

    /**
     * The value of {@code {name}}.
     *
     * @param name the variable's name, as {@code coins::steve}
     * @return its value, or {@code null} if it isn't set
     */
    Object get(String name);

    /**
     * Sets {@code {name}}, or deletes it with {@code null}. A list under it ({@code {name::*}}) is kept.
     *
     * @param name the variable's name
     * @param value its new value, or {@code null}
     */
    void set(String name, Object value);

    /**
     * The values of the list {@code {name::*}}, each by its index, in the order a script loops them.
     *
     * @param name the list's name without {@code ::*}, as {@code coins} for {@code {coins::*}}
     * @return its values, which don't change when the list does
     */
    Map<String, Object> list(String name);

    /**
     * Deletes the list {@code {name::*}}, as {@code delete {name::*}} does. {@code {name}} itself is kept.
     *
     * @param name the list's name without {@code ::*}
     */
    void deleteList(String name);
}
