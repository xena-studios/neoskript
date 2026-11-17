/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.addons.reflect.fixture;

/** A class one of whose methods names {@link Missing}: loaded without it, its methods can't be looked at. */
public final class Broken {

    public static String ok() {
        return "ok";
    }

    public static Missing missing() {
        return null;
    }
}
