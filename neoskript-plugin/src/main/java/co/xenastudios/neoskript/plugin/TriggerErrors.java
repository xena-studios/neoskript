/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.plugin;

import co.xenastudios.neoskript.runtime.ScriptRuntimeException;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
                co.xenastudios.neoskript.runtime.Overflows.report(e.script());
                return;
            }
            logger.error("Error in {}, line {}: {}", e.script(), e.line(), e.getCause().toString(), e.getCause());
        } else if (thrown instanceof StackOverflowError) {
            co.xenastudios.neoskript.runtime.Overflows.report(null);
        } else {
            logger.error("Unexpected error while running a script", thrown);
        }
    }

    /**
     * What Skript 2.17 broadcasts to {@code skript.admin} when a script recursed until the stack ran out: its prefix,
     * then the message, naming the script by its file name.
     */
    static Component repeated(String name) {
        return Component.text()
                .append(Component.text("[", NamedTextColor.GRAY))
                .append(Component.text("Skript", NamedTextColor.GOLD))
                .append(Component.text("] ", NamedTextColor.GRAY))
                .append(Component.text().color(NamedTextColor.RED)
                        .append(Component.text("The script '"))
                        .append(Component.text(name, NamedTextColor.GOLD))
                        .append(Component.text("' infinitely (or excessively) repeated itself!")))
                .build();
    }

    /** Sends {@link #repeated} to everyone with {@code skript.admin}, as Skript's {@code adminBroadcast}. */
    static void broadcastRepeated(String name) {
        try {
            Bukkit.broadcast(repeated(name), "skript.admin");
        } catch (RuntimeException outsideAServer) {
            System.err.println("The script '" + name + "' infinitely (or excessively) repeated itself!");
        }
    }
}
