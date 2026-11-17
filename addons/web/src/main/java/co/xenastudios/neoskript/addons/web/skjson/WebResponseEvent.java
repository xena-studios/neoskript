/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.addons.web.skjson;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * skJson's {@code on received http response}: a request sent without waiting ({@code execute {_r} as non blocking})
 * was answered, or failed. Called on the main thread (the global thread on Folia).
 */
public final class WebResponseEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();
    private final SkResponse response;

    /**
     * The event.
     *
     * @param response what the request got
     */
    public WebResponseEvent(SkResponse response) {
        super(false);
        this.response = response;
    }

    /**
     * What the request got.
     *
     * @return the response
     */
    public SkResponse response() {
        return response;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    /**
     * Bukkit's handler list.
     *
     * @return the handlers
     */
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
