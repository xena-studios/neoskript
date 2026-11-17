/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.addons.proxy;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.logging.Logger;

/**
 * Redis pub/sub: one connection publishes, from a queue on a thread of its own, and another listens to every
 * channel ({@code PSUBSCRIBE *}, as SkRedis does). Neither ever runs on a server thread. While Redis is down,
 * messages wait in the queue (or are dropped, as configured), both connections try again with a growing pause, up
 * to 30 seconds, and the console says so once each time it goes down and comes back. Messages published while the
 * listener was down are missed: that's pub/sub.
 */
final class RedisLink {

    private record Outgoing(String channel, String payload, long until) {}

    private final Settings settings;
    private final Logger logger;
    private final BiConsumer<String, String> received;
    private final Deque<Outgoing> queue = new ArrayDeque<>();
    private final Thread publisher;
    private final Thread subscriber;
    private volatile boolean closed;
    private volatile boolean publisherUp;
    private volatile boolean subscriberUp;
    private volatile Resp listening;
    private volatile boolean warnedFull;

    /**
     * @param received called with each message's channel and text, on the listener's thread
     */
    RedisLink(Settings settings, Logger logger, BiConsumer<String, String> received) {
        this.settings = settings;
        this.logger = logger;
        this.received = received;
        this.publisher = new Thread(this::publish, "NeoSkriptProxy-redis-publish");
        this.subscriber = new Thread(this::listen, "NeoSkriptProxy-redis-listen");
        publisher.setDaemon(true);
        subscriber.setDaemon(true);
    }

    void start() {
        publisher.start();
        subscriber.start();
    }

    /** Whether both connections are up. */
    boolean up() {
        return publisherUp && subscriberUp;
    }

    /**
     * Publishes a message, soon, from the publishing thread.
     *
     * @return whether it was queued; {@code false} if it was dropped (Redis is down and messages aren't kept, or
     *     too many wait)
     */
    boolean publish(String channel, String payload) {
        if (closed) {
            return false;
        }
        synchronized (queue) {
            if (!publisherUp && !settings.redisQueueOnOutage()) {
                return false;
            }
            dropExpired();
            if (queue.size() >= Math.max(settings.redisMaxQueued(), 1)) {
                if (!warnedFull) {
                    warnedFull = true;
                    logger.warning(settings.redisMaxQueued() + " messages are waiting for Redis already"
                            + " (redis.max-queued): later ones are dropped until it catches up.");
                }
                return false;
            }
            queue.add(new Outgoing(channel, payload, System.currentTimeMillis() + settings.redisQueuedForMillis()));
            queue.notifyAll();
            return true;
        }
    }

    /** How many messages wait to be published. */
    int queued() {
        synchronized (queue) {
            return queue.size();
        }
    }

    private void dropExpired() {
        long now = System.currentTimeMillis();
        queue.removeIf(outgoing -> outgoing.until() < now);
    }

    private Resp connect(int readTimeout) throws IOException {
        int timeout = (int) settings.redisTimeoutMillis();
        return Resp.connect(settings.redisHost(), settings.redisPort(), settings.redisSsl(), timeout, readTimeout,
                settings.redisUser(), settings.redisPassword(), settings.redisDatabase());
    }

    private void publish() {
        long pause = 1_000;
        while (!closed) {
            try (Resp connection = connect((int) settings.redisTimeoutMillis())) {
                connection.call("PING");
                if (!publisherUp) {
                    publisherUp = true;
                    logger.info("Connected to Redis at " + settings.redisHost() + ":" + settings.redisPort()
                            + " to publish.");
                }
                pause = 1_000;
                while (!closed) {
                    Outgoing next;
                    synchronized (queue) {
                        dropExpired();
                        while (queue.isEmpty() && !closed) {
                            queue.wait(5_000);
                            dropExpired();
                        }
                        if (closed) {
                            return;
                        }
                        next = queue.peek();
                    }
                    connection.call("PUBLISH", next.channel(), next.payload());
                    synchronized (queue) {
                        if (queue.peek() == next) {
                            queue.poll();
                        }
                        warnedFull = false;
                        queue.notifyAll();
                    }
                }
            } catch (IOException e) {
                if (publisherUp || pause == 1_000) {
                    logger.warning("Can't publish to Redis at " + settings.redisHost() + ":" + settings.redisPort()
                            + ": " + e.getMessage() + (settings.redisQueueOnOutage()
                            ? ". Messages wait until it's back." : ". Messages are dropped until it's back."));
                }
                publisherUp = false;
                if (!settings.redisQueueOnOutage()) {
                    synchronized (queue) {
                        queue.clear();
                    }
                }
                pause = sleep(pause);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    private void listen() {
        long pause = 1_000;
        while (!closed) {
            // A read times out after 30 s; a ping every 10 s keeps a live connection talking.
            try (Resp connection = connect(30_000)) {
                listening = connection;
                connection.send("PSUBSCRIBE", "*");
                Object confirmed = connection.read();
                if (!(confirmed instanceof List<?>)) {
                    throw new IOException("Redis didn't confirm the subscription: " + confirmed);
                }
                if (!subscriberUp) {
                    subscriberUp = true;
                    logger.info("Listening to Redis at " + settings.redisHost() + ":" + settings.redisPort() + ".");
                }
                pause = 1_000;
                long lastPing = System.nanoTime();
                while (!closed) {
                    if (System.nanoTime() - lastPing > TimeUnit.SECONDS.toNanos(10)) {
                        connection.send("PING");
                        lastPing = System.nanoTime();
                    }
                    connection.timeout(10_000);
                    Object reply;
                    try {
                        reply = connection.read();
                    } catch (java.net.SocketTimeoutException quiet) {
                        // Nothing for 10 s: a ping, then wait again; a dead connection fails the ping or the read.
                        connection.send("PING");
                        lastPing = System.nanoTime();
                        connection.timeout(30_000);
                        continue;
                    }
                    if (reply instanceof List<?> parts && parts.size() == 4 && "pmessage".equals(parts.get(0))
                            && parts.get(2) instanceof String channel && parts.get(3) instanceof String text) {
                        try {
                            received.accept(channel, text);
                        } catch (RuntimeException e) {
                            logger.warning("A Redis message on " + channel + " couldn't be handled: " + e);
                        }
                    }
                }
            } catch (IOException e) {
                if (!closed && (subscriberUp || pause == 1_000)) {
                    logger.warning("Can't listen to Redis at " + settings.redisHost() + ":" + settings.redisPort()
                            + ": " + e.getMessage() + ". Messages sent meanwhile are missed.");
                }
                subscriberUp = false;
                listening = null;
                pause = sleep(pause);
            }
        }
    }

    private long sleep(long pause) {
        try {
            synchronized (queue) {
                if (!closed) {
                    queue.wait(pause);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return Math.min(pause * 2, 30_000);
    }

    /**
     * Stops: what's queued gets up to {@code graceMillis} to be published, then both connections close and their
     * threads end.
     */
    void close(long graceMillis) {
        long deadline = System.currentTimeMillis() + graceMillis;
        synchronized (queue) {
            while (!queue.isEmpty() && publisherUp && System.currentTimeMillis() < deadline) {
                try {
                    queue.wait(Math.max(1, Math.min(50, deadline - System.currentTimeMillis())));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            closed = true;
            queue.clear();
            queue.notifyAll();
        }
        Resp current = listening;
        if (current != null) {
            current.close();
        }
        publisher.interrupt();
        subscriber.interrupt();
        try {
            publisher.join(1_000);
            subscriber.join(1_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
