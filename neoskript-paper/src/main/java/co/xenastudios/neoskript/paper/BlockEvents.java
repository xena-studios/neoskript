/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.paper;

import co.xenastudios.neoskript.api.element.Condition;
import co.xenastudios.neoskript.api.registration.Registrar;
import co.xenastudios.neoskript.runtime.Comparison;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.TreeType;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockCanBuildEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.block.SpongeAbsorbEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.hanging.HangingEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.inventory.BrewingStandFuelEvent;
import org.bukkit.event.inventory.FurnaceBurnEvent;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.event.inventory.FurnaceStartSmeltEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerHarvestBlockEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Block events: breaking, placing, burning, growing, flowing, pistons, signs, furnaces, brewing stands and
 * beacons, with the values triggers get from them.
 */
final class BlockEvents {

    private BlockEvents() {}

    static void register(Registrar registrar) {
        registerBlockChanges(registrar);
        registerSimple(registrar);
        registerGrowth(registrar);
        registerFurnaces(registrar);
        registerBrewing(registrar);
        registerBeacons(registrar);
    }

    /** Skript's EvtBlock: breaking, mining, burning, placing, fading, forming and dropping blocks, of a kind. */
    private static void registerBlockChanges(Registrar registrar) {
        String of = " [[of] %-itemtypes/blockdatas%]";
        registrar.event("EvtBlock", List.of(BlockBreakEvent.class, PlayerBucketFillEvent.class, HangingBreakEvent.class),
                info -> blockFilter(info, Events.mark(info) == 1), "[block] (break[ing]|1¦min(e|ing))" + of);
        registrar.event("EvtBlockBurn", List.of(BlockBurnEvent.class), info -> blockFilter(info, false),
                "[block] burn[ing]" + of);
        registrar.event("EvtBlockPlace", List.of(BlockPlaceEvent.class, PlayerBucketEmptyEvent.class, HangingPlaceEvent.class),
                info -> blockFilter(info, false), "[block] (plac(e|ing)|build[ing])" + of);
        registrar.event("EvtBlockFade", List.of(BlockFadeEvent.class), info -> blockFilter(info, false),
                "[block] fad(e|ing)" + of);
        registrar.event("EvtBlockForm", List.of(BlockFormEvent.class), info -> blockFilter(info, false),
                "[block] form[ing]" + of);
        registrar.event("EvtBlockDrop", List.of(BlockDropItemEvent.class), info -> blockFilter(info, false),
                "block drop[ping]" + of);

        registrar.eventValue(BlockEvent.class, "block", BlockEvent::getBlock);
        registrar.eventValue(BlockEvent.class, "world", event -> event.getBlock().getWorld());
        registrar.eventValue(BlockEvent.class, "location", event -> center(event.getBlock()));

        registrar.eventValue(BlockPlaceEvent.class, "player", BlockPlaceEvent::getPlayer);
        registrar.eventValue(BlockPlaceEvent.class, "itemstack", -1, BlockPlaceEvent::getItemInHand);
        registrar.eventValue(BlockPlaceEvent.class, "itemstack", BlockPlaceEvent::getItemInHand);
        registrar.eventValue(BlockPlaceEvent.class, "itemstack", 1, event -> {
            ItemStack item = event.getItemInHand().clone();
            if (event.getPlayer().getGameMode() != org.bukkit.GameMode.CREATIVE) {
                item.setAmount(item.getAmount() - 1);
            }
            return item;
        });
        registrar.eventValue(BlockPlaceEvent.class, "block", -1, event -> Events.stateBlock(event.getBlockReplacedState()));
        registrar.eventValue(BlockPlaceEvent.class, "direction", event -> {
            BlockFace face = event.getBlockPlaced().getFace(event.getBlockAgainst());
            return new WorldSyntax.Direction(face == null ? new org.bukkit.util.Vector()
                    : new org.bukkit.util.Vector(face.getModX(), face.getModY(), face.getModZ()), false);
        });
        registrar.eventValue(BlockFadeEvent.class, "block", -1, BlockEvent::getBlock);
        registrar.eventValue(BlockFadeEvent.class, "block", BlockEvent::getBlock);
        registrar.eventValue(BlockFadeEvent.class, "block", 1, event -> Events.stateBlock(event.getNewState()));
        // Growing and forming (a kind of growing): the block as it will be, and as it was.
        registrar.eventValue(BlockGrowEvent.class, "block", event -> Events.stateBlock(event.getNewState()));
        registrar.eventValue(BlockGrowEvent.class, "block", -1, BlockEvent::getBlock);
        registrar.eventValue(BlockDamageEvent.class, "player", BlockDamageEvent::getPlayer);
        registrar.eventValue(BlockBreakEvent.class, "player", BlockBreakEvent::getPlayer);
        registrar.eventValue(BlockBreakEvent.class, "block", -1, BlockEvent::getBlock);
        registrar.eventValue(BlockFromToEvent.class, "block", 1, BlockFromToEvent::getToBlock);
        registrar.eventValue(BlockIgniteEvent.class, "player", BlockIgniteEvent::getPlayer);
        registrar.eventValue(BlockDispenseEvent.class, "itemstack", BlockDispenseEvent::getItem);
        registrar.eventValue(BlockCanBuildEvent.class, "block", -1, BlockEvent::getBlock);
        registrar.eventValue(BlockCanBuildEvent.class, "block", event -> Events.stateBlock(event.getBlock(), event.getMaterial()));
        registrar.eventValue(BlockCanBuildEvent.class, "player", BlockCanBuildEvent::getPlayer);
        registrar.eventValue(BlockFertilizeEvent.class, "player", BlockFertilizeEvent::getPlayer);
        registrar.eventValue(BlockFertilizeEvent.class, "block", Block[].class, 0, event -> event.getBlocks().stream()
                .map(BlockState::getBlock).toArray(Block[]::new));
        registrar.eventValue(BlockDropItemEvent.class, "block", -1, event -> Events.stateBlock(event.getBlockState()));
        registrar.eventValue(BlockDropItemEvent.class, "player", BlockDropItemEvent::getPlayer);
        registrar.eventValue(BlockDropItemEvent.class, "itemstack", ItemStack[].class, 0, event -> event.getItems().stream()
                .map(org.bukkit.entity.Item::getItemStack).toArray(ItemStack[]::new));
        registrar.eventValue(BlockDropItemEvent.class, "entity", org.bukkit.entity.Entity[].class, 0,
                event -> event.getItems().toArray(org.bukkit.entity.Entity[]::new));

        // Buckets: the block clicked, and where the liquid goes or came from.
        registrar.eventValue(PlayerBucketFillEvent.class, "block", PlayerBucketFillEvent::getBlockClicked);
        registrar.eventValue(PlayerBucketFillEvent.class, "block", 1, event -> Events.stateBlock(event.getBlockClicked(),
                Material.AIR));
        registrar.eventValue(PlayerBucketEmptyEvent.class, "block", -1, event -> event.getBlockClicked()
                .getRelative(event.getBlockFace()));
        registrar.eventValue(PlayerBucketEmptyEvent.class, "block", event -> Events.stateBlock(event.getBlockClicked()
                .getRelative(event.getBlockFace()), event.getBucket() == Material.WATER_BUCKET ? Material.WATER : Material.LAVA));

        // Hanging entities: paintings and item frames.
        registrar.eventValue(HangingEvent.class, "entity", org.bukkit.entity.Hanging.class, 0, HangingEvent::getEntity);
        registrar.eventValue(HangingEvent.class, "world", event -> event.getEntity().getWorld());
        registrar.eventValue(HangingEvent.class, "location", event -> event.getEntity().getLocation());
        registrar.eventValue(HangingBreakEvent.class, "entity", event ->
                event instanceof org.bukkit.event.hanging.HangingBreakByEntityEvent byEntity ? byEntity.getRemover() : null);
        registrar.eventValue(HangingPlaceEvent.class, "player", HangingPlaceEvent::getPlayer);
    }

    /** Skript's BlockUtils.getLocation: the middle of a block. */
    static Location center(Block block) {
        return block.getLocation().add(0.5, 0.5, 0.5);
    }

    /**
     * EvtBlock's filter: of the item types or block datas written, if any. For mining, only breaks that drop
     * something with the player's tool.
     */
    private static Condition blockFilter(co.xenastudios.neoskript.api.element.ElementInfo info, boolean mine) {
        return (context, args) -> {
            Object event = context.event();
            if (mine && event instanceof BlockBreakEvent breaking
                    && breaking.getBlock().getDrops(breaking.getPlayer().getInventory().getItemInMainHand()).isEmpty()) {
                return false;
            }
            Object[] types = Events.values(args[0]);
            if (types == null) {
                return true;
            }
            BlockData data;
            Material material;
            switch (event) {
                case BlockFormEvent form -> {
                    data = form.getNewState().getBlockData();
                    material = data.getMaterial();
                }
                case BlockEvent block -> {
                    data = block.getBlock().getBlockData();
                    material = block.getBlock().getType();
                }
                case PlayerBucketFillEvent fill -> {
                    data = fill.getBlockClicked().getBlockData();
                    material = fill.getBlockClicked().getType();
                }
                case PlayerBucketEmptyEvent empty -> {
                    data = null;
                    material = empty.getItemStack() == null ? Material.AIR : empty.getItemStack().getType();
                }
                case HangingEvent hanging -> {
                    return Events.check(info, 0, types, type -> type instanceof ItemType
                            && Comparison.equal(hanging.getEntity(), type));
                }
                default -> {
                    return false;
                }
            }
            return Events.check(info, 0, types, type -> switch (type) {
                case ItemType item -> item.material() == material;
                case BlockData pattern -> data != null && data.matches(pattern);
                default -> false;
            });
        };
    }

    /** Skript's simple block events, which have no filter. */
    private static void registerSimple(Registrar registrar) {
        Events.simple(registrar, "EvtCanBuildCheck", BlockCanBuildEvent.class, "[block] can build check");
        Events.simple(registrar, "EvtBlockDamage", BlockDamageEvent.class, "block damag(ing|e)");
        Events.simple(registrar, "EvtFlow", BlockFromToEvent.class, "[block] flow[ing]", "block mov(e|ing)");
        Events.simple(registrar, "EvtIgnition", BlockIgniteEvent.class, "[block] ignit(e|ion)");
        Events.simple(registrar, "EvtPhysics", BlockPhysicsEvent.class, "[block] physics");
        Events.simple(registrar, "EvtPistonExtend", BlockPistonExtendEvent.class, "piston extend[ing]");
        Events.simple(registrar, "EvtPistonRetract", BlockPistonRetractEvent.class, "piston retract[ing]");
        Events.simple(registrar, "EvtRedstone", BlockRedstoneEvent.class, "redstone [current] [chang(e|ing)]");
        Events.simple(registrar, "EvtSpread", BlockSpreadEvent.class, "spread[ing]");
        Events.simple(registrar, "EvtLeavesDecay", LeavesDecayEvent.class, "leaves decay[ing]");
        Events.simple(registrar, "EvtSpongeAbsorb", SpongeAbsorbEvent.class, "sponge absorb");
        Events.simple(registrar, "EvtBlockFertilize", BlockFertilizeEvent.class, "[block] fertilize");
        Events.simple(registrar, "EvtBellRing", org.bukkit.event.block.BellRingEvent.class, "bell ring[ing]");
        Events.simple(registrar, "EvtBellResonate", org.bukkit.event.block.BellResonateEvent.class, "bell resonat(e|ing)");
        Events.simple(registrar, "EvtVaultDisplayItem", org.bukkit.event.block.VaultDisplayItemEvent.class,
                "vault display[ing] item");
        Events.simple(registrar, "EvtSignChange", SignChangeEvent.class, "sign (chang[e]|edit)[ing]", "[player] (chang[e]|edit)[ing] [a] sign");

        registrar.eventValue(org.bukkit.event.block.BellRingEvent.class, "entity", org.bukkit.event.block.BellRingEvent::getEntity);
        registrar.eventValue(org.bukkit.event.block.BellRingEvent.class, "direction", event -> new WorldSyntax.Direction(
                new org.bukkit.util.Vector(event.getDirection().getModX(), event.getDirection().getModY(),
                        event.getDirection().getModZ()), false));
        registrar.eventValue(org.bukkit.event.block.BellResonateEvent.class, "entity", org.bukkit.entity.Entity[].class, 0,
                event -> event.getResonatedEntities().toArray(new org.bukkit.entity.LivingEntity[0]));
        registrar.eventValue(org.bukkit.event.block.VaultDisplayItemEvent.class, "itemstack",
                org.bukkit.event.block.VaultDisplayItemEvent::getDisplayItem);
        registrar.eventValue(SignChangeEvent.class, "player", SignChangeEvent::getPlayer);
        registrar.eventValue(SignChangeEvent.class, "string", String[].class, 0, event -> event.lines().stream()
                .map(line -> line == null ? "" : Messages.LEGACY_SECTION.serialize(line)).toArray(String[]::new));
    }

    /** Growing: trees and blocks, from and into kinds of them, and harvesting. */
    private static void registerGrowth(Registrar registrar) {
        // Pattern 0 'of', 1 'from', 2 'into', 3 'from ... into'; mark 1 for tree types, 2 for blocks.
        registrar.event("EvtGrow", List.of(StructureGrowEvent.class, BlockGrowEvent.class), info -> {
            int pattern = info.pattern();
            int kind = Events.mark(info);
            return (context, args) -> {
                Object[] from = null;
                Object[] into = null;
                switch (pattern) {
                    case 0 -> from = kind == 1 ? Events.values(args[0]) : kind == 2 ? Events.values(args[1]) : null;
                    case 1 -> from = Events.values(args[0]);
                    case 2 -> into = kind == 1 ? Events.values(args[0]) : Events.values(args[1]);
                    default -> {
                        from = Events.values(args[0]);
                        into = kind == 1 ? Events.values(args[1]) : Events.values(args[2]);
                    }
                }
                Object event = context.event();
                if (from == null && pattern != 2) {
                    return pattern == 0;
                }
                if (kind == 1 && !(event instanceof StructureGrowEvent)) {
                    return false;
                } else if (kind == 2 && !(event instanceof BlockGrowEvent) && pattern != 0) {
                    return false;
                }
                return switch (pattern) {
                    case 0 -> growsFrom(event, from) || growsInto(event, from);
                    case 1 -> growsFrom(event, from);
                    case 2 -> growsInto(event, into);
                    default -> growsFrom(event, from) && growsInto(event, into);
                };
            };
        }, "grow[th] [of (1:%-treetypes%|2:%-itemtypes/blockdatas%)]",
                "grow[th] from %itemtypes/blockdatas%",
                "grow[th] [in]to (1:%treetypes%|2:%itemtypes/blockdatas%)",
                "grow[th] from %itemtypes/blockdatas% [in]to (1:%treetypes%|2:%itemtypes/blockdatas%)");
        registrar.event("EvtPlantGrowth", List.of(BlockGrowEvent.class), Events.optional(0, (event, types) -> {
            Block block = ((BlockGrowEvent) event).getBlock();
            return Events.any(types, type -> type instanceof ItemType item && item.material() == block.getType());
        }), "(plant|crop|block) grow[(th|ing)] [[of] %-itemtypes%]");
        registrar.event("EvtHarvestBlock", List.of(PlayerHarvestBlockEvent.class), Events.optional(0, (event, types) -> {
            Block block = ((PlayerHarvestBlockEvent) event).getHarvestedBlock();
            return Events.any(types, type -> Events.isBlock(type, block.getBlockData()));
        }), "[player] [block|crop] harvest[ing] [of %-itemtypes/blockdatas%]");

        registrar.eventValue(StructureGrowEvent.class, "block", event -> event.getLocation().getBlock());
        registrar.eventValue(StructureGrowEvent.class, "block", Block[].class, 0, event -> event.getBlocks().stream()
                .map(BlockState::getBlock).toArray(Block[]::new));
        registrar.eventValue(StructureGrowEvent.class, "block", 1, event -> {
            for (BlockState state : event.getBlocks()) {
                if (state.getLocation().equals(event.getLocation())) {
                    return Events.stateBlock(state);
                }
            }
            return event.getLocation().getBlock();
        });
        registrar.eventValue(PlayerHarvestBlockEvent.class, "block", PlayerHarvestBlockEvent::getHarvestedBlock);
        registrar.eventValue(PlayerHarvestBlockEvent.class, "itemstack", ItemStack[].class, 0,
                event -> event.getItemsHarvested().toArray(ItemStack[]::new));
        registrar.eventValue(PlayerHarvestBlockEvent.class, "equipmentslot", PlayerHarvestBlockEvent::getHand);
        registrar.eventValue(PlayerHarvestBlockEvent.class, "slot",
                event -> Items.Slot.of(event.getPlayer(), event.getHand()));
    }

    /** Whether a grow event grew from one of {@code types}: a tree's sapling, or the block as it was. */
    private static boolean growsFrom(Object event, Object[] types) {
        boolean and = false;
        if (event instanceof StructureGrowEvent grow) {
            Material sapling = sapling(grow.getSpecies());
            return Events.any(types, type -> switch (type) {
                case ItemType item -> item.material() == sapling;
                case BlockData data -> data.getMaterial() == sapling;
                default -> false;
            });
        } else if (event instanceof BlockGrowEvent grow) {
            BlockData old = grow.getBlock().getBlockData();
            return Events.any(types, type -> Events.isBlock(type, old));
        }
        return and;
    }

    /** Whether a grow event grew into one of {@code types}: a kind of tree, or the block as it will be. */
    private static boolean growsInto(Object event, Object[] types) {
        if (event instanceof StructureGrowEvent grow) {
            String species = grow.getSpecies().name();
            return Events.any(types, type -> type instanceof WorldSyntax.TreeKind tree
                    && (tree.types().contains("*") || tree.types().contains(species)));
        } else if (event instanceof BlockGrowEvent grow) {
            BlockData data = grow.getNewState().getBlockData();
            return Events.any(types, type -> Events.isBlock(type, data));
        }
        return false;
    }

    /** The sapling a kind of tree grows from, as Skript's ItemUtils.getTreeSapling. */
    private static Material sapling(TreeType type) {
        return switch (type.name()) {
            case "TREE", "BIG_TREE", "SWAMP" -> Material.OAK_SAPLING;
            case "REDWOOD", "TALL_REDWOOD", "MEGA_REDWOOD", "MEGA_PINE" -> Material.SPRUCE_SAPLING;
            case "BIRCH", "TALL_BIRCH" -> Material.BIRCH_SAPLING;
            case "JUNGLE", "SMALL_JUNGLE", "JUNGLE_BUSH", "COCOA_TREE" -> Material.JUNGLE_SAPLING;
            case "ACACIA" -> Material.ACACIA_SAPLING;
            case "DARK_OAK" -> Material.DARK_OAK_SAPLING;
            case "BROWN_MUSHROOM" -> Material.BROWN_MUSHROOM;
            case "RED_MUSHROOM" -> Material.RED_MUSHROOM;
            case "CHORUS_PLANT" -> Material.CHORUS_FLOWER;
            case "CRIMSON_FUNGUS" -> Material.CRIMSON_FUNGUS;
            case "WARPED_FUNGUS" -> Material.WARPED_FUNGUS;
            case "AZALEA" -> Material.AZALEA;
            case "MANGROVE", "TALL_MANGROVE" -> Material.MANGROVE_PROPAGULE;
            case "CHERRY" -> Material.CHERRY_SAPLING;
            case "PALE_OAK", "PALE_OAK_CREAKING" -> Material.PALE_OAK_SAPLING;
            default -> Material.AIR;
        };
    }

    /** Furnaces: smelting, burning fuel, taking items out and starting to smelt, of kinds of items. */
    private static void registerFurnaces(Registrar registrar) {
        registrar.event("EvtFurnace", List.of(FurnaceSmeltEvent.class), furnaceFilter(),
                "[furnace] [ore] smelt[ed|ing] [of %-itemtypes%]", "[furnace] smelt[ed|ing] of ore");
        registrar.event("EvtFurnaceBurn", List.of(FurnaceBurnEvent.class), furnaceFilter(),
                "[furnace] fuel burn[ing] [of %-itemtypes%]");
        registrar.event("EvtFurnaceExtract", List.of(FurnaceExtractEvent.class), furnaceFilter(),
                "furnace [item] extract[ion] [of %-itemtypes%]");
        registrar.event("EvtFurnaceStartSmelt", List.of(FurnaceStartSmeltEvent.class), furnaceFilter(),
                "[furnace] start [of] smelt[ing] [[of] %-itemtypes%]", "[furnace] smelt[ing] start [of %-itemtypes%]");
        registrar.eventValue(FurnaceExtractEvent.class, "player", FurnaceExtractEvent::getPlayer);
        registrar.eventValue(FurnaceExtractEvent.class, "itemstack", ItemStack[].class, 0,
                event -> new ItemStack[] {new ItemStack(event.getItemType(), event.getItemAmount())});
    }

    /** EvtFurnace's filter: the item smelted, burnt or taken out is of one of the types written. */
    private static co.xenastudios.neoskript.api.element.ElementFactory<Condition> furnaceFilter() {
        return info -> (context, args) -> {
            Object[] types = Events.values(args.length == 0 ? null : args[0]);
            if (types == null) {
                return true;
            }
            ItemStack item = switch (context.event()) {
                case FurnaceSmeltEvent smelt -> smelt.getSource();
                case FurnaceBurnEvent burn -> burn.getFuel();
                case FurnaceExtractEvent extract -> new ItemStack(extract.getItemType());
                case FurnaceStartSmeltEvent start -> start.getSource();
                default -> null;
            };
            return Events.check(info, 0, types, type -> Events.isItem(type, item));
        };
    }

    /** Brewing stands: finishing, using fuel and starting. */
    private static void registerBrewing(Registrar registrar) {
        registrar.event("EvtBrewingComplete", List.of(BrewEvent.class), Events.optional(0, (event, types) -> {
            List<ItemStack> results = ((BrewEvent) event).getResults();
            for (Object type : types) {
                for (ItemStack result : results) {
                    if (type instanceof ItemType && Events.isItem(type, result)) {
                        return true;
                    }
                    if (type instanceof PotionEffectType effect && result != null
                            && result.getItemMeta() instanceof PotionMeta meta) {
                        List<PotionEffect> effects = new java.util.ArrayList<>(meta.getCustomEffects());
                        if (meta.getBasePotionType() != null) {
                            effects.addAll(meta.getBasePotionType().getPotionEffects());
                        }
                        for (PotionEffect potion : effects) {
                            if (potion.getType().equals(effect)) {
                                return true;
                            }
                        }
                    }
                }
            }
            return false;
        }), "brew[ing] [complet(e[d]|ion)|finish[ed]] [(of|for) %-itemtypes/potioneffecttypes%]");
        registrar.event("EvtBrewingFuel", List.of(BrewingStandFuelEvent.class), Events.optional(0, (event, types) ->
                Events.any(types, type -> Events.isItem(type, ((BrewingStandFuelEvent) event).getFuel()))),
                "brew[ing [stand]] consum(e|ing) fuel [of %-itemtypes%]",
                "brew[ing [stand]] fuel consumption [of %-itemtypes%]");
        Events.simple(registrar, "EvtBrewingStart", org.bukkit.event.block.BrewingStartEvent.class, "brew[ing] start[ed|ing]");
        registrar.eventValue(BrewingStandFuelEvent.class, "itemstack", BrewingStandFuelEvent::getFuel);
    }

    /** Beacons: applying their effects, being turned on and off, and players changing their effects. */
    private static void registerBeacons(Registrar registrar) {
        registrar.event("EvtBeaconEffect", List.of(com.destroystokyo.paper.event.block.BeaconEffectEvent.class), info -> {
            Boolean primary = info.hasTag("primary") ? Boolean.TRUE : info.hasTag("secondary") ? Boolean.FALSE : null;
            return (context, args) -> {
                var event = (com.destroystokyo.paper.event.block.BeaconEffectEvent) context.event();
                if (primary != null && event.isPrimary() != primary) {
                    return false;
                }
                Object[] types = Events.values(args[0]);
                return types == null || Events.check(info, 0, types, type -> event.getEffect().getType().equals(type));
            };
        }, "[:primary|:secondary] beacon effect [of %-potioneffecttypes%]",
                "application of [:primary|:secondary] beacon effect [of %-potioneffecttypes%]",
                "[:primary|:secondary] beacon effect apply [of %-potioneffecttypes%]");
        registrar.event("EvtBeaconToggle", List.of(io.papermc.paper.event.block.BeaconActivatedEvent.class,
                io.papermc.paper.event.block.BeaconDeactivatedEvent.class), info -> {
            int pattern = info.pattern();
            return (context, args) -> pattern == 0
                    || (pattern == 1) == (context.event() instanceof io.papermc.paper.event.block.BeaconActivatedEvent);
        }, "beacon toggle", "beacon activat(e|ion)", "beacon deactivat(e|ion)");
        Events.simple(registrar, "EvtBeaconChangeEffect", io.papermc.paper.event.player.PlayerChangeBeaconEffectEvent.class,
                "beacon change effect", "beacon effect change", "player chang(e[s]|ing) [of] beacon effect");

        registrar.eventValueExcluded(com.destroystokyo.paper.event.block.BeaconEffectEvent.class, "potioneffecttype",
                "Use 'applied effect' in beacon effect events.");
        registrar.eventValue(com.destroystokyo.paper.event.block.BeaconEffectEvent.class, "player",
                com.destroystokyo.paper.event.block.BeaconEffectEvent::getPlayer);
        registrar.eventValue(io.papermc.paper.event.player.PlayerChangeBeaconEffectEvent.class, "block",
                io.papermc.paper.event.player.PlayerChangeBeaconEffectEvent::getBeacon);
    }
}
