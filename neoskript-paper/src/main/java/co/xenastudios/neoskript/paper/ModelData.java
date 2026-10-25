/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.paper;

import co.xenastudios.neoskript.api.element.Changeable;
import co.xenastudios.neoskript.api.element.Expression;
import co.xenastudios.neoskript.api.element.TriggerContext;
import co.xenastudios.neoskript.api.registration.Patterns;
import co.xenastudios.neoskript.api.registration.Registrar;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;

/**
 * Custom model data, as Skript has it since 1.21.4: the old single number, and the lists of floats, flags,
 * strings and colours an item's model data is made of.
 */
final class ModelData {

    private ModelData() {}

    /** What part of the model data an expression or condition is about, by Skript's parse mark. */
    private enum Part { SINGLE, FLOATS, FLAGS, STRINGS, COLORS, ALL }

    /** {@code ItemMeta#hasCustomModelDataComponent}, which servers before 1.21.5 lack. */
    private static final java.lang.reflect.Method HAS_COMPONENT = hasComponent();

    private static java.lang.reflect.Method hasComponent() {
        try {
            return ItemMeta.class.getMethod("hasCustomModelDataComponent");
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    static void register(Registrar registrar) {
        List<String> patterns = new ArrayList<>();
        patterns.addAll(Arrays.asList(Patterns.property("[custom] model data", "itemtypes")));
        patterns.addAll(Arrays.asList(Patterns.property("[custom] model data (1¦floats|2¦flags|3¦strings|4¦colo[u]rs)",
                "itemtype")));
        patterns.addAll(Arrays.asList(Patterns.property("(5¦(complete|full)) [custom] model data", "itemtype")));
        registrar.expression("ExprCustomModelData", "object", info -> new CustomModelData(Part.values()[info.mark()]),
                patterns.toArray(String[]::new));

        registrar.condition("CondHasCustomModelData", info -> {
            Part part = Part.values()[info.mark()];
            boolean negated = info.pattern() == 1;
            boolean and = info.and(0);
            return (context, args) -> {
                Object[] items = Property.of(args[0]);
                if (items.length == 0) {
                    return negated;
                }
                return co.xenastudios.neoskript.api.element.Condition.check(items, and,
                        value -> (value instanceof ItemType item && has(item, part)) != negated);
            };
        }, Patterns.hasProperty("[custom] model data [1¦floats|2¦flags|3¦strings|4¦colo[u]rs]", "itemtypes"));

        registrar.expression("ExprItemWithCustomModelData", "itemtype", info -> (context, args) -> {
            if (!(args[0] instanceof ItemType item)) {
                return null;
            }
            Object[] data = Property.of(args[1]);
            if (data.length == 0) {
                return item;
            }
            List<Float> floats = new ArrayList<>();
            List<Boolean> flags = new ArrayList<>();
            List<String> strings = new ArrayList<>();
            List<org.bukkit.Color> colors = new ArrayList<>();
            sort(data, floats, flags, strings, colors);
            return item.withMeta(meta -> {
                CustomModelDataComponent component = meta.getCustomModelDataComponent();
                component.setFloats(floats);
                component.setFlags(flags);
                component.setStrings(strings);
                component.setColors(colors);
                meta.setCustomModelDataComponent(component);
            });
        }, "%itemtype% with [custom] model data %numbers/booleans/strings/colors%");
    }

    private static boolean has(ItemType item, Part part) {
        ItemMeta meta = item.stack().getItemMeta();
        if (meta == null) {
            return false;
        }
        if (part == Part.SINGLE) {
            if (HAS_COMPONENT != null) {
                try {
                    return (boolean) HAS_COMPONENT.invoke(meta);
                } catch (ReflectiveOperationException e) {
                    return meta.hasCustomModelData();
                }
            }
            return meta.hasCustomModelData();
        }
        CustomModelDataComponent component = meta.getCustomModelDataComponent();
        return switch (part) {
            case FLOATS -> !component.getFloats().isEmpty();
            case FLAGS -> !component.getFlags().isEmpty();
            case STRINGS -> !component.getStrings().isEmpty();
            default -> !component.getColors().isEmpty();
        };
    }

    /** Sorts values into the lists of the model data they belong in. */
    private static void sort(Object[] values, List<Float> floats, List<Boolean> flags, List<String> strings,
            List<org.bukkit.Color> colors) {
        for (Object value : values) {
            switch (value) {
                case Number number -> floats.add(number.floatValue());
                case Boolean flag -> flags.add(flag);
                case String string -> strings.add(string);
                case Colors.Colour colour -> colors.add(bukkit(colour));
                case null, default -> {}
            }
        }
    }

    private static org.bukkit.Color bukkit(Colors.Colour colour) {
        return org.bukkit.Color.fromARGB(colour.alpha(), colour.red(), colour.green(), colour.blue());
    }

    private static Colors.Colour colour(org.bukkit.Color color) {
        return new Colors.Rgb(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
    }

    /** {@code the custom model data of %itemtypes%}, its floats, flags, strings or colours, or all of it. */
    private record CustomModelData(Part part) implements Expression, Changeable {
        @Override
        public Object get(TriggerContext context, Object[] arguments) {
            // As in Skript, only the first item's model data is given.
            for (Object value : Property.of(arguments[0])) {
                if (!(value instanceof ItemType item)) {
                    continue;
                }
                ItemMeta meta = item.stack().getItemMeta();
                if (meta == null) {
                    return part == Part.SINGLE ? 0L : new Object[0];
                }
                if (part == Part.SINGLE) {
                    return meta.hasCustomModelData() && !meta.getCustomModelDataComponent().getFloats().isEmpty()
                            ? (Object) (long) meta.getCustomModelData() : (Object) 0L;
                }
                CustomModelDataComponent component = meta.getCustomModelDataComponent();
                List<Object> data = new ArrayList<>();
                if (part == Part.FLOATS || part == Part.ALL) {
                    data.addAll(component.getFloats());
                }
                if (part == Part.FLAGS || part == Part.ALL) {
                    data.addAll(component.getFlags());
                }
                if (part == Part.STRINGS || part == Part.ALL) {
                    data.addAll(component.getStrings());
                }
                if (part == Part.COLORS || part == Part.ALL) {
                    component.getColors().forEach(color -> data.add(colour(color)));
                }
                return data.toArray();
            }
            return new Object[0];
        }

        @Override
        public Class<?>[] acceptChange(Mode mode) {
            return switch (mode) {
                case ADD, REMOVE, SET, DELETE, RESET -> switch (part) {
                    case SINGLE, FLOATS -> new Class<?>[] {Number.class};
                    case FLAGS -> new Class<?>[] {Boolean.class};
                    case STRINGS -> new Class<?>[] {String.class};
                    case COLORS -> new Class<?>[] {Colors.Colour.class};
                    case ALL -> new Class<?>[] {Number.class, Boolean.class, String.class, Colors.Colour.class};
                };
                default -> null;
            };
        }

        @Override
        public void change(TriggerContext context, Object[] arguments, Object[] delta, Mode mode) {
            for (Object value : Property.of(arguments[0])) {
                if (value instanceof ItemType item) {
                    item.editMeta(meta -> {
                        switch (part) {
                            case SINGLE -> changeSingle(meta, mode, delta == null || delta.length == 0 ? 0
                                    : ((Number) delta[0]).intValue());
                            case ALL -> changeAll(meta, mode, delta);
                            default -> changePart(meta, mode, delta);
                        }
                    });
                }
            }
        }

        private static void changeSingle(ItemMeta meta, Mode mode, long delta) {
            if (mode == Mode.DELETE || mode == Mode.RESET) {
                meta.setCustomModelData(null);
                return;
            }
            long old = meta.hasCustomModelData() && !meta.getCustomModelDataComponent().getFloats().isEmpty()
                    ? meta.getCustomModelData() : 0;
            switch (mode) {
                case REMOVE -> meta.setCustomModelData((int) (old - delta));
                case ADD -> meta.setCustomModelData((int) (old + delta));
                default -> meta.setCustomModelData((int) delta);
            }
        }

        @SuppressWarnings("unchecked")
        private void changePart(ItemMeta meta, Mode mode, Object[] delta) {
            if (delta == null && mode != Mode.DELETE && mode != Mode.RESET) {
                return;
            }
            CustomModelDataComponent component = meta.getCustomModelDataComponent();
            List<Object> data = new ArrayList<>(switch (part) {
                case FLOATS -> component.getFloats();
                case FLAGS -> component.getFlags();
                case STRINGS -> component.getStrings();
                default -> component.getColors();
            });
            List<Object> given = new ArrayList<>();
            for (Object value : delta == null ? new Object[0] : delta) {
                given.add(switch (value) {
                    case Number number -> number.floatValue();
                    case Colors.Colour colour -> bukkit(colour);
                    default -> value;
                });
            }
            switch (mode) {
                case REMOVE -> data.removeAll(given);
                case ADD -> data.addAll(given);
                case SET -> data = given;
                default -> data.clear();
            }
            List<?> list = data;
            switch (part) {
                case FLOATS -> component.setFloats((List<Float>) list);
                case FLAGS -> component.setFlags((List<Boolean>) list);
                case STRINGS -> component.setStrings((List<String>) list);
                default -> component.setColors((List<org.bukkit.Color>) list);
            }
            meta.setCustomModelDataComponent(component);
        }

        private static void changeAll(ItemMeta meta, Mode mode, Object[] delta) {
            if (mode == Mode.DELETE || mode == Mode.RESET) {
                meta.setCustomModelDataComponent(null);
                return;
            }
            if (delta == null) {
                return;
            }
            CustomModelDataComponent component = meta.getCustomModelDataComponent();
            List<Float> floats = new ArrayList<>(component.getFloats());
            List<Boolean> flags = new ArrayList<>(component.getFlags());
            List<String> strings = new ArrayList<>(component.getStrings());
            List<org.bukkit.Color> colors = new ArrayList<>(component.getColors());
            if (mode == Mode.REMOVE) {
                List<Float> removedFloats = new ArrayList<>();
                List<Boolean> removedFlags = new ArrayList<>();
                List<String> removedStrings = new ArrayList<>();
                List<org.bukkit.Color> removedColors = new ArrayList<>();
                sort(delta, removedFloats, removedFlags, removedStrings, removedColors);
                // One of each value given is taken out, as Skript's list removal does.
                removedFloats.forEach(floats::remove);
                removedFlags.forEach(flags::remove);
                removedStrings.forEach(strings::remove);
                removedColors.forEach(colors::remove);
            } else {
                if (mode == Mode.SET) {
                    floats.clear();
                    flags.clear();
                    strings.clear();
                    colors.clear();
                }
                sort(delta, floats, flags, strings, colors);
            }
            component.setFloats(floats);
            component.setFlags(flags);
            component.setStrings(strings);
            component.setColors(colors);
            meta.setCustomModelDataComponent(component);
        }
    }
}
