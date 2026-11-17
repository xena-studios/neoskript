/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package org.skriptlang.skript.util;

import java.util.Collection;
import java.util.Set;

/** Where syntax goes in the order Skript tries it, relative to other priorities. */
public interface Priority extends Comparable<Priority> {

    static Priority base() {
        return co.xenastudios.neoskript.compat.CompatPriority.base();
    }

    static Priority before(Priority priority) {
        return co.xenastudios.neoskript.compat.CompatPriority.before(priority);
    }

    static Priority after(Priority priority) {
        return co.xenastudios.neoskript.compat.CompatPriority.after(priority);
    }

    Collection<Priority> after();

    Collection<Priority> before();
}
