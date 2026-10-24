/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.paper;

import co.xenastudios.neoskript.api.element.Changeable;
import co.xenastudios.neoskript.api.element.Expression;
import co.xenastudios.neoskript.api.element.TriggerContext;
import co.xenastudios.neoskript.api.registration.Registrar;
import co.xenastudios.neoskript.runtime.Values;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.block.Banner;
import org.bukkit.block.Block;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BannerMeta;

/** Banner patterns: their types by Skript's names, patterns of a colour, and the patterns of banners. */
final class Banners {

    private Banners() {}

    /** Skript's names for banner pattern types, by key, from its English language file. */
    private static final String[] NAMES = {
        "base", "base banner pattern",
        "border", "border banner pattern, bordure banner pattern, bordure indented banner pattern",
        "bricks", "bricks banner pattern, field masoned banner pattern",
        "circle", "circle banner pattern",
        "creeper", "creeper banner pattern, creeper charged banner pattern",
        "cross", "cross banner pattern",
        "curly_border", "curly border banner pattern",
        "diagonal_left", "left diagonal banner pattern",
        "diagonal_right", "right diagonal banner pattern",
        "diagonal_up_left", "diagonal up left banner pattern",
        "diagonal_up_right", "diagonal up right banner pattern",
        "flow", "flow banner pattern",
        "flower", "flower banner pattern",
        "globe", "globe banner pattern",
        "gradient", "gradient banner pattern",
        "gradient_up", "gradient up banner pattern",
        "guster", "guster banner pattern",
        "half_horizontal", "horizontal half banner pattern",
        "half_horizontal_bottom", "horizontal bottom half banner pattern",
        "half_vertical", "vertical half banner pattern",
        "half_vertical_right", "right vertical half banner pattern",
        "mojang", "mojang banner pattern, thing banner pattern",
        "piglin", "piglin banner pattern, snout banner pattern",
        "rhombus", "rhombus banner pattern",
        "skull", "skull banner pattern",
        "small_stripes", "small stripes banner pattern",
        "square_bottom_left", "bottom left square banner pattern",
        "square_bottom_right", "bottom right square banner pattern",
        "square_top_left", "top left square banner pattern",
        "square_top_right", "top right square banner pattern",
        "straight_cross", "straight cross banner pattern",
        "stripe_bottom", "bottom stripe banner pattern",
        "stripe_center", "center stripe banner pattern",
        "stripe_downleft", "down left stripe banner pattern",
        "stripe_downright", "down right stripe banner pattern",
        "stripe_left", "left stripe banner pattern",
        "stripe_middle", "middle stripe banner pattern",
        "stripe_right", "right stripe banner pattern",
        "stripe_top", "top stripe banner pattern",
        "triangle_bottom", "bottom triangle banner pattern",
        "triangle_top", "top triangle banner pattern",
        "triangles_bottom", "tbottom triangles banner pattern",
        "triangles_top", "top triangles banner pattern",
        "stripe_small", "small stripe banner pattern",
        "diagonal_left_mirror", "left diagonal mirror banner pattern",
        "diagonal_right_mirror", "right diagonal mirror banner pattern",
        "circle_middle", "middle circle banner pattern",
        "rhombus_middle", "middle rhombus banner pattern",
        "half_vertical_mirror", "vertical half mirror banner pattern",
        "half_horizontal_mirror", "horizontal half mirror banner pattern"

    };

    private static volatile Map<String, PatternType> byName;

    static void register(Registrar registrar) {
        registrar.type("bannerpatterntype", "banner pattern type", PatternType.class);
        registrar.literalParser("bannerpatterntype", Banners::type);
        registrar.parser("bannerpatterntype", Banners::type);
        registrar.text(PatternType.class, Banners::name);
        registrar.type("bannerpattern", "banner pattern", Pattern.class);
        registrar.text(Pattern.class, pattern -> name(pattern.getPattern()) + " colored "
                + pattern.getColor().name().toLowerCase(Locale.ROOT).replace('_', ' '));

        // 'a creeper banner pattern colored red': only the dye colours are banner colours.
        registrar.expression("ExprNewBannerPattern", "bannerpattern", info -> (context, args) -> {
            DyeColor dye = dye(Values.single(args[1]));
            return dye != null && Values.single(args[0]) instanceof PatternType type ? new Pattern(dye, type) : null;
        }, "[a] %bannerpatterntype% colo[u]red %color%");
        // The item for a pattern type, as a loom uses: 'a creeper banner pattern item'.
        registrar.expression("ExprBannerItem", "itemtype", info -> {
            String written = info.argumentLiteral(0);
            if (written != null) {
                PatternType type = type(written);
                if (type != null && item(type) == null) {
                    throw new co.xenastudios.neoskript.api.element.SyntaxError(
                            "There is no item for the banner pattern type '" + name(type) + "'.");
                }
            }
            return (context, args) -> {
                List<Object> items = new ArrayList<>();
                for (Object value : Values.array(args[0])) {
                    Material material = value instanceof PatternType type ? item(type) : null;
                    if (material != null) {
                        items.add(new ItemType(new ItemStack(material), false));
                    }
                }
                return items.size() == 1 ? items.getFirst() : items.toArray();
            };
        }, "[a[n]] %*bannerpatterntypes% item[s]");
        registrar.expression("ExprBannerPatterns", "bannerpattern", info -> new Patterns(info.pattern()),
                "[all [[of] the]|the] banner pattern[s] of %itemstacks/itemtypes/slots/blocks%",
                "%itemstacks/itemtypes/slots/blocks%'[s] banner pattern[s]",
                "[the] %integer%[st|nd|rd|th] [banner] pattern of %itemstacks/itemtypes/slots/blocks%",
                "%itemstacks/itemtypes/slots/blocks%'[s] %integer%[st|nd|rd|th] [banner] pattern");
    }

    /** The pattern type {@code text} names, by Skript's name or its key, or {@code null}. */
    static PatternType type(String text) {
        String name = text.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        Map<String, PatternType> names = byName;
        if (names == null) {
            names = new HashMap<>();
            for (int i = 0; i < NAMES.length; i += 2) {
                PatternType type = Registry.BANNER_PATTERN.get(NamespacedKey.minecraft(NAMES[i]));
                if (type != null) {
                    for (String alias : NAMES[i + 1].split(", ")) {
                        names.put(alias, type);
                    }
                }
            }
            byName = names;
        }
        return names.get(name);
    }

    static String name(PatternType type) {
        String key = type.getKey().getKey();
        for (int i = 0; i < NAMES.length; i += 2) {
            if (NAMES[i].equals(key)) {
                return NAMES[i + 1].split(", ")[0];
            }
        }
        return key.replace('_', ' ') + " banner pattern";
    }

    /** The banner pattern item for {@code type}, or {@code null}, as Skript finds them. */
    private static Material item(PatternType type) {
        Material material = Material.getMaterial(type.getKey().getKey().toUpperCase(Locale.ROOT) + "_BANNER_PATTERN");
        if (material == null && type == PatternType.BRICKS) {
            material = Material.getMaterial("FIELD_MASONED_BANNER_PATTERN");
        }
        return material;
    }

    /** The dye colour a named colour is, or {@code null} for other colours. */
    private static DyeColor dye(Object colour) {
        if (!(colour instanceof Colors.Named named)) {
            return null;
        }
        int rgb = named.red() << 16 | named.green() << 8 | named.blue();
        for (DyeColor dye : DyeColor.values()) {
            if (dye.getColor().asRGB() == rgb) {
                return dye;
            }
        }
        return null;
    }

    /** {@code the banner patterns of X}, or {@code the 1st banner pattern of X}: changeable. */
    private record Patterns(int pattern) implements Expression, Changeable {
        @Override
        public Object get(TriggerContext context, Object[] arguments) {
            Integer placement = placement(arguments);
            List<Object> found = new ArrayList<>();
            for (Object owner : owners(arguments)) {
                List<Pattern> patterns = patterns(owner);
                if (patterns == null) {
                    continue;
                }
                if (placement == null) {
                    found.addAll(patterns);
                } else if (placement >= 1 && patterns.size() >= placement) {
                    found.add(patterns.get(placement - 1));
                }
            }
            return found.size() == 1 && placement != null ? found.getFirst() : found.toArray();
        }

        @Override
        public Class<?>[] acceptChange(Mode mode) {
            boolean one = pattern >= 2;
            return switch (mode) {
                case SET -> new Class<?>[] {Pattern.class};
                case ADD, REMOVE -> one ? null : new Class<?>[] {Pattern.class};
                case DELETE -> new Class<?>[0];
                default -> null;
            };
        }

        @Override
        public void change(TriggerContext context, Object[] arguments, Object[] delta, Mode mode) {
            Integer placement = placement(arguments);
            List<Pattern> given = new ArrayList<>();
            for (Object value : delta == null ? new Object[0] : delta) {
                if (value instanceof Pattern p) {
                    given.add(p);
                }
            }
            for (Object owner : owners(arguments)) {
                List<Pattern> patterns = patterns(owner);
                if (patterns == null) {
                    continue;
                }
                List<Pattern> next = new ArrayList<>(patterns);
                if (placement != null) {
                    if (mode == Mode.SET && !given.isEmpty()) {
                        // Filler patterns up to the place, as in Skript.
                        while (next.size() < placement) {
                            next.add(new Pattern(owner instanceof Block ? DyeColor.GRAY : DyeColor.WHITE, PatternType.BASE));
                        }
                        next.set(placement - 1, given.getFirst());
                    } else if (mode == Mode.DELETE && next.size() >= placement) {
                        next.remove(placement - 1);
                    }
                } else {
                    switch (mode) {
                        case SET -> {
                            next.clear();
                            next.addAll(given);
                        }
                        case DELETE -> next.clear();
                        case ADD -> next.addAll(given);
                        case REMOVE -> next.removeAll(given);
                        default -> {}
                    }
                }
                setPatterns(owner, next);
            }
        }

        private Integer placement(Object[] arguments) {
            if (pattern < 2) {
                return null;
            }
            Object number = Values.single(arguments[pattern == 2 ? 0 : 1]);
            return number instanceof Number n ? n.intValue() : null;
        }

        private Object[] owners(Object[] arguments) {
            return Values.array(arguments[pattern == 2 ? 1 : 0]);
        }

        private static List<Pattern> patterns(Object owner) {
            if (owner instanceof Block block) {
                return block.getState() instanceof Banner banner ? banner.getPatterns() : null;
            }
            ItemStack stack = stack(owner);
            return stack != null && stack.getItemMeta() instanceof BannerMeta meta ? meta.getPatterns() : null;
        }

        private static void setPatterns(Object owner, List<Pattern> patterns) {
            switch (owner) {
                case Block block when block.getState() instanceof Banner banner -> {
                    banner.setPatterns(patterns);
                    banner.update(true, false);
                }
                case ItemType item -> item.editMeta(meta -> {
                    if (meta instanceof BannerMeta banner) {
                        banner.setPatterns(patterns);
                    }
                });
                case ItemStack stack -> stack.editMeta(meta -> {
                    if (meta instanceof BannerMeta banner) {
                        banner.setPatterns(patterns);
                    }
                });
                case Items.Slot slot when slot.item() != null -> {
                    ItemStack stack = slot.item();
                    stack.editMeta(meta -> {
                        if (meta instanceof BannerMeta banner) {
                            banner.setPatterns(patterns);
                        }
                    });
                    slot.set(stack);
                }
                default -> {}
            }
        }

        private static ItemStack stack(Object owner) {
            return switch (owner) {
                case ItemType item -> item.stack();
                case ItemStack stack -> stack;
                case Items.Slot slot -> slot.item();
                default -> null;
            };
        }
    }
}
