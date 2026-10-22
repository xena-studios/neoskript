/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.runtime.generated;

import java.lang.invoke.MethodHandles;

/**
 * The package compiled triggers are defined in, as hidden classes. They can only be defined through a
 * lookup with full access to this package, which this class hands to the compiler.
 */
public final class TriggerHost {

    private TriggerHost() {}

    public static MethodHandles.Lookup lookup() {
        return MethodHandles.lookup();
    }
}
