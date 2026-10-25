/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.paper;

import org.bukkit.command.CommandSender;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * An effect command being run, as Skript's EffectCommandEvent: what a console command or chat message
 * starting with the effect command token ({@code !}) says, without the token. It's called on the Bukkit API
 * before the effect is parsed, so other plugins can change the effect or cancel it; the effect's code then
 * runs in it, where {@code me}, {@code player} and {@code command sender} are its sender.
 */
public final class EffectCommandEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final CommandSender sender;
    private String command;
    private boolean cancelled;

    public EffectCommandEvent(CommandSender sender, String command) {
        this.sender = sender;
        this.command = command;
    }

    /** Who ran it. */
    public CommandSender getSender() {
        return sender;
    }

    /** The effect, without the token. */
    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        cancelled = cancel;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
