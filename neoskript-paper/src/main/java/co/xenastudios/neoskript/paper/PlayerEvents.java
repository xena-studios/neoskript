/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.paper;

import co.xenastudios.neoskript.api.element.ElementInfo;
import co.xenastudios.neoskript.api.registration.Registrar;
import co.xenastudios.neoskript.runtime.Comparison;
import co.xenastudios.neoskript.runtime.Timespan;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBedLeaveEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketEntityEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.event.player.PlayerEggThrowEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerExpCooldownChangeEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemBreakEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerItemMendEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerLevelChangeEvent;
import org.bukkit.event.player.PlayerLocaleChangeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPickupArrowEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerRiptideEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

/**
 * Player events: clicking, teleporting, portals, levels and experience, armour, chunks, spectating, books,
 * fishing, game modes and Skript's simple player events, with the values triggers get from them.
 */
final class PlayerEvents {

    private PlayerEvents() {}

    static void register(Registrar registrar) {
        registerClick(registrar);
        registerMovement(registrar);
        registerExperience(registrar);
        registerSimple(registrar);
        registerPlayerValues(registrar);
        registerOther(registrar);
    }

    private static final int RIGHT = 1;
    private static final int LEFT = 2;
    private static final int ANY = RIGHT | LEFT;

    /** The first click event each player had this tick, so a click with both hands runs once, as in Skript. */
    private static final Map<UUID, long[]> FIRST_CLICKS = new ConcurrentHashMap<>();
    private static final Map<UUID, Event> FIRST_CLICK_EVENTS = new ConcurrentHashMap<>();

    /** Whether {@code event} is the first click {@code player} made this tick, or the same one again. */
    private static boolean firstClick(Player player, Event event) {
        long tick = Bukkit.getCurrentTick();
        UUID id = player.getUniqueId();
        long[] last = FIRST_CLICKS.get(id);
        Event first = FIRST_CLICK_EVENTS.get(id);
        if (last != null && last[0] == tick && first != null && first != event) {
            return false;
        }
        FIRST_CLICKS.put(id, new long[] {tick});
        FIRST_CLICK_EVENTS.put(id, event);
        return true;
    }

    /**
     * Whether interacting at entities still fires both entity events, as before Minecraft 26.1.1: then only clicks
     * at armour stands count for the 'at' one.
     */
    private static boolean oldEntityInteract() {
        String[] version = Bukkit.getMinecraftVersion().split("\\.");
        try {
            int major = Integer.parseInt(version[0]);
            int minor = version.length > 1 ? Integer.parseInt(version[1]) : 0;
            int patch = version.length > 2 ? Integer.parseInt(version[2]) : 0;
            return major < 26 || major == 26 && (minor < 1 || minor == 1 && patch < 1);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static volatile Boolean oldEntityInteract;

    /** {@code on rightclick on a cow holding a bucket}. */
    private static void registerClick(Registrar registrar) {
        String clicks = "[(1:right|2:left)(| |-)][mouse(| |-)]click[ing]";
        registrar.event("EvtClick", List.of(PlayerInteractEvent.class, PlayerInteractEntityEvent.class,
                PlayerInteractAtEntityEvent.class), info -> {
            int mark = Events.mark(info);
            int click = mark == 0 ? ANY : mark;
            int typeIndex = info.pattern();
            int toolIndex = 1 - info.pattern();
            String typeName = info.argumentType(typeIndex);
            if ("entitytype".equals(typeName) || "countedentitytype".equals(typeName)) {
                String written = info.argumentLiteral(typeIndex);
                EntityKind kind = written == null ? null : EntityKind.parse(written);
                boolean vehicle = kind != null && Vehicle.class.isAssignableFrom(kind.entityClass());
                String what = vehicle ? "A leftclick on a vehicle entity is an attack and thus not covered by the 'click' "
                        + "event, but the 'vehicle damage' event." : "A leftclick on an entity is an attack and thus not "
                        + "covered by the 'click' event, but the 'damage' event.";
                if (click == LEFT) {
                    throw Events.error(what);
                } else if (click == ANY) {
                    throw Events.error(what + " Change this event to a rightclick to fix this warning message.");
                }
            }
            return (context, args) -> clicked((Event) context.event(), click, Events.values(args[typeIndex]),
                    Events.values(args[toolIndex]), info, typeIndex);
        }, clicks + " [on %-entitytype/itemtype/blockdata%] [(with|using|holding) %-itemtype%]",
                clicks + " (with|using|holding) %itemtype% on %entitytype/itemtype/blockdata%");
        registrar.eventValue(PlayerInteractEntityEvent.class, "entity", PlayerInteractEntityEvent::getRightClicked);
        registrar.eventValue(PlayerInteractEntityEvent.class, "itemstack", event -> event.getHand() == EquipmentSlot.HAND
                ? event.getPlayer().getInventory().getItemInMainHand() : event.getHand() == EquipmentSlot.OFF_HAND
                ? event.getPlayer().getInventory().getItemInOffHand() : null);
        registrar.eventValue(PlayerInteractEvent.class, "itemstack", PlayerInteractEvent::getItem);
        registrar.eventValue(PlayerInteractEvent.class, "block", PlayerInteractEvent::getClickedBlock);
        registrar.eventValue(PlayerInteractEvent.class, "direction", event -> new WorldSyntax.Direction(
                new org.bukkit.util.Vector(event.getBlockFace().getModX(), event.getBlockFace().getModY(),
                        event.getBlockFace().getModZ()), false));

        // Stepping on pressure plates and tripwires.
        registrar.event("EvtPressurePlate", List.of(PlayerInteractEvent.class), info -> {
            boolean tripwire = info.pattern() == 1;
            return (context, args) -> {
                PlayerInteractEvent event = (PlayerInteractEvent) context.event();
                Block block = event.getClickedBlock();
                if (block == null || event.getAction() != Action.PHYSICAL) {
                    return false;
                }
                Material type = block.getType();
                return tripwire ? type == Material.TRIPWIRE || type == Material.TRIPWIRE_HOOK
                        : Tag.PRESSURE_PLATES.isTagged(type);
            };
        }, "[step[ping] on] [a] [pressure] plate", "(trip|[step[ping] on] [a] tripwire)");
    }

    /** EvtClick's check: the kind of click, what was clicked and what was held. */
    private static boolean clicked(Event event, int click, Object[] types, Object[] tools, ElementInfo info, int typeIndex) {
        Block block;
        Entity entity;
        if (event instanceof PlayerInteractEntityEvent interact) {
            Entity target = interact.getRightClicked();
            boolean old = oldEntityInteract == null ? (oldEntityInteract = oldEntityInteract()) : oldEntityInteract;
            if (old && interact instanceof PlayerInteractAtEntityEvent && !(target instanceof org.bukkit.entity.ArmorStand)) {
                return false;
            }
            if (click == LEFT) {
                return false;
            }
            if ((!old || !(event instanceof PlayerInteractAtEntityEvent)) && !firstClick(interact.getPlayer(), event)) {
                return false;
            }
            entity = target;
            block = null;
        } else if (event instanceof PlayerInteractEvent interact) {
            int kind = switch (interact.getAction()) {
                case LEFT_CLICK_AIR, LEFT_CLICK_BLOCK -> LEFT;
                case RIGHT_CLICK_AIR, RIGHT_CLICK_BLOCK -> RIGHT;
                default -> 0;
            };
            if ((click & kind) == 0 || !firstClick(interact.getPlayer(), event)) {
                return false;
            }
            block = interact.getClickedBlock();
            entity = null;
        } else {
            return false;
        }
        if (tools != null) {
            ItemStack held;
            if (event instanceof PlayerInteractEvent interact) {
                held = interact.getItem();
            } else {
                PlayerInteractEntityEvent interact = (PlayerInteractEntityEvent) event;
                held = interact.getHand() == EquipmentSlot.HAND ? interact.getPlayer().getInventory().getItemInMainHand()
                        : interact.getPlayer().getInventory().getItemInOffHand();
            }
            ItemStack item = held == null ? new ItemStack(Material.AIR) : held;
            if (!Events.check(info, 1 - typeIndex, tools, tool -> Events.isItem(tool, item))) {
                return false;
            }
        }
        if (types == null) {
            return true;
        }
        return Events.check(info, typeIndex, types, type -> {
            if (entity != null) {
                return type instanceof EntityKind ? Events.isKind(type, entity)
                        : type instanceof ItemType && Comparison.equal(entity, type);
            }
            return block != null && Events.isBlock(type, block.getBlockData());
        });
    }

    /** Moving, stepping on blocks, entering chunks, teleporting and portals. */
    private static void registerMovement(Registrar registrar) {
        registrar.event("EvtMove", List.of(PlayerMoveEvent.class, io.papermc.paper.event.entity.EntityMoveEvent.class), info -> {
            String written = info.argumentLiteral(0);
            EntityKind kind = written == null ? null : EntityKind.parse(written);
            Class<? extends Entity> type = kind == null ? Entity.class : kind.entityClass();
            boolean player = Player.class.isAssignableFrom(type);
            boolean canBePlayer = type.isAssignableFrom(Player.class);
            int move = info.pattern() > 0 ? 1 : info.hasTag("rotate") ? 2 : 0;
            List<Class<?>> classes = player ? List.of(PlayerMoveEvent.class) : canBePlayer
                    ? List.of(io.papermc.paper.event.entity.EntityMoveEvent.class, PlayerMoveEvent.class)
                    : List.of(io.papermc.paper.event.entity.EntityMoveEvent.class);
            return Events.narrowed(classes, (context, args) -> {
                Location from;
                Location to;
                if (canBePlayer && context.event() instanceof PlayerMoveEvent moved) {
                    from = moved.getFrom();
                    to = moved.getTo();
                } else if (context.event() instanceof io.papermc.paper.event.entity.EntityMoveEvent moved) {
                    if (kind != null && !kind.matches(moved.getEntity())) {
                        return false;
                    }
                    from = moved.getFrom();
                    to = moved.getTo();
                } else {
                    return false;
                }
                return switch (move) {
                    case 0 -> from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()
                            || from.getWorld() != to.getWorld();
                    case 2 -> from.getYaw() != to.getYaw() || from.getPitch() != to.getPitch();
                    default -> true;
                };
            });
        }, "%entitytype% (move|walk|step|rotate:(turn[ing] around|rotate))",
                "%entitytype% (move|walk|step) or (turn[ing] around|rotate)",
                "%entitytype% (turn[ing] around|rotate) or (move|walk|step)");
        registrar.eventValue(io.papermc.paper.event.entity.EntityMoveEvent.class, "location",
                io.papermc.paper.event.entity.EntityMoveEvent::getFrom);
        registrar.eventValue(io.papermc.paper.event.entity.EntityMoveEvent.class, "location", 1,
                io.papermc.paper.event.entity.EntityMoveEvent::getTo);

        registrar.event("EvtMoveOn", List.of(PlayerMoveEvent.class), info -> {
            String written = info.argumentLiteral(0);
            return (context, args) -> {
                PlayerMoveEvent event = (PlayerMoveEvent) context.event();
                Location from = event.getFrom();
                Location to = event.getTo();
                Block block = onBlock(to);
                if (block == null || block.getType().isAir()) {
                    return false;
                }
                Material id = block.getType();
                int y = blockY(to.getY(), block);
                if (to.getWorld().equals(from.getWorld()) && to.getBlockX() == from.getBlockX()
                        && to.getBlockZ() == from.getBlockZ()) {
                    Block fromBlock = onBlock(from);
                    if (fromBlock != null && y == blockY(from.getY(), fromBlock) && fromBlock.getType() == id) {
                        return false;
                    }
                }
                return Events.any(Events.values(args[0]), type -> type instanceof ItemType item && item.material() == id);
            };
        }, "(step|walk)[ing] (on|over) %*itemtypes%");

        registrar.event("EvtPlayerChunkEnter", List.of(PlayerMoveEvent.class), info -> (context, args) -> {
            PlayerMoveEvent event = (PlayerMoveEvent) context.event();
            return !event.getFrom().getChunk().equals(event.getTo().getChunk());
        }, "[player] (enter[s] [a] chunk|chunk enter[ing])");

        registrar.eventValue(PlayerMoveEvent.class, "block", event -> event.getTo().clone().subtract(0, 0.5, 0).getBlock());
        registrar.eventValue(PlayerMoveEvent.class, "location", -1, PlayerMoveEvent::getFrom);
        registrar.eventValue(PlayerMoveEvent.class, "location", PlayerMoveEvent::getTo);
        registrar.eventValue(PlayerMoveEvent.class, "chunk", -1, event -> event.getFrom().getChunk());
        registrar.eventValue(PlayerMoveEvent.class, "chunk", event -> event.getTo().getChunk());

        registrar.event("EvtTeleport", List.of(EntityTeleportEvent.class, PlayerTeleportEvent.class), info -> (context, args) -> {
            Entity entity = switch (context.event()) {
                case EntityTeleportEvent teleport -> teleport.getEntity();
                case PlayerTeleportEvent teleport -> teleport.getPlayer();
                default -> null;
            };
            Object[] kinds = Events.values(args[0]);
            return entity != null && (kinds == null || Events.any(kinds, kind -> Events.isKind(kind, entity)));
        }, "[%entitytypes%] teleport[ing]");
        registrar.eventValue(PlayerTeleportEvent.class, "location", -1, PlayerTeleportEvent::getFrom);
        registrar.eventValue(PlayerTeleportEvent.class, "location", PlayerTeleportEvent::getTo);
        registrar.eventValue(EntityTeleportEvent.class, "location", -1, EntityTeleportEvent::getFrom);
        registrar.eventValue(EntityTeleportEvent.class, "location", EntityTeleportEvent::getTo);
        registrar.eventValue(PlayerTeleportEvent.class, "teleportcause", PlayerTeleportEvent::getCause);

        registrar.event("EvtPortal", List.of(PlayerPortalEvent.class, EntityPortalEvent.class), info ->
                Events.narrowed(List.of(info.pattern() == 0 ? PlayerPortalEvent.class : EntityPortalEvent.class),
                        (context, args) -> info.pattern() == 0 ? context.event() instanceof PlayerPortalEvent
                                : context.event() instanceof EntityPortalEvent), "[player] portal", "entity portal");
    }

    /** The block a location stands on, as Skript's EvtMoveOn: the one below, or a fence's top. */
    private static Block onBlock(Location location) {
        Block block = location.getWorld().getBlockAt(location.getBlockX(), (int) (Math.ceil(location.getY()) - 1),
                location.getBlockZ());
        if (block.getType() == Material.AIR && Math.abs((location.getY() - location.getBlockY()) - 0.5) < 1e-10) {
            block = location.getWorld().getBlockAt(location.getBlockX(), location.getBlockY() - 1, location.getBlockZ());
            if (!isFence(block)) {
                return null;
            }
        }
        return block;
    }

    private static boolean isFence(Block block) {
        return Tag.FENCES.isTagged(block.getType()) || Tag.FENCE_GATES.isTagged(block.getType())
                || Tag.WALLS.isTagged(block.getType());
    }

    private static int blockY(double y, Block block) {
        if (isFence(block) && Math.abs((y - Math.floor(y)) - 0.5) < 1e-10) {
            return (int) Math.floor(y) - 1;
        }
        return (int) Math.ceil(y) - 1;
    }

    /** Levels and experience. */
    private static void registerExperience(Registrar registrar) {
        registrar.event("EvtLevel", List.of(PlayerLevelChangeEvent.class), info -> {
            int leveling = Events.mark(info);
            return (context, args) -> {
                PlayerLevelChangeEvent event = (PlayerLevelChangeEvent) context.event();
                return leveling == 1 ? event.getNewLevel() > event.getOldLevel()
                        : leveling == 2 ? event.getNewLevel() < event.getOldLevel() : true;
            };
        }, "[player] level (change|1¦up|2¦down)");
        registrar.event("EvtExperienceChange", List.of(PlayerExpChangeEvent.class), info -> {
            int mode = info.hasTag("increase") ? 1 : info.hasTag("decrease") ? 2 : 0;
            return (context, args) -> {
                int amount = ((PlayerExpChangeEvent) context.event()).getAmount();
                return mode == 0 || (mode == 1 ? amount > 0 : amount < 0);
            };
        }, "[player] (level progress|[e]xp|experience) (change|update|:increase|:decrease)");
        registrar.eventValue(PlayerExpChangeEvent.class, "experience", event -> new Experience(event.getAmount()));
        Events.simple(registrar, "EvtExperienceCooldownChange", PlayerExpCooldownChangeEvent.class,
                "player (experience|[e]xp) cooldown change");
        registrar.eventValue(PlayerExpCooldownChangeEvent.class, "experiencecooldownchangereason",
                PlayerExpCooldownChangeEvent::getReason);
        registrar.eventValue(PlayerExpCooldownChangeEvent.class, "timespan",
                event -> new Timespan(event.getNewCooldown() * 50L));
        registrar.eventValue(PlayerExpCooldownChangeEvent.class, "timespan", -1,
                event -> new Timespan(event.getPlayer().getExpCooldown() * 50L));
        Events.simple(registrar, "EvtItemMend", PlayerItemMendEvent.class, "item mend[ing]");
        registrar.eventValue(PlayerItemMendEvent.class, "itemstack", PlayerItemMendEvent::getItem);
        registrar.eventValue(PlayerItemMendEvent.class, "entity", PlayerItemMendEvent::getExperienceOrb);
    }

    /** Skript's simple player events, which have no filter. */
    private static void registerSimple(Registrar registrar) {
        Events.simple(registrar, "EvtBedEnter", PlayerBedEnterEvent.class, "bed enter[ing]", "[player] enter[ing] [a] bed");
        Events.simple(registrar, "EvtBedLeave", PlayerBedLeaveEvent.class, "bed leav(e|ing)", "[player] leav(e|ing) [a] bed");
        Events.simple(registrar, "EvtBucketEmpty", PlayerBucketEmptyEvent.class, "bucket empty[ing]",
                "[player] empty[ing] [a] bucket");
        Events.simple(registrar, "EvtBucketFill", PlayerBucketFillEvent.class, "bucket fill[ing]", "[player] fill[ing] [a] bucket");
        Events.simple(registrar, "EvtEggThrow", PlayerEggThrowEvent.class, "throw[ing] [of] [an] egg", "[player] egg throw");
        Events.simple(registrar, "EvtItemBreak", PlayerItemBreakEvent.class, "[player] tool break[ing]",
                "[player] break[ing] (a|the|) tool");
        Events.simple(registrar, "EvtItemDamage", PlayerItemDamageEvent.class, "item damag(e|ing)");
        Events.simple(registrar, "EvtToolChange", PlayerItemHeldEvent.class, "[player['s]] (tool|item held|held item) chang(e|ing)");
        Events.simple(registrar, "EvtKick", PlayerKickEvent.class, "[player] (kick|being kicked)");
        Events.simple(registrar, "EvtRespawn", PlayerRespawnEvent.class, "[player] respawn[ing]");
        Events.simple(registrar, "EvtSneakToggle", PlayerToggleSneakEvent.class, "[player] toggl(e|ing) sneak",
                "[player] sneak toggl(e|ing)");
        Events.simple(registrar, "EvtSprintToggle", PlayerToggleSprintEvent.class, "[player] toggl(e|ing) sprint",
                "[player] sprint toggl(e|ing)");
        Events.simple(registrar, "EvtPlayerWorldChange", PlayerChangedWorldEvent.class, "[player] world chang(ing|e[d])");
        Events.simple(registrar, "EvtFlightToggle", PlayerToggleFlightEvent.class, "[player] flight toggl(e|ing)",
                "[player] toggl(e|ing) flight");
        Events.simple(registrar, "EvtLanguageChange", PlayerLocaleChangeEvent.class, "[player] (language|locale) chang(e|ing)",
                "[player] chang(e|ing) (language|locale)");
        Events.simple(registrar, "EvtJump", com.destroystokyo.paper.event.player.PlayerJumpEvent.class, "[player] jump[ing]");
        Events.simple(registrar, "EvtHandItemSwap", PlayerSwapHandItemsEvent.class, "swap[ping of] [(hand|held)] item[s]");
        Events.simple(registrar, "EvtRiptide", PlayerRiptideEvent.class, "[use of] riptide [enchant[ment]]");
        Events.simple(registrar, "EvtArmSwing", PlayerAnimationEvent.class, "[player] arm swing");
        Events.simple(registrar, "EvtPlayerTrade", io.papermc.paper.event.player.PlayerTradeEvent.class, "player trad(e|ing)");
        Events.simple(registrar, "EvtStopUsingItem", io.papermc.paper.event.player.PlayerStopUsingItemEvent.class,
                "[player] (stop|end) (using item|item use)");
        Events.simple(registrar, "EvtReadyArrow", com.destroystokyo.paper.event.player.PlayerReadyArrowEvent.class,
                "[player] ((ready|choose|draw|load) arrow|arrow (choose|draw|load))");
        Events.simple(registrar, "EvtInventorySlotChange", io.papermc.paper.event.player.PlayerInventorySlotChangeEvent.class,
                "[player] inventory slot chang(e|ing)");
        Events.simple(registrar, "EvtPlayerDeepSleep", io.papermc.paper.event.player.PlayerDeepSleepEvent.class,
                "[player] deep sleep[ing]");
        Events.simple(registrar, "EvtPlayerPickupArrow", PlayerPickupArrowEvent.class,
                "[player] (pick[ing| ]up [an] arrow|arrow pick[ing| ]up)");
        Events.simple(registrar, "EvtBroadcast", org.bukkit.event.server.BroadcastMessageEvent.class, "broadcast");
        Events.simple(registrar, "EvtElytraBoost", com.destroystokyo.paper.event.player.PlayerElytraBoostEvent.class,
                "elytra boost");
        Events.simple(registrar, "EvtUnknownCommand", org.bukkit.event.command.UnknownCommandEvent.class,
                "(unknown|non-existent) command [execution]");
    }

    /** The values of player events. */
    private static void registerPlayerValues(Registrar registrar) {
        registrar.eventValue(PlayerBedEnterEvent.class, "block", PlayerBedEnterEvent::getBed);
        registrar.eventValue(PlayerBedLeaveEvent.class, "block", PlayerBedLeaveEvent::getBed);
        registrar.eventValue(PlayerEggThrowEvent.class, "entity", org.bukkit.entity.Egg.class, 0, PlayerEggThrowEvent::getEgg);
        registrar.eventValue(PlayerItemBreakEvent.class, "itemstack", PlayerItemBreakEvent::getBrokenItem);
        registrar.eventValue(PlayerItemDamageEvent.class, "itemstack", PlayerItemDamageEvent::getItem);
        registrar.eventValue(PlayerItemHeldEvent.class, "slot", event -> new Items.Slot(event.getPlayer().getInventory(),
                event.getNewSlot()));
        registrar.eventValue(PlayerItemHeldEvent.class, "slot", -1, event -> new Items.Slot(event.getPlayer().getInventory(),
                event.getPreviousSlot()));
        registrar.eventValue(PlayerShearEntityEvent.class, "entity", PlayerShearEntityEvent::getEntity);
        registrar.eventValue(PlayerRespawnEvent.class, "respawnreason", PlayerRespawnEvent::getRespawnReason);
        registrar.eventValue(PlayerRiptideEvent.class, "itemstack", PlayerRiptideEvent::getItem);
        registrar.eventValue(PlayerChangedWorldEvent.class, "world", -1, PlayerChangedWorldEvent::getFrom);
        registrar.eventValue(PlayerQuitEvent.class, "quitreason", PlayerQuitEvent::getReason);
        registrar.eventValue(PlayerPickupArrowEvent.class, "entity", org.bukkit.entity.AbstractArrow.class, 0,
                PlayerPickupArrowEvent::getArrow);
        registrar.eventValue(PlayerPickupArrowEvent.class, "itemstack", event -> event.getItem().getItemStack());
        var trade = io.papermc.paper.event.player.PlayerTradeEvent.class;
        registrar.eventValue(trade, "entity", org.bukkit.entity.AbstractVillager.class, 0,
                io.papermc.paper.event.player.PlayerTradeEvent::getVillager);
        var stopUsing = io.papermc.paper.event.player.PlayerStopUsingItemEvent.class;
        registrar.eventValue(stopUsing, "timespan", event -> new Timespan(event.getTicksHeldFor() * 50L));
        registrar.eventValue(stopUsing, "itemtype", event -> new ItemType(event.getItem(), true));
        // Skript has only the item type, which its items convert from.
        registrar.eventValue(stopUsing, "itemstack", io.papermc.paper.event.player.PlayerStopUsingItemEvent::getItem);
        var slotChange = io.papermc.paper.event.player.PlayerInventorySlotChangeEvent.class;
        registrar.eventValue(slotChange, "itemstack", io.papermc.paper.event.player.PlayerInventorySlotChangeEvent::getNewItemStack);
        registrar.eventValue(slotChange, "itemstack", -1, io.papermc.paper.event.player.PlayerInventorySlotChangeEvent::getOldItemStack);
        registrar.eventValue(slotChange, "slot", event -> slot(event.getPlayer(), event.getSlot()));
        var boost = com.destroystokyo.paper.event.player.PlayerElytraBoostEvent.class;
        registrar.eventValue(boost, "itemstack", com.destroystokyo.paper.event.player.PlayerElytraBoostEvent::getItemStack);
        registrar.eventValue(boost, "entity", com.destroystokyo.paper.event.player.PlayerElytraBoostEvent::getFirework);
        var unknown = org.bukkit.event.command.UnknownCommandEvent.class;
        registrar.eventValue(unknown, "commandsender", org.bukkit.event.command.UnknownCommandEvent::getSender);
        registrar.eventValue(unknown, "block", event -> event.getSender() instanceof org.bukkit.command.BlockCommandSender sender
                ? sender.getBlock() : null);
        registrar.eventValue(unknown, "location", event -> where(event.getSender()));
        registrar.eventValue(unknown, "world", event -> where(event.getSender()) == null ? null
                : where(event.getSender()).getWorld());
    }

    /** Where a command's sender is: an entity's location, a command block's, or the main world's spawn. */
    private static Location where(org.bukkit.command.CommandSender sender) {
        return switch (sender) {
            case Entity entity -> entity.getLocation();
            case org.bukkit.command.BlockCommandSender block -> block.getBlock().getLocation();
            default -> Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().getFirst().getSpawnLocation();
        };
    }

    /** A slot of a player's inventory by its index: the armour and off hand past the 36 others. */
    static Items.Slot slot(Player player, int index) {
        return switch (index) {
            case 36 -> Items.Slot.of(player, EquipmentSlot.FEET);
            case 37 -> Items.Slot.of(player, EquipmentSlot.LEGS);
            case 38 -> Items.Slot.of(player, EquipmentSlot.CHEST);
            case 39 -> Items.Slot.of(player, EquipmentSlot.HEAD);
            case 40 -> Items.Slot.of(player, EquipmentSlot.OFF_HAND);
            default -> new Items.Slot(player.getInventory(), index);
        };
    }

    /** Armour, command lists, spectating, books, resource packs, game modes, fishing and bucketing entities. */
    private static void registerOther(Registrar registrar) {
        var armor = com.destroystokyo.paper.event.player.PlayerArmorChangeEvent.class;
        registrar.event("EvtPlayerArmorChange", List.of(armor), info -> {
            return (context, args) -> {
                Object slot = args.length == 0 ? null : args[0];
                var event = (com.destroystokyo.paper.event.player.PlayerArmorChangeEvent) context.event();
                return slot == null || slot == armorSlot(event);
            };
        }, "[player] armo[u]r change[d]", "[player] %equipmentslot% change[d]");
        registrar.eventValue(armor, "equipmentslot", PlayerEvents::armorSlot);
        registrar.eventValue(armor, "itemstack", -1, com.destroystokyo.paper.event.player.PlayerArmorChangeEvent::getOldItem);
        registrar.eventValue(armor, "itemstack", 1, com.destroystokyo.paper.event.player.PlayerArmorChangeEvent::getNewItem);
        registrar.eventValue(armor, "slot", event -> Items.Slot.of(event.getPlayer(), armorSlot(event)));

        registrar.event("EvtPlayerCommandSend", List.of(PlayerCommandSendEvent.class), null,
                "send[ing] [of [the]] [server] command[s] list", "[server] command list send");

        var start = com.destroystokyo.paper.event.player.PlayerStartSpectatingEntityEvent.class;
        var stop = com.destroystokyo.paper.event.player.PlayerStopSpectatingEntityEvent.class;
        registrar.event("EvtSpectate", List.of(start, stop), info -> {
            int pattern = info.pattern() - 1;
            return (context, args) -> {
                boolean swap = false;
                Entity entity;
                if (pattern != -1 && context.event() instanceof com.destroystokyo.paper.event.player.PlayerStartSpectatingEntityEvent spectating) {
                    entity = spectating.getNewSpectatorTarget();
                    swap = pattern == 0 && entity != null && spectating.getCurrentSpectatorTarget() != null;
                    if (swap) {
                        entity = spectating.getCurrentSpectatorTarget();
                    }
                } else if (context.event() instanceof com.destroystokyo.paper.event.player.PlayerStopSpectatingEntityEvent stopped) {
                    entity = stopped.getSpectatorTarget();
                } else {
                    return false;
                }
                if (pattern == 0 && !swap) {
                    return false;
                }
                Object[] kinds = Events.values(args[0]);
                Entity target = entity;
                return kinds == null || Events.any(kinds, kind -> Events.isKind(kind, target));
            };
        }, "[player] stop spectating [(of|from) %-*entitytypes%]",
                "[player] (swap|switch) spectating [(of|from) %-*entitytypes%]",
                "[player] start spectating [of %-*entitytypes%]");

        registrar.event("EvtBookEdit", List.of(PlayerEditBookEvent.class),
                info -> (context, args) -> !((PlayerEditBookEvent) context.event()).isSigning(), "book (edit|change|write)");
        registrar.event("EvtBookSign", List.of(PlayerEditBookEvent.class),
                info -> (context, args) -> ((PlayerEditBookEvent) context.event()).isSigning(), "book sign[ing]");
        registrar.eventValue(PlayerEditBookEvent.class, "itemstack", -1, event -> book(event.getPreviousBookMeta()));
        registrar.eventValue(PlayerEditBookEvent.class, "itemstack", event -> book(event.getNewBookMeta()));
        registrar.eventValue(PlayerEditBookEvent.class, "string", String[].class, -1, event -> pages(event.getPreviousBookMeta()));
        registrar.eventValue(PlayerEditBookEvent.class, "string", String[].class, 0, event -> pages(event.getNewBookMeta()));

        registrar.event("EvtResourcePackResponse", List.of(PlayerResourcePackStatusEvent.class), info -> (context, args) ->
                args.length == 0 || args[0] == null || Events.check(info, 0, Events.values(args[0]),
                        state -> state == ((PlayerResourcePackStatusEvent) context.event()).getStatus()),
                "resource pack [request] response", "resource pack [request] %resourcepackstates%");

        registrar.event("EvtPlayerGameModeChange", List.of(PlayerGameModeChangeEvent.class), Events.optional(0, (event, modes) ->
                Events.any(modes, mode -> mode == ((PlayerGameModeChangeEvent) event).getNewGameMode())),
                "[player] game[ ]mode change [to %-gamemodes%]");
        registrar.eventValue(PlayerGameModeChangeEvent.class, "gamemode", PlayerGameModeChangeEvent::getNewGameMode);

        registerFishing(registrar);
    }

    /** The slot an armour change is in, by the event's slot type on any version. */
    private static EquipmentSlot armorSlot(com.destroystokyo.paper.event.player.PlayerArmorChangeEvent event) {
        return event.getSlot();
    }

    /** A writable book with {@code meta}, as Skript gives the book of a book edit. */
    private static ItemStack book(BookMeta meta) {
        ItemStack book = new ItemStack(Material.WRITABLE_BOOK);
        book.setItemMeta(meta);
        return book;
    }

    private static String[] pages(BookMeta meta) {
        return meta.pages().stream().map(Messages.LEGACY_SECTION::serialize).toArray(String[]::new);
    }

    /** Fishing: casting, catching, hooking, escaping; and catching fish in buckets. */
    private static void registerFishing(Registrar registrar) {
        List<PlayerFishEvent.State> states = new java.util.ArrayList<>();
        List<String> patterns = new java.util.ArrayList<>();
        java.util.function.BiConsumer<String, String> state = (name, pattern) -> {
            try {
                states.add(PlayerFishEvent.State.valueOf(name));
                patterns.add(pattern);
            } catch (IllegalArgumentException missing) {
                // Not on this version.
            }
        };
        state.accept("FISHING", "[fishing] (line|rod) cast");
        state.accept("CAUGHT_FISH", "fish (caught|catch)");
        state.accept("CAUGHT_ENTITY", "entity (hook[ed]|caught|catch)");
        state.accept("IN_GROUND", "(bobber|hook) (in|hit) ground");
        state.accept("FAILED_ATTEMPT", "fish (escape|get away)");
        state.accept("REEL_IN", "[fishing] (rod|line) reel in");
        state.accept("BITE", "fish bit(e|ing)");
        state.accept("LURED", "(fish approach[ing]|(bobber|hook) lure[d])");
        patterns.add("fishing state change[d]");
        registrar.event("EvtFish", List.of(PlayerFishEvent.class), info -> {
            PlayerFishEvent.State wanted = info.pattern() < states.size() ? states.get(info.pattern()) : null;
            return (context, args) -> wanted == null || ((PlayerFishEvent) context.event()).getState() == wanted;
        }, patterns.toArray(String[]::new));
        registrar.eventValue(PlayerFishEvent.class, "entity", PlayerFishEvent::getCaught);
        registrar.eventValue(PlayerFishEvent.class, "fishingstate", PlayerFishEvent::getState);
        registrar.eventValue(PlayerFishEvent.class, "entity", org.bukkit.entity.FishHook.class, 0, PlayerFishEvent::getHook);

        registrar.event("EvtBucketEntity", List.of(PlayerBucketEntityEvent.class), Events.optional(0, (event, kinds) ->
                kinds.length == 0 || Events.any(kinds, kind -> kind instanceof EntityKind entityKind
                        && entityKind.entityClass().isInstance(((PlayerBucketEntityEvent) event).getEntity()))),
                "bucket (catch[ing]|captur(e|ing)) [[of] %-entitytypes%]");
        registrar.eventValue(PlayerBucketEntityEvent.class, "itemstack", PlayerBucketEntityEvent::getOriginalBucket);
        registrar.eventValue(PlayerBucketEntityEvent.class, "itemstack", 1, PlayerBucketEntityEvent::getEntityBucket);
        registrar.eventValue(PlayerBucketEntityEvent.class, "entity", PlayerBucketEntityEvent::getEntity);
    }
}
