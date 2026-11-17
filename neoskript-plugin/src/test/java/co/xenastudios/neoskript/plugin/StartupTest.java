/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.slf4j.helpers.NOPLogger;

class StartupTest {

    @Test
    void readyRunsEvenWhenStartingFails() {
        AtomicBoolean ready = new AtomicBoolean();
        // A codec whose classes can't load, during the first tick's start.
        Startup.run(() -> {
            throw new NoClassDefFoundError("org/example/OptionalPlugin");
        }, () -> ready.set(true), NOPLogger.NOP_LOGGER);
        assertTrue(ready.get());
    }

    @Test
    void aTasksOwnerIsTheFirstPluginPastNeoSkriptAndTheJdk() throws Exception {
        try (URLClassLoader neoskript = new URLClassLoader(new URL[0]);
                URLClassLoader addon = new URLClassLoader(new URL[0]);
                URLClassLoader addonLibraries = new URLClassLoader(new URL[0]);
                URLClassLoader other = new URLClassLoader(new URL[0])) {
            Map<ClassLoader, String> plugins = Map.of(neoskript, "NeoSkript", addon, "Addon", addonLibraries, "Addon");
            ClassLoader jdk = ClassLoader.getPlatformClassLoader();
            // threads::runGlobal called through List.forEach, from the addon.
            assertEquals("Addon", AddonApi.owner(Stream.of(neoskript, null, jdk, addon), neoskript, plugins::get));
            // From a library in the addon's libraries: loader.
            assertEquals("Addon", AddonApi.owner(Stream.of(neoskript, addonLibraries, addon), neoskript, plugins::get));
            // From nothing a plugin loaded.
            assertNull(AddonApi.owner(Stream.of(neoskript, other, jdk), neoskript, plugins::get));
        }
    }
}
