/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.harness.bot;

import java.lang.reflect.Method;
import net.minecraft.network.Connection;
import net.minecraft.server.MinecraftServer;

/** Hands a new connection to the server to tick, the way its own network listener does. */
final class ServerConnections {

    // Folia ticks connections that haven't joined yet on its global region, then on the player's
    // region. The bot compiles against Paper, so Folia's class is looked up at runtime.
    private static final Method FOLIA_ADD = foliaAdd();

    static void add(MinecraftServer server, Connection connection) {
        if (FOLIA_ADD != null) {
            try {
                Object regionizedServer = FOLIA_ADD.getDeclaringClass().getMethod("getInstance").invoke(null);
                FOLIA_ADD.invoke(regionizedServer, connection);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Couldn't hand the bot's connection to Folia", e);
            }
            return;
        }
        // Paper's listener queues new connections and moves them to this list on its next tick.
        connection.isPending = false;
        server.getConnection().getConnections().add(connection);
    }

    private static Method foliaAdd() {
        try {
            return Class.forName("io.papermc.paper.threadedregions.RegionizedServer")
                    .getMethod("addConnection", Connection.class);
        } catch (ClassNotFoundException e) {
            return null;
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("Folia's RegionizedServer has no addConnection", e);
        }
    }

    private ServerConnections() {}
}
