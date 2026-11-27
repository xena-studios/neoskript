/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.addons.proxy;

import co.xenastudios.neoskript.api.VariableDatabase;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * {@code {network::...}} variables. They're kept in a database of NeoSkript's {@code databases} section that every
 * server shares: MySQL with {@code monitor-changes: true}, whose {@code pattern} takes {@code network::} names. That
 * database is what holds them, saves them and merges two servers' changes to one (an addition on each counts both;
 * see NeoSkript's SQL storage). What this adds: each time this server writes to it, the other servers are told, over
 * Redis or plugin messaging, and read the change at once instead of at their next {@code monitor-interval}. Only the
 * database's id travels, never a value, so a notice that's lost, late or forged costs at most an extra read.
 */
final class NetworkVariables {

    /** The network channel notices go on: NeoSkriptProxy's own, never handed to scripts. */
    static final String CHANNEL = "neoskript:network-variables";
    /** The least time between two notices this server sends. */
    static final long GAP_MILLIS = 100;
    /** Where the docs explain the setup. */
    static final String DOCS = "https://neoskript.dev/docs/addons/proxy/#network-variables";

    private final Settings settings;
    private final Logger logger;
    private final Network network;
    private final ScheduledExecutorService timer;
    private volatile VariableDatabase database;
    private volatile String id;
    private volatile Runnable stop = () -> {};
    private final AtomicBoolean queued = new AtomicBoolean();
    private volatile long sentAt;
    private final AtomicLong sent = new AtomicLong();
    private final AtomicLong heard = new AtomicLong();

    NetworkVariables(Settings settings, Logger logger, Network network, ScheduledExecutorService timer) {
        this.settings = settings;
        this.logger = logger;
        this.network = network;
        this.timer = timer;
    }

    /**
     * Finds the database named in config.yml among NeoSkript's, and starts telling the other servers about writes to
     * it. Says in the console what's missing if it can't be used: network variables are then saved as any other
     * variable is, on this server only.
     */
    void start(List<VariableDatabase> databases) {
        String wanted = settings.networkDatabase();
        VariableDatabase found = null;
        for (VariableDatabase candidate : databases) {
            if (candidate.name().equalsIgnoreCase(wanted)) {
                found = candidate;
                break;
            }
        }
        if (found == null) {
            logger.info("NeoSkript's config.yml has no database named '" + wanted + "', so {network::...} variables"
                    + " are saved like any other, on this server only. To share them across the network, add one: "
                    + DOCS);
            return;
        }
        if (!takesNetworkNames(found.pattern())) {
            logger.warning("Database '" + found.name() + "' in NeoSkript's config.yml has the pattern '"
                    + found.pattern() + "', which doesn't take {network::...} variables: give it the pattern"
                    + " network::.* and put it above the others. " + DOCS);
        }
        if (!found.shared()) {
            logger.warning("Database '" + found.name() + "' in NeoSkript's config.yml isn't a MySQL database with"
                    + " monitor-changes: true, so other servers don't see the changes made here. " + DOCS);
            return;
        }
        database = found;
        id = found.id();
        if (!settings.networkNotify()) {
            return;
        }
        if (!network.canNotify()) {
            logger.info("Network variables: other servers see this one's changes every monitor-interval of database '"
                    + found.name() + "'. Turn on Redis, or set a secret for plugin messaging, and they see them at"
                    + " once.");
        }
        stop = found.whenWritten(this::written);
    }

    /** Whether {@code pattern} matches {@code network::} names. */
    static boolean takesNetworkNames(String pattern) {
        if (pattern.equals(".*") || pattern.equals(".+")) {
            return true;
        }
        try {
            return Pattern.compile(pattern).matcher("network::x").matches();
        } catch (PatternSyntaxException e) {
            return false;
        }
    }

    /** This server wrote to the database, on the database's thread: the others are told, off it, soon. */
    private void written() {
        if (!queued.compareAndSet(false, true)) {
            return;
        }
        long wait = Math.max(0, sentAt + GAP_MILLIS - System.currentTimeMillis());
        try {
            timer.schedule(() -> {
                queued.set(false);
                sentAt = System.currentTimeMillis();
                String which = id;
                if (which != null && network.notice(which)) {
                    sent.incrementAndGet();
                }
            }, wait, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException closed) {
            queued.set(false);
        }
    }

    /** Another server says it wrote to a database: if it's this one's, it's read now. */
    void heard(String message) {
        VariableDatabase shared = database;
        String which = id;
        if (shared != null && which != null && which.equals(message.trim().toLowerCase(Locale.ROOT))) {
            heard.incrementAndGet();
            shared.checkForChanges();
        }
    }

    /** Whether network variables are shared and their database answers now. */
    boolean available() {
        VariableDatabase shared = database;
        return shared != null && shared.available();
    }

    /** Whether network variables are shared at all: their database is set up for it. */
    boolean shared() {
        return database != null;
    }

    /** Notices sent and heard, for tests. */
    long[] counts() {
        return new long[] {sent.get(), heard.get()};
    }

    /** Stops telling the other servers. */
    void stop() {
        stop.run();
        database = null;
    }
}
