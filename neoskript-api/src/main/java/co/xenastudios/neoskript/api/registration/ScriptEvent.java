/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.api.registration;

/** Events NeoSkript fires itself, rather than a server event. */
public enum ScriptEvent {
    /** When a script has loaded, including on reload. */
    LOAD,
    /** When a script unloads: before it reloads, when it's removed, and when the server stops. */
    UNLOAD
}
