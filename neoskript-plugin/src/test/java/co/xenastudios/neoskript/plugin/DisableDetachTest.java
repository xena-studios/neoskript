/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.plugin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Which plugins being disabled have their variables' values turned into saved bytes. NeoSkript itself never does:
 * it owns codecs, and forgetting them before its unload triggers ran lost what they set (the string set in
 * {@code on unload} in neoskript-addon wasn't saved).
 */
class DisableDetachTest {

    @Test
    void neoskriptItselfNeverDetaches() {
        assertFalse(NeoSkriptPlugin.detachesOnDisable(true, false, () -> true));
        assertFalse(NeoSkriptPlugin.detachesOnDisable(true, true, () -> true));
    }

    @Test
    void anAddonDetaches() {
        assertTrue(NeoSkriptPlugin.detachesOnDisable(false, true, () -> false));
    }

    @Test
    void aPluginWhoseClassesACodecSavesDetaches() {
        // A Skript addon (SkBee) whose types NeoSkriptCompat's codecs save.
        assertTrue(NeoSkriptPlugin.detachesOnDisable(false, false, () -> true));
    }

    @Test
    void anyOtherPluginDoesnt() {
        assertFalse(NeoSkriptPlugin.detachesOnDisable(false, false, () -> false));
    }
}
