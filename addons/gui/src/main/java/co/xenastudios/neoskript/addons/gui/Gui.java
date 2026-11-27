/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.addons.gui;

import co.xenastudios.neoskript.api.element.SectionCode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Nameable;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * A GUI: an inventory whose slots, named by the characters of its layout, hold items, code to run when they're
 * clicked or changed, and whether their items can be taken. As skript-gui's {@code GUI} and {@code VirtualGUI}.
 *
 * <p>Folia: a GUI can be open for players in several regions, whose clicks come on their regions' threads, and
 * scripts can change it from any thread. Everything here is under the GUI's lock, held only briefly and never while
 * script code runs.
 */
final class Gui implements Nameable {

    /** A slot the layout doesn't have. */
    static final char UNKNOWN_SLOT = ' ';

    /** What a slot of the layout does. */
    static final class SlotData {
        SectionCode onClick;
        SectionCode onChange;
        boolean changeable;
    }

    private final Guis guis;
    private volatile Inventory inventory;
    private Component name;
    private String layout;
    private final Map<Character, SlotData> slots = new HashMap<>();
    private boolean changeable;
    private volatile String id;
    private SectionCode onOpen;
    private SectionCode onClose;

    Gui(Guis guis, Inventory inventory, boolean changeable, Component name, String layout) {
        this.guis = guis;
        this.inventory = inventory;
        this.changeable = changeable;
        this.name = name == null ? inventory.getType().defaultTitle() : name;
        setLayout(layout);
    }

    Inventory inventory() {
        return inventory;
    }

    /** The GUI's code, all of it, for knowing which scripts it's from. */
    synchronized List<SectionCode> code() {
        List<SectionCode> code = new ArrayList<>();
        if (onOpen != null) {
            code.add(onOpen);
        }
        if (onClose != null) {
            code.add(onClose);
        }
        for (SlotData slot : slots.values()) {
            if (slot.onClick != null) {
                code.add(slot.onClick);
            }
            if (slot.onChange != null) {
                code.add(slot.onChange);
            }
        }
        return code;
    }

    // Slots

    /** Puts {@code item} in every slot of {@code slot}'s character, with what clicking it does. */
    synchronized void setItem(Object slot, ItemStack item, boolean slotChangeable, SectionCode onClick) {
        char ch = convert(slot);
        if (ch == UNKNOWN_SLOT) {
            return;
        }
        SlotData data = new SlotData();
        data.onClick = onClick;
        data.changeable = slotChangeable;
        SlotData existing = slots.get(ch);
        if (existing != null) {
            data.onChange = existing.onChange;
        }
        slots.put(ch, data);
        Inventory inventory = this.inventory;
        for (int i = 0; i < layout.length() && i < inventory.getSize(); i++) {
            if (layout.charAt(i) == ch) {
                inventory.setItem(i, item == null ? null : item.clone());
            }
        }
    }

    /** Runs {@code code} when the slots of {@code slot}'s character change. */
    synchronized void onChange(Object slot, SectionCode code) {
        char ch = convert(slot);
        SlotData data = slots.get(ch);
        if (data == null) {
            // Not made yet: made empty, as skript-gui does.
            setItem(ch, null, false, null);
            data = slots.get(ch);
            if (data == null) {
                return;
            }
        }
        data.onChange = code;
    }

    synchronized void clear() {
        inventory.clear();
        slots.clear();
    }

    synchronized void clear(Object slot) {
        char ch = convert(slot);
        // Nothing in its slots: as skript-gui's air.
        setItem(ch, null, false, null);
        slots.remove(ch);
    }

    synchronized SlotData slot(char ch) {
        return slots.get(ch);
    }

    // Layout

    synchronized String layout() {
        return layout;
    }

    /** Sets the layout, keeping the items and code of the characters it still has. {@code null} is one per slot. */
    synchronized void setLayout(String layout) {
        int size = inventory.getSize();
        if (layout == null) {
            StringBuilder unique = new StringBuilder();
            for (char c = 'A'; c < 'A' + size; c++) {
                unique.append(c);
            }
            layout = unique.toString();
        } else if (layout.length() < size) {
            layout = layout + String.valueOf(UNKNOWN_SLOT).repeat(size - layout.length());
        }
        if (this.layout == null) {
            this.layout = layout;
            return;
        }
        Map<Character, ItemStack> retained = new LinkedHashMap<>();
        String old = this.layout;
        for (int pos = 0; pos < old.length(); pos++) {
            char ch = old.charAt(pos);
            if (old.indexOf(ch) != pos) {
                continue;
            }
            if (layout.indexOf(ch) == -1) {
                clear(ch);
            } else {
                retained.put(ch, pos < size ? inventory.getItem(pos) : null);
            }
        }
        inventory.clear();
        this.layout = layout;
        for (Map.Entry<Character, ItemStack> moved : retained.entrySet()) {
            SlotData data = slots.get(moved.getKey());
            if (data != null) {
                setItem(moved.getKey(), moved.getValue(), data.changeable, data.onClick);
            }
        }
    }

    /** A slot number, a character of the layout, or text starting with one, as the layout's character. */
    synchronized char convert(Object slot) {
        return switch (slot) {
            case Character character -> character;
            case Number number -> {
                long index = number.longValue();
                yield index >= 0 && index < layout.length() ? layout.charAt((int) index) : UNKNOWN_SLOT;
            }
            case String text -> !text.isEmpty() && layout.contains(text) ? text.charAt(0) : UNKNOWN_SLOT;
            case null, default -> UNKNOWN_SLOT;
        };
    }

    /** The first character of the layout no slot has been made for. */
    synchronized char nextSlot() {
        for (char ch : layout.toCharArray()) {
            if (!slots.containsKey(ch)) {
                return ch;
            }
        }
        return UNKNOWN_SLOT;
    }

    /** The last character of the layout a slot has been made for. */
    synchronized char lastSlot() {
        for (int i = layout.length() - 1; i >= 0; i--) {
            if (slots.containsKey(layout.charAt(i))) {
                return layout.charAt(i);
            }
        }
        return UNKNOWN_SLOT;
    }

    // Properties

    String id() {
        return id;
    }

    /**
     * Sets the id, which makes it a global GUI. {@code null} makes it an ordinary one again, which goes away now
     * if nobody has it open.
     */
    void setId(String id) {
        this.id = id;
        if (id == null && inventory.getViewers().isEmpty()) {
            guis.unregister(this);
            clear();
        }
    }

    synchronized boolean changeable() {
        return changeable;
    }

    synchronized void setChangeable(boolean changeable) {
        this.changeable = changeable;
    }

    /** Whether any slot of the inventory can't be changed by a player. */
    synchronized boolean anyLocked() {
        for (int i = 0; i < inventory.getSize() && i < layout.length(); i++) {
            if (!changeable(layout.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    /** Whether the slots of {@code ch} can have their item changed by a player. */
    synchronized boolean changeable(char ch) {
        SlotData data = slots.get(ch);
        return data == null ? changeable : changeable(data);
    }

    /** As skript-gui: a slot made removable always is; others only in a GUI that is, and only without code. */
    synchronized boolean changeable(SlotData data) {
        return data.changeable || (changeable && data.onClick == null);
    }

    synchronized SectionCode onOpen() {
        return onOpen;
    }

    synchronized void onOpen(SectionCode code) {
        onOpen = code;
    }

    synchronized SectionCode onClose() {
        return onClose;
    }

    synchronized void onClose(SectionCode code) {
        onClose = code;
    }

    synchronized Component name() {
        return name;
    }

    synchronized int size() {
        return inventory.getSize();
    }

    /** Gives it a new name (its kind's default for {@code null}), which its viewers see at once. */
    void rename(Component name) {
        change(size(), name == null ? inventory.getType().defaultTitle() : name);
    }

    /**
     * Swaps in a new inventory, with another size or name, and moves its viewers over, as skript-gui does. Events
     * while they move aren't handled.
     */
    private void change(int size, Component name) {
        Inventory old;
        Inventory replacement;
        synchronized (this) {
            old = inventory;
            if (old.getType() == InventoryType.CHEST && (size < 9 || size > 54 || size % 9 != 0)) {
                return;
            }
            if (size == old.getSize() && name.equals(this.name)) {
                return;
            }
            replacement = old.getType() == InventoryType.CHEST ? Bukkit.createInventory(null, size, name)
                    : Bukkit.createInventory(null, old.getType(), name);
            ItemStack[] contents = old.getContents();
            for (int slot = 0; slot < Math.min(contents.length, replacement.getSize()); slot++) {
                replacement.setItem(slot, contents[slot]);
            }
            inventory = replacement;
            this.name = name;
        }
        guis.moved(this, old, replacement);
        for (HumanEntity viewer : new ArrayList<>(old.getViewers())) {
            guis.onThreadOf(viewer, () -> {
                guis.pause(viewer);
                try {
                    ItemStack cursor = viewer.getItemOnCursor();
                    viewer.setItemOnCursor(null);
                    viewer.openInventory(replacement);
                    viewer.setItemOnCursor(cursor);
                } finally {
                    guis.resume(viewer);
                }
            });
        }
    }

    Collection<HumanEntity> viewers() {
        return new ArrayList<>(inventory.getViewers());
    }

    // org.bukkit.Nameable, so 'name of' and 'set name of' work on it, as skript-gui's name property

    @Override
    public Component customName() {
        return name();
    }

    @Override
    public void customName(Component customName) {
        rename(customName);
    }

    @Override
    @SuppressWarnings("deprecation")
    public String getCustomName() {
        return LegacyComponentSerializer.legacySection().serialize(name());
    }

    @Override
    @SuppressWarnings("deprecation")
    public void setCustomName(String customName) {
        rename(customName == null ? null : LegacyComponentSerializer.legacySection().deserialize(customName));
    }
}
