/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.plugin;

import co.xenastudios.neoskript.runtime.Trigger;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * The triggers of scripts being loaded that aren't listening yet, as Skript has them: it registers each event's
 * trigger in turn, in its load order (each script in turn, each trigger in the order of its lines), and runs each
 * {@code on load} when it gets to it, so what that does reaches only the triggers before it. The triggers of the
 * scripts that weren't loaded again listen all along.
 *
 * <p>Listeners ask {@link #pending} from any thread (on Folia, each region's), so the order is fixed when the walk
 * is made and only how far it has got changes.
 */
final class LoadWalk {

    /** Each trigger that waits its turn, by identity, with its place in the walk. */
    private final Map<Trigger, Integer> places;
    /** The place up to which triggers listen. */
    private volatile int reached;
    private volatile boolean finished;

    /**
     * @param order the triggers of the scripts being loaded, in Skript's load order
     * @param from the place of the first {@code on load}: triggers before it listen from the start
     */
    LoadWalk(List<Trigger> order, int from) {
        Map<Trigger, Integer> places = new IdentityHashMap<>();
        for (int i = from; i < order.size(); i++) {
            places.put(order.get(i), i);
        }
        this.places = places;
        this.reached = from;
    }

    /** Whether {@code trigger} doesn't listen yet, as the walk hasn't got to it. */
    boolean pending(Trigger trigger) {
        if (finished) {
            return false;
        }
        Integer place = places.get(trigger);
        return place != null && place >= reached;
    }

    /** The trigger at {@code place} listens from now on, and those before it. */
    void reach(int place) {
        if (place + 1 > reached) {
            reached = place + 1;
        }
    }

    /** Every trigger listens. */
    void finish() {
        finished = true;
    }
}
