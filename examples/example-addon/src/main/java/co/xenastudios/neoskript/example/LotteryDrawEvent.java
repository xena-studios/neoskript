/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.example;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** The event {@code draw the lottery} fires: {@code on lottery draw} in scripts, with the winning ticket. */
public final class LotteryDrawEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Ticket winner;

    /**
     * A draw.
     *
     * @param winner the winning ticket
     */
    public LotteryDrawEvent(Ticket winner) {
        this.winner = winner;
    }

    /**
     * The winning ticket.
     *
     * @return the ticket
     */
    public Ticket winner() {
        return winner;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    /**
     * Bukkit's handlers of this event.
     *
     * @return the handlers
     */
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
