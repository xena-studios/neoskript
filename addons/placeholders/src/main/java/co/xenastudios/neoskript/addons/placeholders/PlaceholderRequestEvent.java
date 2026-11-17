/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.addons.placeholders;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * A request for one of a script's placeholders, which its trigger answers by setting the result. It isn't fired
 * through Bukkit: the addon runs the trigger for the placeholder's prefix itself, on the thread that asked.
 */
public class PlaceholderRequestEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String placeholder;
    private final String prefix;
    private final String identifier;
    private final OfflinePlayer player;
    private volatile String result;

    /**
     * A request.
     *
     * @param placeholder the placeholder, without {@code %}: {@code myserver_coins}
     * @param player who it's for, or {@code null}
     * @param async whether it's off the server's threads
     */
    public PlaceholderRequestEvent(String placeholder, OfflinePlayer player, boolean async) {
        super(async);
        this.placeholder = placeholder;
        // As skript-placeholders splits it: the prefix up to the first _, and the rest.
        int underscore = placeholder.indexOf('_');
        this.prefix = underscore < 0 ? null : placeholder.substring(0, underscore);
        this.identifier = underscore < 0 ? null : placeholder.substring(underscore + 1);
        this.player = player;
    }

    /** The placeholder, without {@code %}. */
    public String placeholder() {
        return placeholder;
    }

    /** The part before the first {@code _}, or {@code null} if there's none. */
    public String prefix() {
        return prefix;
    }

    /** The part after the first {@code _}, or {@code null} if there's none. */
    public String identifier() {
        return identifier;
    }

    /** Who it's for, or {@code null}. */
    public OfflinePlayer offlinePlayer() {
        return player;
    }

    /** Who it's for, if they're online, else {@code null}. */
    public Player player() {
        return player == null ? null : player.getPlayer();
    }

    /** The value the trigger gave, or {@code null}. */
    public String result() {
        return result;
    }

    /** Sets the value, or with {@code null} clears it. */
    public void result(String result) {
        this.result = result;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    /** Bukkit's handler list, which every event class has. */
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
