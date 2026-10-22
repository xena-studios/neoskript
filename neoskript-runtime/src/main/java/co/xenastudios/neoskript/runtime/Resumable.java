/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.runtime;

import co.xenastudios.neoskript.api.element.TriggerContext;

/** A compiled trigger that can wait: it carries on from {@code frame} when the wait is over. */
public interface Resumable {

    void resume(TriggerContext context, Frame frame);
}
