/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.paper;

import co.xenastudios.neoskript.api.element.Changeable;
import co.xenastudios.neoskript.api.element.Expression;
import co.xenastudios.neoskript.api.element.TriggerContext;
import co.xenastudios.neoskript.api.element.Update;
import co.xenastudios.neoskript.api.registration.Registrar;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;

/** Enchantments, and enchantments with a level ({@code sharpness 5}), on items. */
final class Enchantments {

    private Enchantments() {}

    /** Skript's names for enchantments whose keys read differently. */
    private static final Map<String, String> NAMES = Map.of("curse of binding", "binding_curse",
            "curse of vanishing", "vanishing_curse", "sweeping", "sweeping_edge");

    private static final Pattern LEVELLED = Pattern.compile("(.+?)(?:\\s+(\\d+|[ivx]+))?");
    private static final List<String> ROMAN = List.of("i", "ii", "iii", "iv", "v", "vi", "vii", "viii", "ix", "x");

    /**
     * An enchantment with a level, Skript's {@code enchantment type}: {@code sharpness 5}, or {@code sharpness}
     * alone, which is any level of it.
     *
     * @param level the level, or -1 for any
     */
    record Levelled(Enchantment enchantment, int level) {
        @Override
        public String toString() {
            return Enchantments.name(enchantment) + (level < 0 ? "" : " " + level);
        }
    }

    static void register(Registrar registrar) {
        registrar.type("enchantment", "enchantment", Enchantment.class);
        registrar.literalParser("enchantment", Enchantments::enchantment);
        registrar.parser("enchantment", Enchantments::enchantment);
        registrar.text(Enchantment.class, Enchantments::name);
        registrar.type("enchantmenttype", "enchantment type", Levelled.class);
        registrar.literalParser("enchantmenttype", Enchantments::levelled);
        registrar.parser("enchantmenttype", Enchantments::levelled);
        registrar.converter(Enchantment.class, Levelled.class, enchantment -> new Levelled(enchantment, -1));
        registrar.converter(Levelled.class, Enchantment.class, Levelled::enchantment);
        // 'sharpness' is any level of it; levels given have to match.
        registrar.comparison(Levelled.class, Levelled.class, (a, b) -> a.enchantment().equals(b.enchantment())
                && (a.level() < 0 || b.level() < 0 || a.level() == b.level()));
        registrar.comparison(Enchantment.class, Levelled.class, (a, b) -> a.equals(b.enchantment()));

        registrar.effect("EffEnchant", info -> {
            boolean disenchant = info.pattern() == 1;
            return Update.of(0, (context, args) -> {
                Object[] items = (Object[]) args[0];
                Object[] changed = new Object[items.length];
                for (int i = 0; i < items.length; i++) {
                    changed[i] = items[i] instanceof ItemType item
                            ? disenchant ? item.withMeta(meta -> meta.removeEnchantments()) : enchanted(item, (Object[]) args[1])
                            : items[i];
                }
                return changed;
            });
        }, "enchant %~objects% with %enchantmenttypes%", "disenchant %~objects%");
        registrar.expression("ExprEnchantmentLevel", "integer", info -> new Level(),
                "[the] [enchant[ment]] level[s] of %enchantments% (on|of) %itemtypes%",
                "[the] %enchantments% [enchant[ment]] level[s] (on|of) %itemtypes%",
                "%itemtypes%'[s] %enchantments% [enchant[ment]] level[s]",
                "%itemtypes%'[s] [enchant[ment]] level[s] of %enchantments%");
        registrar.expression("ExprEnchantments", "enchantmenttype", info -> (context, args) -> {
            List<Object> found = new ArrayList<>();
            for (Object value : (Object[]) args[0]) {
                if (value instanceof ItemType item && item.stack().getItemMeta() != null) {
                    item.stack().getItemMeta().getEnchants().forEach((enchantment, level) -> found.add(new Levelled(enchantment, level)));
                }
            }
            return found.toArray();
        }, "[the] enchantments of %itemtypes%", "%itemtypes%'[s] enchantments");
        registrar.condition("CondIsEnchanted", info -> {
            boolean negated = info.pattern() == 1;
            int bound = info.hasTag("greater") ? 1 : info.hasTag("lesser") ? -1 : 0;
            return (context, args) -> {
                Object[] items = (Object[]) args[0];
                Object[] wanted = args[1] == null ? null : (Object[]) args[1];
                if (items.length == 0) {
                    return negated;
                }
                for (Object value : items) {
                    Map<Enchantment, Integer> enchants = value instanceof ItemType item && item.stack().getItemMeta() != null
                            ? item.stack().getItemMeta().getEnchants() : Map.of();
                    boolean has = wanted == null ? !enchants.isEmpty() : java.util.Arrays.stream(wanted).allMatch(w ->
                            w instanceof Levelled levelled && enchants.containsKey(levelled.enchantment())
                                    && fits(enchants.get(levelled.enchantment()), levelled.level(), bound));
                    if (has == negated) {
                        return false;
                    }
                }
                return true;
            };
        }, "%itemtypes% (is|are) enchanted [with %enchantmenttypes% [(greater:or (greater|better)|lesser:or (lesser|worse))]]",
                "%itemtypes% (isn't|is not|aren't|are not) enchanted [with %enchantmenttypes% [(greater:or (greater|better)|lesser:or (lesser|worse))]]");
    }

    private static boolean fits(int level, int wanted, int bound) {
        if (wanted < 0) {
            return true;
        }
        return bound > 0 ? level >= wanted : bound < 0 ? level <= wanted : level == wanted;
    }

    /** A copy of {@code item} with the enchantments added; one with no level given gets level 1. */
    private static ItemType enchanted(ItemType item, Object[] enchantments) {
        return item.withMeta(meta -> {
            for (Object value : enchantments) {
                Levelled levelled = value instanceof Levelled l ? l : value instanceof Enchantment e ? new Levelled(e, -1) : null;
                if (levelled != null) {
                    meta.addEnchant(levelled.enchantment(), levelled.level() < 0 ? 1 : levelled.level(), true);
                }
            }
        });
    }

    /** The enchantment {@code text} names, by Skript's name or its key, or {@code null}. */
    static Enchantment enchantment(String text) {
        String name = text.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        String key = NAMES.getOrDefault(name, name.replace(' ', '_'));
        NamespacedKey namespaced = NamespacedKey.fromString(key);
        return namespaced == null ? null : RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT).get(namespaced);
    }

    /** {@code sharpness 5}, {@code sharpness v} or {@code sharpness}: an enchantment with a level, or {@code null}. */
    static Levelled levelled(String text) {
        Matcher matcher = LEVELLED.matcher(text.strip().toLowerCase(Locale.ROOT));
        if (!matcher.matches()) {
            return null;
        }
        Enchantment enchantment = enchantment(matcher.group(1));
        if (enchantment == null) {
            // 'curse of vanishing' has no level, so the whole text may be its name.
            enchantment = matcher.group(2) == null ? null : enchantment(text);
            return enchantment == null ? null : new Levelled(enchantment, -1);
        }
        String level = matcher.group(2);
        if (level == null) {
            return new Levelled(enchantment, -1);
        }
        int roman = ROMAN.indexOf(level);
        return new Levelled(enchantment, roman >= 0 ? roman + 1 : Integer.parseInt(level));
    }

    /** How Skript writes an enchantment: {@code sharpness}, {@code curse of vanishing}. */
    static String name(Enchantment enchantment) {
        String key = enchantment.getKey().getKey();
        for (Map.Entry<String, String> entry : NAMES.entrySet()) {
            if (entry.getValue().equals(key)) {
                return entry.getKey();
            }
        }
        return key.replace('_', ' ');
    }

    /** {@code the level of sharpness on {_item}}: settable, and 0 or less takes the enchantment off. */
    private static final class Level implements Expression, Changeable {
        @Override
        public Object get(TriggerContext context, Object[] arguments) {
            List<Object> levels = new ArrayList<>();
            for (Object item : items(arguments)) {
                for (Object value : enchantments(arguments)) {
                    if (item instanceof ItemType type && value instanceof Enchantment enchantment
                            && type.stack().getItemMeta() != null && type.stack().getItemMeta().hasEnchant(enchantment)) {
                        levels.add((long) type.stack().getItemMeta().getEnchantLevel(enchantment));
                    }
                }
            }
            return levels.size() == 1 ? levels.getFirst() : levels.toArray();
        }

        @Override
        public Class<?>[] acceptChange(Mode mode) {
            return switch (mode) {
                case SET, ADD, REMOVE -> new Class<?>[] {Number.class};
                case DELETE, RESET -> new Class<?>[0];
                default -> null;
            };
        }

        @Override
        public void change(TriggerContext context, Object[] arguments, Object[] delta, Mode mode) {
            int by = delta != null && delta.length > 0 && delta[0] instanceof Number n ? n.intValue() : 0;
            for (Object item : items(arguments)) {
                for (Object value : enchantments(arguments)) {
                    if (!(item instanceof ItemType type) || !(value instanceof Enchantment enchantment)) {
                        continue;
                    }
                    type.editMeta(meta -> {
                        int current = meta.getEnchantLevel(enchantment);
                        int next = switch (mode) {
                            case ADD -> current + by;
                            case REMOVE -> current - by;
                            case SET -> by;
                            default -> 0;
                        };
                        if (next <= 0) {
                            meta.removeEnchant(enchantment);
                        } else {
                            meta.addEnchant(enchantment, next, true);
                        }
                    });
                }
            }
        }

        private static Object[] items(Object[] arguments) {
            int index = arguments[0] instanceof Object[] first && first.length > 0 && first[0] instanceof Enchantment ? 1 : 0;
            return arguments[index] instanceof Object[] array ? array : new Object[] {arguments[index]};
        }

        private static Object[] enchantments(Object[] arguments) {
            int index = arguments[0] instanceof Object[] first && first.length > 0 && first[0] instanceof Enchantment ? 0 : 1;
            return arguments[index] instanceof Object[] array ? array : new Object[] {arguments[index]};
        }
    }
}
