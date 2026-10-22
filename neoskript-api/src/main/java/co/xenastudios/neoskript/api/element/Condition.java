/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.api.element;

/** A condition. Arguments are passed as for {@link Effect}. */
@FunctionalInterface
public interface Condition {

    boolean check(TriggerContext context, Object[] arguments);
}
