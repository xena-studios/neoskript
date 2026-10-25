/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.paper;

import co.xenastudios.neoskript.api.element.Changeable;
import co.xenastudios.neoskript.api.element.Expression;
import co.xenastudios.neoskript.api.element.TriggerContext;
import co.xenastudios.neoskript.api.registration.Registrar;
import co.xenastudios.neoskript.runtime.Timespan;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;

/** Display entities' properties: size, shadow, view range, interpolation, brightness, glow, text and the rest. */
final class Displays {

    private Displays() {}

    static void register(Registrar registrar) {
        registrar.type("display", "display", Display.class);
        registrar.subtype("display", "entity");
        String of = " [of %entities%]";
        number(registrar, "ExprDisplayHeightWidth", "display (height|width:width)", "width", d -> (double) d.getDisplayHeight(),
                d -> (double) d.getDisplayWidth(), (d, v) -> d.setDisplayHeight(v.floatValue()), (d, v) -> d.setDisplayWidth(v.floatValue()),
                0, Double.MAX_VALUE, 0.0, 0.0);
        number(registrar, "ExprDisplayShadow", "shadow (radius|strength:strength)", "strength", d -> (double) d.getShadowRadius(),
                d -> (double) d.getShadowStrength(), (d, v) -> d.setShadowRadius(v.floatValue()),
                (d, v) -> d.setShadowStrength(v.floatValue()), 0, Double.MAX_VALUE, 0.0, 1.0);
        number(registrar, "ExprDisplayViewRange", "view range", "", d -> (double) d.getViewRange(), null,
                (d, v) -> d.setViewRange(v.floatValue()), null, 0, Double.MAX_VALUE, 1.0, 1.0);
        ticks(registrar, "ExprDisplayInterpolation", "interpolation (delay|duration:duration)[s]", "duration",
                Display::getInterpolationDelay, Display::getInterpolationDuration, Display::setInterpolationDelay,
                Display::setInterpolationDuration, Integer.MAX_VALUE);
        ticks(registrar, "ExprDisplayTeleportationDuration", "teleport[ation] duration[s]", "", Display::getTeleportDuration,
                null, Display::setTeleportDuration, null, 59);
        registrar.expression("ExprDisplayBrightness", "integer", info -> new Brightness(info.hasTag("block") ? 1
                : info.hasTag("sky") ? 2 : 0), "[the] [block:block|sky:sky] (light [level]|brightness) override[s]" + of,
                "%entities%'[s] [block:block|sky:sky] (light [level]|brightness) override[s]");

        Map<String, Display.Billboard> billboards = new LinkedHashMap<>();
        for (Display.Billboard billboard : Display.Billboard.values()) {
            billboards.put(billboard.name().toLowerCase(java.util.Locale.ROOT), billboard);
        }
        registrar.namedType("billboard", "billboard", Display.Billboard.class, billboards);
        registrar.expression("ExprDisplayBillboard", "billboard", info -> new SettableProperty<>(Display.class,
                Display.Billboard.class, Display::getBillboard, Display::setBillboard).resettingTo(Display.Billboard.FIXED),
                "[the] bill[ |-]board[ing] [setting][s]" + of, "%entities%'[s] bill[ |-]board[ing] [setting][s]");
        registrar.expression("ExprDisplayGlowOverride", "color", info -> new SettableProperty<>(Display.class,
                Colors.Colour.class, display -> display.getGlowColorOverride() == null ? null : colour(display.getGlowColorOverride()),
                (display, colour) -> display.setGlowColorOverride(org.bukkit.Color.fromARGB(colour.alpha(), colour.red(),
                        colour.green(), colour.blue())))
                .resettingTo((java.util.function.Consumer<Object>) display -> ((Display) display).setGlowColorOverride(null)),
                "[the] glow[ing] colo[u]r[s] override[s]" + of, "%entities%'[s] glow[ing] colo[u]r[s] override[s]");

        Map<String, ItemDisplay.ItemDisplayTransform> transforms = new LinkedHashMap<>();
        transforms.put("no transform", ItemDisplay.ItemDisplayTransform.NONE);
        transforms.put("third person left hand", ItemDisplay.ItemDisplayTransform.THIRDPERSON_LEFTHAND);
        transforms.put("third person right hand", ItemDisplay.ItemDisplayTransform.THIRDPERSON_RIGHTHAND);
        transforms.put("first person left hand", ItemDisplay.ItemDisplayTransform.FIRSTPERSON_LEFTHAND);
        transforms.put("first person right hand", ItemDisplay.ItemDisplayTransform.FIRSTPERSON_RIGHTHAND);
        transforms.put("head", ItemDisplay.ItemDisplayTransform.HEAD);
        transforms.put("gui", ItemDisplay.ItemDisplayTransform.GUI);
        transforms.put("ground", ItemDisplay.ItemDisplayTransform.GROUND);
        transforms.put("fixed", ItemDisplay.ItemDisplayTransform.FIXED);
        registrar.namedType("itemdisplaytransform", "item display transform", ItemDisplay.ItemDisplayTransform.class, transforms);
        registrar.expression("ExprItemDisplayTransform", "itemdisplaytransform", info -> new SettableProperty<>(ItemDisplay.class,
                ItemDisplay.ItemDisplayTransform.class, ItemDisplay::getItemDisplayTransform, ItemDisplay::setItemDisplayTransform)
                .resettingTo(ItemDisplay.ItemDisplayTransform.NONE),
                "[the] item [display] transform[s]" + of, "%entities%'[s] item [display] transform[s]");

        Map<String, TextDisplay.TextAlignment> alignments = new LinkedHashMap<>();
        alignments.put("left aligned", TextDisplay.TextAlignment.LEFT);
        alignments.put("right aligned", TextDisplay.TextAlignment.RIGHT);
        alignments.put("centered", TextDisplay.TextAlignment.CENTER);
        alignments.put("middle aligned", TextDisplay.TextAlignment.CENTER);
        registrar.namedType("textalignment", "text alignment", TextDisplay.TextAlignment.class, alignments);
        registrar.expression("ExprTextDisplayAlignment", "textalignment", info -> new SettableProperty<>(TextDisplay.class,
                TextDisplay.TextAlignment.class, TextDisplay::getAlignment, TextDisplay::setAlignment)
                .resettingTo(TextDisplay.TextAlignment.CENTER),
                "[the] text alignment[s]" + of, "%entities%'[s] text alignment[s]");
        // A whole number of pixels, no less than 0; infinity is as wide as can be.
        registrar.expression("ExprTextDisplayLineWidth", "integer", info -> new SettableProperty<>(TextDisplay.class,
                Number.class, display -> (long) display.getLineWidth(), (display, width) -> display.setLineWidth((int) Math.max(0,
                        Math.min(Integer.MAX_VALUE, width.doubleValue())))).resettingTo(200L),
                "[the] line width[s]" + of, "%entities%'[s] line width[s]");
        registrar.expression("ExprTextDisplayOpacity", "integer", info -> new Opacity(),
                "[the] [text] opacit(y|ies)" + of, "%entities%'[s] [text] opacit(y|ies)");
        registrar.expression("ExprTextOf", "string", info -> new Text(), "[the] text[s]" + of, "%entities%'[s] text[s]");
        registrar.effect("EffTextDisplayDropShadow", info -> {
            boolean add = info.pattern() < 2;
            return (context, args) -> each(args[0], TextDisplay.class, display -> display.setShadowed(add));
        }, "(add|apply) [a] (drop|text) shadow to [the] [text of] %entities%",
                "(add|apply) [a] (drop|text) shadow to %entities%'[s] text",
                "remove [the] (drop|text) shadow from [the] [text of] %entities%",
                "remove [the] (drop|text) shadow from %entities%'[s] text");
        registrar.effect("EffTextDisplaySeeThroughBlocks", info -> {
            boolean see = info.pattern() < 2;
            return (context, args) -> each(args[0], TextDisplay.class, display -> display.setSeeThrough(see));
        }, "make %entities% visible through (blocks|walls)", "force %entities% to be visible through (blocks|walls)",
                "(prevent|block) %entities% from being (visible|seen) through (blocks|walls)");
    }

    private static <T> void each(Object values, Class<T> type, java.util.function.Consumer<T> action) {
        for (Object value : values instanceof Object[] array ? array : new Object[] {values}) {
            if (type.isInstance(value)) {
                action.accept(type.cast(value));
            }
        }
    }

    static Colors.Colour colour(org.bukkit.Color color) {
        return new Colors.Rgb(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
    }

    /** A float as the shortest decimal that reads back as it: 0.1f is 0.1, not 0.10000000149. */
    private static double exact(double value) {
        return Double.parseDouble(Float.toString((float) value));
    }

    /**
     * A display's number, one of two by a tag: kept between {@code min} and {@code max}, with infinite and
     * NaN values ignored, and reset to its default.
     */
    private static void number(Registrar registrar, String id, String property, String tag, Function<Display, Double> getter,
            Function<Display, Double> tagGetter, BiConsumer<Display, Number> setter, BiConsumer<Display, Number> tagSetter,
            double min, double max, double reset, double tagReset) {
        registrar.expression(id, "number", info -> {
            boolean tagged = !tag.isEmpty() && info.hasTag(tag);
            Function<Display, Double> get = tagged ? tagGetter : getter;
            BiConsumer<Display, Number> set = tagged ? tagSetter : setter;
            return new Amount((context, display) -> exact(get.apply(display)), (display, value) -> {
                if (Double.isFinite(value)) {
                    set.accept(display, Math.max(min, Math.min(max, value)));
                }
            }, tagged ? tagReset : reset, false);
        }, "[the] " + property + " [of %entities%]", "%entities%'[s] " + property);
    }

    /** A display's number of ticks, as a timespan, one of two by a tag, between 0 and {@code max}. */
    private static void ticks(Registrar registrar, String id, String property, String tag, Function<Display, Integer> getter,
            Function<Display, Integer> tagGetter, BiConsumer<Display, Integer> setter, BiConsumer<Display, Integer> tagSetter, int max) {
        registrar.expression(id, "timespan", info -> {
            boolean tagged = !tag.isEmpty() && info.hasTag(tag);
            Function<Display, Integer> get = tagged ? tagGetter : getter;
            BiConsumer<Display, Integer> set = tagged ? tagSetter : setter;
            return new Amount((context, display) -> (double) get.apply(display),
                    (display, value) -> set.accept(display, (int) Math.max(0, Math.min(max, value))), 0, true);
        }, "[the] " + property + " [of %entities%]", "%entities%'[s] " + property);
    }

    /** A number (or with {@code ticks}, a timespan) of each display, which can be set, added to, taken from and reset. */
    private record Amount(BiConsumerFunction get, BiConsumer<Display, Double> set, double reset, boolean ticks)
            implements Expression, Changeable {
        @Override
        public Object get(TriggerContext context, Object[] arguments) {
            List<Object> values = new ArrayList<>();
            for (Object value : owners(arguments)) {
                if (value instanceof Display display) {
                    double amount = get.apply(context, display);
                    values.add(ticks ? new Timespan((long) amount * 50) : (Object) amount);
                }
            }
            return values.size() == 1 ? values.getFirst() : values.toArray();
        }

        @Override
        public Class<?>[] acceptChange(Mode mode) {
            return switch (mode) {
                case SET, ADD, REMOVE -> new Class<?>[] {ticks ? Timespan.class : Number.class};
                case RESET -> new Class<?>[0];
                default -> null;
            };
        }

        @Override
        public void change(TriggerContext context, Object[] arguments, Object[] delta, Mode mode) {
            double by = 0;
            if (mode != Mode.RESET) {
                if (delta == null || delta.length == 0 || delta[0] == null) {
                    return;
                }
                by = delta[0] instanceof Timespan time ? time.ticks() : delta[0] instanceof Number n ? n.doubleValue() : Double.NaN;
                if (Double.isNaN(by)) {
                    return;
                }
            }
            for (Object value : owners(arguments)) {
                if (value instanceof Display display) {
                    double current = get.apply(context, display);
                    set.accept(display, switch (mode) {
                        case ADD -> current + by;
                        case REMOVE -> current - by;
                        case RESET -> reset;
                        default -> by;
                    });
                }
            }
        }
    }

    @FunctionalInterface
    private interface BiConsumerFunction {
        double apply(TriggerContext context, Display display);
    }

    private static Object[] owners(Object[] arguments) {
        Object owners = arguments.length == 0 ? null : arguments[0];
        return owners instanceof Object[] array ? array : owners == null ? new Object[0] : new Object[] {owners};
    }

    /** {@code the brightness override of %displays%}: block and sky light levels, 0 to 15, or not set. */
    private record Brightness(int which) implements Expression, Changeable {
        @Override
        public Object get(TriggerContext context, Object[] arguments) {
            List<Object> values = new ArrayList<>();
            for (Object value : owners(arguments)) {
                if (value instanceof Display display && display.getBrightness() != null) {
                    Display.Brightness brightness = display.getBrightness();
                    if (which != 2) {
                        values.add((long) brightness.getBlockLight());
                    }
                    if (which != 1) {
                        values.add((long) brightness.getSkyLight());
                    }
                }
            }
            return values.size() == 1 ? values.getFirst() : values.toArray();
        }

        @Override
        public Class<?>[] acceptChange(Mode mode) {
            return switch (mode) {
                case SET, ADD, REMOVE -> new Class<?>[] {Number.class};
                case RESET, DELETE -> new Class<?>[0];
                default -> null;
            };
        }

        @Override
        public void change(TriggerContext context, Object[] arguments, Object[] delta, Mode mode) {
            for (Object value : owners(arguments)) {
                if (!(value instanceof Display display)) {
                    continue;
                }
                if (mode == Mode.RESET || mode == Mode.DELETE) {
                    display.setBrightness(null);
                    continue;
                }
                if (delta == null || delta.length == 0 || !(delta[0] instanceof Number number)) {
                    continue;
                }
                if (Double.isNaN(number.doubleValue())) {
                    continue;
                }
                // Kept in range before it's a whole number, so infinity is the brightest, not a wrapped-round number.
                int by = (int) Math.round(Math.max(-1000, Math.min(1000, number.doubleValue())));
                Display.Brightness current = display.getBrightness();
                // A light level set alone starts the other at the same level.
                int block = current == null ? (mode == Mode.SET ? by : 0) : current.getBlockLight();
                int sky = current == null ? (mode == Mode.SET ? by : 0) : current.getSkyLight();
                if (which != 2) {
                    block = level(block, by, mode);
                }
                if (which != 1) {
                    sky = level(sky, by, mode);
                }
                display.setBrightness(new Display.Brightness(block, sky));
            }
        }

        private static int level(int current, int by, Mode mode) {
            int next = switch (mode) {
                case ADD -> current + by;
                case REMOVE -> current - by;
                default -> by;
            };
            return Math.max(0, Math.min(15, next));
        }
    }

    /** {@code the opacity of %text displays%}: 0 to 255, or as Minecraft stores it, a byte, when set below 0. */
    private static final class Opacity implements Expression, Changeable {
        @Override
        public Object get(TriggerContext context, Object[] arguments) {
            List<Object> values = new ArrayList<>();
            for (Object value : owners(arguments)) {
                if (value instanceof TextDisplay display) {
                    values.add((long) Byte.toUnsignedInt(display.getTextOpacity()));
                }
            }
            return values.size() == 1 ? values.getFirst() : values.toArray();
        }

        @Override
        public Class<?>[] acceptChange(Mode mode) {
            return switch (mode) {
                case SET, ADD, REMOVE -> new Class<?>[] {Number.class};
                case RESET -> new Class<?>[0];
                default -> null;
            };
        }

        @Override
        public void change(TriggerContext context, Object[] arguments, Object[] delta, Mode mode) {
            double by = mode == Mode.RESET || delta == null || delta.length == 0 || !(delta[0] instanceof Number n) ? 0 : n.doubleValue();
            for (Object value : owners(arguments)) {
                if (!(value instanceof TextDisplay display)) {
                    continue;
                }
                int current = Byte.toUnsignedInt(display.getTextOpacity());
                int next = switch (mode) {
                    case RESET -> 255;
                    case ADD -> (int) Math.max(0, Math.min(255, current + by));
                    case REMOVE -> (int) Math.max(0, Math.min(255, current - by));
                    // Set takes a byte's range too: -50 is 206.
                    default -> (int) Math.max(-128, Math.min(255, by));
                };
                display.setTextOpacity((byte) next);
            }
        }
    }

    /** {@code the text of %text displays%}: settable, with several texts as lines. */
    private static final class Text implements Expression, Changeable {
        @Override
        public Object get(TriggerContext context, Object[] arguments) {
            List<Object> values = new ArrayList<>();
            for (Object value : owners(arguments)) {
                if (value instanceof TextDisplay display) {
                    values.add(display.text() == null ? "" : Messages.LEGACY_SECTION.serialize(display.text()));
                }
            }
            return values.size() == 1 ? values.getFirst() : values.toArray();
        }

        @Override
        public Class<?>[] acceptChange(Mode mode) {
            return switch (mode) {
                case SET -> new Class<?>[] {String.class};
                case RESET, DELETE -> new Class<?>[0];
                default -> null;
            };
        }

        @Override
        public void change(TriggerContext context, Object[] arguments, Object[] delta, Mode mode) {
            List<String> lines = new ArrayList<>();
            if (mode == Mode.SET && delta != null) {
                for (Object line : delta) {
                    lines.add(co.xenastudios.neoskript.runtime.Values.text(line));
                }
            }
            for (Object value : owners(arguments)) {
                if (value instanceof TextDisplay display) {
                    display.text(lines.isEmpty() ? null : Messages.component(String.join("\n", lines)));
                }
            }
        }
    }
}
