/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.runtime;

import co.xenastudios.neoskript.api.element.TriggerContext;

/** Compiled code for an event, command or function body. The compiler generates the implementations. */
public interface Trigger {

    void execute(TriggerContext context);
}
