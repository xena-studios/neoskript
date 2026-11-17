/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.api;

/** Where the running NeoSkript is kept for {@link NeoSkript#get()}. */
final class Installed {

    static volatile NeoSkript instance;

    private Installed() {}
}
