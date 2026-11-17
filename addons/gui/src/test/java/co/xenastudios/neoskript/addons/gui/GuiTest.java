/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.addons.gui;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.xenastudios.neoskript.api.element.SectionCode;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

class GuiTest {

    private final Guis guis = new Guis(() -> null, Logger.getLogger("test"));
    /** Scripts whose code isn't the loaded version any more. */
    private static final java.util.Set<String> stale = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** A chest inventory of {@code size} slots, without a server: what slots hold, by index. */
    private static Inventory inventory(int size) {
        Object[] slots = new Object[size];
        return (Inventory) Proxy.newProxyInstance(Inventory.class.getClassLoader(), new Class<?>[] {Inventory.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getSize" -> size;
                    case "getType" -> InventoryType.CHEST;
                    case "getItem" -> slots[(int) args[0]];
                    case "setItem" -> {
                        slots[(int) args[0]] = args[1];
                        yield null;
                    }
                    case "clear" -> {
                        java.util.Arrays.fill(slots, null);
                        yield null;
                    }
                    case "getViewers" -> new ArrayList<>();
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> null;
                });
    }

    private static ItemStack item() {
        // An ItemStack needs a server for its material; the GUI only stores and clones what it's given.
        return null;
    }

    @Test
    void codeOfTheLoadedVersionStaysThoughItsScriptReloaded() {
        // Made by the new version before addons heard of the reload: its code is loaded, and it stays.
        Gui gui = gui(9, null);
        gui.setItem(0L, item(), false, code("menus/new.sk"));
        guis.scriptsUnloaded(Set.of("menus/new.sk"));
        assertSame(gui, guis.of(gui.inventory()));
    }

    @Test
    void aShiftClickIntoAGuiWithALockedSlotIsKnownToHaveOne() {
        Gui gui = gui(9, null);
        assertTrue(gui.anyLocked());
        gui.setChangeable(true);
        assertFalse(gui.anyLocked());
        gui.setItem(3L, item(), false, code("a.sk"));
        assertTrue(gui.anyLocked());
    }

    private static SectionCode code(String script) {
        return new SectionCode() {
            @Override
            public void run(Object event, Object owner) {}

            @Override
            public String script() {
                return script;
            }

            @Override
            public boolean loaded() {
                return !stale.contains(script);
            }
        };
    }

    private Gui gui(int size, String layout) {
        Gui gui = new Gui(guis, inventory(size), false, Component.text("Test"), layout);
        guis.register(gui);
        return gui;
    }

    @Test
    void withoutALayoutEachSlotIsItsOwnCharacter() {
        Gui gui = gui(9, null);
        assertEquals("ABCDEFGHI", gui.layout());
        assertEquals('C', gui.convert(2L));
        assertEquals(Gui.UNKNOWN_SLOT, gui.convert(9L));
        assertEquals(Gui.UNKNOWN_SLOT, gui.convert(-1L));
        assertEquals('A', gui.nextSlot());
    }

    @Test
    void aShortLayoutIsFilledWithSlotsTheLayoutDoesntHave() {
        Gui gui = gui(18, "xxxxxxxxx-");
        assertEquals("xxxxxxxxx-" + " ".repeat(8), gui.layout());
        assertEquals('x', gui.convert("x"));
        assertEquals(Gui.UNKNOWN_SLOT, gui.convert("y"));
    }

    @Test
    void nextAndLastSlotsFollowWhatsMade() {
        Gui gui = gui(9, "abcabcabc");
        assertEquals('a', gui.nextSlot());
        gui.setItem("a", item(), false, null);
        gui.setItem("c", item(), false, null);
        assertEquals('b', gui.nextSlot());
        assertEquals('c', gui.lastSlot());
        gui.clear("c");
        assertEquals('a', gui.lastSlot());
    }

    @Test
    void lockingFollowsSkriptGuisRules() {
        Gui gui = gui(9, null);
        gui.setItem(0L, item(), false, null);
        gui.setItem(1L, item(), false, code("a.sk"));
        gui.setItem(2L, item(), true, code("a.sk"));
        // A locked GUI: only removable slots change.
        assertFalse(gui.changeable('A'));
        assertFalse(gui.changeable('B'));
        assertTrue(gui.changeable('C'));
        assertFalse(gui.changeable('D'));
        // Unlocked: also slots without code, and slots not made.
        gui.setChangeable(true);
        assertTrue(gui.changeable('A'));
        assertFalse(gui.changeable('B'));
        assertTrue(gui.changeable('D'));
    }

    @Test
    void aSlotsChangeCodeStaysWhenItsRemade() {
        Gui gui = gui(9, null);
        SectionCode change = code("a.sk");
        gui.onChange(4L, change);
        gui.setItem(4L, item(), false, code("a.sk"));
        assertSame(change, gui.slot('E').onChange);
    }

    @Test
    void aNewLayoutKeepsTheCharactersItStillHas() {
        Gui gui = gui(9, "aaabbbccc");
        SectionCode click = code("a.sk");
        gui.setItem("a", item(), false, click);
        gui.setItem("c", item(), true, null);
        gui.setLayout("aaaaaaaaa");
        assertSame(click, gui.slot('a').onClick);
        assertNull(gui.slot('c'));
    }

    @Test
    void scriptsReloadingCloseTheGuisWithTheirCode() {
        Gui fromA = gui(9, null);
        fromA.setItem(0L, item(), false, code("menus/a.sk"));
        Gui fromB = gui(9, null);
        fromB.onClose(code("menus/b.sk"));
        Gui plain = gui(9, null);
        stale.add("menus/a.sk");
        guis.scriptsUnloaded(Set.of("menus/a.sk"));
        stale.remove("menus/a.sk");
        assertNull(guis.of(fromA.inventory()));
        assertTrue(guis.retired(fromA.inventory()));
        assertSame(fromB, guis.of(fromB.inventory()));
        assertSame(plain, guis.of(plain.inventory()));
    }

    @Test
    void globalGuisAreFoundByTheirId() {
        Gui gui = gui(9, null);
        gui.setId("shop");
        assertSame(gui, guis.byId("shop"));
        assertNull(guis.byId("Shop"));
        assertEquals(List.of(gui), guis.global());
        // Without an id and with nobody looking, it goes.
        gui.setId(null);
        assertNull(guis.of(gui.inventory()));
    }

    @Test
    void pagesAreTheirValues() {
        Object[] items = {1, 2, 3, 4, 5, 6, 7};
        assertArrayEquals(new Object[] {4, 5, 6}, Syntax.paginate(new Object[] {2L}, items, 3L));
        assertArrayEquals(new Object[] {1, 2, 3, 7}, Syntax.paginate(new Object[] {1L, 3L}, items, 3L));
        assertArrayEquals(new Object[0], Syntax.paginate(new Object[] {0L, 9L}, items, 3L));
        assertArrayEquals(new Object[0], Syntax.paginate(new Object[] {1L}, items, 0L));
    }

    @Test
    void theLastGuiIsKeptOnlyWhileItsRunIs() throws Exception {
        Guis.WeakIdentityMap<Object, String> map = new Guis.WeakIdentityMap<>();
        Object run = new Object();
        map.put(run, "gui");
        assertEquals("gui", map.get(run));
        // Equal but not the same run: not its GUI.
        assertNull(map.get(new Object()));
        run = null;
        for (int i = 0; i < 50 && map.size() > 0; i++) {
            System.gc();
            Thread.sleep(20);
        }
        assertEquals(0, map.size());
    }
}
