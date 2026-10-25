/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.paper;

import co.xenastudios.neoskript.api.element.Changeable;
import co.xenastudios.neoskript.api.element.Condition;
import co.xenastudios.neoskript.api.element.ElementInfo;
import co.xenastudios.neoskript.api.element.Expression;
import co.xenastudios.neoskript.api.element.SettableCondition;
import co.xenastudios.neoskript.api.element.SyntaxError;
import co.xenastudios.neoskript.api.element.TriggerContext;
import co.xenastudios.neoskript.api.registration.Patterns;
import co.xenastudios.neoskript.api.registration.Registrar;
import co.xenastudios.neoskript.runtime.Timespan;
import co.xenastudios.neoskript.runtime.Values;
import com.destroystokyo.paper.ClientOption;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

/**
 * More about players: what they're doing (sneaking, flying, blocking), their client's settings, bans and
 * operators, experience levels, hunger, speeds, the tab list, and making them do things (fly, look, respawn).
 */
final class PlayerSyntax {

    private PlayerSyntax() {}

    /** The keys of a player's movement input, Skript's {@code input key}s. */
    enum InputKey {
        FORWARD("forward movement key", "forward key"),
        BACKWARD("backward movement key", "backward key"),
        LEFT("left movement key", "left key"),
        RIGHT("right movement key", "right key"),
        JUMP("jump key", "jumping key"),
        SNEAK("sneak key", "sneaking key"),
        SPRINT("sprint key", "sprinting key");

        final String[] names;

        InputKey(String... names) {
            this.names = names;
        }

        boolean pressed(Input input) {
            return switch (this) {
                case FORWARD -> input.isForward();
                case BACKWARD -> input.isBackward();
                case LEFT -> input.isLeft();
                case RIGHT -> input.isRight();
                case JUMP -> input.isJump();
                case SNEAK -> input.isSneak();
                case SPRINT -> input.isSprint();
            };
        }
    }

    static void register(Registrar registrar) {
        registerTypes(registrar);
        registerConditions(registrar);
        registerEffects(registrar);
        registerExpressions(registrar);
        registerMoreExpressions(registrar);
    }

    private static void registerTypes(Registrar registrar) {
        Map<String, InputKey> keys = new LinkedHashMap<>();
        for (InputKey key : InputKey.values()) {
            for (String name : key.names) {
                keys.put(name, key);
            }
        }
        registrar.namedType("inputkey", "input key", InputKey.class, keys);
        registrar.text(InputKey.class, key -> key.names[0]);

        Map<String, PlayerResourcePackStatusEvent.Status> states = new LinkedHashMap<>();
        names(states, PlayerResourcePackStatusEvent.Status.ACCEPTED, "accept", "accepted");
        names(states, PlayerResourcePackStatusEvent.Status.DECLINED, "decline", "refuse", "reject", "declined", "refused",
                "rejected");
        names(states, PlayerResourcePackStatusEvent.Status.FAILED_DOWNLOAD, "download fail", "fail", "failed to download",
                "failed");
        names(states, PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED, "successfully load", "successfully install",
                "success", "successfully loaded", "successfully installed");
        names(states, PlayerResourcePackStatusEvent.Status.DOWNLOADED, "downloaded");
        names(states, PlayerResourcePackStatusEvent.Status.INVALID_URL, "invalid url");
        names(states, PlayerResourcePackStatusEvent.Status.FAILED_RELOAD, "failed reload", "failed to reload");
        names(states, PlayerResourcePackStatusEvent.Status.DISCARDED, "discarded");
        registrar.namedType("resourcepackstate", "resource pack state", PlayerResourcePackStatusEvent.Status.class, states);
        registrar.text(PlayerResourcePackStatusEvent.Status.class, status -> firstName(states, status));
    }

    /** Puts {@code value} in {@code map} under each of {@code names}. */
    static <T> void names(Map<String, T> map, T value, String... names) {
        for (String name : names) {
            map.put(name, value);
        }
    }

    /** The first name {@code value} has in {@code map}, as Skript writes it. */
    static <T> String firstName(Map<String, T> map, T value) {
        for (Map.Entry<String, T> entry : map.entrySet()) {
            if (entry.getValue() == value) {
                return entry.getKey();
            }
        }
        return String.valueOf(value).toLowerCase(Locale.ROOT);
    }

    // ---------------------------------------------------------------- conditions

    private static void registerConditions(Registrar registrar) {
        can(registrar, "CondChatColors", "see chat colo[u]r[s|ing]", "players", Player.class,
                player -> player.getClientOption(ClientOption.CHAT_COLORS_ENABLED));
        have(registrar, "CondChatFiltering", "(chat|text) filtering (on|enabled)", "players", Player.class,
                player -> player.getClientOption(ClientOption.TEXT_FILTERING_ENABLED));
        registrar.condition("CondChatVisibility", info -> {
            int pattern = info.pattern();
            return (context, args) -> {
                if (!(Values.single(args[0]) instanceof Player player)) {
                    return false;
                }
                ClientOption.ChatVisibility current = player.getClientOption(ClientOption.CHAT_VISIBILITY);
                return switch (pattern) {
                    case 0 -> current == ClientOption.ChatVisibility.FULL;
                    case 1 -> current == ClientOption.ChatVisibility.SYSTEM;
                    case 2 -> current == ClientOption.ChatVisibility.HIDDEN;
                    case 3 -> current != ClientOption.ChatVisibility.FULL;
                    default -> current != ClientOption.ChatVisibility.SYSTEM;
                };
            };
        }, "%player% can see all messages [in chat]",
                "%player% can only see (commands|system messages) [in chat]",
                "%player% can('t|[ ]not) see any (command[s]|message[s]) [in chat]",
                "%player% can('t|[ ]not) see all messages [in chat]",
                "%player% can('t|[ ]not) only see (commands|system messages) [in chat]");
        have(registrar, "CondHasClientWeather", "[a] (client|custom) weather [set]", "players", Player.class,
                player -> player.getPlayerWeather() != null);
        have(registrar, "CondHasResourcePack", "[a] resource pack [(loaded|installed)]", "players", Player.class,
                Player::hasResourcePack);

        registrar.condition("CondIsBanned", info -> {
            boolean negated = info.pattern() >= 2;
            boolean ip = info.pattern() % 2 != 0;
            boolean and = info.and(0);
            return (context, args) -> negated != Condition.check(owners(args[0]), and, value -> banned(value, ip));
        }, "%offlineplayers/strings% (is|are) banned", "%players/strings% (is|are) IP(-| |)banned",
                "%offlineplayers/strings% (isn't|is not|aren't|are not) banned",
                "%players/strings% (isn't|is not|aren't|are not) IP(-| |)banned");

        EntitySyntax.conditionPatterns(registrar, "CondIsFlying", Player.class, Player::isFlying, Player::setFlying,
                Patterns.isProperty("flying", "players"));
        EntitySyntax.conditionPatterns(registrar, "CondIsOp", OfflinePlayer.class, OfflinePlayer::isOp, OfflinePlayer::setOp,
                Patterns.isProperty("[[a] server|an] op[erator][s]", "offlineplayers"));
        EntitySyntax.conditionPatterns(registrar, "CondIsSneaking", Player.class, Player::isSneaking, Player::setSneaking,
                Patterns.isProperty("sneaking", "players"));
        EntitySyntax.conditionPatterns(registrar, "CondIsSprinting", Player.class, Player::isSprinting, Player::setSprinting,
                Patterns.isProperty("sprinting", "players"));
        EntitySyntax.conditionPatterns(registrar, "CondIsBlocking", Player.class, Player::isBlocking, null,
                Patterns.isProperty("(blocking|defending) [with [a] shield]", "players"));
        EntitySyntax.conditionPatterns(registrar, "CondIsClimbing", LivingEntity.class, LivingEntity::isClimbing, null,
                Patterns.isProperty("climbing", "livingentities"));
        EntitySyntax.conditionPatterns(registrar, "CondIsOnGround", Entity.class, Entity::isOnGround, null,
                Patterns.isProperty("on [the] ground", "entities"));

        registrar.condition("CondIsHandRaised", info -> {
            boolean negated = info.pattern() % 2 == 1;
            EquipmentSlot hand = info.pattern() >= 4 ? EquipmentSlot.OFF_HAND : info.hasTag("main") ? EquipmentSlot.HAND : null;
            boolean and = info.and(0);
            return (context, args) -> negated != Condition.check(owners(args[0]), and, value -> value instanceof LivingEntity living
                    && living.isHandRaised() && (hand == null || living.getHandRaised() == hand));
        }, "%livingentities%'[s] [:main] hand[s] (is|are) raised",
                "%livingentities%'[s] [:main] hand[s] (isn't|is not|aren't|are not) raised",
                "[:main] hand[s] of %livingentities% (is|are) raised",
                "[:main] hand[s] of %livingentities% (isn't|is not|aren't|are not) raised",
                "%livingentities%'[s] off[ |-]hand[s] (is|are) raised",
                "%livingentities%'[s] off[ |-]hand[s] (isn't|is not|aren't|are not) raised",
                "off[ |-]hand[s] of %livingentities% (is|are) raised",
                "off[ |-]hand[s] of %livingentities% (isn't|is not|aren't|are not) raised");

        // Whether players can see entities; settable, and resetting shows them again.
        registrar.condition("CondCanSee", info -> {
            boolean viewersFirst = info.pattern() == 1 || info.pattern() == 3;
            boolean negated = info.pattern() > 1 ^ info.hasTag("invisible");
            int viewersAt = viewersFirst ? 0 : 1;
            boolean viewersAnd = info.and(viewersAt);
            boolean entitiesAnd = info.and(1 - viewersAt);
            return new SettableCondition() {
                @Override
                public boolean check(TriggerContext context, Object[] args) {
                    Object[] entities = owners(args[1 - viewersAt]);
                    return negated != Condition.check(owners(args[viewersAt]), viewersAnd, viewer -> viewer instanceof Player player
                            && Condition.check(entities, entitiesAnd, entity -> entity instanceof Entity e && player.canSee(e)));
                }

                @Override
                public void set(TriggerContext context, Object[] args, boolean value) {
                    boolean show = value != negated;
                    for (Object viewer : owners(args[viewersAt])) {
                        for (Object entity : owners(args[1 - viewersAt])) {
                            if (viewer instanceof Player player && entity instanceof Entity e) {
                                if (show) {
                                    player.showEntity(plugin(), e);
                                } else {
                                    player.hideEntity(plugin(), e);
                                }
                            }
                        }
                    }
                }
            };
        }, "%entities% (is|are) [visible|:invisible] for %players%", "%players% can see %entities%",
                "%entities% (is|are)(n't| not) [visible|:invisible] for %players%", "%players% can('t| not) see %entities%");

        registrar.condition("CondHasScoreboardTag", info -> {
            boolean negated = info.pattern() == 1;
            boolean and = info.and(0);
            boolean tagsAnd = info.and(1);
            return (context, args) -> {
                Object[] tags = owners(args[1]);
                return negated != Condition.check(owners(args[0]), and, entity -> entity instanceof Entity e
                        && Condition.check(tags, tagsAnd, tag -> e.getScoreboardTags().contains(Values.text(tag))));
            };
        }, Patterns.hasProperty("[the] score[ ]board tag[s] %strings%", "entities"));

        registrar.condition("CondIsPressingKey", info -> {
            boolean negated = info.pattern() == 1 || info.pattern() == 3;
            boolean past = info.pattern() > 1;
            if (past && !info.isEvent(org.bukkit.event.player.PlayerInputEvent.class)) {
                co.xenastudios.neoskript.api.element.Warnings.warn(
                        "Checking the past state of a player's input outside the 'player input' event has no effect.");
            } else if (past && co.xenastudios.neoskript.api.element.Warnings.delayed()) {
                co.xenastudios.neoskript.api.element.Warnings.warn(
                        "Checking the past state of a player's input after the event has passed has no effect.");
            }
            boolean and = info.and(0);
            boolean keysAnd = info.and(1);
            return (context, args) -> {
                Object[] wanted = owners(args[1]);
                Object event = context.event();
                return negated != Condition.check(owners(args[0]), and, value -> {
                    if (!(value instanceof Player player)) {
                        return false;
                    }
                    // The event's player's new input is the event's; their current one is still the old one.
                    Input input = !past && event instanceof org.bukkit.event.player.PlayerInputEvent inputEvent
                            && inputEvent.getPlayer().equals(player) ? inputEvent.getInput() : player.getCurrentInput();
                    return Condition.check(wanted, keysAnd, key -> key instanceof InputKey k && k.pressed(input));
                });
            };
        }, "%players% (is|are) pressing %inputkeys%", "%players% (isn't|is not|aren't|are not) pressing %inputkeys%",
                "%players% (was|were) pressing %inputkeys%", "%players% (wasn't|was not|weren't|were not) pressing %inputkeys%");

        registrar.condition("CondResourcePack", info -> {
            boolean negated = info.pattern() == 1;
            boolean and = info.and(0);
            return (context, args) -> {
                if (!(context.event() instanceof PlayerResourcePackStatusEvent event)) {
                    return negated;
                }
                return negated != Condition.check(owners(args[0]), and, state -> state == event.getStatus());
            };
        }, "[the] resource pack (was|is|has) [been] %resourcepackstate%",
                "[the] resource pack (was|is|has)(n't| not) [been] %resourcepackstate%");
        registrar.condition("CondRespawnLocation", info -> {
            boolean negated = info.mark() == 1;
            boolean bed = info.hasTag("bed");
            return (context, args) -> context.event() instanceof org.bukkit.event.player.PlayerRespawnEvent event
                    && (bed ? event.isBedSpawn() : event.isAnchorSpawn()) != negated;
        }, "[the] respawn location (was|is)[1¦(n'| no)t] [a] (:bed|respawn anchor)");
    }

    /** Whether a player, offline player or IP (or name) is banned, or IP-banned. */
    private static boolean banned(Object value, boolean ip) {
        if (value instanceof Player player) {
            if (ip) {
                InetSocketAddress address = player.getAddress();
                return address != null && Bukkit.getIPBans().contains(address.getAddress().getHostAddress());
            }
            return player.isBanned();
        }
        if (value instanceof OfflinePlayer offline) {
            return offline.isBanned();
        }
        if (value instanceof String text) {
            return Bukkit.getIPBans().contains(text) || !ip && Bukkit.getBannedPlayers().stream()
                    .anyMatch(banned -> banned != null && text.equals(banned.getName()));
        }
        return false;
    }

    /** {@code %owners% can <property>} and {@code %owners% (can't|cannot|can not) <property>}. */
    private static <T> void can(Registrar registrar, String id, String property, String owners, Class<T> type,
            Predicate<? super T> test) {
        EntitySyntax.conditionPatterns(registrar, id, type, test, null, "%" + owners + "% can " + property,
                "%" + owners + "% (can't|cannot|can not) " + property);
    }

    /** {@code %owners% (has|have) <property>} and its negation. */
    private static <T> void have(Registrar registrar, String id, String property, String owners, Class<T> type,
            Predicate<? super T> test) {
        EntitySyntax.conditionPatterns(registrar, id, type, test, null, Patterns.hasProperty(property, owners));
    }

    // ---------------------------------------------------------------- effects

    private static void registerEffects(Registrar registrar) {
        registrar.effect("EffBan", info -> {
            boolean ban = info.pattern() % 2 == 0;
            boolean ipBan = info.pattern() >= 2;
            boolean kick = info.hasTag("kick");
            return (context, args) -> {
                Component reason = ban && args.length > 1 && args[1] != null ? Messages.component(args[1]) : null;
                String legacy = reason == null ? null : Messages.LEGACY_SECTION.serialize(reason);
                java.util.Date expires = ban && args.length > 2 && args[2] instanceof Timespan time
                        ? new java.util.Date(System.currentTimeMillis() + time.millis()) : null;
                for (Object value : owners(args[0])) {
                    switch (value) {
                        case Player player -> {
                            if (ipBan) {
                                InetSocketAddress address = player.getAddress();
                                if (address == null) {
                                    // An unknown address can't be banned.
                                    return;
                                }
                                ban(BanList.Type.IP, address.getAddress().getHostAddress(), ban, legacy, expires);
                            } else {
                                ban(BanList.Type.NAME, player.getName(), ban, legacy, expires);
                            }
                            if (kick) {
                                if (reason == null) {
                                    player.kick();
                                } else {
                                    player.kick(reason);
                                }
                            }
                        }
                        case OfflinePlayer offline -> {
                            if (offline.getName() == null) {
                                return;
                            }
                            ban(BanList.Type.NAME, offline.getName(), ban, legacy, expires);
                        }
                        case String ip -> {
                            ban(BanList.Type.IP, ip, ban, legacy, expires);
                            ban(BanList.Type.NAME, ip, ban, legacy, expires);
                        }
                        default -> {}
                    }
                }
            };
        }, "ban [kick:and kick] %strings/offlineplayers% [(by reason of|because [of]|on account of|due to) %-text%] [for %-timespan%]",
                "unban %strings/offlineplayers%",
                "ban [kick:and kick] %players% by IP [(by reason of|because [of]|on account of|due to) %-text%] [for %-timespan%]",
                "unban %players% by IP",
                "IP(-| )ban [kick:and kick] %players% [(by reason of|because [of]|on account of|due to) %-text%] [for %-timespan%]",
                "(IP(-| )unban|un[-]IP[-]ban) %players%");

        registrar.effect("EffConnect", info -> {
            boolean transfer = info.pattern() == 2;
            return (context, args) -> {
                Object server = Values.single(args[1]);
                List<Player> players = new ArrayList<>();
                for (Object value : owners(args[0])) {
                    if (value instanceof Player player && player.isOnline()) {
                        players.add(player);
                    }
                }
                if (server == null || players.isEmpty()) {
                    return;
                }
                String name = Values.text(server);
                if (transfer) {
                    Object port = args.length > 2 ? Values.single(args[2]) : null;
                    if (args.length > 2 && args[2] != null && !(port instanceof Number)) {
                        return;
                    }
                    int number = port instanceof Number n ? n.intValue() : 25565;
                    players.forEach(player -> player.transfer(name, number));
                    return;
                }
                // Asks the proxy to move them: BungeeCord's (and Velocity's) "Connect" message.
                org.bukkit.plugin.Plugin plugin = plugin();
                if (!Bukkit.getMessenger().isOutgoingChannelRegistered(plugin, "BungeeCord")) {
                    Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, "BungeeCord");
                }
                for (Player player : players) {
                    java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
                    try (java.io.DataOutputStream out = new java.io.DataOutputStream(bytes)) {
                        out.writeUTF("Connect");
                        out.writeUTF(name);
                    } catch (java.io.IOException e) {
                        throw new java.io.UncheckedIOException(e);
                    }
                    player.sendPluginMessage(plugin, "BungeeCord", bytes.toByteArray());
                }
            };
        }, "connect %players% to [proxy|bungeecord] [server] %string%", "send %players% to [proxy|bungeecord] server %string%",
                "transfer %players% to server %string% [on port %-number%]");

        registrar.effect("EffEntityVisibility", info -> {
            boolean reveal = info.pattern() == 1;
            return (context, args) -> {
                Object[] viewers = args[1] == null ? Bukkit.getOnlinePlayers().toArray() : owners(args[1]);
                for (Object viewer : viewers) {
                    for (Object entity : owners(args[0])) {
                        if (viewer instanceof Player player && entity instanceof Entity e) {
                            if (reveal) {
                                player.showEntity(plugin(), e);
                            } else {
                                player.hideEntity(plugin(), e);
                            }
                        }
                    }
                }
            };
        }, "hide %entities% [(from|for) %-players%]", "reveal %entities% [(to|for|from) %-players%]");

        registrar.effect("EffKeepInventory", info -> {
            boolean items = info.pattern() == 0 || info.mark() == 1;
            boolean experience = info.pattern() == 1 || info.mark() == 1;
            return (context, args) -> {
                if (context.event() instanceof org.bukkit.event.entity.PlayerDeathEvent death) {
                    if (items) {
                        death.setKeepInventory(true);
                    }
                    if (experience) {
                        death.setKeepLevel(true);
                    }
                }
            };
        }, "keep [the] (inventory|items) [(1¦and [e]xp[erience][s] [point[s]])]",
                "keep [the] [e]xp[erience][s] [point[s]] [(1¦and (inventory|items))]");

        registrar.effect("EffLook", info -> {
            io.papermc.paper.entity.LookAnchor anchor = info.hasTag("feet") ? io.papermc.paper.entity.LookAnchor.FEET
                    : io.papermc.paper.entity.LookAnchor.EYES;
            return (context, args) -> {
                Object target = Values.single(args[1]);
                if (target == null) {
                    return;
                }
                Float speed = Values.single(args[2]) instanceof Number n ? n.floatValue() : null;
                Float maxPitch = Values.single(args[3]) instanceof Number n ? n.floatValue() : null;
                for (Object value : owners(args[0])) {
                    if (value instanceof LivingEntity living) {
                        look(living, target, anchor, speed, maxPitch);
                    }
                }
            };
        }, "(force|make) %livingentities% [to] (face [towards]|look [(at|towards)]) %entity%'s (feet:feet|eyes) "
                + "[(at|with) [head] [rotation] speed %-number%] [[and] max[imum] [head] pitch %-number%]",
                "(force|make) %livingentities% [to] (face [towards]|look [(at|towards)]) [the] (feet:feet|eyes) of %entity% "
                        + "[(at|with) [head] [rotation] speed %-number%] [[and] max[imum] [head] pitch %-number%]",
                "(force|make) %livingentities% [to] (face [towards]|look [(at|towards)]) %vector/location/entity% "
                        + "[(at|with) [head] [rotation] speed %-number%] [[and] max[imum] [head] pitch %-number%]");

        registrar.effect("EffMakeFly", info -> {
            boolean flying = info.mark() != 1;
            return (context, args) -> each(args[0], Player.class, player -> {
                player.setAllowFlight(flying);
                player.setFlying(flying);
            });
        }, "force %players% to [(start|1¦stop)] fly[ing]", "make %players% (start|1¦stop) flying", "make %players% fly");
        registrar.effect("EffOp", info -> {
            boolean op = !info.hasTag("de");
            return (context, args) -> each(args[0], OfflinePlayer.class, player -> player.setOp(op));
        }, "[de:de[-]]op %offlineplayers%");
        registrar.effect("EffOpenBook", info -> (context, args) -> {
            if (!(Values.single(args[0]) instanceof ItemType book) || book.material() != Material.WRITTEN_BOOK) {
                return;
            }
            each(args[1], Player.class, player -> player.openBook(book.stack().clone()));
        }, "(open|show) book %itemtype% (to|for) %players%");
        registrar.effect("EffRespawn", info -> {
            if (inEvent(info, org.bukkit.event.player.PlayerRespawnEvent.class)) {
                throw new SyntaxError("Respawning the player in a respawn event is not possible");
            }
            // In a death event the player isn't dead yet: they're respawned a tick later.
            boolean later = inEvent(info, org.bukkit.event.entity.EntityDeathEvent.class);
            return (context, args) -> each(args[0], Player.class, player -> {
                if (later) {
                    player.getScheduler().runDelayed(plugin(), task -> player.spigot().respawn(), null, 1);
                } else {
                    player.spigot().respawn();
                }
            });
        }, "force %players% to respawn");
        registrar.effect("EffSendResourcePack", info -> {
            boolean hashed = info.pattern() == 1;
            return (context, args) -> {
                Object url = Values.single(args[0]);
                if (url == null) {
                    return;
                }
                Object hash = hashed ? Values.single(args[1]) : null;
                each(args[hashed ? 2 : 1], Player.class, player -> {
                    try {
                        if (hash == null) {
                            player.setResourcePack(Values.text(url));
                        } else {
                            player.setResourcePack(Values.text(url), Values.text(hash));
                        }
                    } catch (RuntimeException ignored) {
                        // A bad URL or hash sends nothing, as in Skript.
                    }
                });
            };
        }, "send [the] resource pack [from [[the] URL]] %string% to %players%",
                "send [the] resource pack [from [[the] URL]] %string% with hash %string% to %players%");
        registrar.effect("EffSprinting", info -> {
            boolean sprint = info.pattern() <= 1;
            return (context, args) -> each(args[0], Player.class, player -> player.setSprinting(sprint));
        }, "make %players% (start sprinting|sprint)", "force %players% to (start sprinting|sprint)",
                "make %players% (stop sprinting|not sprint)", "force %players% to (stop sprinting|not sprint)");
        registrar.effect("EffSwingHand", info -> {
            boolean main = info.pattern() == 0;
            return (context, args) -> each(args[0], LivingEntity.class, main ? LivingEntity::swingMainHand : LivingEntity::swingOffHand);
        }, "make %livingentities% swing [their] [main] hand", "make %livingentities% swing [their] off[ ]hand");
        registrar.effect("EffToggleFlight", info -> {
            boolean allow = info.pattern() == 0;
            return (context, args) -> each(args[0], Player.class, player -> player.setAllowFlight(allow));
        }, "(allow|enable) (fly|flight) (for|to) %players%", "(disallow|disable) (fly|flight) (for|to) %players%");
        registrar.effect("EffMakeSay", info -> (context, args) -> {
            for (Object player : owners(args[0])) {
                for (Object message : owners(args[1])) {
                    if (player instanceof Player p) {
                        p.chat(Values.text(message));
                    }
                }
            }
        }, "make %players% (say|send [the] message[s]) %strings%", "force %players% to (say|send [the] message[s]) %strings%");
        registrar.effect("EffResetTitle", info -> {
            boolean reset = info.hasTag("reset");
            return (context, args) -> {
                for (Object value : args[0] == null ? new Object[] {Bukkit.getConsoleSender()} : owners(args[0])) {
                    if (value instanceof Audience audience) {
                        if (reset) {
                            audience.resetTitle();
                        } else {
                            audience.clearTitle();
                        }
                    }
                }
            };
        }, "(clear|delete|:reset) [the] title[s] [of %audiences%]", "(clear|delete|:reset) [the] %audiences%'[s] title[s]");

        java.util.regex.Pattern soundKey = java.util.regex.Pattern.compile("([a-z0-9._-]+:)?[a-z0-9/._-]+");
        registrar.effect("EffStopSound", info -> {
            boolean all = info.hasTag("all");
            return (context, args) -> {
                Object written = Values.single(args[1]);
                org.bukkit.SoundCategory category = written instanceof org.bukkit.SoundCategory c ? c
                        : all ? null : org.bukkit.SoundCategory.MASTER;
                Object[] targets = owners(args[2]);
                if (all) {
                    each(targets, Player.class, player -> {
                        if (category == null) {
                            player.stopAllSounds();
                        } else {
                            player.stopSound(category);
                        }
                    });
                    return;
                }
                for (Object sound : owners(args[0])) {
                    String key = Values.text(sound).toLowerCase(Locale.ROOT);
                    if (soundKey.matcher(key).matches()) {
                        each(targets, Player.class, player -> player.stopSound(key, category));
                    }
                }
            };
        }, "stop (all:all sound[s]|sound[s] %-strings%) [(in [the]|from) %-soundcategory%] [(from playing to|for) %players%]",
                "stop playing sound[s] %strings% [(in [the]|from) %-soundcategory%] [(to|for) %players%]");

        registrar.effect("EffSendBlockChange", info -> {
            boolean original = info.pattern() == 1;
            return (context, args) -> {
                Object type = original ? null : Values.single(args[2]);
                if (!original && type == null) {
                    return;
                }
                for (Object place : owners(args[1])) {
                    if (!(place instanceof Location location)) {
                        continue;
                    }
                    BlockData data = original ? location.getBlock().getBlockData() : switch (type) {
                        case BlockData given -> given;
                        case ItemType item when item.material().isBlock() -> item.material().createBlockData();
                        default -> null;
                    };
                    if (data != null) {
                        each(args[0], Player.class, player -> player.sendBlockChange(location, data));
                    }
                }
            };
        }, "make %players% see %locations% as %itemtype/blockdata%",
                "make %players% see %locations% as [the|its|their] (original|normal|actual) [block[s]]");
    }

    private static void ban(BanList.Type type, String target, boolean ban, String reason, java.util.Date expires) {
        @SuppressWarnings({"unchecked", "rawtypes"})
        BanList<Object> list = (BanList) Bukkit.getBanList(type);
        if (ban) {
            list.addBan(target, reason, expires, "Skript ban effect");
        } else {
            list.pardon(target);
        }
    }

    /** Makes {@code living} look at a vector, location or entity: players at once, mobs over the next 50 ticks. */
    private static void look(LivingEntity living, Object target, io.papermc.paper.entity.LookAnchor anchor, Float speed,
            Float maxPitch) {
        Object at = target;
        if (at instanceof Location location && !location.isWorldLoaded()) {
            at = new Location(living.getWorld(), location.getX(), location.getY(), location.getZ());
        }
        if (living instanceof Player player) {
            for (io.papermc.paper.entity.LookAnchor from : io.papermc.paper.entity.LookAnchor.values()) {
                switch (at) {
                    case org.bukkit.util.Vector vector -> player.lookAt(player.getEyeLocation().add(vector), from);
                    case Location location -> player.lookAt(location, from);
                    case Entity entity -> player.lookAt(entity, from, anchor);
                    default -> {}
                }
            }
        } else if (living instanceof org.bukkit.entity.Mob mob) {
            LookGoal.start(mob, at, speed == null ? mob.getHeadRotationSpeed() : speed,
                    maxPitch == null ? mob.getMaxHeadPitch() : maxPitch);
        }
    }

    /** A mob's goal to keep looking at something for 50 ticks, as Skript's look effect gives mobs. */
    private static final class LookGoal implements com.destroystokyo.paper.entity.ai.Goal<org.bukkit.entity.Mob> {

        private static com.destroystokyo.paper.entity.ai.GoalKey<org.bukkit.entity.Mob> key;

        private final org.bukkit.entity.Mob mob;
        private final Object target;
        private final float speed;
        private final float maxPitch;
        private int ticks;

        private LookGoal(org.bukkit.entity.Mob mob, Object target, float speed, float maxPitch) {
            this.mob = mob;
            this.target = target;
            this.speed = speed;
            this.maxPitch = maxPitch;
        }

        static void start(org.bukkit.entity.Mob mob, Object target, float speed, float maxPitch) {
            com.destroystokyo.paper.entity.ai.MobGoals goals = Bukkit.getMobGoals();
            goals.getRunningGoals(mob, com.destroystokyo.paper.entity.ai.GoalType.LOOK).forEach(goal -> goals.removeGoal(mob, goal));
            goals.addGoal(mob, 0, new LookGoal(mob, target, speed, maxPitch));
        }

        @Override
        public boolean shouldActivate() {
            return ticks < 50;
        }

        @Override
        public void tick() {
            switch (target) {
                case org.bukkit.util.Vector vector -> mob.lookAt(mob.getEyeLocation().add(vector), speed, maxPitch);
                case Location location -> mob.lookAt(location, speed, maxPitch);
                case Entity entity -> mob.lookAt(entity, speed, maxPitch);
                default -> {}
            }
            ticks++;
        }

        @Override
        public com.destroystokyo.paper.entity.ai.GoalKey<org.bukkit.entity.Mob> getKey() {
            if (key == null) {
                key = com.destroystokyo.paper.entity.ai.GoalKey.of(org.bukkit.entity.Mob.class,
                        new org.bukkit.NamespacedKey(plugin(), "skript_entity_look"));
            }
            return key;
        }

        @Override
        public java.util.EnumSet<com.destroystokyo.paper.entity.ai.GoalType> getTypes() {
            return java.util.EnumSet.of(com.destroystokyo.paper.entity.ai.GoalType.LOOK);
        }
    }

    // ---------------------------------------------------------------- expressions

    private static void registerExpressions(Registrar registrar) {
        property(registrar, "ExprClientViewDistance", "integer", Patterns.property("client view distance[s]", "players"),
                Player.class, player -> (long) player.getClientViewDistance(), null, null);
        property(registrar, "ExprCompassTarget", "location", Patterns.property("compass target", "players"), Player.class,
                Player::getCompassTarget, mode -> mode == Changeable.Mode.SET || mode == Changeable.Mode.RESET
                        ? new Class<?>[] {Location.class} : null,
                (player, delta, mode) -> player.setCompassTarget(delta == null || delta.length == 0
                        ? player.getWorld().getSpawnLocation() : (Location) delta[0]));
        registrar.expression("ExprCursorSlot", "slot", info -> (context, args) -> map(args[0], Player.class,
                Items.Slot::cursor), Patterns.property("cursor slot", "players"));
        property(registrar, "ExprEnderChest", "inventory", Patterns.property("ender[ ]chest[s]", "players"), Player.class,
                Player::getEnderChest, null, null);
        property(registrar, "ExprExhaustion", "number", Patterns.property("exhaustion", "players"), Player.class,
                player -> (double) player.getExhaustion(), mode -> new Class<?>[] {Number.class},
                (player, delta, mode) -> player.setExhaustion(switch (mode) {
                    case ADD -> player.getExhaustion() + number(delta);
                    case REMOVE -> player.getExhaustion() - number(delta);
                    case SET -> number(delta);
                    default -> 0;
                }));
        registrar.expression("ExprHiddenPlayers", "player", info -> (context, args) -> {
            List<Object> hidden = new ArrayList<>();
            each(args[0], Player.class, player -> hidden.addAll(player.spigot().getHiddenPlayers()));
            return hidden.toArray();
        }, "[(all [[of] the]|the)] hidden players (of|for) %players%", "[(all [[of] the]|the)] players hidden (from|for|by) %players%");
        TimedProperties.registerHotbarSlot(registrar);
        registrar.expression("ExprIP", "string", info -> {
            if (info.pattern() == 2 && !inEvent(info, org.bukkit.event.player.PlayerLoginEvent.class)
                    && !inEvent(info, org.bukkit.event.server.ServerListPingEvent.class)) {
                throw new SyntaxError("You must specify players whose IP addresses to get outside of server list ping and connect events.");
            }
            if (info.pattern() == 2) {
                return (context, args) -> switch (context.event()) {
                    case org.bukkit.event.player.PlayerLoginEvent login -> login.getAddress().getHostAddress();
                    case org.bukkit.event.server.ServerListPingEvent ping -> ping.getAddress().getHostAddress();
                    case null, default -> null;
                };
            }
            return (context, args) -> map(args[0], Player.class, player -> {
                InetAddress address;
                if (context.event() instanceof org.bukkit.event.player.PlayerLoginEvent login && login.getPlayer().equals(player)) {
                    address = login.getAddress();
                } else {
                    InetSocketAddress socket = player.getAddress();
                    address = socket == null ? null : socket.getAddress();
                }
                return address == null ? "unknown" : address.getHostAddress();
            });
        }, "IP[s][( |-)address[es]] of %players%", "%players%'[s] IP[s][( |-)address[es]]", "IP[( |-)address]");
        property(registrar, "ExprLanguage", "string",
                Patterns.property("[([currently] selected|current)] [game] (language|locale) [setting]", "players"), Player.class,
                Player::getLocale, null, null);
        property(registrar, "ExprLastDeathLocation", "location", Patterns.property("[last] death location[s]", "offlineplayers"),
                OfflinePlayer.class, OfflinePlayer::getLastDeathLocation,
                mode -> mode == Changeable.Mode.SET || mode == Changeable.Mode.RESET || mode == Changeable.Mode.DELETE
                        ? new Class<?>[] {Location.class} : null, (player, delta, mode) -> {
                    if (player instanceof Player online) {
                        online.setLastDeathLocation(delta != null && delta.length > 0 && delta[0] instanceof Location l ? l : null);
                    }
                });
        registrar.expression("ExprLastLoginTime", "date", info -> {
            boolean first = info.mark() == 2;
            return (context, args) -> map(args[0], OfflinePlayer.class, player ->
                    new co.xenastudios.neoskript.runtime.Date(first ? player.getFirstPlayed() : player.getLastLogin()));
        }, Patterns.property("(1¦last|2¦first) login", "offlineplayers"));
        property(registrar, "ExprLastResourcePackResponse", "resourcepackstate",
                Patterns.property("[last] resource pack response[s]", "players"), Player.class, Player::getResourcePackStatus,
                null, null);
        TimedProperties.registerLevel(registrar);
        property(registrar, "ExprLevelProgress", "number", Patterns.property("level progress", "players"), Player.class,
                player -> (double) player.getExp(), mode -> mode == Changeable.Mode.REMOVE_ALL ? null : new Class<?>[] {Number.class},
                (player, delta, mode) -> {
                    float by = delta == null || delta.length == 0 ? 0 : ((Number) delta[0]).floatValue();
                    float progress = switch (mode) {
                        case SET -> by;
                        case ADD -> player.getExp() + by;
                        case REMOVE -> player.getExp() - by;
                        default -> 0;
                    };
                    // Progress past a whole level moves the level.
                    if (Float.isNaN(progress) || Float.isInfinite(progress)) {
                        progress = 0;
                    }
                    player.setLevel(Math.max(0, player.getLevel() + (int) Math.floor(progress)));
                    float rest = progress % 1;
                    player.setExp(rest < 0 ? rest + 1 : rest);
                });
        registrar.expression("ExprPermissions", "string", info -> (context, args) -> {
            Set<String> permissions = new LinkedHashSet<>();
            each(args[0], Player.class, player -> player.getEffectivePermissions()
                    .forEach(permission -> permissions.add(permission.getPermission())));
            return permissions.toArray();
        }, "[(all [[of] the]|the)] permissions (from|of) %players%", "[(all [[of] the]|the)] %players%'[s] permissions");
        property(registrar, "ExprPing", "integer", Patterns.property("ping", "players"), Player.class,
                player -> (long) player.getPing(), null, null);
        // Bukkit can't tell which completions a player has, so it's nothing; it can be changed though.
        property(registrar, "ExprPlayerChatCompletions", "string", Patterns.property("[custom] chat completion[s]", "players"),
                Player.class, player -> null, mode -> switch (mode) {
                    case ADD, SET, REMOVE, DELETE, RESET -> new Class<?>[] {String.class};
                    default -> null;
                }, (player, delta, mode) -> {
                    List<String> completions = new ArrayList<>();
                    if (delta != null && (mode == Changeable.Mode.ADD || mode == Changeable.Mode.REMOVE || mode == Changeable.Mode.SET)) {
                        for (Object value : delta) {
                            if (value instanceof String text) {
                                completions.add(text);
                            }
                        }
                    }
                    switch (mode) {
                        case ADD -> player.addCustomChatCompletions(completions);
                        case REMOVE -> player.removeCustomChatCompletions(completions);
                        default -> player.setCustomChatCompletions(completions);
                    }
                });
        registrar.expression("ExprPlayerListHeaderFooter", "string", info -> {
            boolean footer = info.hasTag("footer");
            return new Property<>(Player.class, player -> {
                Component text = footer ? player.playerListFooter() : player.playerListHeader();
                return text == null ? null : Messages.LEGACY_SECTION.serialize(text);
            }, mode -> mode == Changeable.Mode.SET || mode == Changeable.Mode.DELETE || mode == Changeable.Mode.RESET
                    ? new Class<?>[] {Object.class} : null, (player, delta, mode) -> {
                        Component text = Component.empty();
                        if (delta != null) {
                            List<Component> lines = new ArrayList<>();
                            for (Object line : delta) {
                                lines.add(Messages.component(line));
                            }
                            text = Component.join(net.kyori.adventure.text.JoinConfiguration.newlines(), lines);
                        }
                        if (footer) {
                            player.sendPlayerListFooter(text);
                        } else {
                            player.sendPlayerListHeader(text);
                        }
                    });
        }, Patterns.property("(player|tab)[ ]list (header|:footer) [text|message]", "players"));
        property(registrar, "ExprPlayerListPriority", "integer", Patterns.property("(player|tab)[ ]list priority", "players"),
                Player.class, player -> (long) player.getPlayerListOrder(), mode -> switch (mode) {
                    case ADD, REMOVE, SET, RESET -> new Class<?>[] {Number.class};
                    default -> null;
                }, (player, delta, mode) -> {
                    long by = delta == null || delta.length == 0 ? 0 : ((Number) delta[0]).longValue();
                    long order = switch (mode) {
                        case ADD -> player.getPlayerListOrder() + by;
                        case REMOVE -> player.getPlayerListOrder() - by;
                        default -> by;
                    };
                    player.setPlayerListOrder((int) Math.max(0, Math.min(Integer.MAX_VALUE, order)));
                });
        property(registrar, "ExprPlayerProtocolVersion", "integer", Patterns.property("protocol version", "players"), Player.class,
                player -> player.getProtocolVersion() == -1 ? null : (long) player.getProtocolVersion(), null, null);
        property(registrar, "ExprSaturation", "number", Patterns.property("saturation", "players"), Player.class,
                player -> (double) player.getSaturation(), mode -> mode == Changeable.Mode.REMOVE_ALL ? null : new Class<?>[] {Number.class},
                (player, delta, mode) -> player.setSaturation(switch (mode) {
                    case ADD -> player.getSaturation() + number(delta);
                    case REMOVE -> player.getSaturation() - number(delta);
                    case SET -> number(delta);
                    default -> 0;
                }));
    }

    private static void registerMoreExpressions(Registrar registrar) {
        registrar.expression("ExprSpectatorTarget", "entity", info -> new SpectatorTarget(info.time()), "spectator target [of %-players%]", "%players%'[s] spectator target");
        // 'past spectator target' when a player starts spectating something is what they spectated before.
        registrar.timeStates("ExprSpectatorTarget", 0, com.destroystokyo.paper.event.player.PlayerStartSpectatingEntityEvent.class,
                com.destroystokyo.paper.event.player.PlayerStopSpectatingEntityEvent.class);
        registrar.expression("ExprSpeed", "number", info -> {
            boolean walk = info.mark() == 0;
            return new Property<>(Player.class, player -> (double) (walk ? player.getWalkSpeed() : player.getFlySpeed()),
                    mode -> switch (mode) {
                        case SET, RESET, ADD, REMOVE -> new Class<?>[] {Number.class};
                        default -> null;
                    }, (player, delta, mode) -> {
                        float old = walk ? player.getWalkSpeed() : player.getFlySpeed();
                        float speed = switch (mode) {
                            case SET -> number(delta);
                            case ADD -> old + number(delta);
                            case REMOVE -> old - number(delta);
                            default -> walk ? 0.2f : 0.1f;
                        };
                        speed = Math.max(-1, Math.min(1, speed));
                        if (walk) {
                            player.setWalkSpeed(speed);
                        } else {
                            player.setFlySpeed(speed);
                        }
                    });
        }, Patterns.property("(0¦walk[ing]|1¦fl(y[ing]|ight))[( |-)]speed", "players"));
        registrar.expression("ExprTablistedPlayers", "player", info -> new TablistedPlayers(),
                defaultProperty("(tablist[ed]|listed) players", "players"));
        registrar.expression("ExprScoreboardTags", "string", info -> new Property<>(Entity.class,
                entity -> entity.getScoreboardTags().toArray(), mode -> switch (mode) {
                    case SET, ADD, REMOVE, DELETE, RESET -> new Class<?>[] {String.class};
                    default -> null;
                }, (entity, delta, mode) -> {
                    switch (mode) {
                        case SET -> {
                            entity.getScoreboardTags().clear();
                            for (Object tag : delta) {
                                entity.addScoreboardTag((String) tag);
                            }
                        }
                        case ADD -> {
                            for (Object tag : delta) {
                                entity.addScoreboardTag((String) tag);
                            }
                        }
                        case REMOVE -> {
                            for (Object tag : delta) {
                                entity.removeScoreboardTag((String) tag);
                            }
                        }
                        default -> entity.getScoreboardTags().clear();
                    }
                }), "[(all [[of] the]|the)] scoreboard tags of %entities%", "%entities%'[s] scoreboard tags");
        // Blocks that aren't air count as solid; 'actual' targets the real hit box. Skript looks 100 blocks.
        // Any time state is taken, and changes nothing, as in Skript.
        registrar.timeStates("ExprTargetedBlock", info -> true);
        registrar.expression("ExprTargetedBlock", "block", info -> {
            boolean actual = info.hasTag("actual");
            return (context, args) -> map(args[0], LivingEntity.class, living -> {
                Block block = actual ? living.getTargetBlockExact(100) : living.getTargetBlock(null, 100);
                return block == null || block.getType().isAir() ? null : block;
            });
        }, "[the] [actual:(actual[ly]|exact)] target[ed] block[s] [of %livingentities%]",
                "%livingentities%'[s] [actual:(actual[ly]|exact)] target[ed] block[s]");
        property(registrar, "ExprEyeLocation", "location", Patterns.property("(head|eye[s]) [location[s]]", "livingentities"),
                LivingEntity.class, LivingEntity::getEyeLocation, null, null);
        property(registrar, "ExprSkull", "itemtype", Patterns.property("skull", "offlineplayers"), OfflinePlayer.class,
                PlayerSyntax::skull, null, null);
        // Before an input event ('past input keys'), the keys the player pressed until then; there's no future state.
        registrar.timeStates("ExprCurrentInputKeys", info -> info.time() < 0
                && info.isEvent(org.bukkit.event.player.PlayerInputEvent.class));
        registrar.expression("ExprCurrentInputKeys", "inputkey", info -> (context, args) -> {
            List<Object> pressed = new ArrayList<>();
            Object event = info.time() == 0 ? context.event() : null;
            each(args[0], Player.class, player -> {
                Input input = event instanceof org.bukkit.event.player.PlayerInputEvent inputEvent && inputEvent.getPlayer().equals(player)
                        ? inputEvent.getInput() : player.getCurrentInput();
                for (InputKey key : InputKey.values()) {
                    if (key.pressed(input)) {
                        pressed.add(key);
                    }
                }
            });
            return pressed.toArray();
        }, Patterns.property("[current] (inputs|input keys)", "players"));
        // A player's open inventory, or their crafting grid when nothing's open.
        registrar.expression("ExprOpenedInventory", "inventory", info -> (context, args) -> map(args[0], Player.class,
                player -> player.getOpenInventory().getTopInventory()),
                "[the] (current|open|top) inventory [of %players%]", "%players%'[s] (current|open|top) inventory");
        registrar.expression("ExprHotbarButton", "integer", info -> (context, args) ->
                context.event() instanceof org.bukkit.event.inventory.InventoryClickEvent click ? (long) click.getHotbarButton() : null,
                "[the] hotbar button");
        // The raw names of items: their Minecraft ids.
        registrar.expression("ExprRawName", "string", info -> (context, args) -> {
            List<Object> names = new ArrayList<>();
            for (Object item : owners(args[0])) {
                if (item instanceof ItemType type) {
                    names.add(type.material().getKey().toString());
                }
            }
            return names.size() == 1 ? names.getFirst() : names.toArray();
        }, "(raw|minecraft|vanilla) name[s] of %itemtypes%");
    }

    /** A player head of {@code player}. */
    private static ItemType skull(OfflinePlayer player) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        head.editMeta(SkullMeta.class, meta -> {
            if (player.getName() != null) {
                meta.setOwningPlayer(player);
            } else {
                meta.setPlayerProfile(Bukkit.createProfile(player.getUniqueId()));
            }
        });
        return new ItemType(head, false);
    }

    /** The entities players are spectating; in a spectating event without players, the event's target. */
    private record SpectatorTarget(int time) implements Expression, Changeable {
        @Override
        public Object get(TriggerContext context, Object[] arguments) {
            if (arguments[0] == null) {
                return switch (context.event()) {
                    case com.destroystokyo.paper.event.player.PlayerStartSpectatingEntityEvent start ->
                            time < 0 ? start.getCurrentSpectatorTarget() : start.getNewSpectatorTarget();
                    // Nothing is spectated after a player stops.
                    case com.destroystokyo.paper.event.player.PlayerStopSpectatingEntityEvent stop ->
                            time > 0 ? null : stop.getSpectatorTarget();
                    case null, default -> null;
                };
            }
            return map(arguments[0], Player.class, Player::getSpectatorTarget);
        }

        @Override
        public Class<?>[] acceptChange(Mode mode) {
            return mode == Mode.SET || mode == Mode.RESET || mode == Mode.DELETE ? new Class<?>[] {Entity.class} : null;
        }

        @Override
        public void change(TriggerContext context, Object[] arguments, Object[] delta, Mode mode) {
            Entity target = mode == Mode.SET && delta != null && delta.length > 0 ? (Entity) delta[0] : null;
            each(arguments[0], Player.class, player -> {
                // Only spectators can spectate something.
                if (player.getGameMode() == GameMode.SPECTATOR) {
                    player.setSpectatorTarget(target);
                }
            });
        }
    }

    /** The players in the tab lists of players: who they can see listed. */
    private static final class TablistedPlayers implements Expression, Changeable {
        @Override
        public Object get(TriggerContext context, Object[] arguments) {
            Set<Object> listed = new LinkedHashSet<>();
            each(arguments[0], Player.class, viewer -> Bukkit.getOnlinePlayers().stream().filter(viewer::isListed).forEach(listed::add));
            return listed.toArray();
        }

        @Override
        public Class<?>[] acceptChange(Mode mode) {
            return new Class<?>[] {Player.class};
        }

        @Override
        public void change(TriggerContext context, Object[] arguments, Object[] delta, Mode mode) {
            Object[] players = delta == null ? new Object[0] : delta;
            for (Object value : owners(arguments[0])) {
                if (!(value instanceof Player viewer)) {
                    continue;
                }
                switch (mode) {
                    case DELETE -> Bukkit.getOnlinePlayers().forEach(viewer::unlistPlayer);
                    case REMOVE, REMOVE_ALL -> each(players, Player.class, viewer::unlistPlayer);
                    case SET, ADD -> {
                        if (mode == Mode.SET) {
                            List<Object> keep = List.of(players);
                            Bukkit.getOnlinePlayers().stream().filter(player -> !keep.contains(player)).forEach(viewer::unlistPlayer);
                        }
                        each(players, Player.class, player -> {
                            if (viewer.canSee(player)) {
                                viewer.listPlayer(player);
                            }
                        });
                    }
                    case RESET -> Bukkit.getOnlinePlayers().forEach(player -> {
                        if (!viewer.isListed(player) && viewer.canSee(player)) {
                            viewer.listPlayer(player);
                        }
                    });
                }
            }
        }
    }

    // ---------------------------------------------------------------- helpers

    /** How a property's owner is changed. */
    @FunctionalInterface
    interface Change<T> {
        void apply(T owner, Object[] delta, Changeable.Mode mode);
    }

    /**
     * A property expression of owners of {@code ownerClass}: {@code getter} gives each one's value (or values, as an
     * array), and {@code accepts}/{@code change} say how it's changed, if it can be.
     */
    record Property<T>(Class<T> ownerClass, Function<? super T, ?> getter, Function<Changeable.Mode, Class<?>[]> accepts,
            Change<T> change) implements Expression, Changeable {

        @Override
        public Object get(TriggerContext context, Object[] arguments) {
            return map(arguments[0], ownerClass, getter);
        }

        @Override
        public Class<?>[] acceptChange(Mode mode) {
            return accepts == null ? null : accepts.apply(mode);
        }

        @Override
        public void change(TriggerContext context, Object[] arguments, Object[] delta, Mode mode) {
            for (Object owner : owners(arguments[0])) {
                if (ownerClass.isInstance(owner)) {
                    change.apply(ownerClass.cast(owner), delta, mode);
                }
            }
        }
    }

    /** Registers a {@link Property}; without {@code accepts} it can't be changed. */
    static <T> void property(Registrar registrar, String id, String returnType, String[] patterns, Class<T> ownerClass,
            Function<? super T, ?> getter, Function<Changeable.Mode, Class<?>[]> accepts, Change<T> change) {
        if (accepts == null) {
            registrar.expression(id, returnType, info -> (context, args) -> map(args[0], ownerClass, getter), patterns);
        } else {
            registrar.expression(id, returnType, info -> new Property<>(ownerClass, getter, accepts, change), patterns);
        }
    }

    /** Skript's default property patterns: {@code [the] <property> [of %owners%]} and {@code %owners%'[s] <property>}. */
    static String[] defaultProperty(String property, String owners) {
        return new String[] {"[the] " + property + " [of %" + owners + "%]", "%" + owners + "%'[s] " + property};
    }

    /** {@code getter} for each owner of {@code type}, leaving out nothing, and spreading arrays. */
    static <T> Object map(Object owners, Class<T> type, Function<? super T, ?> getter) {
        List<Object> values = new ArrayList<>();
        for (Object owner : owners(owners)) {
            if (type.isInstance(owner)) {
                Object value = getter.apply(type.cast(owner));
                if (value instanceof Object[] array) {
                    values.addAll(List.of(array));
                } else if (value != null) {
                    values.add(value);
                }
            }
        }
        return values.size() == 1 ? values.getFirst() : values.toArray();
    }

    /** Runs {@code action} for each value of {@code type} among {@code values}. */
    static <T> void each(Object values, Class<T> type, java.util.function.Consumer<? super T> action) {
        for (Object value : owners(values)) {
            if (type.isInstance(value)) {
                action.accept(type.cast(value));
            }
        }
    }

    /** An argument's values: none for {@code null}, one, or the array's. */
    static Object[] owners(Object value) {
        return value instanceof Object[] array ? array : value == null ? new Object[0] : new Object[] {value};
    }

    /** The first value of a change, as a float; 0 if there's none. */
    private static float number(Object[] delta) {
        return delta != null && delta.length > 0 && delta[0] instanceof Number n ? n.floatValue() : 0;
    }

    static org.bukkit.plugin.Plugin plugin() {
        return org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(PlayerSyntax.class);
    }

    /** Whether the element is in an event of {@code event}, or a subclass of it. */
    static boolean inEvent(ElementInfo info, Class<?> event) {
        return info.eventClasses().stream().anyMatch(event::isAssignableFrom);
    }
}
