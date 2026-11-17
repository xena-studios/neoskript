/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.UUID;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** What the standard library keeps between events and scripts doesn't grow without end or stay open. */
class HeldStateTest {

    private static final class Click extends Event {
        private static final HandlerList HANDLERS = new HandlerList();

        @Override
        public HandlerList getHandlers() {
            return HANDLERS;
        }
    }

    @Test
    void aClickWithBothHandsCountsOnce() {
        UUID player = UUID.randomUUID();
        Click main = new Click();
        assertTrue(PlayerEvents.firstClick(player, main, 5));
        assertTrue(PlayerEvents.firstClick(player, main, 5), "the same event again");
        assertFalse(PlayerEvents.firstClick(player, new Click(), 5), "the other hand, the same tick");
        assertTrue(PlayerEvents.firstClick(player, new Click(), 6), "the next tick");
    }

    @Test
    void clicksOfPlayersWhoLeftAreLetGo() {
        // Each player's last click event was kept for good, and with it the player, after they left.
        for (int i = 0; i < 1_000; i++) {
            PlayerEvents.firstClick(UUID.randomUUID(), new Click(), 1_000);
        }
        PlayerEvents.firstClick(UUID.randomUUID(), new Click(), 2_000);
        assertEquals(1, PlayerEvents.clicksKept());
    }

    @Test
    void logFilesCloseWithThePlugin(@TempDir Path logs) throws Exception {
        PrintWriter writer = ServerSyntax.writer("soak.log", logs.toFile());
        assertSame(writer, ServerSyntax.writer("soak.log", logs.toFile()));
        assertEquals(1, ServerSyntax.openLogs());
        StandardLibrary.close();
        assertEquals(0, ServerSyntax.openLogs());
        writer.println("after");
        assertTrue(writer.checkError(), "the writer is closed");
    }
}
