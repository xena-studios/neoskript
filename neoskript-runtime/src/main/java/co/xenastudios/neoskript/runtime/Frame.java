/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.runtime;

import co.xenastudios.neoskript.runtime.variables.VariableStore;

/**
 * What a waiting trigger needs to carry on: which {@code wait} it stopped at, its local variables, and the
 * state of the loops around that {@code wait}. The generated code fills it in and reads it back.
 */
public final class Frame {

    public int state;
    public VariableStore locals;
    public Object parseLogs;
    public Object[] slots;
}
