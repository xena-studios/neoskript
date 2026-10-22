/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.runtime;

import co.xenastudios.neoskript.api.element.TriggerContext;

/**
 * @param owner what the event happened to, so a {@code wait} carries on on its thread on Folia: an entity,
 *     a location, or {@code null} for the global thread
 */
public record SimpleTriggerContext(Object event, Object owner) implements TriggerContext {

    public SimpleTriggerContext(Object event) {
        this(event, null);
    }
}
