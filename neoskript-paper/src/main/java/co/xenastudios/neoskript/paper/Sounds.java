/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.paper;

import co.xenastudios.neoskript.api.registration.Registrar;
import co.xenastudios.neoskript.runtime.Values;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/** Playing sounds, as Skript does. */
final class Sounds {

    private Sounds() {}

    static void register(Registrar registrar) {
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
