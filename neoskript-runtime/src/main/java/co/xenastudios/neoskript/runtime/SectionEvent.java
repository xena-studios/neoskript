/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.runtime;

/**
 * The event the code in an expression's section ({@code set {_bar} to a boss bar:}) is compiled for, as Skript
 * loads it for an event of the section's own: none of the trigger's event values can be used there, only the
 * value being made. Nothing fires it; the code runs in the trigger's own event.
 */
public final class SectionEvent {

    private SectionEvent() {}
}
