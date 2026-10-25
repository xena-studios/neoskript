/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.paper;

import co.xenastudios.neoskript.api.registration.Registrar;
import co.xenastudios.neoskript.runtime.Values;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/** Playing and stopping sounds, and sound categories, as Skript has them. */
final class Sounds {

    private Sounds() {}

    /** What {@code stop playing sound "..."} accepts as a sound's key, when it isn't one Minecraft has. */
    private static final Pattern KEY = Pattern.compile("([a-z0-9._-]+:)?[a-z0-9/._-]+");

    /** Skript's names for sound categories, from its default.lang. */
    private static final Map<String, String> CATEGORY_NAMES = new LinkedHashMap<>();

    static {
        CATEGORY_NAMES.put("ambient", "ambient category, environment category");
        CATEGORY_NAMES.put("blocks", "block category, blocks category");
        CATEGORY_NAMES.put("hostile", "hostile category, hostile creature category, hostile creatures category, "
                + "hostile mob category, hostile mobs category");
        CATEGORY_NAMES.put("master", "master category, master volume category");
        CATEGORY_NAMES.put("music", "music category");
        CATEGORY_NAMES.put("neutral", "neutral category, friendly creature category, friendly creatures category, "
                + "friendly mob category, friendly mobs category");
        CATEGORY_NAMES.put("players", "player category, players category");
        CATEGORY_NAMES.put("records", "record category, records category, jukebox category, jukeboxes category, "
                + "noteblock category, noteblocks category, note block category, note blocks category");
        CATEGORY_NAMES.put("voice", "voice category, speech category");
        CATEGORY_NAMES.put("weather", "weather category");
        CATEGORY_NAMES.put("ui", "ui, user interface");
    }

    static void register(Registrar registrar) {
        Map<String, SoundCategory> categories = new LinkedHashMap<>();
        Map<SoundCategory, String> names = new java.util.EnumMap<>(SoundCategory.class);
        for (SoundCategory category : SoundCategory.values()) {
            String options = CATEGORY_NAMES.get(category.name().toLowerCase(Locale.ROOT));
            if (options == null) {
                String name = category.name().replace('_', ' ').toLowerCase(Locale.ROOT) + " sound category";
                categories.put(name, category);
                names.put(category, name);
                continue;
            }
            for (String option : options.split(",")) {
                categories.put(option.strip(), category);
                names.putIfAbsent(category, option.strip());
            }
        }
        registrar.namedType("soundcategory", "sound category", SoundCategory.class, categories);
        registrar.alias("soundcategory", "sound category", "sound categories");
        registrar.text(SoundCategory.class, names::get);

        registrar.effect("EffPlaySound", info -> {
            boolean emittersFirst = info.pattern() == 0;
            return (context, args) -> {
                Long seed = args[1] instanceof Number n ? n.longValue() : null;
                SoundCategory category = args[2] instanceof SoundCategory c ? c : SoundCategory.MASTER;
                float volume = args[3] instanceof Number v ? v.floatValue() : 1;
                float pitch = args[4] instanceof Number p ? p.floatValue() : 1;
                Object emitters = emittersFirst ? args[5] : args[6];
                Object players = emittersFirst ? args[6] : args[5];
                List<Sound> sounds = new ArrayList<>();
                for (Object name : Values.array(args[0])) {
                    NamespacedKey key = key(Values.text(name));
                    if (key != null) {
                        Sound.Builder sound = Sound.sound().type(key).source(category).volume(volume).pitch(pitch);
                        if (seed != null) {
                            sound.seed(seed);
                        }
                        sounds.add(sound.build());
                    }
                }
                if (sounds.isEmpty()) {
                    return;
                }
                if (players != null) {
                    for (Object value : Values.array(players)) {
                        if (!(value instanceof Player player)) {
                            continue;
                        }
                        if (emitters == null) {
                            Location at = player.getLocation();
                            for (Sound sound : sounds) {
                                player.playSound(sound, at.getX(), at.getY(), at.getZ());
                            }
                            continue;
                        }
                        for (Object emitter : Values.array(emitters)) {
                            for (Sound sound : sounds) {
                                if (emitter instanceof Location location) {
                                    player.playSound(sound, location.getX(), location.getY(), location.getZ());
                                } else if (emitter instanceof Entity entity) {
                                    player.playSound(sound, entity);
                                }
                            }
                        }
                    }
                } else if (emitters != null) {
                    for (Object emitter : Values.array(emitters)) {
                        for (Sound sound : sounds) {
                            if (emitter instanceof Entity entity) {
                                entity.getWorld().playSound(sound, entity);
                            } else if (emitter instanceof Location location && location.getWorld() != null) {
                                location.getWorld().playSound(sound, location.getX(), location.getY(), location.getZ());
                            }
                        }
                    }
                }
            };
        }, "play sound[s] %strings% [[with] seed %-number%] [(in|from) %-soundcategory%] [(at|with) volume %-number%] "
                + "[(and|at|with) pitch %-number%] (at|on|from) %locations/entities% [(to|for) %-players%]",
                "play sound[s] %strings% [[with] seed %-number%] [(in|from) %-soundcategory%] [(at|with) volume %-number%] "
                        + "[(and|at|with) pitch %-number%] [(to|for) %players%] [(at|on|from) %-locations/entities%]");

        registrar.effect("EffStopSound", info -> {
            boolean all = info.hasTag("all");
            return (context, args) -> {
                // Stopping every sound wants its category given; a sound is stopped in the master one.
                SoundCategory category = args[1] instanceof SoundCategory c ? c : null;
                Object[] targets = Values.array(args[2]);
                if (all) {
                    for (Object target : targets) {
                        if (target instanceof Player player) {
                            if (category == null) {
                                player.stopAllSounds();
                            } else {
                                player.stopSound(category);
                            }
                        }
                    }
                    return;
                }
                for (Object name : Values.array(args[0])) {
                    String text = Values.text(name);
                    NamespacedKey key = key(text);
                    org.bukkit.Sound sound = key == null ? null : Registry.SOUNDS.get(key);
                    for (Object target : targets) {
                        if (!(target instanceof Player player)) {
                            continue;
                        }
                        if (sound != null) {
                            player.stopSound(sound, category);
                        } else if (KEY.matcher(text).matches()) {
                            player.stopSound(text, category);
                        }
                    }
                }
            };
        }, "stop (all:all sound[s]|sound[s] %-strings%) [(in [the]|from) %-soundcategory%] [(from playing to|for) %players%]",
                "stop playing sound[s] %strings% [(in [the]|from) %-soundcategory%] [(to|for) %players%]");
    }

    /**
     * A sound's key from its name: a Minecraft sound by its enum name ({@code BLOCK_STONE_BREAK}), or any key
     * ({@code block.stone.break}), as Skript reads them. {@code null} if it's neither.
     */
    @SuppressWarnings({"deprecation", "removal"})
    static NamespacedKey key(String name) {
        String upper = name.toUpperCase(Locale.ENGLISH);
        try {
            return org.bukkit.Sound.valueOf(upper).getKey();
        } catch (Exception notOne) {
            // Not one of Minecraft's by its enum name.
        }
        return NamespacedKey.fromString(name.toLowerCase(Locale.ENGLISH));
    }
}
