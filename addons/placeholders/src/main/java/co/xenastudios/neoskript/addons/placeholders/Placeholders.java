/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.addons.placeholders;

import co.xenastudios.neoskript.api.registration.LoadedTrigger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

/**
 * The placeholders scripts register, by prefix, and the expansions registered for them.
 *
 * <p>NeoSkript says which placeholder triggers are loaded each time scripts load, reload or unload ({@link #update}).
 * The lookup by prefix switches over at once, and NeoSkript runs nothing for a trigger whose script has unloaded,
 * so a request never runs unloaded code; the expansions follow on
 * the global thread, where PlaceholderAPI wants them registered: one for each prefix in use, and none for a prefix
 * no script uses any more, so nothing is left behind.
 *
 * <p>A request ({@link #request}) runs on the thread that asked, whatever it is, and never waits for another: the
 * triggers for the prefix run there, in script order, until one gives a result. NeoSkript's region rules apply to
 * what they read, as for any trigger.
 */
final class Placeholders {

    /** How deep placeholders may ask for placeholders, as one that asks for itself would forever. */
    static final int MAX_DEPTH = 16;
    /**
     * How many triggers one request may run, the placeholders it asks for included: depth alone doesn't bound the
     * work, as a placeholder asking for itself twice runs 2^16 triggers in 16 levels.
     */
    static final int MAX_RUNS = 256;

    /** Where expansions are registered: PlaceholderAPI, or a stand-in in tests. */
    interface Expansions {

        /**
         * Registers an expansion that answers placeholders with {@code prefix} through {@code placeholders}.
         *
         * @param prefix the prefix, as the first script that uses it writes it
         * @param placeholders what answers its requests
         * @return what to unregister it with, or {@code null} if it couldn't be registered
         */
        Object register(String prefix, Placeholders placeholders);

        /**
         * Unregisters an expansion.
         *
         * @param expansion what {@link #register} returned
         */
        void unregister(Object expansion);
    }

    /** One trigger's part in the placeholders of a prefix. */
    record Entry(String prefix, boolean relational, LoadedTrigger trigger) {}

    private final Expansions expansions;
    private final Consumer<Runnable> onGlobalThread;
    private final BooleanSupplier async;
    private final Logger logger;
    /** The triggers for each prefix, by the prefix in lower case, as PlaceholderAPI looks them up. */
    private volatile Map<String, List<Entry>> byPrefix = Map.of();
    /** The expansions registered, by prefix in lower case. Only changed on the global thread, in {@link #apply}. */
    private final Map<String, Object> registered = new HashMap<>();
    private boolean closed;
    /** For this thread's request: how deep placeholders are asking for placeholders, and the triggers run. */
    private final ThreadLocal<int[]> depth = ThreadLocal.withInitial(() -> new int[2]);
    private final AtomicLong lastWarning = new AtomicLong(Long.MIN_VALUE);

    /**
     * @param expansions where expansions are registered
     * @param onGlobalThread runs a task on the global thread: now if this is it, else soon
     * @param async whether this thread is off the server's threads, for the events' {@code isAsynchronous}
     * @param logger the addon's logger
     */
    Placeholders(Expansions expansions, Consumer<Runnable> onGlobalThread, BooleanSupplier async, Logger logger) {
        this.expansions = expansions;
        this.onGlobalThread = onGlobalThread;
        this.async = async;
        this.logger = logger;
    }

    /**
     * The placeholder triggers loaded now, as NeoSkript gives them.
     *
     * @param triggers the loaded triggers of the request event, in script order
     */
    void update(List<LoadedTrigger> triggers) {
        Map<String, List<Entry>> next = new LinkedHashMap<>();
        for (LoadedTrigger trigger : triggers) {
            if (trigger.filter() instanceof RequestFilter filter) {
                for (String prefix : filter.prefixes()) {
                    next.computeIfAbsent(prefix.toLowerCase(Locale.ROOT), key -> new ArrayList<>())
                            .add(new Entry(prefix, filter.relational(), trigger));
                }
            }
        }
        next.replaceAll((prefix, entries) -> List.copyOf(entries));
        synchronized (this) {
            if (closed) {
                return;
            }
            byPrefix = Map.copyOf(next);
        }
        onGlobalThread.accept(this::apply);
    }

    /** Registers and unregisters expansions to match the prefixes in use. On the global thread. */
    synchronized void apply() {
        Map<String, List<Entry>> wanted = byPrefix;
        for (var iterator = registered.entrySet().iterator(); iterator.hasNext(); ) {
            var expansion = iterator.next();
            if (!wanted.containsKey(expansion.getKey())) {
                iterator.remove();
                unregister(expansion.getKey(), expansion.getValue());
            }
        }
        for (var prefix : wanted.entrySet()) {
            if (!registered.containsKey(prefix.getKey())) {
                String written = prefix.getValue().getFirst().prefix();
                try {
                    Object expansion = expansions.register(written, this);
                    if (expansion != null) {
                        registered.put(prefix.getKey(), expansion);
                    } else {
                        logger.warning("PlaceholderAPI refused the placeholders with the prefix \"" + written + "\"");
                    }
                } catch (RuntimeException | LinkageError e) {
                    logger.log(Level.WARNING, "Couldn't register the placeholders with the prefix \"" + written + "\"",
                            e);
                }
            }
        }
    }

    /** Unregisters every expansion, as the addon disables. Later updates change nothing. */
    synchronized void close() {
        closed = true;
        byPrefix = Map.of();
        apply();
    }

    /** The prefixes with an expansion registered, in lower case. */
    synchronized java.util.Set<String> registered() {
        return java.util.Set.copyOf(registered.keySet());
    }

    private void unregister(String prefix, Object expansion) {
        try {
            expansions.unregister(expansion);
        } catch (RuntimeException | LinkageError e) {
            logger.log(Level.WARNING, "Couldn't unregister the placeholders with the prefix \"" + prefix + "\"", e);
        }
    }

    /**
     * Answers a placeholder: {@code %prefix_params%} for {@code player}.
     *
     * @param prefix the expansion's prefix, in lower case
     * @param params what follows the prefix and {@code _}
     * @param player who it's for, or {@code null}
     * @return the value, or {@code null} for none, which leaves the placeholder as it is
     */
    String request(String prefix, String params, OfflinePlayer player) {
        return answer(prefix, false, entry -> new PlaceholderRequestEvent(entry.prefix() + "_" + params, player,
                async.getAsBoolean()), player == null ? null : player.getPlayer());
    }

    /**
     * Answers a relational placeholder: {@code %rel_prefix_params%} for two players.
     *
     * @param prefix the expansion's prefix, in lower case
     * @param params what follows the prefix and {@code _}
     * @param first the first player
     * @param second the second player
     * @return the value, or {@code null} for none
     */
    String requestRelational(String prefix, String params, Player first, Player second) {
        if (first == null || second == null) {
            return null;
        }
        return answer(prefix, true, entry -> new RelationalPlaceholderRequestEvent(entry.prefix() + "_" + params,
                first, second, async.getAsBoolean()), first);
    }

    private String answer(String prefix, boolean relational,
            java.util.function.Function<Entry, PlaceholderRequestEvent> event, Player owner) {
        List<Entry> entries = prefix == null ? null : byPrefix.get(prefix.toLowerCase(Locale.ROOT));
        if (entries == null) {
            return null;
        }
        int[] level = depth.get();
        if (level[0] >= MAX_DEPTH) {
            warn("Placeholders with the prefix \"" + prefix + "\" asked for placeholders " + MAX_DEPTH
                    + " deep, so the last one got no value: does a placeholder ask for itself?");
            return null;
        }
        if (level[0] == 0) {
            level[1] = 0;
        }
        level[0]++;
        try {
            for (Entry entry : entries) {
                if (entry.relational() != relational) {
                    continue;
                }
                if (level[1] >= MAX_RUNS) {
                    warn("A request for placeholders with the prefix \"" + prefix + "\" ran " + MAX_RUNS
                            + " placeholder triggers, so the rest got no value: do placeholders ask for each other?");
                    return null;
                }
                level[1]++;
                PlaceholderRequestEvent request = event.apply(entry);
                try {
                    entry.trigger().run(request, owner);
                } catch (RuntimeException | LinkageError | StackOverflowError e) {
                    warn("The placeholder trigger at " + entry.trigger().script() + ", line " + entry.trigger().line()
                            + " failed: " + e);
                    continue;
                }
                String result = request.result();
                if (result != null) {
                    return result;
                }
            }
            return null;
        } finally {
            level[0]--;
        }
    }

    /** A warning, at most once a minute: a placeholder can be asked for many times a second. */
    void warn(String message) {
        long now = System.nanoTime();
        long last = lastWarning.get();
        if ((last == Long.MIN_VALUE || now - last >= 60_000_000_000L) && lastWarning.compareAndSet(last, now)) {
            logger.warning(message);
        }
    }
}
