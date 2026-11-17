/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.addons.gui;

import co.xenastudios.neoskript.api.NeoSkript;
import co.xenastudios.neoskript.api.element.Changeable;
import co.xenastudios.neoskript.api.element.Condition;
import co.xenastudios.neoskript.api.element.ElementInfo;
import co.xenastudios.neoskript.api.element.Expression;
import co.xenastudios.neoskript.api.element.SyntaxError;
import co.xenastudios.neoskript.api.element.TriggerContext;
import co.xenastudios.neoskript.api.element.Warnings;
import co.xenastudios.neoskript.api.registration.Patterns;
import co.xenastudios.neoskript.api.registration.Registrar;
import co.xenastudios.neoskript.api.registration.ThreadAffinity;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/** skript-gui's syntax (1.4.0), as NeoSkript elements. Ids start with {@code NeoSkriptGui}. */
final class Syntax {

    /** What skript-gui calls the events of a GUI's code, as its errors say them. */
    static final String CLICK = "inventory click";
    static final String OPEN = "inventory open";
    static final String CLOSE = "inventory close";

    private final Guis guis;
    private final GuiListener listener;
    /** The names virtual inventories were made with, which a GUI made with one takes. */
    private final Map<Inventory, Component> names = Collections.synchronizedMap(new WeakHashMap<>());

    Syntax(Guis guis, GuiListener listener) {
        this.guis = guis;
        this.listener = listener;
    }

    void register(Registrar registrar) {
        types(registrar);
        conditions(registrar);
        effects(registrar);
        expressions(registrar);
        sections(registrar);
        registrar.scriptsUnloaded(guis::scriptsUnloaded);
    }

    // Where code is

    /** In a GUI's creation or editing section. */
    private static boolean inCreate(ElementInfo info) {
        return "gui".equals(info.sectionType());
    }

    /** In a GUI section: creating or editing one, or a slot's, its opening's or its closing's code. */
    private static boolean inGuiSection(ElementInfo info) {
        return inCreate(info) || inGuiCode(info);
    }

    /** In a slot's, a GUI's opening's or closing's code. */
    private static boolean inGuiCode(ElementInfo info) {
        return CLICK.equals(info.eventName()) || OPEN.equals(info.eventName()) || CLOSE.equals(info.eventName());
    }

    // Types

    private void types(Registrar registrar) {
        registrar.type("gui", "GUI", Gui.class);
        registrar.alias("gui", "gui inventory", "gui inventories");
        registrar.text(Gui.class, Syntax::text);
        registrar.variableNameText(Gui.class, Syntax::text);
        registrar.converter(Gui.class, Inventory.class, Gui::inventory);
        // 'delete {_gui}': it goes, and its viewers' screens close.
        registrar.changer(Gui.class, Changeable.Mode.DELETE, (gui, delta) -> {
            guis.unregister(gui);
            gui.setId(null);
        });
        // 'name of gui', and setting it, through Nameable.
        registrar.typeProperty("name", "gui");
        Map<String, InventoryType.SlotType> slotTypes = new LinkedHashMap<>();
        for (InventoryType.SlotType type : InventoryType.SlotType.values()) {
            slotTypes.put(type.name().toLowerCase(Locale.ROOT).replace('_', ' '), type);
        }
        registrar.namedType("slottype", "slot type", InventoryType.SlotType.class, slotTypes);
        registrar.text(InventoryType.SlotType.class, type -> type.name().toLowerCase(Locale.ROOT).replace('_', ' '));
    }

    /** As skript-gui writes a GUI: {@code chest inventory gui named Shop with 3 rows and layout "..."}. */
    static String text(Gui gui) {
        Inventory inventory = gui.inventory();
        return inventoryType(inventory.getType()) + " gui named " + plain(gui.name()) + " with "
                + inventory.getSize() / 9 + " rows and layout \"" + gui.layout() + "\"";
    }

    private static String inventoryType(InventoryType type) {
        return switch (type) {
            case WORKBENCH -> "crafting table inventory";
            default -> type.name().toLowerCase(Locale.ROOT).replace('_', ' ') + " inventory";
        };
    }

    private static String plain(Component component) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(component);
    }

    // Conditions

    private void conditions(Registrar registrar) {
        registrar.condition("NeoSkriptGuiCondHasGUI", info -> {
            boolean negated = info.pattern() == 1;
            return (context, args) -> {
                boolean holds = Condition.check(all(args[0]), info.and(0), player -> player instanceof HumanEntity viewer
                        && guis.of(viewer.getOpenInventory().getTopInventory()) != null);
                return negated != holds;
            };
        }, "%players% (has|have) a gui [open]", "%players% (doesn't|does not|do not|don't) have a gui [open]");
        registrar.condition("NeoSkriptGuiCondIsLocked", info -> {
            boolean locked = info.hasTag("locked") || info.pattern() == 3;
            boolean negated = info.pattern() == 1;
            return (context, args) -> negated != Condition.check(all(args[0]), info.and(0),
                    gui -> gui instanceof Gui g && g.changeable() != locked);
        }, "[gui[s]] %guis% (is|are) (:locked|unlocked)", "[gui[s]] %guis% (isn't|is not|aren't|are not) (:locked|unlocked)",
                "%guis% allow[s] items to be (removed|changed)", "%guis% (disallow|prevent)[s] items [from] being (removed|changed)");
        registrar.affinity("NeoSkriptGuiCondIsLocked", ThreadAffinity.ANY);
    }

    // Effects

    private void effects(Registrar registrar) {
        registrar.effect("NeoSkriptGuiEffCancelClose", info -> {
            if (!CLOSE.equals(info.eventName())) {
                throw new SyntaxError("Canceling or uncanceling the closing of a GUI can only be done within a GUI close"
                        + " section.");
            }
            boolean cancel = info.hasTag("cancel");
            return (context, args) -> {
                if (context.event() instanceof InventoryCloseEvent close) {
                    listener.cancelClose(close, cancel);
                }
            };
        }, "(:cancel|uncancel) [the] gui clos(e|ing)");
        registrar.affinity("NeoSkriptGuiEffCancelClose", ThreadAffinity.ANY);
        registrar.effect("NeoSkriptGuiEffLock", info -> {
            boolean changeable = info.hasTag("unlock") || info.pattern() == 1;
            return (context, args) -> {
                for (Object gui : all(args[0])) {
                    if (gui instanceof Gui g) {
                        g.setChangeable(changeable);
                    }
                }
            };
        }, "(lock|:unlock) [gui[s]] %guis%", "allow items to be (removed|changed) from %guis%",
                "(disallow|prevent) items [from] being (removed|changed) from %guis%");
        registrar.affinity("NeoSkriptGuiEffLock", ThreadAffinity.ANY);
        // Opening happens on each player's thread (NeoSkript runs it there).
        registrar.effect("NeoSkriptGuiEffShowGUI", info -> (context, args) -> {
            if (args[0] instanceof Gui gui && guis.of(gui.inventory()) == gui) {
                for (Object player : all(args[1])) {
                    if (player instanceof Player p) {
                        p.openInventory(gui.inventory());
                    }
                }
            }
        }, "(open|show|display) [gui] %gui% (to|for) %players%");
        registrar.effect("NeoSkriptGuiEffRemoveSlot", info -> {
            if (!inGuiSection(info)) {
                throw new SyntaxError("You can't make a GUI slot outside of a GUI section.");
            }
            int pattern = info.pattern();
            return (context, args) -> {
                Gui gui = guis.last(context);
                if (gui == null) {
                    return;
                }
                switch (pattern) {
                    case 0 -> gui.clear(gui.lastSlot());
                    case 1 -> {
                        for (Object slot : all(args[0])) {
                            gui.clear(slot);
                        }
                    }
                    default -> gui.clear();
                }
            };
        }, "(un(make|format)|remove) [the] (next|last) gui slot", "(un(make|format)|remove) gui [slot[s]] %integers/strings%",
                "(un(make|format)|remove) [all [[of] the]|the] gui [slots]");
        registrar.affinity("NeoSkriptGuiEffRemoveSlot", ThreadAffinity.ANY);
    }

    // Expressions

    private enum Value {
        SLOT("slot"), RAW_SLOT("raw slot"), HOTBAR_SLOT("hotbar slot"), INVENTORY("inventory"),
        INVENTORY_ACTION("inventory action"), CLICK_TYPE("click (type|action)"), CURSOR_ITEM("cursor [item]"),
        CLICKED_ITEM("[clicked|current] item"), SLOT_TYPE("slot type"), PLAYER("player"), VIEWERS("(viewer|player)s"),
        SLOT_ID("slot id[entifier]"), GUI("");

        final String pattern;

        Value(String pattern) {
            this.pattern = ("[the] gui " + pattern).stripTrailing();
        }

        String type() {
            return switch (this) {
                case SLOT, RAW_SLOT, HOTBAR_SLOT -> "integer";
                case INVENTORY -> "inventory";
                case INVENTORY_ACTION -> "inventoryaction";
                case CLICK_TYPE -> "clicktype";
                case CURSOR_ITEM, CLICKED_ITEM -> "itemstack";
                case SLOT_TYPE -> "slottype";
                case PLAYER, VIEWERS -> "player";
                case SLOT_ID -> "string";
                case GUI -> "gui";
            };
        }
    }

    private void expressions(Registrar registrar) {
        registrar.property("NeoSkriptGuiExprGUI", "gui", "gui", "players", Player.class,
                player -> guis.of(player.getOpenInventory().getTopInventory()));

        String[] valuePatterns = java.util.Arrays.stream(Value.values()).map(v -> v.pattern).toArray(String[]::new);
        registrar.expression("NeoSkriptGuiExprGUIValues", "object", this::guiValue, valuePatterns);
        registrar.returnType("NeoSkriptGuiExprGUIValues", info -> Value.values()[info.pattern()].type());
        registrar.affinity("NeoSkriptGuiExprGUIValues", ThreadAffinity.ANY);

        registrar.expression("NeoSkriptGuiExprGUIWithId", "gui", info -> (context, args) -> {
            List<Object> found = new ArrayList<>();
            for (Object id : all(args[0])) {
                Gui gui = id instanceof String text ? guis.byId(text) : null;
                if (gui != null) {
                    found.add(gui);
                }
            }
            return found.toArray();
        }, "[the] gui[s] [with [the] id[entifier][s]] %strings%");
        registrar.affinity("NeoSkriptGuiExprGUIWithId", ThreadAffinity.ANY);
        registrar.expression("NeoSkriptGuiExprGlobalGUIs", "gui", info -> (context, args) -> guis.global().toArray(),
                "[all [[of] the]|the] global guis");
        registrar.affinity("NeoSkriptGuiExprGlobalGUIs", ThreadAffinity.ANY);
        registrar.expression("NeoSkriptGuiExprLastGUI", "gui", info -> (context, args) -> guis.last(context),
                "[the] (last[ly] [created|edited]|created|edited) gui");
        registrar.affinity("NeoSkriptGuiExprLastGUI", ThreadAffinity.ANY);

        registrar.expression("NeoSkriptGuiExprId", "string", info -> new GuiProperty(gui -> gui.id(), String.class,
                (gui, value) -> gui.setId((String) value), false, false), Patterns.property("[gui] id[entifier][s]", "guis"));
        registrar.affinity("NeoSkriptGuiExprId", ThreadAffinity.ANY);
        registrar.expression("NeoSkriptGuiExprLayout", "string", info -> new GuiProperty(Gui::layout, String.class,
                (gui, value) -> gui.setLayout((String) value), true, true), Patterns.property("[gui] (layout|shape)[s]", "guis"));
        registrar.affinity("NeoSkriptGuiExprLayout", ThreadAffinity.ANY);
        registrar.expression("NeoSkriptGuiExprLockStatus", "boolean", info -> {
            Warnings.deprecated("This expression has been deprecated in favor of an equivalent Effect and Condition."
                    + " For getting, use 'whether " + argumentText(info, 0) + " is locked'. For setting, use 'lock"
                    + argumentText(info, 0) + "'.");
            return new LockStatus();
        }, Patterns.property("[gui] lock status[es]", "guis"));
        registrar.affinity("NeoSkriptGuiExprLockStatus", ThreadAffinity.ANY);

        registrar.expression("NeoSkriptGuiExprNextSlot", "string", info -> {
            if (info.pattern() == 2 && !inGuiSection(info)) {
                throw new SyntaxError("The 'next gui slot' expression must have a GUI specified unless it is used in a GUI"
                        + " section.");
            }
            boolean ofLast = info.pattern() == 2;
            return (context, args) -> {
                if (ofLast) {
                    Gui gui = guis.last(context);
                    return gui == null ? null : String.valueOf(gui.nextSlot());
                }
                List<Object> slots = new ArrayList<>();
                for (Object gui : all(args[0])) {
                    if (gui instanceof Gui g) {
                        slots.add(String.valueOf(g.nextSlot()));
                    }
                }
                return slots.toArray();
            };
        }, "[the] next gui slot[s] of %guis%", "%guis%'[s] next gui slot[s]", "[the] next gui slot");
        registrar.affinity("NeoSkriptGuiExprNextSlot", ThreadAffinity.ANY);

        registrar.expression("NeoSkriptGuiExprPaginatedList", "object", info -> {
            if (info.isSingle(1)) {
                throw new SyntaxError("You cannot paginate a single value.");
            }
            return (context, args) -> paginate(all(args[0]), all(args[1]), args[2]);
        }, "page[s] %numbers% of %objects% with %number% (lines|values)");
        registrar.returnType("NeoSkriptGuiExprPaginatedList", info -> info.argumentType(1));
        registrar.affinity("NeoSkriptGuiExprPaginatedList", ThreadAffinity.ANY);

        String virtual = "virtual (1:crafting [table]|1:workbench|2:chest|3:anvil|4:hopper|5:dropper|6:dispenser|%-inventorytype%)";
        registrar.expression("NeoSkriptGuiExprVirtualInventory", "inventory", this::virtualInventory,
                virtual + " [with size %-number%] [(named|with (name|title)) %-textcomponent%]",
                virtual + " [with %-number% row[s]] [(named|with (name|title)) %-textcomponent%]",
                virtual + " [(named|with (name|title)) %-textcomponent%] with size %-number%",
                virtual + " [(named|with (name|title)) %-textcomponent%] with %-number% row[s]");
        registrar.affinity("NeoSkriptGuiExprVirtualInventory", ThreadAffinity.ANY);
    }

    private static String argumentText(ElementInfo info, int index) {
        String written = info.argumentLiteral(index);
        String text = Warnings.argument(index);
        return text != null ? text : written != null ? written : "the gui";
    }

    /** {@code page 2 of {_items::*} with 36 values}: the values on those pages. */
    static Object[] paginate(Object[] pages, Object[] contents, Object linesValue) {
        int lines = linesValue instanceof Number number ? number.intValue() : 0;
        if (lines < 1 || contents.length == 0) {
            return new Object[0];
        }
        List<Object> values = new ArrayList<>();
        for (Object page : pages) {
            if (!(page instanceof Number number) || number.intValue() < 1) {
                continue;
            }
            long start = (long) (number.intValue() - 1) * lines;
            for (long i = start; i < start + lines && i < contents.length; i++) {
                values.add(contents[(int) i]);
            }
        }
        return values.toArray();
    }

    private Expression guiValue(ElementInfo info) {
        Value value = Value.values()[info.pattern()];
        String written = info.textOr("the gui" + (value.pattern.isEmpty() ? "" : " " + value.name().toLowerCase(Locale.ROOT)));
        if (inCreate(info) && !inGuiCode(info)) {
            if (value != Value.GUI) {
                throw new SyntaxError("You can't use '" + written + "' in a GUI creation section.");
            }
        } else if (!inGuiCode(info)) {
            throw new SyntaxError("You can't use '" + written + "' outside of a GUI section.");
        }
        boolean openClose = OPEN.equals(info.eventName()) || CLOSE.equals(info.eventName());
        if (openClose && value != Value.GUI && value != Value.INVENTORY && value != Value.PLAYER && value != Value.VIEWERS) {
            throw new SyntaxError("You can't use '" + written + "' in a GUI open/close section.");
        }
        if (value == Value.CLICKED_ITEM) {
            return new ClickedItem(this);
        }
        if (value == Value.GUI) {
            return new GuiValue(guis);
        }
        return (context, args) -> get(value, openClose, context);
    }

    private Object get(Value value, boolean openClose, TriggerContext context) {
        Object event = context.event();
        if (openClose && event instanceof InventoryEvent inventoryEvent) {
            return switch (value) {
                case INVENTORY -> inventoryEvent.getInventory();
                case PLAYER -> {
                    HumanEntity human = event instanceof InventoryCloseEvent close ? close.getPlayer()
                            : event instanceof InventoryOpenEvent open ? open.getPlayer() : null;
                    yield human instanceof Player player ? player : null;
                }
                case VIEWERS -> inventoryEvent.getViewers().toArray();
                default -> null;
            };
        }
        if (!(event instanceof InventoryClickEvent click)) {
            return null;
        }
        return switch (value) {
            case SLOT -> (long) click.getSlot();
            case RAW_SLOT -> (long) click.getRawSlot();
            case HOTBAR_SLOT -> (long) click.getHotbarButton();
            case INVENTORY -> click.getClickedInventory();
            case INVENTORY_ACTION -> click.getAction();
            case CLICK_TYPE -> click.getClick();
            case CURSOR_ITEM -> click.getCursor();
            case SLOT_TYPE -> click.getSlotType();
            case PLAYER -> click.getWhoClicked() instanceof Player player ? player : null;
            case VIEWERS -> click.getViewers().stream().filter(Player.class::isInstance).toArray();
            case SLOT_ID -> {
                Gui gui = guis.last(context);
                yield gui == null ? null : String.valueOf(gui.convert(click.getSlot()));
            }
            default -> null;
        };
    }

    /** {@code the gui clicked item}, which can be set while the click hasn't passed. */
    private record ClickedItem(Syntax syntax) implements Expression, Changeable {
        @Override
        public Object get(TriggerContext context, Object[] arguments) {
            return context.event() instanceof InventoryClickEvent click ? click.getCurrentItem() : null;
        }

        @Override
        public Class<?>[] acceptChange(Mode mode) {
            return mode == Mode.SET ? new Class<?>[] {ItemStack.class} : null;
        }

        @Override
        public boolean takesOne(Mode mode) {
            return true;
        }

        @Override
        public void change(TriggerContext context, Object[] arguments, Object[] delta, Mode mode) {
            if (delta != null && delta.length > 0 && delta[0] instanceof ItemStack item
                    && context.event() instanceof InventoryClickEvent click) {
                click.setCurrentItem(item);
            }
        }
    }

    /** {@code the gui}: the GUI of the code, which can be deleted. */
    private record GuiValue(Guis guis) implements Expression, Changeable {
        @Override
        public Object get(TriggerContext context, Object[] arguments) {
            return guis.last(context);
        }

        @Override
        public Class<?>[] acceptChange(Mode mode) {
            return mode == Mode.DELETE ? new Class<?>[0] : null;
        }

        @Override
        public void change(TriggerContext context, Object[] arguments, Object[] delta, Mode mode) {
            Gui gui = guis.last(context);
            if (gui != null) {
                guis.unregister(gui);
                gui.setId(null);
            }
        }
    }

    /** A GUI's property: got for each GUI, and set (or deleted and reset) for each. */
    private record GuiProperty(java.util.function.Function<Gui, Object> getter, Class<?> type,
            java.util.function.BiConsumer<Gui, Object> setter, boolean several, boolean resettable)
            implements Expression, Changeable {
        @Override
        public Object get(TriggerContext context, Object[] arguments) {
            List<Object> values = new ArrayList<>();
            for (Object gui : all(arguments[0])) {
                if (gui instanceof Gui g) {
                    Object value = getter.apply(g);
                    if (value != null) {
                        values.add(value);
                    }
                }
            }
            return values.toArray();
        }

        @Override
        public Class<?>[] acceptChange(Mode mode) {
            return switch (mode) {
                case SET -> new Class<?>[] {type};
                case DELETE, RESET -> resettable ? new Class<?>[0] : null;
                default -> null;
            };
        }

        @Override
        public boolean takesOne(Mode mode) {
            return !several;
        }

        @Override
        public void change(TriggerContext context, Object[] arguments, Object[] delta, Mode mode) {
            Object value = null;
            if (delta != null && delta.length > 0) {
                if (several) {
                    StringBuilder joined = new StringBuilder();
                    for (Object part : delta) {
                        joined.append(part);
                    }
                    value = joined.toString();
                } else {
                    value = delta[0];
                }
            }
            for (Object gui : all(arguments[0])) {
                if (gui instanceof Gui g) {
                    setter.accept(g, value);
                }
            }
        }
    }

    /** The deprecated {@code lock status of gui}: true when locked; adding and resetting change it, as in skript-gui. */
    private static final class LockStatus implements Expression, Changeable {
        @Override
        public Object get(TriggerContext context, Object[] arguments) {
            List<Object> values = new ArrayList<>();
            for (Object gui : all(arguments[0])) {
                if (gui instanceof Gui g) {
                    values.add(!g.changeable());
                }
            }
            return values.toArray();
        }

        @Override
        public Class<?>[] acceptChange(Mode mode) {
            return mode == Mode.ADD || mode == Mode.RESET ? new Class<?>[] {Boolean.class} : null;
        }

        @Override
        public void change(TriggerContext context, Object[] arguments, Object[] delta, Mode mode) {
            boolean changeable = delta != null && delta.length > 0 && !Boolean.TRUE.equals(delta[0]);
            for (Object gui : all(arguments[0])) {
                if (gui instanceof Gui g) {
                    g.setChangeable(changeable);
                }
            }
        }
    }

    private Expression virtualInventory(ElementInfo info) {
        InventoryType fixed = info.hasTag("1") ? InventoryType.WORKBENCH : info.hasTag("2") ? InventoryType.CHEST
                : info.hasTag("3") ? InventoryType.ANVIL : info.hasTag("4") ? InventoryType.HOPPER
                : info.hasTag("5") ? InventoryType.DROPPER : info.hasTag("6") ? InventoryType.DISPENSER : null;
        boolean nameFirst = info.pattern() > 1;
        return (context, args) -> {
            InventoryType type = fixed != null ? fixed : inventoryType(args[0]);
            Object rowsValue = nameFirst ? args[2] : args[1];
            Object nameValue = nameFirst ? args[1] : args[2];
            if (type == null) {
                return null;
            }
            if (type == InventoryType.CRAFTING) {
                // As skript-gui: not the same, but likely what's meant.
                type = InventoryType.WORKBENCH;
            } else if (!type.isCreatable()) {
                return null;
            }
            Component name = component(nameValue);
            if (name == null) {
                name = type.defaultTitle();
            }
            Inventory inventory;
            if (type == InventoryType.CHEST) {
                int size = -1;
                if (rowsValue instanceof Number rows) {
                    size = rows.intValue();
                    if (size <= 6) {
                        size *= 9;
                    }
                }
                if (size < 9 || size > 54 || size % 9 != 0) {
                    size = type.getDefaultSize();
                }
                inventory = Bukkit.createInventory(null, size, name);
            } else {
                inventory = Bukkit.createInventory(null, type, name);
            }
            names.put(inventory, name);
            return inventory;
        };
    }

    /** A Bukkit inventory type from NeoSkript's inventory type value, by its name. */
    private static InventoryType inventoryType(Object value) {
        if (value == null) {
            return null;
        }
        String text = NeoSkript.get().text(value).toLowerCase(Locale.ROOT).replace(" inventory", "").strip();
        if (text.equals("crafting table") || text.equals("workbench")) {
            return InventoryType.WORKBENCH;
        }
        try {
            return InventoryType.valueOf(text.replace(' ', '_').toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }

    private static Component component(Object value) {
        return switch (value) {
            case Component component -> component;
            case String text -> net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
                    .deserialize(text);
            case null, default -> null;
        };
    }

    // Sections

    private void sections(Registrar registrar) {
        registrar.section("NeoSkriptGuiSecCreateGUI", "gui", info -> {
            boolean edit = info.pattern() == 1;
            boolean removable = info.hasTag("removable");
            return (context, args) -> {
                Gui gui;
                if (edit) {
                    gui = args[0] instanceof Gui g ? g : null;
                    if (gui == null) {
                        return null;
                    }
                } else {
                    if (!(args[1] instanceof Inventory inventory)) {
                        return null;
                    }
                    if (guis.of(inventory) != null) {
                        throw new IllegalStateException("Attempted to create a GUI using an inventory already associated"
                                + " with a GUI! This is not permitted.");
                    }
                    String layout = null;
                    if (args[2] != null) {
                        StringBuilder joined = new StringBuilder();
                        for (Object part : all(args[2])) {
                            joined.append(part);
                        }
                        layout = joined.toString();
                    }
                    gui = new Gui(guis, inventory, removable, names.remove(inventory), layout);
                    guis.register(gui);
                    String id = args[0] instanceof String text ? text : null;
                    if (id != null && !id.isEmpty()) {
                        Gui old = guis.byId(id);
                        if (old != null) {
                            // As skript-gui (its issue 72): the new one takes the id, the old one goes.
                            guis.unregister(old);
                        }
                        gui.setId(id);
                    }
                }
                guis.last(context, gui);
                return gui;
            };
        }, "create [a] [new] gui [[with id[entifier]] %-string%] with [a] %inventory% [removable:(and|with) ([re]mov[e]able|stealable) items] [(and|with) (layout|shape) %-strings%]",
                "(change|edit) [gui] %gui%");
        registrar.affinity("NeoSkriptGuiSecCreateGUI", ThreadAffinity.ANY);

        registrar.laterSection("NeoSkriptGuiSecMakeSlot", CLICK, List.of(InventoryClickEvent.class), info -> {
            if (!inGuiSection(info)) {
                throw new SyntaxError("You can't make a GUI slot outside of a GUI section.");
            }
            boolean next = info.pattern() == 0;
            boolean removable = info.hasTag("removable");
            return (context, args, code) -> {
                Gui gui = guis.last(context);
                Object itemValue = next ? args[0] : args[1];
                if (gui == null || !(itemValue instanceof ItemStack item)) {
                    return;
                }
                Object[] slots = next ? new Object[] {gui.nextSlot()} : all(args[0]);
                for (Object slot : slots) {
                    gui.setItem(slot, item, removable, code);
                }
            };
        }, "(make|format) [the] next gui [slot] (with|to) [removable:([re]mov[e]able|stealable)] %itemstack%",
                "(make|format) gui [slot[s]] %integers/strings% (with|to) [removable:([re]mov[e]able|stealable)] %itemstack%");
        registrar.affinity("NeoSkriptGuiSecMakeSlot", ThreadAffinity.ANY);

        registrar.laterSection("NeoSkriptGuiSecOpen", OPEN, List.of(InventoryOpenEvent.class), info -> {
            requireCreate(info, "GUI open/close sections can only be put within GUI creation or editing sections.");
            return (context, args, code) -> {
                Gui gui = guis.last(context);
                if (gui != null && code != null) {
                    gui.onOpen(code);
                }
            };
        }, "run (when|while) open[ing] [[the] gui]", "run (when|while) [the] gui opens", "run on [gui] open[ing]");
        registrar.affinity("NeoSkriptGuiSecOpen", ThreadAffinity.ANY);
        registrar.laterSection("NeoSkriptGuiSecClose", CLOSE, List.of(InventoryCloseEvent.class), info -> {
            requireCreate(info, "GUI open/close sections can only be put within GUI creation or editing sections.");
            return (context, args, code) -> {
                Gui gui = guis.last(context);
                if (gui != null && code != null) {
                    gui.onClose(code);
                }
            };
        }, "run (when|while) clos(e|ing) [[the] gui]", "run (when|while) [the] gui closes", "run on [gui] clos(e|ing)");
        registrar.affinity("NeoSkriptGuiSecClose", ThreadAffinity.ANY);

        registrar.laterSection("NeoSkriptGuiSecSlotChange", CLICK, List.of(InventoryClickEvent.class), info -> {
            requireCreate(info, "You can't listen for changes of a slot outside of a GUI creation or editing section.");
            return (context, args, code) -> {
                Gui gui = guis.last(context);
                if (gui != null && code != null) {
                    for (Object slot : all(args[0])) {
                        gui.onChange(slot, code);
                    }
                }
            };
        }, "run when [gui] slot[s] %integers/strings% change[s]", "run when [gui] slot[s] %integers/strings% (is|are) changed",
                "run on change of [gui] slot[s] %integers/strings%");
        registrar.affinity("NeoSkriptGuiSecSlotChange", ThreadAffinity.ANY);
    }

    private static void requireCreate(ElementInfo info, String error) {
        if (!inCreate(info)) {
            throw new SyntaxError(error);
        }
    }

    static Object[] all(Object value) {
        if (value == null) {
            return new Object[0];
        }
        return value instanceof Object[] array ? array : new Object[] {value};
    }
}
