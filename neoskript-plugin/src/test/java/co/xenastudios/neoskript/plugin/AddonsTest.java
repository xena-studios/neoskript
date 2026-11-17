/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import co.xenastudios.neoskript.api.registration.Registrar;
import co.xenastudios.neoskript.api.registration.ThreadAffinity;
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
    void theSyntaxExportIsToldWhichAddonIsRegistering() {
        Addons addons = new Addons(NOPLogger.NOP_LOGGER);
        List<String> told = new ArrayList<>();
        addons.registeringWith(name -> told.add(String.valueOf(name)));
        addons.add("Lottery", "1.0", registrar -> told.add("registers"));
        addons.add("Broken", "1.0", registrar -> {
            throw new IllegalStateException("broken");
        });
        addons.registerAll(REGISTRAR);
        // Ended even when the registration fails, so what's registered after it isn't named for it.
        assertEquals(List.of("Lottery", "registers", "null", "Broken", "null"), told);
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

    @Test
    void anAddonSetsTheAffinityOfItsOwnElementsOnly() {
        Addons addons = new Addons(NOPLogger.NOP_LOGGER);
        // NeoSkript's own: EffMessage, and its variants.
        addons.knownWhen(id -> id.equals("EffMessage"));
        List<String> errors = new ArrayList<>();
        addons.add("Lottery", "1.0", registrar -> {
            registrar.effect("LotteryEffDraw", info -> null, "draw");
            registrar.affinity("LotteryEffDraw", ThreadAffinity.ANY);
            registrar.condition("EffMessage", info -> null, "x");
            for (String id : List.of("Lottery-Eff", "LotteryEffOther", "EffMessage")) {
                try {
                    registrar.affinity(id, ThreadAffinity.ANY);
                } catch (IllegalArgumentException refused) {
                    errors.add(id);
                }
            }
        });
        addons.registerAll(REGISTRAR);
        assertEquals(List.of("Lottery-Eff", "LotteryEffOther", "EffMessage"), errors);
    }

    @Test
    void anAddonSetsThePriorityOfItsOwnElementsOnly() {
        Addons addons = new Addons(NOPLogger.NOP_LOGGER);
        addons.knownWhen(id -> id.equals("ExprTagKey"));
        List<String> errors = new ArrayList<>();
        addons.add("Lottery", "1.0", registrar -> {
            registrar.expression("LotteryExprKeys", "string", info -> null, "keys of %objects%");
            registrar.priority("LotteryExprKeys", co.xenastudios.neoskript.api.registration.SyntaxPriority.SIMPLE);
            for (String id : List.of("ExprTagKey", "LotteryExprOther")) {
                try {
                    registrar.priority(id, co.xenastudios.neoskript.api.registration.SyntaxPriority.SIMPLE);
                } catch (IllegalArgumentException refused) {
                    errors.add(id);
                }
            }
        });
        addons.registerAll(REGISTRAR);
        assertEquals(List.of("ExprTagKey", "LotteryExprOther"), errors);
    }

    @Test
    void anAddonCantReplaceACodecNeoSkriptHas() {
        Addons addons = new Addons(NOPLogger.NOP_LOGGER);
        addons.savedWhen(type -> type == String.class);
        List<String> errors = new ArrayList<>();
        addons.add("Lottery", "1.0", registrar -> {
            try {
                registrar.storage("lottery:text", String.class, null);
            } catch (IllegalArgumentException refused) {
                errors.add("String");
            }
            registrar.storage("lottery:ticket", Long.class, null);
        });
        addons.registerAll(REGISTRAR);
        assertEquals(List.of("String"), errors);
    }

    @Test
    void anAddonMakesOnlyItsOwnTypesStrict() {
        Addons addons = new Addons(NOPLogger.NOP_LOGGER);
        List<String> errors = new ArrayList<>();
        addons.add("Lottery", "1.0", registrar -> {
            registrar.type("lotteryticket", "lottery ticket", Long.class);
            registrar.strictType("lotteryticket");
            try {
                registrar.strictType("location");
            } catch (IllegalArgumentException refused) {
                errors.add("location");
            }
        });
        addons.registerAll(REGISTRAR);
        assertEquals(List.of("location"), errors);
    }

    @Test
    void anAddonsEntryGoesWhenItsPluginDisables() {
        Addons addons = new Addons(NOPLogger.NOP_LOGGER);
        addons.add("Lottery", "1.0", registrar -> {}, "LotteryPlugin");
        addons.add("Other", "2.0", registrar -> {}, "OtherPlugin");
        assertEquals(java.util.Set.of("LotteryPlugin", "OtherPlugin"), addons.plugins());
        addons.remove("LotteryPlugin");
        assertEquals(List.of("Other 2.0"), addons.names());
        assertEquals(java.util.Set.of("OtherPlugin"), addons.plugins());
    }
}
