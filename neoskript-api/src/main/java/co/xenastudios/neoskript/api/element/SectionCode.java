/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.api.element;

/**
 * The code under a {@link LaterSection}, to run later for an event the addon fires itself, as a GUI slot's code runs
 * when it's clicked. Addons are given these; they don't implement them.
 *
 * <p>It runs with the trigger's local variables as they were when the section was reached: one copy, which every
 * run shares, as Skript's sections that run later do, so what one run sets the next sees.
 */
public interface SectionCode {

    /**
     * Runs the code now, on this thread, for {@code event}, until it ends or waits. A runtime error in it is logged as
     * any trigger's is. It can be called from any thread; on Folia, NeoSkript's region rules apply to what it reads
     * and changes, as for any trigger. It may run again from inside itself; limiting that is the caller's.
     *
     * @param event the event, which the code's event values and syntax read
     * @param owner the entity, block or location whose thread the code carries on on after a {@code wait} (the
     *     region's on Folia), or {@code null} for the global thread
     */
    void run(Object event, Object owner);

    /**
     * The script the code is in: when that script reloads or unloads ({@code Registrar#scriptsUnloaded}), this code
     * is its old version.
     *
     * @return the script's name, as {@code menus/shop.sk}
     */
    String script();
}
