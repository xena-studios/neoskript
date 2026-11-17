/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.addons.gui;

import co.xenastudios.neoskript.api.element.SectionCode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

/**
 * Clicks, drags, opening and closing in GUIs, as skript-gui's {@code GUIEvents} and its GUIs' event handler: whether
 * a click may change the GUI is decided first, under the GUI's lock, and the event is cancelled before any script
 * code runs, so code that fails or waits can't let an item out. Clicks run on the clicking player's thread, their
 * region's on Folia.
 */
final class GuiListener implements Listener {

    private final Guis guis;
    /** Close events whose closing a script cancelled ({@code cancel the gui closing}), until they're handled. */
    private final Set<Event> closeCancelled = ConcurrentHashMap.newKeySet();

    GuiListener(Guis guis) {
        this.guis = guis;
    }

    void cancelClose(Event event, boolean cancel) {
        if (cancel) {
            closeCancelled.add(event);
        } else {
            closeCancelled.remove(event);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent click) {
        Inventory top = click.getView().getTopInventory();
        if (guis.retired(top)) {
            // A GUI that went away while open: nothing comes out of it.
            click.setCancelled(true);
            return;
        }
        // As skript-gui: a cancelled click is only handled for spectators.
        if (click.getWhoClicked().getGameMode() != GameMode.SPECTATOR && click.isCancelled()) {
            return;
        }
        switch (click.getClick()) {
            case WINDOW_BORDER_LEFT, WINDOW_BORDER_RIGHT, CREATIVE -> {
                return;
            }
            default -> {}
        }
        Inventory clicked = click.getClickedInventory();
        if (clicked == null) {
            return;
        }
        Gui gui = guis.of(click.getInventory());
        if (gui == null) {
            return;
        }
        if (click.getClick() == ClickType.UNKNOWN) {
            click.setCancelled(true);
            return;
        }
        List<InventoryClickEvent> changes = new ArrayList<>();
        if (clicked.equals(click.getView().getBottomInventory())) {
            // The player's own inventory, which they may use, except for what would reach the GUI.
            synchronized (gui) {
                switch (click.getClick()) {
                    case SHIFT_LEFT, SHIFT_RIGHT -> {
                        if (!shiftIntoGui(gui, click, changes)) {
                            click.setCancelled(true);
                            return;
                        }
                    }
                    case DOUBLE_CLICK -> {
                        if (!collect(gui, click, changes)) {
                            click.setCancelled(true);
                            return;
                        }
                    }
                    default -> {
                        return;
                    }
                }
            }
            for (InventoryClickEvent change : changes) {
                change(gui, change);
            }
            return;
        }
        synchronized (gui) {
            if (click.getClick() == ClickType.DOUBLE_CLICK && gui.changeable(gui.convert(click.getSlot()))
                    && !collect(gui, click, changes)) {
                click.setCancelled(true);
                return;
            }
        }
        for (InventoryClickEvent change : changes) {
            change(gui, change);
        }
        click(gui, click);
    }

    /** A click in the GUI: decided under the lock, then its slot's code runs. */
    private void click(Gui gui, InventoryClickEvent click) {
        if (!(click.getWhoClicked() instanceof Player player) || guis.paused(player)) {
            click.setCancelled(true);
            return;
        }
        SectionCode onClick;
        boolean changes;
        synchronized (gui) {
            Gui.SlotData data = gui.slot(gui.convert(click.getSlot()));
            if (data == null) {
                click.setCancelled(!gui.changeable());
                return;
            }
            click.setCancelled(!gui.changeable(data));
            ItemStack current = click.getCurrentItem();
            changes = !click.isCancelled() && (current == null || !click.getCursor().isSimilar(current)
                    || current.getAmount() < current.getMaxStackSize());
            onClick = data.onClick;
        }
        if (changes) {
            change(gui, click);
        }
        if (onClick != null) {
            run(gui, click, onClick, player);
        }
    }

    /** A change to a slot of the GUI: its slot's change code runs, if the change is allowed. */
    private void change(Gui gui, InventoryClickEvent click) {
        if (!(click.getWhoClicked() instanceof Player player) || guis.paused(player)) {
            click.setCancelled(true);
            return;
        }
        SectionCode onChange;
        synchronized (gui) {
            Gui.SlotData data = gui.slot(gui.convert(click.getSlot()));
            if (data == null) {
                click.setCancelled(!gui.changeable());
                return;
            }
            click.setCancelled(!gui.changeable(data));
            onChange = click.isCancelled() ? null : data.onChange;
        }
        if (onChange != null) {
            run(gui, click, onChange, player);
        }
    }

    /**
     * A shift-click in the player's inventory, which moves the item into the GUI: allowed only if every slot it would
     * fill can change. Under the GUI's lock.
     */
    private static boolean shiftIntoGui(Gui gui, InventoryClickEvent click, List<InventoryClickEvent> changes) {
        ItemStack moved = click.getCurrentItem();
        if (moved == null || moved.getType().isAir()) {
            return false;
        }
        Inventory inventory = gui.inventory();
        int left = moved.getAmount();
        for (int slot = 0; slot < inventory.getSize() && left > 0; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item != null && !item.getType().isAir() && item.isSimilar(moved)
                    && item.getAmount() < item.getMaxStackSize()) {
                if (!gui.changeable(gui.convert(slot))) {
                    return false;
                }
                changes.add(withSlot(click, slot));
                left -= item.getMaxStackSize() - item.getAmount();
            }
        }
        if (left > 0) {
            int empty = inventory.firstEmpty();
            if (empty != -1) {
                if (!gui.changeable(gui.convert(empty))) {
                    return false;
                }
                changes.add(withSlot(click, empty));
            }
        }
        return true;
    }

    /**
     * A double-click, which gathers the cursor's item from both inventories: allowed only if every GUI slot it
     * would take from can change. Under the GUI's lock.
     */
    private static boolean collect(Gui gui, InventoryClickEvent click, List<InventoryClickEvent> changes) {
        ItemStack cursor = click.getCursor();
        if (cursor.getType().isAir()) {
            return true;
        }
        Inventory inventory = gui.inventory();
        int total = cursor.getAmount();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || !item.isSimilar(cursor)) {
                continue;
            }
            if (!gui.changeable(gui.convert(slot))) {
                return false;
            }
            changes.add(withSlot(click, slot));
            total += item.getAmount();
            if (total >= cursor.getMaxStackSize()) {
                break;
            }
        }
        return true;
    }

    private static InventoryClickEvent withSlot(InventoryClickEvent click, int slot) {
        return new InventoryClickEvent(click.getView(), click.getSlotType(), slot, click.getClick(), click.getAction());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent drag) {
        Inventory top = drag.getView().getTopInventory();
        if (guis.retired(top)) {
            drag.setCancelled(true);
            return;
        }
        if (drag.isCancelled()) {
            return;
        }
        Gui gui = guis.of(drag.getInventory());
        if (gui == null) {
            return;
        }
        int end = top.getSize();
        if (drag.getRawSlots().stream().noneMatch(slot -> slot < end)) {
            // Only the player's own inventory.
            return;
        }
        if (!(drag.getWhoClicked() instanceof Player player) || guis.paused(player)) {
            drag.setCancelled(true);
            return;
        }
        List<InventoryClickEvent> changes = new ArrayList<>();
        synchronized (gui) {
            for (int slot : drag.getRawSlots()) {
                if (slot < end && !gui.changeable(gui.convert(slot))) {
                    drag.setCancelled(true);
                    return;
                }
            }
            InventoryView view = drag.getView();
            for (int slot : drag.getRawSlots()) {
                if (slot < end) {
                    changes.add(new InventoryClickEvent(view, view.getSlotType(slot), slot, ClickType.UNKNOWN,
                            InventoryAction.UNKNOWN));
                }
            }
        }
        for (InventoryClickEvent change : changes) {
            change(gui, change);
            if (change.isCancelled()) {
                drag.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent open) {
        Gui gui = guis.of(open.getInventory());
        if (gui == null || !(open.getPlayer() instanceof Player player) || guis.paused(player)) {
            return;
        }
        SectionCode code = gui.onOpen();
        if (code != null) {
            run(gui, open, code, player);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClose(InventoryCloseEvent close) {
        Gui gui = guis.of(close.getInventory());
        if (gui == null || !(close.getPlayer() instanceof Player player) || guis.paused(player)) {
            return;
        }
        SectionCode code = gui.onClose();
        try {
            if (code != null) {
                run(gui, close, code, player);
            }
        } finally {
            boolean reopen = closeCancelled.remove(close);
            if (reopen && player.isOnline() && close.getReason() != InventoryCloseEvent.Reason.DISCONNECT) {
                // Opened again on the player's own thread (skript-gui used the global one, which Folia refuses).
                guis.later(player, () -> {
                    if (player.isOnline() && guis.of(gui.inventory()) == gui) {
                        guis.pause(player);
                        try {
                            player.openInventory(gui.inventory());
                        } finally {
                            guis.resume(player);
                        }
                    }
                });
            } else if (gui.id() == null && gui.inventory().getViewers().size() <= 1) {
                // Nobody else has it open, and it isn't global: it goes.
                guis.later(player, () -> {
                    if (gui.id() == null && gui.inventory().getViewers().isEmpty()) {
                        guis.unregister(gui);
                    }
                });
            }
            // As skript-gui (its issue 60): the player's inventory is sent again, so nothing they were shown sticks.
            guis.later(player, () -> {
                if (player.isOnline()) {
                    player.updateInventory();
                }
            });
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onRecipeBook(com.destroystokyo.paper.event.player.PlayerRecipeBookClickEvent click) {
        if (guis.of(click.getPlayer().getOpenInventory().getTopInventory()) != null) {
            click.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent quit) {
        guis.forget(quit.getPlayer());
    }

    /** Runs a GUI's code for its event, on this thread: a script error in it can't undo what was decided. */
    private void run(Gui gui, Event event, SectionCode code, HumanEntity player) {
        guis.last(event, gui);
        try {
            code.run(event, player);
        } catch (RuntimeException | LinkageError | StackOverflowError e) {
            guis.logger().log(Level.WARNING, "A GUI's code in " + code.script() + " failed", e);
        }
    }
}
