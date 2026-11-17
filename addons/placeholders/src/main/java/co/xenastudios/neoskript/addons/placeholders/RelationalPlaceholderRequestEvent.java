/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.addons.placeholders;

import org.bukkit.entity.Player;

/** A request for a relational placeholder ({@code %rel_myserver_friends%}), which is about two players. */
public final class RelationalPlaceholderRequestEvent extends PlaceholderRequestEvent {

    private final Player first;
    private final Player second;

    /**
     * A request.
     *
     * @param placeholder the placeholder, without {@code %} and {@code rel_}: {@code myserver_friends}
     * @param first the first player
     * @param second the second player
     * @param async whether it's off the server's threads
     */
    public RelationalPlaceholderRequestEvent(String placeholder, Player first, Player second, boolean async) {
        super(placeholder, first, async);
        this.first = first;
        this.second = second;
    }

    /** The first player. */
    public Player first() {
        return first;
    }

    /** The second player. */
    public Player second() {
        return second;
    }

    @Override
    public Player player() {
        return first;
    }
}
