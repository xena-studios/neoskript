package co.xenastudios.neoskript.lang.generated;

import co.xenastudios.neoskript.api.registry.SyntaxRegistry;
import co.xenastudios.neoskript.api.syntax.Expression;
import co.xenastudios.neoskript.core.expression.ComputedExpression;
import co.xenastudios.neoskript.core.expression.ComputedListExpression;

/**
 * Generated condition syntax (property and event-restricted boolean checks).
 *
 * <p><strong>Generated code.</strong> These registrations were produced by the Skript-conformance
 * tooling (see {@code docs/conformance}) from the upstream Skript 2.15.3 source, then compile- and
 * parse-verified. Prefer regenerating over editing by hand; order is significant for overlapping
 * patterns and mirrors the original per-pass generation order.
 */
public final class GeneratedConditions {
    private GeneratedConditions() {}

    public static void register(SyntaxRegistry registry) {
        // bulk boolean conditions
        registry.registerCondition("%object% can duplicate", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                return o instanceof org.bukkit.entity.Allay h && h.canDuplicate();
            };
        });
        registry.registerCondition("%object% is dashing", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                return o instanceof org.bukkit.entity.Camel h && h.isDashing();
            };
        });
        registry.registerCondition("%object% can breed", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                return o instanceof org.bukkit.entity.Breedable h && h.canBreed();
            };
        });
        registry.registerCondition("%object% can despawn when far away", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                return o instanceof org.bukkit.entity.LivingEntity h && h.getRemoveWhenFarAway();
            };
        });
        registry.registerCondition("%object% can pick up items", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                return o instanceof org.bukkit.entity.LivingEntity h && h.getCanPickupItems();
            };
        });
        registry.registerCondition("%object% scales damage with difficulty", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                return o instanceof org.bukkit.damage.DamageSource h && h.scalesWithDifficulty();
            };
        });
        registry.registerCondition("respawn anchors work in %object%", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                return o instanceof org.bukkit.World h && h.isRespawnAnchorWorks();
            };
        });
        registry.registerCondition("%object% is wet", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                return o instanceof org.bukkit.entity.Entity h && h.isInWaterOrRainOrBubbleColumn();
            };
        });
        registry.registerCondition("%object% has AI", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                return o instanceof org.bukkit.entity.LivingEntity h && h.hasAI();
            };
        });
        registry.registerCondition("%object% has a resource pack loaded", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                return o instanceof org.bukkit.entity.Player h && h.hasResourcePack();
            };
        });
        registry.registerCondition("%object% is climbing", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                return o instanceof org.bukkit.entity.LivingEntity h && h.isClimbing();
            };
        });
        registry.registerCondition("%object%'s custom name is visible", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                return o instanceof org.bukkit.entity.Entity h && h.isCustomNameVisible();
            };
        });
        registry.registerCondition("%object% is from a mob spawner", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                return o instanceof org.bukkit.entity.Entity h && h.fromMobSpawner();
            };
        });
        registry.registerCondition("%object% is jumping", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                return o instanceof org.bukkit.entity.LivingEntity h && h.isJumping();
            };
        });
        registry.registerCondition("%object% is normalized", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                return o instanceof org.bukkit.util.Vector h && h.isNormalized();
            };
        });
        registry.registerCondition("%object% is passable", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                return o instanceof org.bukkit.block.Block h && h.isPassable();
            };
        });
        registry.registerCondition("%object% is riptiding", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                return o instanceof org.bukkit.entity.LivingEntity h && h.isRiptiding();
            };
        });
        registry.registerCondition("%object% is a slime chunk", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                return o instanceof org.bukkit.Chunk h && h.isSlimeChunk();
            };
        });
        registry.registerCondition("%object% is ticking", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                return o instanceof org.bukkit.entity.Entity h && h.isTicking();
            };
        });
        registry.registerCondition("%object% is on its back", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                return o instanceof org.bukkit.entity.Panda h && h.isOnBack();
            };
        });
        registry.registerCondition("%object% is rolling", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                return o instanceof org.bukkit.entity.Panda h && h.isRolling();
            };
        });
        registry.registerCondition("%object% is scared", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                return o instanceof org.bukkit.entity.Panda h && h.isScared();
            };
        });
        registry.registerCondition("%object% is sneezing", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                return o instanceof org.bukkit.entity.Panda h && h.isSneezing();
            };
        });
        registry.registerCondition("%object% is instant", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                return o instanceof org.bukkit.potion.PotionEffectType h && h.isInstant();
            };
        });
        registry.registerCondition("%object% will despawn naturally", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                return o instanceof org.bukkit.entity.Item h && !h.isUnlimitedLifetime();
            };
        });
        // PropertyCondition extraction
        registry.registerCondition("%object% (is|are) normalized", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                return o instanceof org.bukkit.util.Vector h && h.isNormalized();
            };
        });
        registry.registerCondition("%object% (is|are) riptiding", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                return o instanceof org.bukkit.entity.LivingEntity h && h.isRiptiding();
            };
        });
        registry.registerCondition("%object% (is|are) flying", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                return o instanceof org.bukkit.entity.Player h && h.isFlying();
            };
        });
        registry.registerCondition("%object% (is|are) (invisible|visible)", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                return o instanceof org.bukkit.entity.LivingEntity h && h.isInvisible();
            };
        });
        registry.registerCondition("%object% (is|are) ticking", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                return o instanceof org.bukkit.entity.Entity h && h.isTicking();
            };
        });
        registry.registerCondition("%object% (is|are) passable", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                return o instanceof org.bukkit.block.Block h && h.isPassable();
            };
        });
        registry.registerCondition("%object% (is|are) swimming", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                return o instanceof org.bukkit.entity.LivingEntity h && h.isSwimming();
            };
        });
        registry.registerCondition("%object% (is|are) sprinting", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                return o instanceof org.bukkit.entity.Player h && h.isSprinting();
            };
        });
        registry.registerCondition("%object% (is|are) [[a] server|an] op[erator][s]", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                return o instanceof org.bukkit.OfflinePlayer h && h.isOp();
            };
        });
        registry.registerCondition("%object% (is|are) on [the] ground", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                return o instanceof org.bukkit.entity.Entity h && h.isOnGround();
            };
        });
        registry.registerCondition("%object% (is|are) gliding", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                return o instanceof org.bukkit.entity.LivingEntity h && h.isGliding();
            };
        });
        registry.registerCondition("%object% (is|are) silent", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                return o instanceof org.bukkit.entity.Entity h && h.isSilent();
            };
        });
        registry.registerCondition("%object% (is|are) climbing", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                return o instanceof org.bukkit.entity.LivingEntity h && h.isClimbing();
            };
        });
        registry.registerCondition("%object% (is|are) frozen", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                return o instanceof org.bukkit.entity.Entity h && h.isFrozen();
            };
        });
        registry.registerCondition("%object% (is|are) wet", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                return o instanceof org.bukkit.entity.Entity h && h.isInWaterOrRainOrBubbleColumn();
            };
        });
        registry.registerCondition("%object% (is|are) sleeping", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                return o instanceof org.bukkit.entity.LivingEntity h && h.isSleeping();
            };
        });
        registry.registerCondition("%object% (is|are) sneaking", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                return o instanceof org.bukkit.entity.Player h && h.isSneaking();
            };
        });
        registry.registerCondition("%object% (is|are) jumping", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                return o instanceof org.bukkit.entity.LivingEntity h && h.isJumping();
            };
        });
        registry.registerCondition("%object% (is|are) (blocking|defending) [with [a] shield]", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                return o instanceof org.bukkit.entity.Player h && h.isBlocking();
            };
        });
        // remaining boolean conditions
        registry.registerCondition("%object% can see chat colors", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                return o instanceof org.bukkit.entity.Player h && h.getClientOption(com.destroystokyo.paper.ClientOption.CHAT_COLORS_ENABLED);
            };
        });
        registry.registerCondition("%object% has been stared at", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                return o instanceof org.bukkit.entity.Enderman h && h.hasBeenStaredAt();
            };
        });
        registry.registerCondition("%object% is in open water", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                return o instanceof org.bukkit.entity.FishHook h && h.isInOpenWater();
            };
        });
        registry.registerCondition("%object% is in love", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                return o instanceof org.bukkit.entity.Animals h && h.isLoveMode();
            };
        });
        // source-grounded property conditions
        registry.registerCondition("%object% (is|are) sedated", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                return o instanceof org.bukkit.block.Block h && (h.getState() instanceof org.bukkit.block.Beehive beehive && beehive.isSedated());
            };
        });
        registry.registerCondition("%object% (is|are) resonating", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                return o instanceof org.bukkit.block.Block h && (h.getState(false) instanceof org.bukkit.block.Bell bell && bell.isResonating());
            };
        });
        registry.registerCondition("%object% (is|are) ringing", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                return o instanceof org.bukkit.block.Block h && (h.getState(false) instanceof org.bukkit.block.Bell bell && bell.isShaking());
            };
        });
        registry.registerCondition("[the] entity storage of %object% (is|are) full", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                return o instanceof org.bukkit.block.Block h && (h.getState() instanceof org.bukkit.block.EntityBlockStorage<?> s && s.isFull());
            };
        });
        registry.registerCondition("%object% can be dispensed", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                return o instanceof io.papermc.paper.datacomponent.item.Equippable h && (h.dispensable());
            };
        });
        registry.registerCondition("%object% can be sheared off [of entities]", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                return o instanceof io.papermc.paper.datacomponent.item.Equippable h && (h.canBeSheared());
            };
        });
        registry.registerCondition("%object% can be (equipped|put) on[to] entities", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                return o instanceof io.papermc.paper.datacomponent.item.Equippable h && (h.equipOnInteract());
            };
        });
        registry.registerCondition("%object% can swap equipment [on right click|when right clicked]", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                return o instanceof io.papermc.paper.datacomponent.item.Equippable h && (h.swappable());
            };
        });
        registry.registerCondition("%object% will (lose durability|be damaged) (on [wearer['s]] injury|when [[the] wearer [is]] (hurt|injured|damaged))", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                return o instanceof io.papermc.paper.datacomponent.item.Equippable h && (h.damageOnHurt());
            };
        });
        registry.registerCondition("%object% (has|have) (chat|text) filtering (on|enabled)", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                return o instanceof org.bukkit.entity.Player h && (java.lang.Boolean.TRUE.equals(h.getClientOption(com.destroystokyo.paper.ClientOption.TEXT_FILTERING_ENABLED)));
            };
        });
        registry.registerCondition("%object% (has|have) [a] (client|custom) weather [set]", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                return o instanceof org.bukkit.entity.Player h && (h.getPlayerWeather() != null);
            };
        });
        registry.registerCondition("%object% (has|have) glowing text", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                return o instanceof java.lang.Object h && ((h instanceof org.bukkit.block.Block b && b.getState() instanceof org.bukkit.block.Sign sign1 && sign1.isGlowingText()) || (h instanceof org.bukkit.inventory.ItemStack i && i.getItemMeta() instanceof org.bukkit.inventory.meta.BlockStateMeta meta && meta.getBlockState() instanceof org.bukkit.block.Sign sign2 && sign2.isGlowingText()));
            };
        });
        registry.registerCondition("[creeper[s]] %object% (is|are) going to explode", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                return o instanceof org.bukkit.entity.LivingEntity h && (h instanceof org.bukkit.entity.Creeper && ((org.bukkit.entity.Creeper) h).isIgnited());
            };
        });
        registry.registerCondition("%object% (is|are) charging [a] fireball", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                return o instanceof org.bukkit.entity.LivingEntity h && (h instanceof org.bukkit.entity.Ghast && ((org.bukkit.entity.Ghast) h).isCharging());
            };
        });
        registry.registerCondition("%object% (is|are) conditional", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                return o instanceof org.bukkit.block.Block h && (h.getBlockData() instanceof org.bukkit.block.data.type.CommandBlock && ((org.bukkit.block.data.type.CommandBlock) h.getBlockData()).isConditional());
            };
        });
        registry.registerCondition("%object% (is|are) dancing", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                return o instanceof org.bukkit.entity.LivingEntity h && ((h instanceof org.bukkit.entity.Allay && ((org.bukkit.entity.Allay) h).isDancing()) || (h instanceof org.bukkit.entity.Parrot && ((org.bukkit.entity.Parrot) h).isDancing()) || (h instanceof org.bukkit.entity.Piglin && ((org.bukkit.entity.Piglin) h).isDancing()));
            };
        });
        registry.registerCondition("%object% (is|are) eating", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                return o instanceof org.bukkit.entity.LivingEntity h && ((h instanceof org.bukkit.entity.Panda && ((org.bukkit.entity.Panda) h).isEating()) || (h instanceof org.bukkit.entity.AbstractHorse && ((org.bukkit.entity.AbstractHorse) h).isEating()));
            };
        });
        registry.registerCondition("%object% (is|are) (fire resistant|resistant to fire)", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                return o instanceof org.bukkit.inventory.ItemStack h && (h.getItemMeta() != null && h.getItemMeta().isFireResistant());
            };
        });
        registry.registerCondition("%object% ((is|are) incendiary|cause[s] a[n] (incendiary|fiery) explosion)", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                return o instanceof org.bukkit.entity.Entity h && (h instanceof org.bukkit.entity.Explosive && ((org.bukkit.entity.Explosive) h).isIncendiary());
            };
        });
        registry.registerCondition("%object% (is|are) pathfinding", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                return o instanceof org.bukkit.entity.LivingEntity h && (h instanceof org.bukkit.entity.Mob && ((org.bukkit.entity.Mob) h).getPathfinder().hasPath());
            };
        });
        registry.registerCondition("%object% (is|are) playing dead", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                return o instanceof org.bukkit.entity.LivingEntity h && (h instanceof org.bukkit.entity.Axolotl && ((org.bukkit.entity.Axolotl) h).isPlayingDead());
            };
        });
        registry.registerCondition("%object% (is|are) responsive", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                return o instanceof org.bukkit.entity.Entity h && (h instanceof org.bukkit.entity.Interaction && ((org.bukkit.entity.Interaction) h).isResponsive());
            };
        });
        registry.registerCondition("%object% (is|are) screaming", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                return o instanceof org.bukkit.entity.LivingEntity h && ((h instanceof org.bukkit.entity.Goat && ((org.bukkit.entity.Goat) h).isScreaming()) || (h instanceof org.bukkit.entity.Enderman && ((org.bukkit.entity.Enderman) h).isScreaming()));
            };
        });
        registry.registerCondition("%object% (is|are) solid", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                return o instanceof org.bukkit.inventory.ItemStack h && (h.getType().isSolid());
            };
        });
        registry.registerCondition("%object% (is|are) stackable", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                return o instanceof org.bukkit.inventory.ItemStack h && (h.getMaxStackSize() > 1);
            };
        });
        registry.registerCondition("%object% (is|are) tameable", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                return o instanceof org.bukkit.entity.LivingEntity h && (h instanceof org.bukkit.entity.Tameable);
            };
        });
        registry.registerCondition("%object% (is|are) transparent", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                return o instanceof org.bukkit.inventory.ItemStack h && (h.getType().isTransparent());
            };
        });
        registry.registerCondition("%object% (is|are) unbreakable", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                return o instanceof org.bukkit.inventory.ItemStack h && (h.getItemMeta() != null && h.getItemMeta().isUnbreakable());
            };
        });
        registry.registerCondition("%object% (is|are) valid", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                return o instanceof org.bukkit.entity.Entity h && (h.isValid());
            };
        });
        registry.registerCondition("%object% (has|have) enchantment glint overrid(den|e)", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                return o instanceof org.bukkit.inventory.ItemStack h && (h.getItemMeta() != null && h.getItemMeta().hasEnchantmentGlintOverride());
            };
        });
        registry.registerCondition("%object% (is|are) left( |-)handed", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                return o instanceof org.bukkit.entity.LivingEntity h && (h instanceof org.bukkit.entity.Mob ? ((org.bukkit.entity.Mob) h).isLeftHanded() : (h instanceof org.bukkit.entity.HumanEntity && ((org.bukkit.entity.HumanEntity) h).getMainHand() == org.bukkit.inventory.MainHand.LEFT));
            };
        });
        registry.registerCondition("[the] lid[s] of %object% (is|are) open[ed]", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                return o instanceof org.bukkit.block.Block h && (h.getState() instanceof org.bukkit.block.Lidded && ((org.bukkit.block.Lidded) h.getState()).isOpen());
            };
        });
        registry.registerCondition("%object% (has|have) ([an] icon|icons)", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                return o instanceof org.bukkit.potion.PotionEffect h && (h.hasIcon());
            };
        });
        registry.registerCondition("%object% (has|have) particles", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                return o instanceof org.bukkit.potion.PotionEffect h && (h.hasParticles());
            };
        });
        registry.registerCondition("%object% (is|are) ambient", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                return o instanceof org.bukkit.potion.PotionEffect h && (h.isAmbient());
            };
        });
        registry.registerCondition("(is PvP|PvP is) enabled in %object%", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                return o instanceof org.bukkit.World h && (h.getPVP());
            };
        });
        registry.registerCondition("%object% (is|are) shivering", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                return o instanceof org.bukkit.entity.LivingEntity h && (h instanceof org.bukkit.entity.Strider && ((org.bukkit.entity.Strider) h).isShivering());
            };
        });
        registry.registerCondition("[[the] text of] %object% (has|have) [a] (drop|text) shadow", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                return o instanceof org.bukkit.entity.Display h && (h instanceof org.bukkit.entity.TextDisplay && ((org.bukkit.entity.TextDisplay) h).isShadowed());
            };
        });
        // second-pass recovered conditions
        registry.registerCondition("%object% can see all messages [in chat]", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                return o instanceof org.bukkit.entity.Player h && (h.getClientOption(com.destroystokyo.paper.ClientOption.CHAT_VISIBILITY) == com.destroystokyo.paper.ClientOption.ChatVisibility.FULL);
            };
        });
        registry.registerCondition("%object% can only see (commands|system messages) [in chat]", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                return o instanceof org.bukkit.entity.Player h && (h.getClientOption(com.destroystokyo.paper.ClientOption.CHAT_VISIBILITY) == com.destroystokyo.paper.ClientOption.ChatVisibility.SYSTEM);
            };
        });
        registry.registerCondition("%object% can('t|[ ]not) see any (command[s]|message[s]) [in chat]", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                return o instanceof org.bukkit.entity.Player h && (h.getClientOption(com.destroystokyo.paper.ClientOption.CHAT_VISIBILITY) == com.destroystokyo.paper.ClientOption.ChatVisibility.HIDDEN);
            };
        });
        registry.registerCondition("%object% (was|were) (indirectly caused|caused indirectly)", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                return o instanceof org.bukkit.damage.DamageSource h && (h.isIndirect());
            };
        });
        registry.registerCondition("%object% (was|were) (directly caused|caused directly)", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                return o instanceof org.bukkit.damage.DamageSource h && (!h.isIndirect());
            };
        });
        registry.registerCondition("%object% (has|have) (any|a) horn", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                return o instanceof org.bukkit.entity.LivingEntity h && (h instanceof org.bukkit.entity.Goat goat && (goat.hasLeftHorn() || goat.hasRightHorn()));
            };
        });
        registry.registerCondition("%object% (has|have) [a] left horn[s]", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                return o instanceof org.bukkit.entity.LivingEntity h && (h instanceof org.bukkit.entity.Goat goat && goat.hasLeftHorn());
            };
        });
        registry.registerCondition("%object% (has|have) [a] right horn[s]", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                return o instanceof org.bukkit.entity.LivingEntity h && (h instanceof org.bukkit.entity.Goat goat && goat.hasRightHorn());
            };
        });
        registry.registerCondition("%object% (has|have) both horns", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                return o instanceof org.bukkit.entity.LivingEntity h && (h instanceof org.bukkit.entity.Goat goat && goat.hasLeftHorn() && goat.hasRightHorn());
            };
        });
        registry.registerCondition("%object% (has|have) [custom] model data floats", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                return o instanceof org.bukkit.inventory.ItemStack h && (h.hasItemMeta() && !h.getItemMeta().getCustomModelDataComponent().getFloats().isEmpty());
            };
        });
        registry.registerCondition("%object% (has|have) [custom] model data flags", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                return o instanceof org.bukkit.inventory.ItemStack h && (h.hasItemMeta() && !h.getItemMeta().getCustomModelDataComponent().getFlags().isEmpty());
            };
        });
        registry.registerCondition("%object% (has|have) [custom] model data strings", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                return o instanceof org.bukkit.inventory.ItemStack h && (h.hasItemMeta() && !h.getItemMeta().getCustomModelDataComponent().getStrings().isEmpty());
            };
        });
        registry.registerCondition("%object% (has|have) [custom] model data colo[u]rs", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                return o instanceof org.bukkit.inventory.ItemStack h && (h.hasItemMeta() && !h.getItemMeta().getCustomModelDataComponent().getColors().isEmpty());
            };
        });
        registry.registerCondition("[the] [entire] tool[ ]tip[s] of %object% (is|are) shown", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                return o instanceof org.bukkit.inventory.ItemStack h && (!(h.hasItemMeta() && h.getItemMeta().isHideTooltip()));
            };
        });
        registry.registerCondition("[the] [entire] tool[ ]tip[s] of %object% (is|are) hidden", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                return o instanceof org.bukkit.inventory.ItemStack h && (h.hasItemMeta() && h.getItemMeta().isHideTooltip());
            };
        });
        registry.registerCondition("[the] additional tool[ ]tip[s] of %object% (is|are) shown", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                return o instanceof org.bukkit.inventory.ItemStack h && (!(h.hasItemMeta() && h.getItemMeta().hasItemFlag(org.bukkit.inventory.ItemFlag.HIDE_ADDITIONAL_TOOLTIP)));
            };
        });
        registry.registerCondition("[the] additional tool[ ]tip[s] of %object% (is|are) hidden", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                return o instanceof org.bukkit.inventory.ItemStack h && (h.hasItemMeta() && h.getItemMeta().hasItemFlag(org.bukkit.inventory.ItemFlag.HIDE_ADDITIONAL_TOOLTIP));
            };
        });
        registry.registerCondition("%object%'[s] main hand[s] (is|are) raised", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                return o instanceof org.bukkit.entity.LivingEntity h && (h.isHandRaised() && h.getHandRaised() == org.bukkit.inventory.EquipmentSlot.HAND);
            };
        });
        registry.registerCondition("%object%'[s] off[ |-]hand[s] (is|are) raised", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                return o instanceof org.bukkit.entity.LivingEntity h && (h.isHandRaised() && h.getHandRaised() == org.bukkit.inventory.EquipmentSlot.OFF_HAND);
            };
        });
        registry.registerCondition("%object%'[s] hand[s] (is|are) raised", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                return o instanceof org.bukkit.entity.LivingEntity h && (h.isHandRaised());
            };
        });
        registry.registerCondition("%object% (is|are) infinite", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                return o instanceof org.bukkit.potion.PotionEffect h && (h.isInfinite());
            };
        });
        registry.registerCondition("world[s] %object% (is|are) loaded", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                return o instanceof org.bukkit.World h && (org.bukkit.Bukkit.getWorld(h.getName()) != null);
            };
        });
        registry.registerCondition("chunk[s] at %object% (is|are) loaded", a -> {
            Expression<?> src = a.get(0);
            return ctx -> {
                Object o = src.getSingle(ctx);
                return o instanceof org.bukkit.Location h && (h.getWorld() != null && h.getWorld().isChunkLoaded(h.getBlockX() >> 4, h.getBlockZ() >> 4));
            };
        });
        // event-restricted syntax
        registry.registerCondition("[the] brewing stand will consume [the] fuel", a -> ctx -> {
            org.bukkit.event.Event ev = ctx.event().orElse(null);
            return ev != null && (ev instanceof org.bukkit.event.inventory.BrewingStandFuelEvent _e && _e.isConsuming());
        });
        registry.registerCondition("[the] egg will hatch", a -> ctx -> {
            org.bukkit.event.Event ev = ctx.event().orElse(null);
            return ev != null && (ev instanceof org.bukkit.event.player.PlayerEggThrowEvent _e && _e.isHatching());
        });
        registry.registerCondition("[the] event is cancel[l]ed", a -> ctx -> {
            org.bukkit.event.Event ev = ctx.event().orElse(null);
            return ev != null && (ev instanceof org.bukkit.event.Cancellable _e && _e.isCancelled());
        });
        registry.registerCondition("lure enchantment bonus is (applied|active)", a -> ctx -> {
            org.bukkit.event.Event ev = ctx.event().orElse(null);
            return ev != null && (ev instanceof org.bukkit.event.player.PlayerFishEvent _e && _e.getHook().getApplyLure());
        });
        registry.registerCondition("[the] respawn location (was|is) [a] bed", a -> ctx -> {
            org.bukkit.event.Event ev = ctx.event().orElse(null);
            return ev != null && (ev instanceof org.bukkit.event.player.PlayerRespawnEvent _e && _e.isBedSpawn());
        });
        registry.registerCondition("[the] respawn location (was|is) [a] respawn anchor", a -> ctx -> {
            org.bukkit.event.Event ev = ctx.event().orElse(null);
            return ev != null && (ev instanceof org.bukkit.event.player.PlayerRespawnEvent _e && _e.isAnchorSpawn());
        });
        registry.registerCondition("[the] (lead|leash) [item] will (drop|be dropped)", a -> ctx -> {
            org.bukkit.event.Event ev = ctx.event().orElse(null);
            return ev != null && (ev instanceof org.bukkit.event.entity.EntityUnleashEvent _e && _e.isDropLeash());
        });
        registry.registerCondition("[the] (boosting|used) firework will be consumed", a -> ctx -> {
            org.bukkit.event.Event ev = ctx.event().orElse(null);
            return ev != null && (ev instanceof com.destroystokyo.paper.event.player.PlayerElytraBoostEvent _e && _e.shouldConsume());
        });
        // final permissive condition / effect recovery
        registry.registerCondition("%object% (has|have) [a] loot[ ]table", a -> ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            return ((a0 instanceof org.bukkit.loot.Lootable && ((org.bukkit.loot.Lootable)a0).getLootTable() != null) || (a0 instanceof org.bukkit.block.Block && ((org.bukkit.block.Block)a0).getState() instanceof org.bukkit.loot.Lootable && ((org.bukkit.loot.Lootable)((org.bukkit.block.Block)a0).getState()).getLootTable() != null));
        });
        registry.registerCondition("%object% (has|have) [persistent] data tag[s] %object%", a -> ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            Object a1 = a.get(1).getSingle(ctx);
            return (a1 instanceof String && !((String)a1).isEmpty() && org.bukkit.NamespacedKey.fromString(((String)a1).toLowerCase(java.util.Locale.ENGLISH)) != null && ((a0 instanceof org.bukkit.persistence.PersistentDataHolder && ((org.bukkit.persistence.PersistentDataHolder)a0).getPersistentDataContainer().has(org.bukkit.NamespacedKey.fromString(((String)a1).toLowerCase(java.util.Locale.ENGLISH)))) || (a0 instanceof org.bukkit.inventory.ItemStack && ((org.bukkit.inventory.ItemStack)a0).getItemMeta() != null && ((org.bukkit.inventory.ItemStack)a0).getItemMeta().getPersistentDataContainer().has(org.bukkit.NamespacedKey.fromString(((String)a1).toLowerCase(java.util.Locale.ENGLISH)))) || (a0 instanceof org.bukkit.block.Block && ((org.bukkit.block.Block)a0).getState() instanceof org.bukkit.persistence.PersistentDataHolder && ((org.bukkit.persistence.PersistentDataHolder)((org.bukkit.block.Block)a0).getState()).getPersistentDataContainer().has(org.bukkit.NamespacedKey.fromString(((String)a1).toLowerCase(java.util.Locale.ENGLISH))))));
        });
        registry.registerCondition("%object% (is|are) evenly divisible by %object% [with [a] tolerance [of] %object%]", a -> ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            Object a1 = a.get(1).getSingle(ctx);
            Object a2 = (a.get(2) == null ? null : a.get(2).getSingle(ctx));
            return (a0 instanceof java.lang.Number && a1 instanceof java.lang.Number && ((java.lang.Number)a1).doubleValue() != 0 && (a2 instanceof java.lang.Number ? ((java.lang.Number)a2).doubleValue() : 1e-10) > 0 && !java.lang.Double.isNaN(a2 instanceof java.lang.Number ? ((java.lang.Number)a2).doubleValue() : 1e-10) && ((java.lang.Number)a1).doubleValue() >= (a2 instanceof java.lang.Number ? ((java.lang.Number)a2).doubleValue() : 1e-10) && (java.lang.Math.abs(((java.lang.Number)a0).doubleValue() % ((java.lang.Number)a1).doubleValue()) <= (a2 instanceof java.lang.Number ? ((java.lang.Number)a2).doubleValue() : 1e-10) || java.lang.Math.abs(((java.lang.Number)a0).doubleValue() % ((java.lang.Number)a1).doubleValue()) >= ((java.lang.Number)a1).doubleValue() - (a2 instanceof java.lang.Number ? ((java.lang.Number)a2).doubleValue() : 1e-10)));
        });
        registry.registerCondition("hand[s] of %object% (is|are) raised", a -> ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            return (a0 instanceof org.bukkit.entity.LivingEntity && ((org.bukkit.entity.LivingEntity)a0).isHandRaised());
        });
        registry.registerCondition("main hand[s] of %object% (is|are) raised", a -> ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            return (a0 instanceof org.bukkit.entity.LivingEntity && ((org.bukkit.entity.LivingEntity)a0).isHandRaised() && ((org.bukkit.entity.LivingEntity)a0).getHandRaised() == org.bukkit.inventory.EquipmentSlot.HAND);
        });
        registry.registerCondition("off[ |-]hand[s] of %object% (is|are) raised", a -> ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            return (a0 instanceof org.bukkit.entity.LivingEntity && ((org.bukkit.entity.LivingEntity)a0).isHandRaised() && ((org.bukkit.entity.LivingEntity)a0).getHandRaised() == org.bukkit.inventory.EquipmentSlot.OFF_HAND);
        });
        registry.registerCondition("chunk [at] %number%, %number% (in|of) [world] %world% is loaded", a -> ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            Object a1 = a.get(1).getSingle(ctx);
            Object a2 = a.get(2).getSingle(ctx);
            return (a0 instanceof java.lang.Number && a1 instanceof java.lang.Number && a2 instanceof org.bukkit.World && ((org.bukkit.World) a2).isChunkLoaded(((java.lang.Number) a0).intValue(), ((java.lang.Number) a1).intValue()));
        });
        registry.registerCondition("world[s] %worlds% (is|are) loaded", a -> ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            return (a0 instanceof org.bukkit.World && org.bukkit.Bukkit.getWorld(((org.bukkit.World) a0).getName()) != null);
        });
        registry.registerCondition("%blocks/entities% (is|are) lootable", a -> ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            return (a0 instanceof org.bukkit.loot.Lootable || (a0 instanceof org.bukkit.block.Block && ((org.bukkit.block.Block) a0).getState() instanceof org.bukkit.loot.Lootable));
        });
        registry.registerCondition("%itemstacks/entities% (is|are) of type[s] %itemtypes/entitydatas%", a -> ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            Object a1 = a.get(1).getSingle(ctx);
            return ((a0 instanceof org.bukkit.inventory.ItemStack && a1 instanceof org.bukkit.inventory.ItemStack && ((org.bukkit.inventory.ItemStack) a0).getType() == ((org.bukkit.inventory.ItemStack) a1).getType()) || (a0 instanceof org.bukkit.entity.Entity && a1 instanceof org.bukkit.entity.EntityType && ((org.bukkit.entity.Entity) a0).getType() == (org.bukkit.entity.EntityType) a1));
        });
        registry.registerCondition("%itemtypes% (is|are) %blocks/blockdatas%'s preferred tool[s]", a -> ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            Object a1 = a.get(1).getSingle(ctx);
            return (a0 instanceof org.bukkit.inventory.ItemStack && ((a1 instanceof org.bukkit.block.Block && ((org.bukkit.block.Block) a1).isPreferredTool((org.bukkit.inventory.ItemStack) a0)) || (a1 instanceof org.bukkit.block.data.BlockData && ((org.bukkit.block.data.BlockData) a1).isPreferredTool((org.bukkit.inventory.ItemStack) a0))));
        });
        registry.registerCondition("%itemtypes% (is|are) [the|a] preferred tool[s] (for|of) %blocks/blockdatas%", a -> ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            Object a1 = a.get(1).getSingle(ctx);
            return (a0 instanceof org.bukkit.inventory.ItemStack && ((a1 instanceof org.bukkit.block.Block && ((org.bukkit.block.Block) a1).isPreferredTool((org.bukkit.inventory.ItemStack) a0)) || (a1 instanceof org.bukkit.block.data.BlockData && ((org.bukkit.block.data.BlockData) a1).isPreferredTool((org.bukkit.inventory.ItemStack) a0))));
        });
        registry.registerCondition("%itemtypes/entities/entitydatas% (is|are) tagged (as|with) %minecrafttags%", a -> ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            Object a1 = a.get(1).getSingle(ctx);
            return (a1 instanceof org.bukkit.Tag && ((a0 instanceof org.bukkit.inventory.ItemStack && ((org.bukkit.Tag) a1).isTagged(((org.bukkit.inventory.ItemStack) a0).getType())) || (a0 instanceof org.bukkit.entity.Entity && ((org.bukkit.Tag) a1).isTagged(((org.bukkit.entity.Entity) a0).getType())) || (a0 instanceof org.bukkit.entity.EntityType && ((org.bukkit.Tag) a1).isTagged((org.bukkit.entity.EntityType) a0))));
        });
        registry.registerCondition("%object% (is|are) within %object% and %object%", a -> ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            Object a1 = a.get(1).getSingle(ctx);
            Object a2 = a.get(2).getSingle(ctx);
            return (a0 instanceof org.bukkit.Location loc && a1 instanceof org.bukkit.Location c1 && a2 instanceof org.bukkit.Location c2 && c1.getWorld() != null && c1.getWorld().equals(c2.getWorld()) && c1.getWorld().equals(loc.getWorld()) && org.bukkit.util.BoundingBox.of(c1, c2).contains(loc.toVector()));
        });
        registry.registerCondition("%object% (is|are) (within|in[side [of]]) %object%", a -> ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            Object a1 = a.get(1).getSingle(ctx);
            return (a0 instanceof org.bukkit.Location loc && (a1 instanceof org.bukkit.entity.Entity ent ? ent.getBoundingBox().contains(loc.toVector()) : a1 instanceof org.bukkit.block.Block blk ? blk.getCollisionShape().getBoundingBoxes().stream().anyMatch(bb -> bb.contains(loc.toVector().subtract(blk.getLocation().toVector()))) : a1 instanceof org.bukkit.Chunk ch ? (loc.getWorld() != null && loc.getChunk().equals(ch)) : a1 instanceof org.bukkit.World w ? w.equals(loc.getWorld()) : a1 instanceof org.bukkit.WorldBorder wb ? wb.isInside(loc) : false));
        });
        registry.registerCondition("%object% (is|are) within %object% (block|metre|meter)[s] (around|of) %object%", a -> ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            Object a1 = a.get(1).getSingle(ctx);
            Object a2 = a.get(2).getSingle(ctx);
            return (a0 instanceof org.bukkit.Location loc && a2 instanceof org.bukkit.Location center && a1 instanceof Number num && loc.getWorld() != null && loc.getWorld().equals(center.getWorld()) && loc.distanceSquared(center) <= num.doubleValue() * num.doubleValue() * 1.00001);
        });
    }
}
