/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.addons.proxy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * BungeeCord's plugin messaging, which Velocity speaks too: questions to the proxy (the servers, player counts,
 * where a player is), actions (send a player, kick, message) and network messages forwarded to other servers. A
 * plugin message travels through a player's connection, so with nobody online none can be sent: actions and
 * messages wait for a player (or are dropped, as configured), and questions fail at once.
 *
 * <p>Answers have no ids: they're matched to questions by what they're about, and the same question asked again
 * while it's waiting shares the one answer. An answer nobody asked for is dropped, so a client that sends plugin
 * messages can't fill anything in. Everything is bounded: the questions waiting, the messages queued, and the size
 * of what's read.
 *
 * <p>Its Bukkit side ({@link Carrier}) is apart, so this can be tested without a server.
 */
final class PluginMessaging {

    /** BungeeCord's channel, as plugins name it. */
    static final String CHANNEL = "BungeeCord";
    /** The most questions waiting for answers at once. */
    static final int MAX_WAITING = 1_000;
    /** The longest plugin message read: anything longer isn't BungeeCord's. */
    static final int MAX_READ = 256 * 1024;

    /** What answers "nothing": the proxy didn't answer in time. */
    static final Object NO_ANSWER = new Object() {
        @Override
        public String toString() {
            return "no answer";
        }
    };

    /** How plugin messages leave: through a player whose connection has the channel. */
    interface Carrier {
        /**
         * Sends a plugin message through a player, on the player's thread.
         *
         * @return whether a player could carry it
         */
        boolean send(byte[] payload);
    }

    /** A question to the proxy, and how its answer reads. */
    record Question(String subchannel, List<String> arguments, boolean alwaysAnswered) {

        /** What it's about: the answer's key. */
        String key() {
            return arguments.isEmpty() ? subchannel
                    : subchannel + ":" + arguments.getFirst().toLowerCase(Locale.ROOT);
        }

        byte[] payload() {
            return write(out -> {
                out.writeUTF(subchannel);
                for (String argument : arguments) {
                    out.writeUTF(argument);
                }
            });
        }

        static Question servers() {
            return new Question("GetServers", List.of(), true);
        }

        static Question serverName() {
            return new Question("GetServer", List.of(), true);
        }

        /** The players on a server, or {@code ALL}: answered only for a server the proxy knows. */
        static Question playerCount(String server) {
            return new Question("PlayerCount", List.of(server), server.equals("ALL"));
        }

        static Question playerList(String server) {
            return new Question("PlayerList", List.of(server), server.equals("ALL"));
        }

        /** Where a player is: answered only if they're online. */
        static Question playerServer(String player) {
            return new Question("GetPlayerServer", List.of(player), false);
        }

        static Question uuid(String player) {
            return new Question("UUIDOther", List.of(player), false);
        }

        static Question ip(String player) {
            return new Question("IPOther", List.of(player), false);
        }
    }

    /** A question waiting for its answer; compared by identity, so a timeout only ends its own. */
    private static final class Waiting {
        final Question question;
        final List<CompletableFuture<Object>> answers;

        Waiting(Question question, List<CompletableFuture<Object>> answers) {
            this.question = question;
            this.answers = answers;
        }

        Question question() {
            return question;
        }

        List<CompletableFuture<Object>> answers() {
            return answers;
        }
    }

    private record Queued(byte[] payload, long until) {}

    /** The most answers kept for {@link #cached}: past it, the oldest go. */
    static final int MAX_CACHED = 10_000;

    /** The last answer to each question, for the originals' syntax, which answers from it (see {@link #cached}). */
    private final Map<String, Object> answers = new java.util.LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Object> eldest) {
            return size() > MAX_CACHED;
        }
    };

    private final Settings settings;
    private final Carrier carrier;
    private final ScheduledExecutorService timer;
    private final Logger logger;
    private final Consumer<byte[]> forwarded;
    private final Map<String, Waiting> waiting = new HashMap<>();
    private final Deque<Queued> queued = new ArrayDeque<>();
    private volatile boolean closed;
    private volatile boolean warnedNoAnswer;
    private volatile boolean warnedQueueFull;

    /**
     * @param forwarded where network messages forwarded to this server go: the {@code Forward}'s data
     */
    PluginMessaging(Settings settings, Carrier carrier, ScheduledExecutorService timer, Logger logger,
            Consumer<byte[]> forwarded) {
        this.settings = settings;
        this.carrier = carrier;
        this.timer = timer;
        this.logger = logger;
        this.forwarded = forwarded;
    }

    @FunctionalInterface
    interface Writer {
        void write(DataOutputStream out) throws IOException;
    }

    static byte[] write(Writer writer) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(64);
            writer.write(new DataOutputStream(bytes));
            return bytes.toByteArray();
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    /**
     * Asks the proxy something. The stage completes with the answer, or {@link #NO_ANSWER} if none came in time,
     * or fails if nobody's online to carry the question.
     */
    CompletableFuture<Object> ask(Question question) {
        CompletableFuture<Object> answer = new CompletableFuture<>();
        if (closed) {
            return CompletableFuture.failedFuture(new IllegalStateException("NeoSkriptProxy is disabled"));
        }
        if (!settings.pluginMessaging()) {
            return CompletableFuture.failedFuture(off());
        }
        String key = question.key();
        Waiting asked;
        synchronized (waiting) {
            Waiting existing = waiting.get(key);
            if (existing != null) {
                existing.answers().add(answer);
                return answer;
            }
            if (waiting.size() >= MAX_WAITING) {
                return CompletableFuture.failedFuture(new IllegalStateException("Too many questions to the proxy are"
                        + " waiting (" + MAX_WAITING + ")"));
            }
            asked = new Waiting(question, new ArrayList<>(List.of(answer)));
            waiting.put(key, asked);
        }
        boolean sent;
        try {
            sent = carrier.send(question.payload());
        } catch (RuntimeException | LinkageError e) {
            fail(key, asked, new IllegalStateException("The question couldn't be sent: " + e.getMessage(), e));
            return answer;
        }
        if (!sent) {
            fail(key, asked, new IllegalStateException("Nobody is online, so the proxy can't be asked: plugin messages"
                    + " travel through a player's connection"));
            return answer;
        }
        try {
            timer.schedule(() -> timedOut(key, asked), settings.proxyTimeoutMillis(), TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.RejectedExecutionException stopping) {
            fail(key, asked, new IllegalStateException("NeoSkriptProxy is disabled"));
        }
        return answer;
    }

    private static IllegalStateException off() {
        return new IllegalStateException("Plugin messaging is off (plugin-messaging.enabled in"
                + " plugins/NeoSkriptProxy/config.yml), so the proxy can't be asked or told anything");
    }

    /**
     * The originals' way (SkProxy's, Skelocity's): the last answer to {@code question}, at once, with the question
     * asked again in the background so the next read has a fresher one. {@code null} until the first answer, which
     * the caller turns into what the original gives then.
     */
    Object cached(Question question) {
        if (!closed && settings.pluginMessaging()) {
            ask(question).exceptionally(failure -> null);
        }
        synchronized (answers) {
            return answers.get(question.key());
        }
    }

    /** Whether any answer to {@code question} has come: SkProxy's "Online". */
    boolean answered(Question question) {
        synchronized (answers) {
            return answers.containsKey(question.key());
        }
    }

    private void timedOut(String key, Waiting asked) {
        boolean removed;
        synchronized (waiting) {
            removed = waiting.remove(key, asked);
        }
        if (!removed) {
            return;
        }
        Waiting gone = asked;
        if (gone.question().alwaysAnswered() && !warnedNoAnswer) {
            warnedNoAnswer = true;
            logger.warning("The proxy didn't answer " + gone.question().subchannel() + " within "
                    + settings.proxyTimeoutMillis() + " ms. On Velocity, turn on bungee-plugin-message-channel in"
                    + " velocity.toml; on BungeeCord, check the server is behind it.");
        }
        gone.answers().forEach(answer -> answer.complete(NO_ANSWER));
    }

    private void fail(String key, Waiting asked, Throwable why) {
        boolean removed;
        synchronized (waiting) {
            removed = waiting.remove(key, asked);
        }
        if (removed) {
            asked.answers().forEach(answer -> answer.completeExceptionally(why));
        }
    }

    /**
     * Sends an action or a forwarded message: now, through a player, or once one's online.
     *
     * @return whether it was sent or queued; {@code false} if it was dropped
     */
    boolean send(byte[] payload) {
        if (closed || !settings.pluginMessaging()) {
            return false;
        }
        try {
            if (carrier.send(payload)) {
                return true;
            }
        } catch (RuntimeException | LinkageError e) {
            logger.warning("A plugin message couldn't be sent: " + e);
            return false;
        }
        if (!settings.queueWithoutPlayers() || settings.maxQueued() == 0) {
            return false;
        }
        synchronized (queued) {
            dropExpired();
            if (queued.size() >= settings.maxQueued()) {
                if (!warnedQueueFull) {
                    warnedQueueFull = true;
                    logger.warning("Nobody has been online to carry plugin messages, and " + settings.maxQueued()
                            + " are waiting already (plugin-messaging.max-queued): later ones are dropped.");
                }
                return false;
            }
            queued.add(new Queued(payload, System.currentTimeMillis() + settings.queuedForMillis()));
            return true;
        }
    }

    /** A player who can carry plugin messages is online: what waited for one is sent. */
    void carrierAvailable() {
        List<Queued> due = new ArrayList<>();
        synchronized (queued) {
            dropExpired();
            while (!queued.isEmpty()) {
                due.add(queued.poll());
            }
            warnedQueueFull = false;
        }
        for (int i = 0; i < due.size(); i++) {
            boolean sent;
            try {
                sent = carrier.send(due.get(i).payload());
            } catch (RuntimeException | LinkageError e) {
                sent = false;
            }
            if (!sent) {
                // Gone again: the rest wait for the next one, with the time they had left, ahead of what came since,
                // and no more than the queue holds.
                synchronized (queued) {
                    for (int j = due.size() - 1; j >= i; j--) {
                        if (queued.size() >= settings.maxQueued()) {
                            queued.removeLast();
                        }
                        queued.addFirst(due.get(j));
                    }
                    dropExpired();
                }
                return;
            }
        }
    }

    private void dropExpired() {
        long now = System.currentTimeMillis();
        queued.removeIf(waitingMessage -> waitingMessage.until() < now);
    }

    /** How many messages wait for a player. */
    int queued() {
        synchronized (queued) {
            return queued.size();
        }
    }

    /** How many questions wait for answers. */
    int waiting() {
        synchronized (waiting) {
            return waiting.size();
        }
    }

    /**
     * A plugin message on BungeeCord's channel arrived: an answer, or a network message forwarded here. Anything
     * else, or anything nobody asked for, is dropped.
     */
    void received(byte[] message) {
        if (closed || message.length > MAX_READ) {
            return;
        }
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(message));
            String subchannel = in.readUTF();
            switch (subchannel) {
                case Envelope.SUBCHANNEL -> {
                    int length = in.readUnsignedShort();
                    byte[] data = in.readNBytes(length);
                    if (data.length == length) {
                        forwarded.accept(data);
                    }
                }
                case "GetServers" -> answer("GetServers", list(in.readUTF()));
                case "GetServer" -> answer("GetServer", in.readUTF());
                case "PlayerCount" -> {
                    String server = in.readUTF();
                    answer("PlayerCount:" + server.toLowerCase(Locale.ROOT), (long) in.readInt());
                }
                case "PlayerList" -> {
                    String server = in.readUTF();
                    answer("PlayerList:" + server.toLowerCase(Locale.ROOT), list(in.readUTF()));
                }
                case "GetPlayerServer" -> {
                    String player = in.readUTF();
                    answer("GetPlayerServer:" + player.toLowerCase(Locale.ROOT), in.readUTF());
                }
                case "UUIDOther" -> {
                    String player = in.readUTF();
                    answer("UUIDOther:" + player.toLowerCase(Locale.ROOT), in.readUTF());
                }
                case "IPOther" -> {
                    String player = in.readUTF();
                    answer("IPOther:" + player.toLowerCase(Locale.ROOT), in.readUTF());
                }
                default -> {
                    // Another plugin's, or nothing NeoSkriptProxy asks.
                }
            }
        } catch (IOException garbled) {
            // Not what it says it is: dropped.
        }
    }

    private void answer(String key, Object value) {
        Waiting answered;
        synchronized (waiting) {
            answered = waiting.remove(key);
        }
        if (answered != null) {
            synchronized (answers) {
                answers.put(key, value);
            }
        }
        if (answered != null) {
            answered.answers().forEach(answer -> answer.complete(value));
        }
    }

    /** BungeeCord's lists are one text, joined with {@code ", "}; an empty one is none. */
    static List<String> list(String joined) {
        return joined.isEmpty() ? List.of() : Arrays.stream(joined.split(", ")).filter(s -> !s.isEmpty()).toList();
    }

    /** {@code Forward}: a network message to {@code server}, or {@code ALL} other servers. */
    static byte[] forward(String server, byte[] data) {
        return write(out -> {
            out.writeUTF("Forward");
            out.writeUTF(server);
            out.writeUTF(Envelope.SUBCHANNEL);
            out.writeShort(data.length);
            out.write(data);
        });
    }

    /** {@code Connect}/{@code ConnectOther}, {@code KickPlayer}, {@code Message}: an action with texts. */
    static byte[] action(String subchannel, String... arguments) {
        return write(out -> {
            out.writeUTF(subchannel);
            for (String argument : arguments) {
                out.writeUTF(argument);
            }
        });
    }

    /** Stops: questions waiting fail, and queued messages are dropped. */
    void close() {
        closed = true;
        List<Waiting> left;
        synchronized (waiting) {
            left = new ArrayList<>(waiting.values());
            waiting.clear();
        }
        IllegalStateException stopped = new IllegalStateException("NeoSkriptProxy is disabled");
        left.forEach(gone -> gone.answers().forEach(answer -> answer.completeExceptionally(stopped)));
        synchronized (queued) {
            queued.clear();
        }
    }
}
