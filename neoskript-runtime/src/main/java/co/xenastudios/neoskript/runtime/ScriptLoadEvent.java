/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.runtime;

/**
 * The event a script's {@code on load} and {@code on unload} triggers run in, as Skript's {@code ScriptEvent}:
 * its {@code event-sender} is the console.
 *
 * @param script the script's name
 * @param load whether the script is loading rather than unloading
 */
public record ScriptLoadEvent(String script, boolean load) {}
