/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.addons.proxy;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * A message from another server reached this one: over plugin messaging or Redis. Fired on the global thread (the
 * main thread on Paper), never on a network thread, so its triggers can touch the world.
 */
public final class NetworkMessageEvent extends Event {

    /** What kind of message it is, which decides the script events it runs. */
    public enum Kind {
        /** Sent with {@code send network message}: {@code on network message}. */
        NETWORK,
        /** Sent with SkRedis's {@code send redis message}: {@code on redis message} and {@code on network message}. */
        REDIS,
        /** A JSON object with no SkRedis action, as {@code send custom redis message} sends: {@code on custom redis message}. */
        CUSTOM
    }

    private static final HandlerList HANDLERS = new HandlerList();

    private final Kind kind;
    private final String channel;
    private final String message;
    private final String origin;
    private final long sent;

    /**
     * @param kind what kind it is
     * @param channel its channel
     * @param message the message
     * @param origin the sender's server name, or {@code null} if unknown
     * @param sent when it was sent, in milliseconds since 1970
     */
    public NetworkMessageEvent(Kind kind, String channel, String message, String origin, long sent) {
        this.kind = kind;
        this.channel = channel;
        this.message = message;
        this.origin = origin;
        this.sent = sent;
    }

    public Kind kind() {
        return kind;
    }

    public String channel() {
        return channel;
    }

    public String message() {
        return message;
    }

    public String origin() {
        return origin;
    }

    public long sent() {
        return sent;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
