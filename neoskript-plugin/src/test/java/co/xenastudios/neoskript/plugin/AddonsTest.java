/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import co.xenastudios.neoskript.api.registration.Registrar;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.slf4j.helpers.NOPLogger;

class AddonsTest {

    private static final Registrar REGISTRAR = (Registrar) Proxy.newProxyInstance(Registrar.class.getClassLoader(),
            new Class<?>[] {Registrar.class}, (proxy, method, args) -> null);

    @Test
    void registrationsRunInOrderWhenTheSyntaxIsRegistered() {
        Addons addons = new Addons(NOPLogger.NOP_LOGGER);
        List<String> ran = new ArrayList<>();
        addons.add("First", "1.0", registrar -> ran.add("First"));
        addons.add("Second", "2.0", registrar -> ran.add("Second"));
        assertEquals(List.of(), ran);
        addons.registerAll(REGISTRAR);
        assertEquals(List.of("First", "Second"), ran);
        assertEquals(List.of("First 1.0", "Second 2.0"), addons.names());
        // One added after that registers straight away.
        addons.add("Third", "", registrar -> ran.add("Third"));
        assertEquals(List.of("First", "Second", "Third"), ran);
    }

    @Test
    void oneThatThrowsDoesntStopTheOthers() {
        Addons addons = new Addons(NOPLogger.NOP_LOGGER);
        List<String> ran = new ArrayList<>();
        addons.add("Broken", "1.0", registrar -> {
            throw new IllegalArgumentException("broken");
        });
        addons.add("Fine", "1.0", registrar -> ran.add("Fine"));
        addons.registerAll(REGISTRAR);
        assertEquals(List.of("Fine"), ran);
    }

    @Test
    void namesAreUniqueAndNothingRegistersOnceScriptsHaveLoaded() {
        Addons addons = new Addons(NOPLogger.NOP_LOGGER);
        AtomicBoolean frozen = new AtomicBoolean();
        addons.frozenWhen(frozen::get);
        addons.add("Lottery", "1.0", registrar -> {});
        assertThrows(IllegalStateException.class, () -> addons.add("lottery", "2.0", registrar -> {}));
        frozen.set(true);
        assertThrows(IllegalStateException.class, () -> addons.add("Late", "1.0", registrar -> {}));
    }
}
