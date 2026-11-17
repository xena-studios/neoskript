/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package ch.njol.skript.lang;

import org.bukkit.event.Event;

/** A line of a trigger. */
public abstract class TriggerItem implements Debuggable {

    protected TriggerSection parent = null;
    private TriggerItem next = null;

    protected TriggerItem() {}

    protected TriggerItem(TriggerSection parent) {
        this.parent = parent;
    }

    protected TriggerItem walk(Event event) {
        return run(event) ? next : null;
    }

    protected abstract boolean run(Event event);

    /** Runs the items from {@code start}, each after the one before, as long as each says to carry on. */
    public static boolean walk(TriggerItem start, Event event) {
        TriggerItem item = start;
        while (item != null) {
            item = item.walk(event);
        }
        return true;
    }

    public String getIndentation() {
        return "";
    }

    protected final void debug(Event event, boolean run) {}

    @Override
    public final String toString() {
        return toString(null, false);
    }

    public TriggerItem setParent(TriggerSection parent) {
        this.parent = parent;
        return this;
    }

    public final TriggerSection getParent() {
        return parent;
    }

    public TriggerItem setNext(TriggerItem next) {
        this.next = next;
        return this;
    }

    public TriggerItem getNext() {
        return next;
    }

    public TriggerItem getActualNext() {
        return next;
    }
}
