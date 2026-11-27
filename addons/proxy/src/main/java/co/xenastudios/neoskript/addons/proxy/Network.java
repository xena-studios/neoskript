/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.addons.proxy;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Network messages, over Redis where it's on (it works with nobody online) and plugin messaging where it isn't:
 * sending, and handing what arrives to scripts as {@link NetworkMessageEvent}s on the global thread.
 *
 * <p>Over Redis, messages are JSON, as SkRedis writes them: {@code send redis message} as
 * {@code {"action":"skredis-message","message":...,"date":...}}, so a server running SkRedis and one running this
 * hear each other; {@code send network message} with {@code "action":"neoskript-message"}, its sender and the
 * servers it's for, which SkRedis ignores. A JSON object with no action is a custom message.
 */
final class Network {

    /** The most messages waiting to reach scripts: past this, new ones are dropped. */
    static final int MAX_PENDING = 10_000;
    /** The most bytes of messages waiting to reach scripts. */
    static final long MAX_PENDING_BYTES = 16L * 1024 * 1024;
    /** The most messages for named servers held while this server's name isn't known yet, and for how long. */
    static final int MAX_HELD = 1_000;
    static final long HELD_FOR_MILLIS = 60_000;

    private final Settings settings;
    private final Logger logger;
    private final Consumer<Runnable> global;
    private final Consumer<NetworkMessageEvent> fire;
    private final Envelope envelope;
    private final String instance;
    private volatile PluginMessaging plugin;
    private volatile RedisLink redis;
    /** Gets the messages on {@link NetworkVariables#CHANNEL}, which never reach scripts. */
    private volatile Consumer<String> notices = message -> {};
    private volatile String serverName;
    private final AtomicInteger pending = new AtomicInteger();
    private final java.util.concurrent.atomic.AtomicLong pendingBytes = new java.util.concurrent.atomic.AtomicLong();
    /** Messages for named servers that came before this server knew its name: delivered once it does. */
    private final java.util.Deque<Held> held = new java.util.ArrayDeque<>();

    private record Held(JsonObject json, String channel, long until) {}
    private volatile long warnedAt;

    /**
     * @param global runs a task on the global thread (the main thread on Paper)
     * @param fire fires an event, on the global thread
     */
    Network(Settings settings, Logger logger, Consumer<Runnable> global, Consumer<NetworkMessageEvent> fire,
            Envelope envelope, String instance) {
        this.settings = settings;
        this.logger = logger;
        this.global = global;
        this.fire = fire;
        this.envelope = envelope;
        this.instance = instance;
        this.serverName = settings.serverName();
    }

    void use(PluginMessaging plugin, RedisLink redis) {
        this.plugin = plugin;
        this.redis = redis;
    }

    /** Where messages on {@link NetworkVariables#CHANNEL} go, on the thread they arrive on: never to scripts. */
    void notices(Consumer<String> notices) {
        this.notices = notices;
    }

    /** Whether a notice can reach the other servers now: Redis is up, or plugin messaging is on with a secret. */
    boolean canNotify() {
        RedisLink link = redis;
        return link != null || (plugin != null && envelope.usable());
    }

    /**
     * Tells every other server something on {@link NetworkVariables#CHANNEL}, now or not at all: over Redis if it's
     * up, or else through a player if plugin messaging is on and one is online. Never queued: a notice that's late is
     * useless.
     *
     * @return whether it was sent
     */
    boolean notice(String message) {
        RedisLink link = redis;
        if (link != null && link.up()) {
            JsonObject json = new JsonObject();
            json.addProperty("action", "neoskript-message");
            json.addProperty("message", message);
            json.addProperty("date", System.currentTimeMillis());
            json.addProperty("origin", serverName);
            json.addProperty("instance", instance);
            return link.publish(redisChannel(NetworkVariables.CHANNEL), json.toString());
        }
        PluginMessaging messaging = plugin;
        if (messaging == null || !envelope.usable()) {
            return false;
        }
        return messaging.sendNow(PluginMessaging.forward("ALL",
                envelope.seal(serverName, instance, "ALL", NetworkVariables.CHANNEL, message)));
    }

    /** This server's name on the proxy, or empty if it isn't known yet. */
    String serverName() {
        return serverName;
    }

    /** The proxy said this server's name. */
    void learnName(String name) {
        if (settings.serverName().isEmpty() && name != null && !name.isBlank() && serverName.isEmpty()) {
            serverName = name;
            List<Held> due;
            synchronized (held) {
                due = new java.util.ArrayList<>(held);
                held.clear();
            }
            long now = System.currentTimeMillis();
            for (Held message : due) {
                if (message.until() >= now) {
                    networkMessage(message.channel(), message.json());
                }
            }
        }
    }

    /** The Redis channel a network channel is published on: with redis.channel-prefix in front. */
    private String redisChannel(String channel) {
        return settings.redisChannelPrefix() + channel;
    }

    /**
     * {@code send network message}: to every other server, or to the servers named.
     *
     * @param servers the servers, or {@code null} for every other one
     * @return whether it was sent or queued
     * @throws IllegalArgumentException if it's too long
     */
    boolean send(String channel, String message, List<String> servers) {
        int length = message.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        if (length > settings.maxMessageBytes()) {
            throw new IllegalArgumentException("The message is " + length + " bytes, longer than "
                    + settings.maxMessageBytes() + " (max-message-bytes in plugins/NeoSkriptProxy/config.yml)");
        }
        RedisLink link = redis;
        if (link != null) {
            JsonObject json = new JsonObject();
            json.addProperty("action", "neoskript-message");
            json.addProperty("message", message);
            json.addProperty("date", System.currentTimeMillis());
            json.addProperty("origin", serverName);
            json.addProperty("instance", instance);
            if (servers != null) {
                JsonArray to = new JsonArray();
                servers.forEach(to::add);
                json.add("servers", to);
            }
            return link.publish(redisChannel(channel), json.toString());
        }
        PluginMessaging messaging = plugin;
        if (messaging == null) {
            return false;
        }
        if (!envelope.usable()) {
            warn("A network message wasn't sent: no secret is set in plugins/NeoSkriptProxy/config.yml, so the other"
                    + " servers would refuse it. Set the same secret on every server.");
            return false;
        }
        boolean sent = true;
        for (String server : servers == null ? List.of("ALL") : servers) {
            // Sealed for each server, so a message for one can't be passed on to another.
            sent &= messaging.send(PluginMessaging.forward(server,
                    envelope.seal(serverName, instance, server, channel, message)));
        }
        return sent;
    }

    /** SkRedis's {@code send redis message}. */
    boolean sendRedis(String channel, String message) {
        RedisLink link = redis;
        if (link == null) {
            return false;
        }
        JsonObject json = new JsonObject();
        json.addProperty("message", message);
        json.addProperty("action", "skredis-message");
        json.addProperty("date", System.currentTimeMillis());
        return link.publish(redisChannel(channel), json.toString());
    }

    /**
     * SkRedis's {@code send custom redis message}: a JSON object, as it is.
     *
     * @throws IllegalArgumentException if it isn't a JSON object
     */
    boolean sendCustomRedis(String channel, String json) {
        JsonElement parsed;
        try {
            parsed = JsonParser.parseString(json);
        } catch (RuntimeException notJson) {
            throw new IllegalArgumentException("A message was not sent. Please make sure your message is a valid"
                    + " JSON string.");
        }
        if (!parsed.isJsonObject()) {
            throw new IllegalArgumentException("A message was not sent. Please make sure your message is a valid"
                    + " JSON string.");
        }
        RedisLink link = redis;
        return link != null && link.publish(redisChannel(channel), parsed.toString());
    }

    /** A Redis message arrived, on the listener's thread. */
    void fromRedis(String redisChannel, String text) {
        // A message, with the JSON around it, is held to max-message-bytes plus what SkRedis's format adds.
        if (text.length() > settings.maxMessageBytes() + 1_024
                || !redisChannel.startsWith(settings.redisChannelPrefix())) {
            return;
        }
        String channel = redisChannel.substring(settings.redisChannelPrefix().length());
        JsonObject json;
        try {
            JsonElement parsed = JsonParser.parseString(text);
            if (!parsed.isJsonObject()) {
                return; // Not JSON: SkRedis ignores it too.
            }
            json = parsed.getAsJsonObject();
        } catch (RuntimeException notJson) {
            return;
        }
        JsonElement action = json.get("action");
        if (channel.equals(NetworkVariables.CHANNEL)) {
            // NeoSkriptProxy's own: never to scripts.
            if (action != null && action.isJsonPrimitive() && action.getAsString().equals("neoskript-message")
                    && !instance.equals(string(json, "instance"))) {
                String message = string(json, "message");
                if (message != null) {
                    notices.accept(message);
                }
            }
            return;
        }
        if (action == null) {
            deliver(new NetworkMessageEvent(NetworkMessageEvent.Kind.CUSTOM, channel, text, null,
                    System.currentTimeMillis()));
            return;
        }
        String kind = action.isJsonPrimitive() ? action.getAsString() : "";
        if (kind.equals("skredis-message")) {
            String message = string(json, "message");
            if (message != null) {
                deliver(new NetworkMessageEvent(NetworkMessageEvent.Kind.REDIS, channel, message, null,
                        number(json, "date")));
            }
        } else if (kind.equals("neoskript-message")) {
            if (instance.equals(string(json, "instance"))) {
                return;
            }
            JsonElement servers = json.get("servers");
            if (servers != null && servers.isJsonArray() && serverName.isEmpty()) {
                // For named servers, and this one doesn't know its name yet (it learns it from the proxy once a
                // player is online, or from server-name): held until it does.
                synchronized (held) {
                    if (held.size() >= MAX_HELD) {
                        held.poll();
                    }
                    held.add(new Held(json, channel, System.currentTimeMillis() + HELD_FOR_MILLIS));
                }
                return;
            }
            networkMessage(channel, json);
        }
    }

    private void networkMessage(String channel, JsonObject json) {
        String message = string(json, "message");
        if (message == null || !forThisServer(json.get("servers"))) {
            return;
        }
        String origin = string(json, "origin");
        deliver(new NetworkMessageEvent(NetworkMessageEvent.Kind.NETWORK, channel, message,
                origin == null || origin.isEmpty() ? null : origin, number(json, "date")));
    }

    private boolean forThisServer(JsonElement servers) {
        if (servers == null || servers.isJsonNull()) {
            return true;
        }
        if (!servers.isJsonArray() || serverName.isEmpty()) {
            return false;
        }
        for (JsonElement server : servers.getAsJsonArray()) {
            if (server.isJsonPrimitive() && server.getAsString().equalsIgnoreCase(serverName)) {
                return true;
            }
        }
        return false;
    }

    private static String string(JsonObject json, String key) {
        JsonElement value = json.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
    }

    private static long number(JsonObject json, String key) {
        JsonElement value = json.get(key);
        try {
            return value != null && value.isJsonPrimitive() ? value.getAsLong() : System.currentTimeMillis();
        } catch (RuntimeException notNumber) {
            return System.currentTimeMillis();
        }
    }

    /** A network message forwarded here over plugin messaging, as it came. */
    void fromForward(byte[] data) {
        Envelope.Opened opened;
        try {
            opened = envelope.open(data);
        } catch (Envelope.Refused refused) {
            warn("A network message over plugin messaging was dropped: " + refused.getMessage()
                    + ". Is secret the same in every server's config.yml?");
            return;
        }
        if (opened.instance().equals(instance)) {
            return; // This server's own, back from the proxy.
        }
        if (opened.channel().equals(NetworkVariables.CHANNEL)) {
            notices.accept(opened.message());
            return;
        }
        if (!opened.target().equals("ALL") && !serverName.isEmpty() && !opened.target().equalsIgnoreCase(serverName)) {
            warn("A network message for " + opened.target() + " came to this server (" + serverName + "): dropped.");
            return;
        }
        deliver(new NetworkMessageEvent(NetworkMessageEvent.Kind.NETWORK, opened.channel(), opened.message(),
                opened.origin().isEmpty() ? null : opened.origin(), opened.sent()));
    }

    /** Messages arriving are dropped from now on: NeoSkriptProxy is disabled. */
    void stopDelivering() {
        delivering = false;
    }

    private volatile boolean delivering = true;

    private void deliver(NetworkMessageEvent event) {
        if (!delivering) {
            return;
        }
        long bytes = 64L + 2L * (event.message().length() + event.channel().length());
        if (pending.incrementAndGet() > MAX_PENDING || pendingBytes.addAndGet(bytes) > MAX_PENDING_BYTES) {
            pending.decrementAndGet();
            pendingBytes.addAndGet(-bytes);
            warn("Too many network messages are waiting for the server already (" + MAX_PENDING + ", or "
                    + MAX_PENDING_BYTES / (1024 * 1024) + " MB): new ones are dropped.");
            return;
        }
        global.accept(() -> {
            pending.decrementAndGet();
            pendingBytes.addAndGet(-bytes);
            fire.accept(event);
        });
    }

    /** Says a problem in the console, at most once a minute. */
    private void warn(String message) {
        long now = System.currentTimeMillis();
        if (now - warnedAt > 60_000) {
            warnedAt = now;
            logger.warning(message);
        }
    }

    /** How many messages wait to reach scripts. */
    int pending() {
        return pending.get();
    }
}
