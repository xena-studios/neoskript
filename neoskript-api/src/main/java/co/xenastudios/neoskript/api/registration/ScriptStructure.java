/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.api.registration;

import co.xenastudios.neoskript.api.annotation.Experimental;
import java.util.List;

/**
 * One of an addon's structures in a script, as {@link Registrar#structure} gives it: its header and the lines under
 * it, as written (with the script's options put in). Addons are given these; they don't implement them.
 */
@Experimental
public interface ScriptStructure {

    /**
     * The script the structure is in.
     *
     * @return its name, as {@code quests/main.sk}
     */
    String script();

    /**
     * The line the structure's header is on.
     *
     * @return its number, from 1
     */
    default int line() {
        return 0;
    }

    /**
     * The structure's header, without its {@code :}.
     *
     * @return the header as written
     */
    String header();

    /**
     * Which of the structure's patterns its header matched.
     *
     * @return the pattern's index, from 0
     */
    int pattern();

    /**
     * The lines under the header, in order, comments and blank lines left out.
     *
     * @return the lines
     */
    List<Entry> entries();

    /**
     * The lines under one of the structure's sections ({@code patterns:} in skript-reflect's {@code effect:}), as
     * written, nested sections' lines included, with their indentation left out; {@code null} if there's no section of
     * that name directly under the header.
     *
     * @param name the section's name, without its {@code :}, in any case
     * @return its lines, or {@code null}
     */
    default List<Entry> section(String name) {
        return null;
    }

    /**
     * Reports an error on a line of the structure, as the script's other load errors are; the rest of the script still
     * loads, as in Skript.
     *
     * @param line the line's number, from 1, as {@link Entry#line()}
     * @param message the error
     */
    void error(int line, String message);

    /**
     * Reports a warning on a line of the structure.
     *
     * @param line the line's number, from 1
     * @param message the warning
     */
    void warning(int line, String message);

    /**
     * A line under the structure's header.
     *
     * @param line the line's number in the script, from 1
     * @param text the line as written, without its indentation
     */
    record Entry(int line, String text) {}
}
