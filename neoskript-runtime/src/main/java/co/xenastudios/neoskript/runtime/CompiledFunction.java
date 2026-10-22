/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.runtime;

import co.xenastudios.neoskript.api.element.TriggerContext;

/** A script function's compiled body. Arguments are by parameter, {@code null} where left out. */
public interface CompiledFunction {

    Object call(TriggerContext context, Object[] arguments);
}
