/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.runtime;

import java.util.function.Consumer;

/**
 * Code that recursed until the stack ran out, as Skript's {@code TriggerItem.walk} handles it: the innermost call
 * with room to spare tells admins "The script 'x.sk' infinitely (or excessively) repeated itself!" and stops, and
 * the code that called it carries on.
 */
public final class Overflows {

    /**
     * Where reports go, or {@code null} for standard error. No lambda here: this class is first used when the stack
     * has run out, and an initializer that ran out of stack too would leave the class unusable for good.
     */
    private static volatile Consumer<String> admins;

    private Overflows() {}

    /** Loads and initializes this class while there's stack to spare: the compiler calls it before scripts run. */
    public static void ready() {
        // Nothing to do: calling it is what initializes the class.
    }

    /** Where the message goes, given the script's file name: the plugin broadcasts it to admins. */
    public static void install(Consumer<String> to) {
        admins = to;
    }

    /** Tells admins that {@code script} repeated itself, by its file name as Skript names it. */
    public static void report(String script) {
        String name = script == null ? "<unknown>" : script.substring(script.lastIndexOf('/') + 1);
        Consumer<String> to = admins;
        if (to != null) {
            to.accept(name);
        } else {
            System.err.println("The script '" + name + "' infinitely (or excessively) repeated itself!");
        }
    }
}
