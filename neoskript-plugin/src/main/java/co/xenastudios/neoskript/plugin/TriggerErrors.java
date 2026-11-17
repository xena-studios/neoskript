/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.plugin;

import co.xenastudios.neoskript.runtime.ScriptRuntimeException;
import org.bukkit.Bukkit;
import org.slf4j.Logger;

/**
 * Reports what a trigger threw where NeoSkript runs it (an event listener, a script command, the scheduler), so
 * nothing gets past: not to the event's other triggers, not to Bukkit, not to the server.
 */
final class TriggerErrors {

    private TriggerErrors() {}

    /**
     * Reports {@code thrown}. A trigger that recursed until the stack ran out is told to admins as Skript's
     * {@code TriggerItem.walk} tells them; anything else goes to the log with the script and line.
     */
    static void report(Logger logger, Throwable thrown) {
        if (thrown instanceof ScriptRuntimeException e) {
            if (e.getCause() instanceof StackOverflowError) {
                broadcast("§cThe script '§6" + e.script() + "§c' infinitely (or excessively) repeated itself!");
                return;
            }
            logger.error("Error in {}, line {}: {}", e.script(), e.line(), e.getCause().toString(), e.getCause());
        } else if (thrown instanceof StackOverflowError) {
            broadcast("§cThe script '§6<unknown>§c' infinitely (or excessively) repeated itself!");
        } else {
            logger.error("Unexpected error while running a script", thrown);
        }
    }

    @SuppressWarnings("deprecation") // Skript's adminBroadcast sends legacy text.
    private static void broadcast(String message) {
        try {
            Bukkit.broadcast(message, "skript.admin");
        } catch (RuntimeException outsideAServer) {
            System.err.println(message);
        }
    }
}
