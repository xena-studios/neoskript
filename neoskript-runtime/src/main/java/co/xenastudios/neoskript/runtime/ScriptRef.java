/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.runtime;

/**
 * Skript's {@code script}: a script file, by its name, which is its path relative to the scripts folder
 * ({@code quests/main.sk}). It reads as its name.
 */
public record ScriptRef(String name) {

    @Override
    public String toString() {
        return name;
    }
}
