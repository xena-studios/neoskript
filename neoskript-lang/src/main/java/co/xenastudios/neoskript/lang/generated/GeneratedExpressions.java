package co.xenastudios.neoskript.lang.generated;

import co.xenastudios.neoskript.api.registry.SyntaxRegistry;
import co.xenastudios.neoskript.api.syntax.Expression;
import co.xenastudios.neoskript.core.expression.ComputedExpression;
import co.xenastudios.neoskript.core.expression.ComputedListExpression;

/**
 * Generated expression syntax (property getters, event values, and multi-argument value expressions).
 *
 * <p><strong>Generated code.</strong> These registrations were produced by the Skript-conformance
 * tooling (see {@code docs/conformance}) from the upstream Skript 2.15.3 source, then compile- and
 * parse-verified. Prefer regenerating over editing by hand; order is significant for overlapping
 * patterns and mirrors the original per-pass generation order.
 */
public final class GeneratedExpressions {
    private GeneratedExpressions() {}

    public static void register(SyntaxRegistry registry) {
        // bulk property getters
        registry.registerExpression("active item of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.entity.LivingEntity h)) return null;
                return h.getActiveItem();
            });
        });
        registry.registerExpression("anvil text input of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.inventory.AnvilInventory h)) return null;
                return h.getRenameText();
            });
        });
        registry.registerExpression("arrow knockback strength of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.entity.AbstractArrow h)) return null;
                return h.getKnockbackStrength();
            });
        });
        registry.registerExpression("arrow pierce level of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.entity.Arrow h)) return null;
                return h.getPierceLevel();
            });
        });
        registry.registerExpression("bed location of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.OfflinePlayer h)) return null;
                return h.getBedSpawnLocation();
            });
        });
        registry.registerExpression("language of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.entity.Player h)) return null;
                return h.getLocale();
            });
        });
        registry.registerExpression("last death location of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.OfflinePlayer h)) return null;
                return h.getLastDeathLocation();
            });
        });
        registry.registerExpression("resource pack response of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.entity.Player h)) return null;
                return h.getResourcePackStatus();
            });
        });
        registry.registerExpression("level of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.entity.Player h)) return null;
                return h.getLevel();
            });
        });
        registry.registerExpression("loot location of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.loot.LootContext h)) return null;
                return h.getLocation();
            });
        });
        registry.registerExpression("looted entity of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.loot.LootContext h)) return null;
                return h.getLootedEntity();
            });
        });
        registry.registerExpression("loot luck value of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.loot.LootContext h)) return null;
                return h.getLuck();
            });
        });
        registry.registerExpression("max item use time of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.inventory.ItemStack h)) return null;
                return h.getMaxItemUseDuration();
            });
        });
        registry.registerExpression("max minecart speed of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.entity.Minecart h)) return null;
                return h.getMaxSpeed();
            });
        });
        registry.registerExpression("max freeze time of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.entity.Entity h)) return null;
                return h.getMaxFreezeTicks();
            });
        });
        registry.registerExpression("moon phase of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.World h)) return null;
                return h.getMoonPhase();
            });
        });
        registry.registerExpression("no damage time of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.entity.LivingEntity h)) return null;
                return h.getNoDamageTicks();
            });
        });
        registry.registerExpression("pickup delay of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.entity.Item h)) return null;
                return h.getPickupDelay();
            });
        });
        registry.registerExpression("player list name of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.entity.Player h)) return null;
                return h.playerListName();
            });
        });
        registry.registerExpression("protocol version of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.entity.Player h)) return null;
                return h.getProtocolVersion();
            });
        });
        registry.registerExpression("portal cooldown of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.entity.Entity h)) return null;
                return h.getPortalCooldown();
            });
        });
        registry.registerExpression("potion effect type category of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.potion.PotionEffectType h)) return null;
                return h.getCategory();
            });
        });
        registry.registerExpression("redstone power of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.block.Block h)) return null;
                return h.getBlockPower();
            });
        });
        registry.registerExpression("sea level of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.World h)) return null;
                return h.getSeaLevel();
            });
        });
        registry.registerExpression("spawn of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.World h)) return null;
                return h.getSpawnLocation();
            });
        });
        registry.registerExpression("spectator target of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.entity.Player h)) return null;
                return h.getSpectatorTarget();
            });
        });
        registry.registerExpression("namespaced key of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.Tag h)) return null;
                return h.getKey().toString();
            });
        });
        registry.registerExpression("temperature of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.block.Block h)) return null;
                return h.getTemperature();
            });
        });
        registry.registerExpression("text alignment of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.entity.TextDisplay h)) return null;
                return h.getAlignment();
            });
        });
        registry.registerExpression("line width of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.entity.TextDisplay h)) return null;
                return h.getLineWidth();
            });
        });
        registry.registerExpression("text of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.entity.TextDisplay h)) return null;
                return h.text();
            });
        });
        registry.registerExpression("vector length of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.util.Vector h)) return null;
                return h.length();
            });
        });
        registry.registerExpression("squared length of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.util.Vector h)) return null;
                return h.lengthSquared();
            });
        });
        registry.registerExpression("client view distance of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.entity.Player h)) return null;
                return h.getClientViewDistance();
            });
        });
        registry.registerExpression("world border warning distance of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.WorldBorder h)) return null;
                return h.getWarningDistance();
            });
        });
        registry.registerExpression("world border center of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.WorldBorder h)) return null;
                return h.getCenter();
            });
        });
        registry.registerExpression("environment of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.World h)) return null;
                return h.getEnvironment();
            });
        });
        registry.registerExpression("world border damage amount of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.WorldBorder h)) return null;
                return h.getDamageAmount();
            });
        });
        registry.registerExpression("world border damage buffer of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.WorldBorder h)) return null;
                return h.getDamageBuffer();
            });
        });
        registry.registerExpression("causing entity of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.damage.DamageSource h)) return null;
                return h.getCausingEntity();
            });
        });
        registry.registerExpression("damage location of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.damage.DamageSource h)) return null;
                return h.getDamageLocation();
            });
        });
        registry.registerExpression("damage type of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.damage.DamageSource h)) return null;
                return h.getDamageType();
            });
        });
        registry.registerExpression("direct entity of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.damage.DamageSource h)) return null;
                return h.getDirectEntity();
            });
        });
        registry.registerExpression("food exhaustion of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.damage.DamageSource h)) return null;
                return h.getFoodExhaustion();
            });
        });
        registry.registerExpression("source location of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.damage.DamageSource h)) return null;
                return h.getSourceLocation();
            });
        });
        registry.registerExpression("difficulty of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.World h)) return null;
                return h.getDifficulty();
            });
        });
        registry.registerExpression("billboard of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.entity.Display h)) return null;
                return h.getBillboard();
            });
        });
        registry.registerExpression("teleport duration of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.entity.Display h)) return null;
                return h.getTeleportDuration();
            });
        });
        registry.registerExpression("view range of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.entity.Display h)) return null;
                return h.getViewRange();
            });
        });
        registry.registerExpression("uuid of dropped item owner of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.entity.Item h)) return null;
                return h.getOwner();
            });
        });
        registry.registerExpression("uuid of dropped item thrower of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.entity.Item h)) return null;
                return h.getThrower();
            });
        });
        registry.registerExpression("enchantment cost of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.enchantments.EnchantmentOffer h)) return null;
                return h.getCost();
            });
        });
        registry.registerExpression("ender chest of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.entity.Player h)) return null;
                return h.getEnderChest();
            });
        });
        registry.registerExpression("ai of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.entity.LivingEntity h)) return null;
                return h.hasAI();
            });
        });
        registry.registerExpression("entity snapshot of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.entity.Entity h)) return null;
                return h.createSnapshot();
            });
        });
        registry.registerExpression("experience pickup cooldown of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.entity.Player h)) return null;
                return h.getExpCooldown();
            });
        });
        registry.registerExpression("flight mode of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.entity.Player h)) return null;
                return h.getAllowFlight();
            });
        });
        registry.registerExpression("freeze time of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.entity.Entity h)) return null;
                return h.getFreezeTicks();
            });
        });
        registry.registerExpression("gliding state of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.entity.LivingEntity h)) return null;
                return h.isGliding();
            });
        });
        registry.registerExpression("eye location of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.entity.LivingEntity h)) return null;
                return h.getEyeLocation();
            });
        });
        registry.registerExpression("humidity of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.block.Block h)) return null;
                return h.getHumidity();
            });
        });
        registry.registerExpression("item display transform of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.entity.ItemDisplay h)) return null;
                return h.getItemDisplayTransform();
            });
        });
        // SimplePropertyExpression extraction
        registry.registerExpression("level progress of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.entity.Player h)) return null;
                return h.getExp();
            });
        });
        registry.registerExpression("fall[en] (distance|height) of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.entity.Entity h)) return null;
                return h.getFallDistance();
            });
        });
        registry.registerExpression("saturation of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.entity.Player h)) return null;
                return h.getSaturation();
            });
        });
        registry.registerExpression("ender[ ]chest[s] of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.entity.Player h)) return null;
                return h.getEnderChest();
            });
        });
        registry.registerExpression("[world] environment of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.World h)) return null;
                return h.getEnvironment();
            });
        });
        registry.registerExpression("exhaustion of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.entity.Player h)) return null;
                return h.getExhaustion();
            });
        });
        registry.registerExpression("humidit(y|ies) of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.block.Block h)) return null;
                return h.getHumidity();
            });
        });
        registry.registerExpression("(ai|artificial intelligence) of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.entity.LivingEntity h)) return null;
                return h.hasAI();
            });
        });
        registry.registerExpression("(head|eye[s]) [location[s]] of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.entity.LivingEntity h)) return null;
                return h.getEyeLocation();
            });
        });
        registry.registerExpression("glowing of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.entity.Entity h)) return null;
                return h.isGlowing();
            });
        });
        registry.registerExpression("attack cooldown of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.entity.HumanEntity h)) return null;
                return h.getAttackCooldown();
            });
        });
        registry.registerExpression("squared length[s] of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.util.Vector h)) return null;
                return h.lengthSquared();
            });
        });
        registry.registerExpression("(lunar|moon) phase[s] of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.World h)) return null;
                return h.getMoonPhase();
            });
        });
        registry.registerExpression("gravity of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.entity.Entity h)) return null;
                return h.hasGravity();
            });
        });
        registry.registerExpression("temperature[s] of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.block.Block h)) return null;
                return h.getTemperature();
            });
        });
        registry.registerExpression("difficult(y|ies) of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.World h)) return null;
                return h.getDifficulty();
            });
        });
        registry.registerExpression("(vector|standard|normal) length[s] of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.util.Vector h)) return null;
                return h.length();
            });
        });
        registry.registerExpression("[last] resource pack response[s] of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.entity.Player h)) return null;
                return h.getResourcePackStatus();
            });
        });
        registry.registerExpression("compass target of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.entity.Player h)) return null;
                return h.getCompassTarget();
            });
        });
        registry.registerExpression("altitude[s] of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.Location h)) return null;
                return h.getY();
            });
        });
        registry.registerExpression("fl(y[ing]|ight) (mode|state) of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.entity.Player h)) return null;
                return h.getAllowFlight();
            });
        });
        registry.registerExpression("(gliding|glider) [state] of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.entity.LivingEntity h)) return null;
                return h.isGliding();
            });
        });
        // getter / list expressions
        registry.registerExpression("duplication cooldown of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.entity.Allay h)) return null;
                return h.getDuplicationCooldown();
            });
        });
        registry.registerExpression("food level of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.entity.Player h)) return null;
                return h.getFoodLevel();
            });
        });
        registry.registerExpression("hidden players of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedListExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.entity.Player h)) return new Object[0];
                var c = h.spigot().getHiddenPlayers();
                return c == null ? new Object[0] : c.toArray();
            });
        });
        registry.registerExpression("leash holder of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.entity.LivingEntity h)) return null;
                return h.getLeashHolder();
            });
        });
        registry.registerExpression("looter of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.loot.LootContext h)) return null;
                return h.getKiller();
            });
        });
        registry.registerExpression("arrows stuck in %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.entity.LivingEntity h)) return null;
                return h.getArrowsStuck();
            });
        });
        registry.registerExpression("passengers of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedListExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.entity.Entity h)) return new Object[0];
                var c = h.getPassengers();
                return c == null ? new Object[0] : c.toArray();
            });
        });
        registry.registerExpression("ping of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.entity.Player h)) return null;
                return h.spigot().getPing();
            });
        });
        registry.registerExpression("scoreboard tags of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedListExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.entity.Entity h)) return new Object[0];
                var c = h.getScoreboardTags();
                return c == null ? new Object[0] : c.toArray();
            });
        });
        registry.registerExpression("tag contents of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedListExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.Tag h)) return new Object[0];
                var c = h.getValues();
                return c == null ? new Object[0] : c.toArray();
            });
        });
        registry.registerExpression("text opacity of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.entity.TextDisplay h)) return null;
                return h.getTextOpacity();
            });
        });
        registry.registerExpression("time lived of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.entity.Entity h)) return null;
                return h.getTicksLived();
            });
        });
        registry.registerExpression("vector of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.Location h)) return null;
                return h.toVector();
            });
        });
        registry.registerExpression("vehicle of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.entity.Entity h)) return null;
                return h.getVehicle();
            });
        });
        registry.registerExpression("most angered entity of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.entity.Warden h)) return null;
                return h.getEntityAngryAt();
            });
        });
        registry.registerExpression("world border warning time of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.WorldBorder h)) return null;
                return h.getWarningTime();
            });
        });
        registry.registerExpression("fuel level of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.block.Block h)) return null;
                return ((org.bukkit.block.BrewingStand) h.getState()).getFuelLevel();
            });
        });
        registry.registerExpression("seed of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.World h)) return null;
                return h.getSeed();
            });
        });
        registry.registerExpression("brewing time of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.block.Block h)) return null;
                return ((org.bukkit.block.BrewingStand) h.getState()).getBrewingTime();
            });
        });
        registry.registerExpression("buried item of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.block.Block h)) return null;
                return ((org.bukkit.block.BrushableBlock) h.getState()).getItem();
            });
        });
        registry.registerExpression("codepoint of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof java.lang.String h)) return null;
                return h.codePointAt(0);
            });
        });
        registry.registerExpression("max fuse ticks of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.entity.Creeper h)) return null;
                return h.getMaxFuseTicks();
            });
        });
        registry.registerExpression("glow color override of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.entity.Display h)) return null;
                return h.getGlowColorOverride();
            });
        });
        // source-grounded property / no-arg getters
        registry.registerExpression("[(all [[of] the]|the)] permissions (from|of) %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedListExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.entity.Player h)) return new Object[0];
                java.util.Collection<?> c = (h.getEffectivePermissions().stream().map(org.bukkit.permissions.PermissionAttachmentInfo::getPermission).collect(java.util.stream.Collectors.toSet()));
                return c == null ? new Object[0] : c.toArray();
            });
        });
        registry.registerExpression("target jukebox of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.entity.LivingEntity h)) return null;
                return (h instanceof org.bukkit.entity.Allay ? ((org.bukkit.entity.Allay) h).getJukebox() : null);
            });
        });
        registry.registerExpression("beacon [effect] range of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.block.Block h)) return null;
                return (h.getState() instanceof org.bukkit.block.Beacon ? ((org.bukkit.block.Beacon) h.getState()).getEffectRange() : null);
            });
        });
        registry.registerExpression("beacon tier of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.block.Block h)) return null;
                return (h.getState() instanceof org.bukkit.block.Beacon ? ((org.bukkit.block.Beacon) h.getState()).getTier() : null);
            });
        });
        registry.registerExpression("target flower of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.block.Block h)) return null;
                return (h.getState() instanceof org.bukkit.block.Beehive ? ((org.bukkit.block.Beehive) h.getState()).getFlower() : null);
            });
        });
        registry.registerExpression("%object%'[s] biome", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.Location h)) return null;
                return (h.getBlock().getBiome());
            });
        });
        registry.registerExpression("block[ ]data of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof java.lang.Object h)) return null;
                return (h instanceof org.bukkit.block.Block ? ((org.bukkit.block.Block) h).getBlockData() : (h instanceof org.bukkit.entity.BlockDisplay ? ((org.bukkit.entity.BlockDisplay) h).getBlock() : (h instanceof org.bukkit.entity.FallingBlock ? ((org.bukkit.entity.FallingBlock) h).getBlockData() : null)));
            });
        });
        registry.registerExpression("character (from|at|with) code([ ]point| position) %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof java.lang.Integer h)) return null;
                return (String.valueOf((char) h.intValue()));
            });
        });
        registry.registerExpression("%object%'[s] chunk[s]", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.Location h)) return null;
                return (h.getChunk());
            });
        });
        registry.registerExpression("[the] [command[ ]block] command of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof java.lang.Object h)) return null;
                return ((h instanceof org.bukkit.block.Block && ((org.bukkit.block.Block) h).getState() instanceof org.bukkit.block.CommandBlock) ? (((org.bukkit.block.CommandBlock) ((org.bukkit.block.Block) h).getState()).getCommand().isEmpty() ? null : ((org.bukkit.block.CommandBlock) ((org.bukkit.block.Block) h).getState()).getCommand()) : ((h instanceof org.bukkit.entity.minecart.CommandMinecart) ? (((org.bukkit.entity.minecart.CommandMinecart) h).getCommand().isEmpty() ? null : ((org.bukkit.entity.minecart.CommandMinecart) h).getCommand()) : null));
            });
        });
        registry.registerExpression("[the] carr(ied|ying) block[[ ]data] of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.entity.LivingEntity h)) return null;
                return (h instanceof org.bukkit.entity.Enderman ? ((org.bukkit.entity.Enderman) h).getCarriedBlock() : null);
            });
        });
        registry.registerExpression("[the] entity size of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.entity.LivingEntity h)) return null;
                return (h instanceof org.bukkit.entity.Phantom ? java.lang.Integer.valueOf(((org.bukkit.entity.Phantom) h).getSize()) : (h instanceof org.bukkit.entity.Slime ? java.lang.Integer.valueOf(((org.bukkit.entity.Slime) h).getSize() - 1) : null));
            });
        });
        registry.registerExpression("[the] [stored] entity count of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.block.Block h)) return null;
                return (h.getState() instanceof org.bukkit.block.EntityBlockStorage ? java.lang.Integer.valueOf(((org.bukkit.block.EntityBlockStorage<?>) h.getState()).getEntityCount()) : null);
            });
        });
        registry.registerExpression("positive (infinity|∞) [value]", Object.class, a -> new ComputedExpression(ctx -> (java.lang.Double.POSITIVE_INFINITY)));
        registry.registerExpression("[the] (inverse|opposite)[s] of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof java.lang.Boolean h)) return null;
                return (h == null ? null : !h);
            });
        });
        registry.registerExpression("last damage of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.entity.LivingEntity h)) return null;
                return (h.getLastDamage() / 2);
            });
        });
        registry.registerExpression("last damage (cause|reason|type) of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.entity.LivingEntity h)) return null;
                return (h.getLastDamageCause() == null ? org.bukkit.event.entity.EntityDamageEvent.DamageCause.CUSTOM : h.getLastDamageCause().getCause());
            });
        });
        registry.registerExpression("[(all [[of] the]|the)] [loaded] plugins", Object.class, a -> new ComputedListExpression(ctx -> {
            java.util.Collection<?> c = (java.util.Arrays.stream(org.bukkit.Bukkit.getPluginManager().getPlugins()).map(org.bukkit.plugin.Plugin::getName).collect(java.util.stream.Collectors.toList()));
            return c == null ? new Object[0] : c.toArray();
        }));
        registry.registerExpression("[the] loot[ ]table[s] %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof java.lang.String h)) return null;
                return (org.bukkit.NamespacedKey.fromString(h) != null ? org.bukkit.Bukkit.getLootTable(org.bukkit.NamespacedKey.fromString(h)) : null);
            });
        });
        registry.registerExpression("[the] max[imum] double value", Object.class, a -> new ComputedExpression(ctx -> (java.lang.Double.valueOf(java.lang.Double.MAX_VALUE))));
        registry.registerExpression("[the] max[imum] float value", Object.class, a -> new ComputedExpression(ctx -> (java.lang.Float.valueOf(java.lang.Float.MAX_VALUE))));
        registry.registerExpression("[the] max[imum] integer value", Object.class, a -> new ComputedExpression(ctx -> (java.lang.Integer.valueOf(java.lang.Integer.MAX_VALUE))));
        registry.registerExpression("[the] max[imum] long value", Object.class, a -> new ComputedExpression(ctx -> (java.lang.Long.valueOf(java.lang.Long.MAX_VALUE))));
        registry.registerExpression("[the] max[imum] stack[[ ]size] of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof java.lang.Object h)) return null;
                return (h instanceof org.bukkit.inventory.ItemStack ? java.lang.Integer.valueOf(((org.bukkit.inventory.ItemStack) h).getMaxStackSize()) : (h instanceof org.bukkit.inventory.Inventory ? java.lang.Integer.valueOf(((org.bukkit.inventory.Inventory) h).getMaxStackSize()) : null));
            });
        });
        registry.registerExpression("[the] (middle|center) [point] of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.Location h)) return null;
                return (new org.bukkit.Location(h.getWorld(), h.getBlockX() + 0.5, h.getBlockY(), h.getBlockZ() + 0.5));
            });
        });
        registry.registerExpression("[the] min[imum] double value", Object.class, a -> new ComputedExpression(ctx -> (java.lang.Double.valueOf(java.lang.Double.MIN_VALUE))));
        registry.registerExpression("[the] min[imum] float value", Object.class, a -> new ComputedExpression(ctx -> (java.lang.Float.valueOf(java.lang.Float.MIN_VALUE))));
        registry.registerExpression("[the] min[imum] integer value", Object.class, a -> new ComputedExpression(ctx -> (java.lang.Integer.valueOf(java.lang.Integer.MIN_VALUE))));
        registry.registerExpression("[the] min[imum] long value", Object.class, a -> new ComputedExpression(ctx -> (java.lang.Long.valueOf(java.lang.Long.MIN_VALUE))));
        registry.registerExpression("NaN [value]", Object.class, a -> new ComputedExpression(ctx -> (java.lang.Double.valueOf(java.lang.Double.NaN))));
        registry.registerExpression("(-|minus |negative )(infinity|∞) [value]", Object.class, a -> new ComputedExpression(ctx -> (java.lang.Double.NEGATIVE_INFINITY)));
        registry.registerExpression("[(all [[of] the]|the)] offline[ ]players", Object.class, a -> new ComputedListExpression(ctx -> {
            java.util.Collection<?> c = (java.util.Arrays.asList(org.bukkit.Bukkit.getOfflinePlayers()));
            return c == null ? new Object[0] : c.toArray();
        }));
        registry.registerExpression("(current|open|top) inventory of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.entity.Player h)) return null;
                return (h.getOpenInventory().getTopInventory());
            });
        });
        registry.registerExpression("(pi|π)", Object.class, a -> new ComputedExpression(ctx -> (Math.PI)));
        registry.registerExpression("(projectile|arrow) critical (state|ability|mode) of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.entity.Projectile h)) return null;
                return (h instanceof org.bukkit.entity.AbstractArrow ? Boolean.valueOf(((org.bukkit.entity.AbstractArrow) h).isCritical()) : null);
            });
        });
        registry.registerExpression("[the] resonat(e|ing) time of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.block.Block h)) return null;
                return (h.getState() instanceof org.bukkit.block.Bell b ? (b.getResonatingTicks() == 0 ? null : b.getResonatingTicks()) : null);
            });
        });
        registry.registerExpression("[the] charge[s] of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.block.Block h)) return null;
                return (h.getBlockData() instanceof org.bukkit.block.data.type.RespawnAnchor ra ? ra.getCharges() : null);
            });
        });
        registry.registerExpression("[the] ring[ing] time of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.block.Block h)) return null;
                return (h.getState() instanceof org.bukkit.block.Bell b ? (b.getShakingTicks() == 0 ? null : b.getShakingTicks()) : null);
            });
        });
        registry.registerExpression("[sea] pickle(s| (count|amount)) of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.block.Block h)) return null;
                return (h.getBlockData() instanceof org.bukkit.block.data.type.SeaPickle ? Integer.valueOf(((org.bukkit.block.data.type.SeaPickle) h.getBlockData()).getPickles()) : null);
            });
        });
        registry.registerExpression("[the] shooter of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.entity.Projectile h)) return null;
                return (h.getShooter() instanceof org.bukkit.entity.LivingEntity ? (org.bukkit.entity.LivingEntity) h.getShooter() : null);
            });
        });
        registry.registerExpression("simulation distance[s] of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof java.lang.Object h)) return null;
                return (h instanceof org.bukkit.World ? Integer.valueOf(((org.bukkit.World) h).getSimulationDistance()) : (h instanceof org.bukkit.entity.Player ? Integer.valueOf(((org.bukkit.entity.Player) h).getSimulationDistance()) : null));
            });
        });
        registry.registerExpression("(tablist[ed]|listed) players of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedListExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.entity.Player h)) return new Object[0];
                java.util.Collection<?> c = (org.bukkit.Bukkit.getOnlinePlayers().stream().filter(p -> h.isListed(p)).collect(java.util.stream.Collectors.toList()));
                return c == null ? new Object[0] : c.toArray();
            });
        });
        registry.registerExpression("[the] UUID of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof java.lang.Object h)) return null;
                return (h instanceof org.bukkit.OfflinePlayer ? ((org.bukkit.OfflinePlayer) h).getUniqueId() : (h instanceof org.bukkit.entity.Entity ? ((org.bukkit.entity.Entity) h).getUniqueId() : (h instanceof org.bukkit.World ? ((org.bukkit.World) h).getUID() : null)));
            });
        });
        registry.registerExpression("[a] random vector", Object.class, a -> new ComputedExpression(ctx -> (new org.bukkit.util.Vector(java.util.concurrent.ThreadLocalRandom.current().nextGaussian(), java.util.concurrent.ThreadLocalRandom.current().nextGaussian(), java.util.concurrent.ThreadLocalRandom.current().nextGaussian()).normalize())));
        registry.registerExpression("[the] view distance[s] of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof java.lang.Object h)) return null;
                return (h instanceof org.bukkit.entity.Player ? java.lang.Integer.valueOf(((org.bukkit.entity.Player) h).getViewDistance()) : (h instanceof org.bukkit.World ? java.lang.Integer.valueOf(((org.bukkit.World) h).getViewDistance()) : null));
            });
        });
        registry.registerExpression("villager profession of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.entity.LivingEntity h)) return null;
                return (h instanceof org.bukkit.entity.Villager ? ((org.bukkit.entity.Villager) h).getProfession() : (h instanceof org.bukkit.entity.ZombieVillager ? ((org.bukkit.entity.ZombieVillager) h).getVillagerProfession() : null));
            });
        });
        registry.registerExpression("villager type of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.entity.LivingEntity h)) return null;
                return (h instanceof org.bukkit.entity.Villager ? ((org.bukkit.entity.Villager) h).getVillagerType() : (h instanceof org.bukkit.entity.ZombieVillager ? ((org.bukkit.entity.ZombieVillager) h).getVillagerType() : null));
            });
        });
        registry.registerExpression("[the] white[ ]list", Object.class, a -> new ComputedListExpression(ctx -> {
            java.util.Collection<?> c = (org.bukkit.Bukkit.getServer().getWhitelistedPlayers());
            return c == null ? new Object[0] : c.toArray();
        }));
        registry.registerExpression("[the] world of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof java.lang.Object h)) return null;
                return (h instanceof org.bukkit.entity.Entity ? ((org.bukkit.entity.Entity) h).getWorld() : (h instanceof org.bukkit.Location ? ((org.bukkit.Location) h).getWorld() : (h instanceof org.bukkit.Chunk ? ((org.bukkit.Chunk) h).getWorld() : null)));
            });
        });
        registry.registerExpression("world[ ]border of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof java.lang.Object h)) return null;
                return (h instanceof org.bukkit.World ? ((org.bukkit.World) h).getWorldBorder() : (h instanceof org.bukkit.entity.Player ? ((org.bukkit.entity.Player) h).getWorldBorder() : null));
            });
        });
        registry.registerExpression("[the] world [(named|with name)] %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof java.lang.String h)) return null;
                return (org.bukkit.Bukkit.getWorld(h));
            });
        });
        registry.registerExpression("[(all [[of] the]|the)] worlds", Object.class, a -> new ComputedListExpression(ctx -> {
            java.util.Collection<?> c = (org.bukkit.Bukkit.getWorlds());
            return c == null ? new Object[0] : c.toArray();
        }));
        // event-value expressions
        registry.registerExpression("[the] absorbed blocks", Object.class, a -> new ComputedListExpression(ctx -> {
            org.bukkit.event.Event ev = ctx.event().orElse(null);
            if (ev == null) return new Object[0];
            Object _c = (ev instanceof org.bukkit.event.block.SpongeAbsorbEvent _e ? _e.getBlocks() : null);
            if (_c == null) return new Object[0];
            if (_c instanceof java.util.Collection<?> _col) return _col.toArray();
            if (_c instanceof Object[] _arr) return _arr;
            return new Object[]{
                _c
            };
        }));
        registry.registerExpression("[the] affected entities", Object.class, a -> new ComputedListExpression(ctx -> {
            org.bukkit.event.Event ev = ctx.event().orElse(null);
            if (ev == null) return new Object[0];
            Object _c = (ev instanceof org.bukkit.event.entity.AreaEffectCloudApplyEvent _e ? _e.getAffectedEntities() : null);
            if (_c == null) return new Object[0];
            if (_c instanceof java.util.Collection<?> _col) return _col.toArray();
            if (_c instanceof Object[] _arr) return _arr;
            return new Object[]{
                _c
            };
        }));
        registry.registerExpression("[the] applied [beacon] effect", Object.class, a -> new ComputedExpression(ctx -> {
            org.bukkit.event.Event ev = ctx.event().orElse(null);
            if (ev == null) return null;
            return (ev instanceof com.destroystokyo.paper.event.block.BeaconEffectEvent _e ? _e.getEffect().getType() : null);
        }));
        registry.registerExpression("[the] (attacked|damaged|victim)", Object.class, a -> new ComputedExpression(ctx -> {
            org.bukkit.event.Event ev = ctx.event().orElse(null);
            if (ev == null) return null;
            return (ev instanceof org.bukkit.event.entity.ProjectileHitEvent _ph ? _ph.getHitEntity() : (ev instanceof org.bukkit.event.entity.EntityEvent _ee ? _ee.getEntity() : (ev instanceof org.bukkit.event.vehicle.VehicleEvent _ve ? _ve.getVehicle() : (ev instanceof io.papermc.paper.event.player.PrePlayerAttackEntityEvent _pa ? _pa.getAttacked() : null))));
        }));
        registry.registerExpression("[the] (attacker|damager)", Object.class, a -> new ComputedExpression(ctx -> {
            org.bukkit.event.Event ev = ctx.event().orElse(null);
            if (ev == null) return null;
            return (ev instanceof org.bukkit.event.entity.EntityDamageByEntityEvent _e ? (_e.getDamager() instanceof org.bukkit.entity.Projectile _p ? (_p.getShooter() instanceof org.bukkit.entity.Entity _s ? _s : null) : _e.getDamager()) : (ev instanceof org.bukkit.event.entity.EntityDeathEvent _d ? (_d.getEntity().getLastDamageCause() instanceof org.bukkit.event.entity.EntityDamageByEntityEvent _c ? (_c.getDamager() instanceof org.bukkit.entity.Projectile _p2 ? (_p2.getShooter() instanceof org.bukkit.entity.Entity _s2 ? _s2 : null) : _c.getDamager()) : null) : (ev instanceof org.bukkit.event.vehicle.VehicleDamageEvent _vd ? _vd.getAttacker() : (ev instanceof org.bukkit.event.vehicle.VehicleDestroyEvent _vx ? _vx.getAttacker() : (ev instanceof io.papermc.paper.event.player.PrePlayerAttackEntityEvent _pa ? _pa.getPlayer() : null)))));
        }));
        registry.registerExpression("[the] [piglin] barter[ing] drops", Object.class, a -> new ComputedListExpression(ctx -> {
            org.bukkit.event.Event ev = ctx.event().orElse(null);
            if (ev == null) return new Object[0];
            Object _c = (ev instanceof org.bukkit.event.entity.PiglinBarterEvent _e ? _e.getOutcome() : null);
            if (_c == null) return new Object[0];
            if (_c instanceof java.util.Collection<?> _col) return _col.toArray();
            if (_c instanceof Object[] _arr) return _arr;
            return new Object[]{
                _c
            };
        }));
        registry.registerExpression("[the] [piglin] barter[ing] input", Object.class, a -> new ComputedExpression(ctx -> {
            org.bukkit.event.Event ev = ctx.event().orElse(null);
            if (ev == null) return null;
            return (ev instanceof org.bukkit.event.entity.PiglinBarterEvent _e ? _e.getInput() : null);
        }));
        registry.registerExpression("[the] brewing results", Object.class, a -> new ComputedListExpression(ctx -> {
            org.bukkit.event.Event ev = ctx.event().orElse(null);
            if (ev == null) return new Object[0];
            Object _c = (ev instanceof org.bukkit.event.inventory.BrewEvent _e ? _e.getResults() : null);
            if (_c == null) return new Object[0];
            if (_c instanceof java.util.Collection<?> _col) return _col.toArray();
            if (_c instanceof Object[] _arr) return _arr;
            return new Object[]{
                _c
            };
        }));
        registry.registerExpression("[the] broadcast(-|[ed] )message", Object.class, a -> new ComputedExpression(ctx -> {
            org.bukkit.event.Event ev = ctx.event().orElse(null);
            if (ev == null) return null;
            return (ev instanceof org.bukkit.event.server.BroadcastMessageEvent _e ? _e.message() : null);
        }));
        registry.registerExpression("[the] [chat( |-)]message", Object.class, a -> new ComputedExpression(ctx -> {
            org.bukkit.event.Event ev = ctx.event().orElse(null);
            if (ev == null) return null;
            return (ev instanceof io.papermc.paper.event.player.AsyncChatEvent _e ? _e.message() : null);
        }));
        registry.registerExpression("[the] [chat( | -)]recipients", Object.class, a -> new ComputedListExpression(ctx -> {
            org.bukkit.event.Event ev = ctx.event().orElse(null);
            if (ev == null) return new Object[0];
            Object _c = (ev instanceof io.papermc.paper.event.player.AsyncChatEvent _e ? _e.viewers() : null);
            if (_c == null) return new Object[0];
            if (_c instanceof java.util.Collection<?> _col) return _col.toArray();
            if (_c instanceof Object[] _arr) return _arr;
            return new Object[]{
                _c
            };
        }));
        registry.registerExpression("[the] (full|complete|whole) command", Object.class, a -> new ComputedExpression(ctx -> {
            org.bukkit.event.Event ev = ctx.event().orElse(null);
            if (ev == null) return null;
            return (ev instanceof org.bukkit.event.player.PlayerCommandPreprocessEvent _p ? _p.getMessage().substring(1).trim() : (ev instanceof org.bukkit.event.server.ServerCommandEvent _s ? _s.getCommand().trim() : null));
        }));
        registry.registerExpression("[the] consumed item", Object.class, a -> new ComputedExpression(ctx -> {
            org.bukkit.event.Event ev = ctx.event().orElse(null);
            if (ev == null) return null;
            return (ev instanceof org.bukkit.event.entity.EntityShootBowEvent _b ? _b.getConsumable() : (ev instanceof org.bukkit.event.player.PlayerItemConsumeEvent _c ? _c.getItem() : null));
        }));
        registry.registerExpression("[the] damage", Object.class, a -> new ComputedExpression(ctx -> {
            org.bukkit.event.Event ev = ctx.event().orElse(null);
            if (ev == null) return null;
            return (ev instanceof org.bukkit.event.vehicle.VehicleDamageEvent _v ? _v.getDamage() : (ev instanceof org.bukkit.event.player.PlayerItemDamageEvent _i ? _i.getDamage() : (ev instanceof org.bukkit.event.entity.EntityDamageEvent _e ? _e.getDamage() / 2.0 : null)));
        }));
        registry.registerExpression("[the] drops", Object.class, a -> new ComputedListExpression(ctx -> {
            org.bukkit.event.Event ev = ctx.event().orElse(null);
            if (ev == null) return new Object[0];
            Object _c = (ev instanceof org.bukkit.event.entity.EntityDeathEvent _d ? _d.getDrops() : (ev instanceof org.bukkit.event.player.PlayerHarvestBlockEvent _h ? _h.getItemsHarvested() : null));
            if (_c == null) return new Object[0];
            if (_c instanceof java.util.Collection<?> _col) return _col.toArray();
            if (_c instanceof Object[] _arr) return _arr;
            return new Object[]{
                _c
            };
        }));
        registry.registerExpression("[the] enchant[ed] item", Object.class, a -> new ComputedExpression(ctx -> {
            org.bukkit.event.Event ev = ctx.event().orElse(null);
            if (ev == null) return null;
            return (ev instanceof org.bukkit.event.enchantment.PrepareItemEnchantEvent _p ? _p.getItem() : (ev instanceof org.bukkit.event.enchantment.EnchantItemEvent _e ? _e.getItem() : null));
        }));
        registry.registerExpression("[the] [displayed] ([e]xp[erience]|enchanting) cost", Object.class, a -> new ComputedExpression(ctx -> {
            org.bukkit.event.Event ev = ctx.event().orElse(null);
            if (ev == null) return null;
            return (ev instanceof org.bukkit.event.enchantment.EnchantItemEvent _e ? (long) _e.getExpLevelCost() : null);
        }));
        registry.registerExpression("[the] enchantment bonus", Object.class, a -> new ComputedExpression(ctx -> {
            org.bukkit.event.Event ev = ctx.event().orElse(null);
            if (ev == null) return null;
            return (ev instanceof org.bukkit.event.enchantment.PrepareItemEnchantEvent _e ? (long) _e.getEnchantmentBonus() : null);
        }));
        registry.registerExpression("[the] exploded blocks", Object.class, a -> new ComputedListExpression(ctx -> {
            org.bukkit.event.Event ev = ctx.event().orElse(null);
            if (ev == null) return new Object[0];
            Object _c = (ev instanceof org.bukkit.event.entity.EntityExplodeEvent _e ? _e.blockList() : null);
            if (_c == null) return new Object[0];
            if (_c instanceof java.util.Collection<?> _col) return _col.toArray();
            if (_c instanceof Object[] _arr) return _arr;
            return new Object[]{
                _c
            };
        }));
        registry.registerExpression("[the] [explosion['s]] block (yield|amount)", Object.class, a -> new ComputedExpression(ctx -> {
            org.bukkit.event.Event ev = ctx.event().orElse(null);
            if (ev == null) return null;
            return (ev instanceof org.bukkit.event.entity.EntityExplodeEvent _e ? _e.getYield() : null);
        }));
        registry.registerExpression("[the] explosion (yield|radius|size)", Object.class, a -> new ComputedExpression(ctx -> {
            org.bukkit.event.Event ev = ctx.event().orElse(null);
            if (ev == null) return null;
            return (ev instanceof org.bukkit.event.entity.ExplosionPrimeEvent _e ? _e.getRadius() : null);
        }));
        registry.registerExpression("fish[ing] bit(e|ing) [wait] time", Object.class, a -> new ComputedExpression(ctx -> {
            org.bukkit.event.Event ev = ctx.event().orElse(null);
            if (ev == null) return null;
            return (ev instanceof org.bukkit.event.player.PlayerFishEvent _e ? (Object) java.lang.Integer.valueOf(_e.getHook().getTimeUntilBite()) : null);
        }));
        registry.registerExpression("fish[ing] (hook|bobber)", Object.class, a -> new ComputedExpression(ctx -> {
            org.bukkit.event.Event ev = ctx.event().orElse(null);
            if (ev == null) return null;
            return (ev instanceof org.bukkit.event.player.PlayerFishEvent _e ? _e.getHook() : null);
        }));
        registry.registerExpression("[the] hook[ed] entity", Object.class, a -> new ComputedExpression(ctx -> {
            org.bukkit.event.Event ev = ctx.event().orElse(null);
            if (ev == null) return null;
            return (ev instanceof org.bukkit.event.player.PlayerFishEvent _e ? _e.getHook().getHookedEntity() : null);
        }));
        registry.registerExpression("[the] (smelted item|result[ item]|extracted item[s]|smelting item|burned (fuel|item))", Object.class, a -> new ComputedExpression(ctx -> {
            org.bukkit.event.Event ev = ctx.event().orElse(null);
            if (ev == null) return null;
            return (ev instanceof org.bukkit.event.inventory.FurnaceSmeltEvent _s ? _s.getResult() : (ev instanceof org.bukkit.event.inventory.FurnaceStartSmeltEvent _t ? _t.getSource() : (ev instanceof org.bukkit.event.inventory.FurnaceBurnEvent _b ? _b.getFuel() : (ev instanceof org.bukkit.event.inventory.FurnaceExtractEvent _x ? new org.bukkit.inventory.ItemStack(_x.getItemType(), _x.getItemAmount()) : null))));
        }));
        registry.registerExpression("[the] hatching entity [type]", Object.class, a -> new ComputedExpression(ctx -> {
            org.bukkit.event.Event ev = ctx.event().orElse(null);
            if (ev == null) return null;
            return (ev instanceof org.bukkit.event.player.PlayerEggThrowEvent _e ? _e.getHatchingType() : null);
        }));
        registry.registerExpression("[the] hatching number", Object.class, a -> new ComputedExpression(ctx -> {
            org.bukkit.event.Event ev = ctx.event().orElse(null);
            if (ev == null) return null;
            return (ev instanceof org.bukkit.event.player.PlayerEggThrowEvent _e ? _e.getNumHatches() : null);
        }));
        registry.registerExpression("[the] heal[ing] amount", Object.class, a -> new ComputedExpression(ctx -> {
            org.bukkit.event.Event ev = ctx.event().orElse(null);
            if (ev == null) return null;
            return (ev instanceof org.bukkit.event.entity.EntityRegainHealthEvent _e ? _e.getAmount() : null);
        }));
        registry.registerExpression("(regen|health regain|heal[ing]) (reason|cause)", Object.class, a -> new ComputedExpression(ctx -> {
            org.bukkit.event.Event ev = ctx.event().orElse(null);
            if (ev == null) return null;
            return (ev instanceof org.bukkit.event.entity.EntityRegainHealthEvent _e ? _e.getRegainReason() : null);
        }));
        registry.registerExpression("[the] (host|domain)[ ][name]", Object.class, a -> new ComputedExpression(ctx -> {
            org.bukkit.event.Event ev = ctx.event().orElse(null);
            if (ev == null) return null;
            return (ev instanceof org.bukkit.event.player.PlayerLoginEvent _e ? _e.getHostname() : null);
        }));
        registry.registerExpression("[the] hotbar button", Object.class, a -> new ComputedExpression(ctx -> {
            org.bukkit.event.Event ev = ctx.event().orElse(null);
            if (ev == null) return null;
            return (ev instanceof org.bukkit.event.inventory.InventoryClickEvent _e ? (long) _e.getHotbarButton() : null);
        }));
        registry.registerExpression("[the] [custom] [player|server] (hover|sample) ([message] list|message)", Object.class, a -> new ComputedListExpression(ctx -> {
            org.bukkit.event.Event ev = ctx.event().orElse(null);
            if (ev == null) return new Object[0];
            Object _c = (ev instanceof com.destroystokyo.paper.event.server.PaperServerListPingEvent _e ? _e.getListedPlayers().stream().map(com.destroystokyo.paper.event.server.PaperServerListPingEvent.ListedPlayerInfo::name).toArray(String[]::new) : null);
            if (_c == null) return new Object[0];
            if (_c instanceof java.util.Collection<?> _col) return _col.toArray();
            if (_c instanceof Object[] _arr) return _arr;
            return new Object[]{
                _c
            };
        }));
        registry.registerExpression("[the] [event-]initiator[( |-)inventory]", Object.class, a -> new ComputedExpression(ctx -> {
            org.bukkit.event.Event ev = ctx.event().orElse(null);
            if (ev == null) return null;
            return (ev instanceof org.bukkit.event.inventory.InventoryMoveItemEvent _e ? _e.getInitiator() : null);
        }));
        registry.registerExpression("inventory action", Object.class, a -> new ComputedExpression(ctx -> {
            org.bukkit.event.Event ev = ctx.event().orElse(null);
            if (ev == null) return null;
            return (ev instanceof org.bukkit.event.inventory.InventoryClickEvent _e ? _e.getAction() : null);
        }));
        registry.registerExpression("[the] inventory clos(e|ing) (reason|cause)", Object.class, a -> new ComputedExpression(ctx -> {
            org.bukkit.event.Event ev = ctx.event().orElse(null);
            if (ev == null) return null;
            return (ev instanceof org.bukkit.event.inventory.InventoryCloseEvent _e ? _e.getReason() : null);
        }));
        registry.registerExpression("[the] (join|log[ ]in)( |-)message", Object.class, a -> new ComputedExpression(ctx -> {
            org.bukkit.event.Event ev = ctx.event().orElse(null);
            if (ev == null) return null;
            return (ev instanceof org.bukkit.event.player.PlayerJoinEvent _e ? _e.joinMessage() : null);
        }));
        registry.registerExpression("[the] kick( |-)message", Object.class, a -> new ComputedExpression(ctx -> {
            org.bukkit.event.Event ev = ctx.event().orElse(null);
            if (ev == null) return null;
            return (ev instanceof org.bukkit.event.player.PlayerKickEvent _e ? _e.leaveMessage() : null);
        }));
        registry.registerExpression("[the] loot", Object.class, a -> new ComputedListExpression(ctx -> {
            org.bukkit.event.Event ev = ctx.event().orElse(null);
            if (ev == null) return new Object[0];
            Object _c = (ev instanceof org.bukkit.event.world.LootGenerateEvent _e ? _e.getLoot() : null);
            if (_c == null) return new Object[0];
            if (_c instanceof java.util.Collection<?> _col) return _col.toArray();
            if (_c instanceof Object[] _arr) return _arr;
            return new Object[]{
                _c
            };
        }));
        registry.registerExpression("[the] [mending] repair amount", Object.class, a -> new ComputedExpression(ctx -> {
            org.bukkit.event.Event ev = ctx.event().orElse(null);
            if (ev == null) return null;
            return (ev instanceof org.bukkit.event.player.PlayerItemMendEvent _e ? (long) _e.getRepairAmount() : null);
        }));
        registry.registerExpression("[the] moved blocks", Object.class, a -> new ComputedListExpression(ctx -> {
            org.bukkit.event.Event ev = ctx.event().orElse(null);
            if (ev == null) return new Object[0];
            Object _c = (ev instanceof org.bukkit.event.block.BlockPistonExtendEvent _e ? _e.getBlocks() : (ev instanceof org.bukkit.event.block.BlockPistonRetractEvent _r ? _r.getBlocks() : null));
            if (_c == null) return new Object[0];
            if (_c instanceof java.util.Collection<?> _col) return _col.toArray();
            if (_c instanceof Object[] _arr) return _arr;
            return new Object[]{
                _c
            };
        }));
        registry.registerExpression("[the] on-screen kick message", Object.class, a -> new ComputedExpression(ctx -> {
            org.bukkit.event.Event ev = ctx.event().orElse(null);
            if (ev == null) return null;
            return (ev instanceof org.bukkit.event.player.PlayerKickEvent _e ? _e.reason() : null);
        }));
        registry.registerExpression("[the] portal['s] blocks", Object.class, a -> new ComputedListExpression(ctx -> {
            org.bukkit.event.Event ev = ctx.event().orElse(null);
            if (ev == null) return new Object[0];
            Object _c = (ev instanceof org.bukkit.event.world.PortalCreateEvent _e ? _e.getBlocks().stream().map(org.bukkit.block.BlockState::getBlock).collect(java.util.stream.Collectors.toList()) : null);
            if (_c == null) return new Object[0];
            if (_c instanceof java.util.Collection<?> _col) return _col.toArray();
            if (_c instanceof Object[] _arr) return _arr;
            return new Object[]{
                _c
            };
        }));
        registry.registerExpression("[the] projectile force", Object.class, a -> new ComputedExpression(ctx -> {
            org.bukkit.event.Event ev = ctx.event().orElse(null);
            if (ev == null) return null;
            return (ev instanceof org.bukkit.event.entity.EntityShootBowEvent _e ? _e.getForce() : null);
        }));
        registry.registerExpression("[the] [server] [(sent|required|fake)] protocol version [number]", Object.class, a -> new ComputedExpression(ctx -> {
            org.bukkit.event.Event ev = ctx.event().orElse(null);
            if (ev == null) return null;
            return (ev instanceof com.destroystokyo.paper.event.server.PaperServerListPingEvent _e ? (long) _e.getProtocolVersion() : null);
        }));
        registry.registerExpression("[the] (quit|leave|log[ ]out)( |-)message", Object.class, a -> new ComputedExpression(ctx -> {
            org.bukkit.event.Event ev = ctx.event().orElse(null);
            if (ev == null) return null;
            return (ev instanceof org.bukkit.event.player.PlayerQuitEvent _e ? _e.quitMessage() : null);
        }));
        registry.registerExpression("[the] (readied|selected|drawn) arrow", Object.class, a -> new ComputedExpression(ctx -> {
            org.bukkit.event.Event ev = ctx.event().orElse(null);
            if (ev == null) return null;
            return (ev instanceof com.destroystokyo.paper.event.player.PlayerReadyArrowEvent _e ? _e.getArrow() : null);
        }));
        registry.registerExpression("[the] (readied|selected|drawn) bow", Object.class, a -> new ComputedExpression(ctx -> {
            org.bukkit.event.Event ev = ctx.event().orElse(null);
            if (ev == null) return null;
            return (ev instanceof com.destroystokyo.paper.event.player.PlayerReadyArrowEvent _e ? _e.getBow() : null);
        }));
        registry.registerExpression("[the] respawn location", Object.class, a -> new ComputedExpression(ctx -> {
            org.bukkit.event.Event ev = ctx.event().orElse(null);
            if (ev == null) return null;
            return (ev instanceof org.bukkit.event.player.PlayerRespawnEvent _e ? _e.getRespawnLocation() : null);
        }));
        registry.registerExpression("[the] [sent] [server] command[s] list", Object.class, a -> new ComputedListExpression(ctx -> {
            org.bukkit.event.Event ev = ctx.event().orElse(null);
            if (ev == null) return new Object[0];
            Object _c = (ev instanceof org.bukkit.event.player.PlayerCommandSendEvent _e ? _e.getCommands() : null);
            if (_c == null) return new Object[0];
            if (_c instanceof java.util.Collection<?> _col) return _col.toArray();
            if (_c instanceof Object[] _arr) return _arr;
            return new Object[]{
                _c
            };
        }));
        registry.registerExpression("[the] (shown|sent) [server] icon", Object.class, a -> new ComputedExpression(ctx -> {
            org.bukkit.event.Event ev = ctx.event().orElse(null);
            if (ev == null) return null;
            return (ev instanceof com.destroystokyo.paper.event.server.PaperServerListPingEvent _e && _e.getServerIcon() != null && _e.getServerIcon().getData() != null ? _e.getServerIcon() : null);
        }));
        registry.registerExpression("[the] source block", Object.class, a -> new ComputedExpression(ctx -> {
            org.bukkit.event.Event ev = ctx.event().orElse(null);
            if (ev == null) return null;
            return (ev instanceof org.bukkit.event.block.BlockSpreadEvent _e ? _e.getSource() : null);
        }));
        registry.registerExpression("[the] tamer", Object.class, a -> new ComputedExpression(ctx -> {
            org.bukkit.event.Event ev = ctx.event().orElse(null);
            if (ev == null) return null;
            return (ev instanceof org.bukkit.event.entity.EntityTameEvent _e && _e.getOwner() instanceof org.bukkit.entity.Player _p ? _p : null);
        }));
        registry.registerExpression("teleport (cause|reason|type)", Object.class, a -> new ComputedExpression(ctx -> {
            org.bukkit.event.Event ev = ctx.event().orElse(null);
            if (ev == null) return null;
            return (ev instanceof org.bukkit.event.player.PlayerTeleportEvent _e ? _e.getCause() : null);
        }));
        registry.registerExpression("[thrown] egg", Object.class, a -> new ComputedExpression(ctx -> {
            org.bukkit.event.Event ev = ctx.event().orElse(null);
            if (ev == null) return null;
            return (ev instanceof org.bukkit.event.player.PlayerEggThrowEvent _e ? _e.getEgg() : null);
        }));
        registry.registerExpression("[the] transform[ing] (cause|reason|type)", Object.class, a -> new ComputedExpression(ctx -> {
            org.bukkit.event.Event ev = ctx.event().orElse(null);
            if (ev == null) return null;
            return (ev instanceof org.bukkit.event.entity.EntityTransformEvent _e ? _e.getTransformReason() : null);
        }));
        registry.registerExpression("[the] unleash[ing] reason", Object.class, a -> new ComputedExpression(ctx -> {
            org.bukkit.event.Event ev = ctx.event().orElse(null);
            if (ev == null) return null;
            return (ev instanceof org.bukkit.event.entity.EntityUnleashEvent _e ? _e.getReason() : null);
        }));
        registry.registerExpression("[the] [shown|custom] version [string|text]", Object.class, a -> new ComputedExpression(ctx -> {
            org.bukkit.event.Event ev = ctx.event().orElse(null);
            if (ev == null) return null;
            return (ev instanceof com.destroystokyo.paper.event.server.PaperServerListPingEvent _e ? _e.getVersion() : null);
        }));
        // second-pass recovered expressions
        registry.registerExpression("[all [[of] the]|the] banned players", Object.class, a -> new ComputedListExpression(ctx -> {
            java.util.Collection<?> c = (org.bukkit.Bukkit.getBannedPlayers());
            return c == null ? new Object[0] : c.toArray();
        }));
        registry.registerExpression("[all [[of] the]|the] banned (ips|ip addresses)", Object.class, a -> new ComputedListExpression(ctx -> {
            java.util.Collection<?> c = (org.bukkit.Bukkit.getIPBans());
            return c == null ? new Object[0] : c.toArray();
        }));
        registry.registerExpression("[all [[of] the]|the] [server] op[erator]s", Object.class, a -> new ComputedListExpression(ctx -> {
            java.util.Collection<?> c = (org.bukkit.Bukkit.getOperators());
            return c == null ? new Object[0] : c.toArray();
        }));
        registry.registerExpression("[all [[of] the]|the] [server] non(-| )op[erator]s", Object.class, a -> new ComputedListExpression(ctx -> {
            java.util.Collection<?> c = (org.bukkit.Bukkit.getOnlinePlayers().stream().filter(p -> !p.isOp()).collect(java.util.stream.Collectors.toList()));
            return c == null ? new Object[0] : c.toArray();
        }));
        registry.registerExpression("[all [[of] the]] [persistent] data [tag] keys of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedListExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.persistence.PersistentDataHolder h)) return new Object[0];
                java.util.Collection<?> c = (h.getPersistentDataContainer().getKeys().stream().map(org.bukkit.NamespacedKey::toString).collect(java.util.stream.Collectors.toList()));
                return c == null ? new Object[0] : c.toArray();
            });
        });
        registry.registerExpression("[the] alpha (value|component) of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.Color h)) return null;
                return (h.getAlpha());
            });
        });
        registry.registerExpression("[the] red (value|component) of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.Color h)) return null;
                return (h.getRed());
            });
        });
        registry.registerExpression("[the] green (value|component) of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.Color h)) return null;
                return (h.getGreen());
            });
        });
        registry.registerExpression("[the] blue (value|component) of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.Color h)) return null;
                return (h.getBlue());
            });
        });
        registry.registerExpression("%object% [in] deg[ree][s]", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof java.lang.Number h)) return null;
                return (h);
            });
        });
        registry.registerExpression("%object% [in] rad[ian][s]", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof java.lang.Number h)) return null;
                return (java.lang.Math.toDegrees(h.doubleValue()));
            });
        });
        registry.registerExpression("[all [[of] the]|the] banner pattern[s] of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedListExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.inventory.ItemStack h)) return new Object[0];
                java.util.Collection<?> c = (h.getItemMeta() instanceof org.bukkit.inventory.meta.BannerMeta bm ? bm.getPatterns() : null);
                return c == null ? new Object[0] : c.toArray();
            });
        });
        registry.registerExpression("honey level of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.block.Block h)) return null;
                return (h.getBlockData() instanceof org.bukkit.block.data.type.Beehive bh ? bh.getHoneyLevel() : null);
            });
        });
        registry.registerExpression("max[imum] honey level of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.block.Block h)) return null;
                return (h.getBlockData() instanceof org.bukkit.block.data.type.Beehive bh ? bh.getMaximumHoneyLevel() : null);
            });
        });
        registry.registerExpression("[book] (author|writer|publisher) of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.inventory.ItemStack h)) return null;
                return (h.getItemMeta() instanceof org.bukkit.inventory.meta.BookMeta bm && bm.hasAuthor() ? bm.author() : null);
            });
        });
        registry.registerExpression("[all [[of] the]|the] [book] (pages|content) of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedListExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.inventory.ItemStack h)) return new Object[0];
                java.util.Collection<?> c = (h.getType() == org.bukkit.Material.WRITTEN_BOOK && h.getItemMeta() instanceof org.bukkit.inventory.meta.BookMeta bm ? bm.pages() : null);
                return c == null ? new Object[0] : c.toArray();
            });
        });
        registry.registerExpression("book (name|title) of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.inventory.ItemStack h)) return null;
                return (h.getItemMeta() instanceof org.bukkit.inventory.meta.BookMeta bm && bm.hasTitle() ? bm.title() : null);
            });
        });
        registry.registerExpression("(dust|brush)[ed|ing] (value|stage|progress[ion]) of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.block.Block h)) return null;
                return (h.getBlockData() instanceof org.bukkit.block.data.Brushable br ? Integer.valueOf(br.getDusted()) : null);
            });
        });
        registry.registerExpression("max[imum] (dust|brush)[ed|ing] (value|stage|progress[ion]) of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.block.Block h)) return null;
                return (h.getBlockData() instanceof org.bukkit.block.data.Brushable br ? Integer.valueOf(br.getMaximumDusted()) : null);
            });
        });
        registry.registerExpression("a [virtual] world[ ]border", Object.class, a -> new ComputedExpression(ctx -> (org.bukkit.Bukkit.createWorldBorder())));
        registry.registerExpression("[a] custom damage source (with|using) [the|a] [damage type [of]] %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.damage.DamageType h)) return null;
                return (org.bukkit.damage.DamageSource.builder(h).build());
            });
        });
        registry.registerExpression("sky (light [level]|brightness) override[s] of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.entity.Display h)) return null;
                return (h.getBrightness() == null ? null : Integer.valueOf(h.getBrightness().getSkyLight()));
            });
        });
        registry.registerExpression("block (light [level]|brightness) override[s] of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.entity.Display h)) return null;
                return (h.getBrightness() == null ? null : Integer.valueOf(h.getBrightness().getBlockLight()));
            });
        });
        registry.registerExpression("(light [level]|brightness) override[s] of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedListExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.entity.Display h)) return new Object[0];
                java.util.Collection<?> c = (h.getBrightness() == null ? null : java.util.Arrays.asList(h.getBrightness().getBlockLight(), h.getBrightness().getSkyLight()));
                return c == null ? new Object[0] : c.toArray();
            });
        });
        registry.registerExpression("display height of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.entity.Display h)) return null;
                return (h.getDisplayHeight());
            });
        });
        registry.registerExpression("display width of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.entity.Display h)) return null;
                return (h.getDisplayWidth());
            });
        });
        registry.registerExpression("shadow radius of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.entity.Display h)) return null;
                return (((org.bukkit.entity.Display) h).getShadowRadius());
            });
        });
        registry.registerExpression("shadow strength of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.entity.Display h)) return null;
                return (((org.bukkit.entity.Display) h).getShadowStrength());
            });
        });
        registry.registerExpression("left [transformation] rotation of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.entity.Display h)) return null;
                return (((org.bukkit.entity.Display) h).getTransformation().getLeftRotation());
            });
        });
        registry.registerExpression("right [transformation] rotation of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.entity.Display h)) return null;
                return (((org.bukkit.entity.Display) h).getTransformation().getRightRotation());
            });
        });
        registry.registerExpression("(display|[display] transformation) scale of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.entity.Display h)) return null;
                return (org.bukkit.util.Vector.fromJOML(((org.bukkit.entity.Display) h).getTransformation().getScale()));
            });
        });
        registry.registerExpression("(display|[display] transformation) translation of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.entity.Display h)) return null;
                return (org.bukkit.util.Vector.fromJOML(((org.bukkit.entity.Display) h).getTransformation().getTranslation()));
            });
        });
        registry.registerExpression("[(all|the|all [of] the)] drops of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedListExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.block.Block h)) return new Object[0];
                java.util.Collection<?> c = (((org.bukkit.block.Block) h).getDrops());
                return c == null ? new Object[0] : c.toArray();
            });
        });
        registry.registerExpression("offline player[s] from %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof java.util.UUID h)) return null;
                return (org.bukkit.Bukkit.getOfflinePlayer(h));
            });
        });
        registry.registerExpression("player[s] from %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof java.util.UUID h)) return null;
                return (org.bukkit.Bukkit.getPlayer(h));
            });
        });
        registry.registerExpression("world[s] from %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof java.util.UUID h)) return null;
                return (org.bukkit.Bukkit.getWorld(h));
            });
        });
        registry.registerExpression("entit(y|ies) from %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof java.util.UUID h)) return null;
                return (org.bukkit.Bukkit.getEntity(h));
            });
        });
        registry.registerExpression("equippable component[s] of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.inventory.ItemStack h)) return null;
                return (h.getData(io.papermc.paper.datacomponent.DataComponentTypes.EQUIPPABLE));
            });
        });
        registry.registerExpression("camera overlay of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof io.papermc.paper.datacomponent.item.Equippable h)) return null;
                return (h.cameraOverlay() == null ? null : h.cameraOverlay().toString());
            });
        });
        registry.registerExpression("equip sound of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof io.papermc.paper.datacomponent.item.Equippable h)) return null;
                return (h.equipSound() == null ? null : h.equipSound().toString());
            });
        });
        registry.registerExpression("[the] [server] free (memory|ram)", Object.class, a -> new ComputedExpression(ctx -> (java.lang.Runtime.getRuntime().freeMemory() * 1E-6)));
        registry.registerExpression("[the] [server] max[imum] (memory|ram)", Object.class, a -> new ComputedExpression(ctx -> (java.lang.Runtime.getRuntime().maxMemory() * 1E-6)));
        registry.registerExpression("[the] [server] total (memory|ram)", Object.class, a -> new ComputedExpression(ctx -> (java.lang.Runtime.getRuntime().totalMemory() * 1E-6)));
        registry.registerExpression("interaction height[s] of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.entity.Interaction h)) return null;
                return (h.getInteractionHeight());
            });
        });
        registry.registerExpression("interaction width[s] of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.entity.Interaction h)) return null;
                return (h.getInteractionWidth());
            });
        });
        registry.registerExpression("holder[s] of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.inventory.Inventory h)) return null;
                return (h.getHolder());
            });
        });
        registry.registerExpression("viewers of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedListExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.inventory.Inventory h)) return new Object[0];
                java.util.Collection<?> c = (h.getViewers().stream().filter(v -> v instanceof org.bukkit.entity.Player).collect(java.util.stream.Collectors.toList()));
                return c == null ? new Object[0] : c.toArray();
            });
        });
        registry.registerExpression("[amount of] rows of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.inventory.Inventory h)) return null;
                return (h.getSize() < 9 ? 1 : h.getSize() / 9);
            });
        });
        registry.registerExpression("[amount of] slots of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.inventory.Inventory h)) return null;
                return (h.getSize());
            });
        });
        registry.registerExpression("[the] last player[s] to attack %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.entity.Interaction h)) return null;
                return (h.getLastAttack() != null ? h.getLastAttack().getPlayer() : null);
            });
        });
        registry.registerExpression("[the] last player[s] (who|that) attacked %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.entity.Interaction h)) return null;
                return (h.getLastAttack() != null ? h.getLastAttack().getPlayer() : null);
            });
        });
        registry.registerExpression("[the] last player[s] to interact with %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.entity.Interaction h)) return null;
                return (h.getLastInteraction() != null ? h.getLastInteraction().getPlayer() : null);
            });
        });
        registry.registerExpression("[the] last player[s] (who|that) interacted with %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.entity.Interaction h)) return null;
                return (h.getLastInteraction() != null ? h.getLastInteraction().getPlayer() : null);
            });
        });
        registry.registerExpression("light level of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.Location h)) return null;
                return (((org.bukkit.Location) h).getBlock().getLightLevel());
            });
        });
        registry.registerExpression("sky light level of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.Location h)) return null;
                return (((org.bukkit.Location) h).getBlock().getLightFromSky());
            });
        });
        registry.registerExpression("block light level of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.Location h)) return null;
                return (((org.bukkit.Location) h).getBlock().getLightFromBlocks());
            });
        });
        registry.registerExpression("(location|position) of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.Location h)) return null;
                return (h);
            });
        });
        registry.registerExpression("[the] highest [solid] block (at|of) %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.Location h)) return null;
                return (h.getWorld() == null ? null : h.getWorld().getHighestBlockAt(h));
            });
        });
        registry.registerExpression("[the] default (MOTD|message of [the] day)", Object.class, a -> new ComputedExpression(ctx -> (org.bukkit.Bukkit.motd())));
        registry.registerExpression("[the] [minecart] derailed velocity of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.entity.Minecart h)) return null;
                return (h.getDerailedVelocityMod());
            });
        });
        registry.registerExpression("[the] [minecart] flying velocity of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.entity.Minecart h)) return null;
                return (h.getFlyingVelocityMod());
            });
        });
        registry.registerExpression("number of upper[ ]case char(acters|s) in %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof java.lang.String h)) return null;
                return (h.codePoints().filter(java.lang.Character::isUpperCase).count());
            });
        });
        registry.registerExpression("number of lower[ ]case char(acters|s) in %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof java.lang.String h)) return null;
                return (h.codePoints().filter(java.lang.Character::isLowerCase).count());
            });
        });
        registry.registerExpression("number of digit char(acters|s) in %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof java.lang.String h)) return null;
                return (h.codePoints().filter(java.lang.Character::isDigit).count());
            });
        });
        registry.registerExpression("main gene[s] of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.entity.Panda h)) return null;
                return (h.getMainGene());
            });
        });
        registry.registerExpression("hidden gene[s] of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.entity.Panda h)) return null;
                return (h.getHiddenGene());
            });
        });
        registry.registerExpression("(player|tab)[ ]list header [text|message] of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.entity.Player h)) return null;
                return (h.playerListHeader());
            });
        });
        registry.registerExpression("(player|tab)[ ]list footer [text|message] of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.entity.Player h)) return null;
                return (h.playerListFooter());
            });
        });
        registry.registerExpression("([potion] amplifier|potion tier|potion level)[s] of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.potion.PotionEffect h)) return null;
                return (h.getAmplifier() + 1);
            });
        });
        registry.registerExpression("active potion effects of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedListExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.entity.LivingEntity h)) return new Object[0];
                java.util.Collection<?> c = (h.getActivePotionEffects());
                return c == null ? new Object[0] : c.toArray();
            });
        });
        registry.registerExpression("potion effects of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedListExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.entity.LivingEntity h)) return new Object[0];
                java.util.Collection<?> c = (h.getActivePotionEffects());
                return c == null ? new Object[0] : c.toArray();
            });
        });
        registry.registerExpression("raw %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof java.lang.String h)) return null;
                return (h == null ? null : net.kyori.adventure.text.Component.text(h));
            });
        });
        registry.registerExpression("rotation axis of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.joml.Quaternionf h)) return null;
                return (new org.bukkit.util.Vector(new org.joml.AxisAngle4f().set(h).x, new org.joml.AxisAngle4f().set(h).y, new org.joml.AxisAngle4f().set(h).z));
            });
        });
        registry.registerExpression("rotation angle of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.joml.Quaternionf h)) return null;
                return ((float) (new org.joml.AxisAngle4f().set(h).angle * 180 / java.lang.Math.PI));
            });
        });
        registry.registerExpression("loot[[ ]table] seed[s] of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof java.lang.Object h)) return null;
                return (h instanceof org.bukkit.block.Block b ? (b.getState() instanceof org.bukkit.loot.Lootable l ? l.getSeed() : null) : (h instanceof org.bukkit.loot.Lootable l2 ? l2.getSeed() : null));
            });
        });
        registry.registerExpression("(head|skull) owner of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof java.lang.Object h)) return null;
                return (h instanceof org.bukkit.block.Block b && b.getState() instanceof org.bukkit.block.Skull sk ? (sk.getPlayerProfile() == null ? null : (sk.getPlayerProfile().getId() != null ? org.bukkit.Bukkit.getOfflinePlayer(sk.getPlayerProfile().getId()) : (sk.getPlayerProfile().getName() != null ? org.bukkit.Bukkit.getOfflinePlayer(sk.getPlayerProfile().getName()) : null))) : (h instanceof org.bukkit.inventory.ItemStack it && it.getItemMeta() instanceof org.bukkit.inventory.meta.SkullMeta sm ? (sm.getPlayerProfile() == null ? null : (sm.getPlayerProfile().getId() != null ? org.bukkit.Bukkit.getOfflinePlayer(sm.getPlayerProfile().getId()) : (sm.getPlayerProfile().getName() != null ? org.bukkit.Bukkit.getOfflinePlayer(sm.getPlayerProfile().getName()) : null))) : null));
            });
        });
        registry.registerExpression("spawn egg entity of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.inventory.ItemStack h)) return null;
                return (h.getItemMeta() instanceof org.bukkit.inventory.meta.SpawnEggMeta m ? m.getSpawnedEntity() : null);
            });
        });
        registry.registerExpression("(spawner|entity|creature) type[s] of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.block.Block h)) return null;
                return (h.getState() instanceof org.bukkit.block.CreatureSpawner cs ? cs.getSpawnedType() : (h.getState() instanceof org.bukkit.block.TrialSpawner ts ? (ts.isOminous() ? ts.getOminousConfiguration().getSpawnedType() : ts.getNormalConfiguration().getSpawnedType()) : null));
            });
        });
        registry.registerExpression("walk[ing][( |-)]speed of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.entity.Player h)) return null;
                return (h.getWalkSpeed());
            });
        });
        registry.registerExpression("fl(y[ing]|ight)[( |-)]speed of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.entity.Player h)) return null;
                return (h.getFlySpeed());
            });
        });
        registry.registerExpression("test string literal %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof java.lang.String h)) return null;
                return (h);
            });
        });
        registry.registerExpression("tps from [the] last 1 minute", Object.class, a -> new ComputedExpression(ctx -> (org.bukkit.Bukkit.getServer().getTPS()[0])));
        registry.registerExpression("tps from [the] last 5 minutes", Object.class, a -> new ComputedExpression(ctx -> (org.bukkit.Bukkit.getServer().getTPS()[1])));
        registry.registerExpression("tps from [the] last 15 minutes", Object.class, a -> new ComputedExpression(ctx -> (org.bukkit.Bukkit.getServer().getTPS()[2])));
        registry.registerExpression("[the] tps", Object.class, a -> new ComputedListExpression(ctx -> {
            java.util.Collection<?> c = (java.util.Arrays.stream(org.bukkit.Bukkit.getServer().getTPS()).boxed().collect(java.util.stream.Collectors.toList()));
            return c == null ? new Object[0] : c.toArray();
        }));
        registry.registerExpression("[the] [vector|quaternion] x [component[s]] of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof java.lang.Object h)) return null;
                return (h instanceof org.bukkit.util.Vector ? (java.lang.Number) java.lang.Double.valueOf(((org.bukkit.util.Vector) h).getX()) : (h instanceof org.joml.Quaternionf ? (java.lang.Number) java.lang.Float.valueOf(((org.joml.Quaternionf) h).x()) : null));
            });
        });
        registry.registerExpression("[the] [vector|quaternion] y [component[s]] of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof java.lang.Object h)) return null;
                return (h instanceof org.bukkit.util.Vector ? (java.lang.Number) java.lang.Double.valueOf(((org.bukkit.util.Vector) h).getY()) : (h instanceof org.joml.Quaternionf ? (java.lang.Number) java.lang.Float.valueOf(((org.joml.Quaternionf) h).y()) : null));
            });
        });
        registry.registerExpression("[the] [vector|quaternion] z [component[s]] of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof java.lang.Object h)) return null;
                return (h instanceof org.bukkit.util.Vector ? (java.lang.Number) java.lang.Double.valueOf(((org.bukkit.util.Vector) h).getZ()) : (h instanceof org.joml.Quaternionf ? (java.lang.Number) java.lang.Float.valueOf(((org.joml.Quaternionf) h).z()) : null));
            });
        });
        registry.registerExpression("[the] [vector|quaternion] w [component[s]] of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof java.lang.Object h)) return null;
                return (h instanceof org.joml.Quaternionf ? (java.lang.Number) java.lang.Float.valueOf(((org.joml.Quaternionf) h).w()) : null);
            });
        });
        registry.registerExpression("[craft]bukkit( |-)version", Object.class, a -> new ComputedExpression(ctx -> ("" + org.bukkit.Bukkit.getBukkitVersion())));
        registry.registerExpression("villager level of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.entity.Villager h)) return null;
                return (h.getVillagerLevel());
            });
        });
        registry.registerExpression("villager experience of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof org.bukkit.entity.Villager h)) return null;
                return (h.getVillagerExperience());
            });
        });
        registry.registerExpression("%object% time[s]", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedListExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof java.lang.Number h)) return new Object[0];
                java.util.Collection<?> c = (java.util.stream.LongStream.rangeClosed(1L, (long)(((java.lang.Number)h).doubleValue() + 1e-10)).boxed().collect(java.util.stream.Collectors.toList()));
                return c == null ? new Object[0] : c.toArray();
            });
        });
        registry.registerExpression("once", Object.class, a -> new ComputedListExpression(ctx -> {
            java.util.Collection<?> c = (java.util.Arrays.asList(1L));
            return c == null ? new Object[0] : c.toArray();
        }));
        registry.registerExpression("twice", Object.class, a -> new ComputedListExpression(ctx -> {
            java.util.Collection<?> c = (java.util.Arrays.asList(1L, 2L));
            return c == null ? new Object[0] : c.toArray();
        }));
        registry.registerExpression("thrice", Object.class, a -> new ComputedListExpression(ctx -> {
            java.util.Collection<?> c = (java.util.Arrays.asList(1L, 2L, 3L));
            return c == null ? new Object[0] : c.toArray();
        }));
        registry.registerExpression("yaw of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof java.lang.Object h)) return null;
                return (h instanceof org.bukkit.entity.Entity ? (org.bukkit.Location.normalizeYaw(((org.bukkit.entity.Entity)h).getLocation().getYaw()) < 0f ? org.bukkit.Location.normalizeYaw(((org.bukkit.entity.Entity)h).getLocation().getYaw()) + 360f : org.bukkit.Location.normalizeYaw(((org.bukkit.entity.Entity)h).getLocation().getYaw())) : (h instanceof org.bukkit.Location ? (org.bukkit.Location.normalizeYaw(((org.bukkit.Location)h).getYaw()) < 0f ? org.bukkit.Location.normalizeYaw(((org.bukkit.Location)h).getYaw()) + 360f : org.bukkit.Location.normalizeYaw(((org.bukkit.Location)h).getYaw())) : (h instanceof org.bukkit.util.Vector ? ((((org.bukkit.util.Vector)h).getX()==0.0 && ((org.bukkit.util.Vector)h).getZ()==0.0 ? 0f : (float)(java.lang.Math.atan2(((org.bukkit.util.Vector)h).getZ(), ((org.bukkit.util.Vector)h).getX()) * (180.0/java.lang.Math.PI))) < 90f ? (((org.bukkit.util.Vector)h).getX()==0.0 && ((org.bukkit.util.Vector)h).getZ()==0.0 ? 0f : (float)(java.lang.Math.atan2(((org.bukkit.util.Vector)h).getZ(), ((org.bukkit.util.Vector)h).getX()) * (180.0/java.lang.Math.PI))) + 270f : (((org.bukkit.util.Vector)h).getX()==0.0 && ((org.bukkit.util.Vector)h).getZ()==0.0 ? 0f : (float)(java.lang.Math.atan2(((org.bukkit.util.Vector)h).getZ(), ((org.bukkit.util.Vector)h).getX()) * (180.0/java.lang.Math.PI))) - 90f) : null)));
            });
        });
        registry.registerExpression("pitch of %object%", Object.class, a -> {
            Expression<?> src = a.get(0);
            return new ComputedExpression(ctx -> {
                Object o = src.getSingle(ctx);
                if (!(o instanceof java.lang.Object h)) return null;
                return (h instanceof org.bukkit.entity.Entity ? ((org.bukkit.entity.Entity)h).getLocation().getPitch() : (h instanceof org.bukkit.Location ? ((org.bukkit.Location)h).getPitch() : (h instanceof org.bukkit.util.Vector ? -(float)(java.lang.Math.atan(((org.bukkit.util.Vector)h).getY() / java.lang.Math.sqrt(((org.bukkit.util.Vector)h).getX()*((org.bukkit.util.Vector)h).getX() + ((org.bukkit.util.Vector)h).getZ()*((org.bukkit.util.Vector)h).getZ())) * (180.0/java.lang.Math.PI)) : null)));
            });
        });
        // multi-argument value expressions
        registry.registerExpression("[a|the] (round[ed] down|floored) %object%", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            return (!(a0 instanceof java.lang.Number n) ? null : java.lang.Long.valueOf((long) java.lang.Math.floor(n.doubleValue())));
        }));
        registry.registerExpression("%object% (round[ed] down|floored)", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            return (!(a0 instanceof java.lang.Number n) ? null : java.lang.Long.valueOf((long) java.lang.Math.floor(n.doubleValue())));
        }));
        registry.registerExpression("[a|the] round[ed] %object%", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            return (!(a0 instanceof java.lang.Number n) ? null : java.lang.Long.valueOf(java.lang.Math.round(n.doubleValue())));
        }));
        registry.registerExpression("%object% round[ed]", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            return (!(a0 instanceof java.lang.Number n) ? null : java.lang.Long.valueOf(java.lang.Math.round(n.doubleValue())));
        }));
        registry.registerExpression("[a|the] (round[ed] up|ceil[ing]ed) %object%", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            return (!(a0 instanceof java.lang.Number n) ? null : java.lang.Long.valueOf((long) java.lang.Math.ceil(n.doubleValue())));
        }));
        registry.registerExpression("%object% (round[ed] up|ceil[ing]ed)", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            return (!(a0 instanceof java.lang.Number n) ? null : java.lang.Long.valueOf((long) java.lang.Math.ceil(n.doubleValue())));
        }));
        registry.registerExpression("%object% dot %object%", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            Object a1 = a.get(1).getSingle(ctx);
            return (!(a0 instanceof org.bukkit.util.Vector v0) || !(a1 instanceof org.bukkit.util.Vector v1) ? null : v0.dot(v1));
        }));
        registry.registerExpression("[the] anger level [of] %object% towards %object%", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            Object a1 = a.get(1).getSingle(ctx);
            return (!(a0 instanceof org.bukkit.entity.Warden w) || !(a1 instanceof org.bukkit.entity.Entity e) ? null : w.getAnger(e));
        }));
        registry.registerExpression("%object%'[s] anger level towards %object%", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            Object a1 = a.get(1).getSingle(ctx);
            return (!(a0 instanceof org.bukkit.entity.Warden w) || !(a1 instanceof org.bukkit.entity.Entity e) ? null : w.getAnger(e));
        }));
        registry.registerExpression("[(all [[of] the]|the)] characters (between|from) %object% (and|to) %object%", Object.class, a -> new ComputedListExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            Object a1 = a.get(1).getSingle(ctx);
            Object _c = (!(a0 instanceof String s0) || !(a1 instanceof String s1) || s0.isEmpty() || s1.isEmpty() ? null : java.util.stream.IntStream.rangeClosed(0, Math.abs(s0.charAt(0) - s1.charAt(0))).mapToObj(k -> String.valueOf((char)(s0.charAt(0) <= s1.charAt(0) ? s0.charAt(0) + k : s0.charAt(0) - k))).collect(java.util.stream.Collectors.toList()));
            if (_c == null) return new Object[0];
            if (_c instanceof java.util.Collection<?> _col) return _col.toArray();
            if (_c instanceof Object[] _arr) return _arr;
            return new Object[]{
                _c
            };
        }));
        registry.registerExpression("[(all [[of] the]|the)] alphanumeric characters (between|from) %object% (and|to) %object%", Object.class, a -> new ComputedListExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            Object a1 = a.get(1).getSingle(ctx);
            Object _c = (!(a0 instanceof String s0) || !(a1 instanceof String s1) || s0.isEmpty() || s1.isEmpty() ? null : java.util.stream.IntStream.rangeClosed(0, Math.abs(s0.charAt(0) - s1.charAt(0))).mapToObj(k -> String.valueOf((char)(s0.charAt(0) <= s1.charAt(0) ? s0.charAt(0) + k : s0.charAt(0) - k))).filter(c -> Character.isLetterOrDigit(c.charAt(0))).collect(java.util.stream.Collectors.toList()));
            if (_c == null) return new Object[0];
            if (_c instanceof java.util.Collection<?> _col) return _col.toArray();
            if (_c instanceof Object[] _arr) return _arr;
            return new Object[]{
                _c
            };
        }));
        registry.registerExpression("[the] %object% attribute [value] of %object%", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            Object a1 = a.get(1).getSingle(ctx);
            return (!(a0 instanceof org.bukkit.attribute.Attribute attr) || !(a1 instanceof org.bukkit.attribute.Attributable att) || att.getAttribute(attr) == null ? null : att.getAttribute(attr).getBaseValue());
        }));
        registry.registerExpression("[the] %object% (total|final|modified) attribute [value] of %object%", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            Object a1 = a.get(1).getSingle(ctx);
            return (!(a0 instanceof org.bukkit.attribute.Attribute attr) || !(a1 instanceof org.bukkit.attribute.Attributable att) || att.getAttribute(attr) == null ? null : att.getAttribute(attr).getValue());
        }));
        registry.registerExpression("%object%'[s] %object% attribute [value]", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            Object a1 = a.get(1).getSingle(ctx);
            return (!(a1 instanceof org.bukkit.attribute.Attribute attr) || !(a0 instanceof org.bukkit.attribute.Attributable att) || att.getAttribute(attr) == null ? null : att.getAttribute(attr).getBaseValue());
        }));
        registry.registerExpression("%object%'[s] %object% (total|final|modified) attribute [value]", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            Object a1 = a.get(1).getSingle(ctx);
            return (!(a1 instanceof org.bukkit.attribute.Attribute attr) || !(a0 instanceof org.bukkit.attribute.Attributable att) || att.getAttribute(attr) == null ? null : att.getAttribute(attr).getValue());
        }));
        registry.registerExpression("[vector] projection [of] %object% on[to] %object%", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            Object a1 = a.get(1).getSingle(ctx);
            return (!(a0 instanceof org.bukkit.util.Vector v0) || !(a1 instanceof org.bukkit.util.Vector v1) ? null : v1.clone().multiply(v0.dot(v1) / v1.lengthSquared()));
        }));
        registry.registerExpression("a random character from %object% to %object%", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            Object a1 = a.get(1).getSingle(ctx);
            return (!(a0 instanceof java.lang.String s0) || !(a1 instanceof java.lang.String s1) || s0.isEmpty() || s1.isEmpty() ? null : String.valueOf((char)(java.util.concurrent.ThreadLocalRandom.current().nextInt(Math.abs(s0.charAt(0) - s1.charAt(0)) + 1) + Math.min(s0.charAt(0), s1.charAt(0)))));
        }));
        registry.registerExpression("a random character from %object% and %object%", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            Object a1 = a.get(1).getSingle(ctx);
            return (!(a0 instanceof java.lang.String s0) || !(a1 instanceof java.lang.String s1) || s0.isEmpty() || s1.isEmpty() ? null : String.valueOf((char)(java.util.concurrent.ThreadLocalRandom.current().nextInt(Math.abs(s0.charAt(0) - s1.charAt(0)) + 1) + Math.min(s0.charAt(0), s1.charAt(0)))));
        }));
        registry.registerExpression("a random character between %object% to %object%", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            Object a1 = a.get(1).getSingle(ctx);
            return (!(a0 instanceof java.lang.String s0) || !(a1 instanceof java.lang.String s1) || s0.isEmpty() || s1.isEmpty() ? null : String.valueOf((char)(java.util.concurrent.ThreadLocalRandom.current().nextInt(Math.abs(s0.charAt(0) - s1.charAt(0)) + 1) + Math.min(s0.charAt(0), s1.charAt(0)))));
        }));
        registry.registerExpression("a random character between %object% and %object%", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            Object a1 = a.get(1).getSingle(ctx);
            return (!(a0 instanceof java.lang.String s0) || !(a1 instanceof java.lang.String s1) || s0.isEmpty() || s1.isEmpty() ? null : String.valueOf((char)(java.util.concurrent.ThreadLocalRandom.current().nextInt(Math.abs(s0.charAt(0) - s1.charAt(0)) + 1) + Math.min(s0.charAt(0), s1.charAt(0)))));
        }));
        registry.registerExpression("%location% offset by [[the] vectors] %object%", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            Object a1 = a.get(1).getSingle(ctx);
            return (!(a0 instanceof org.bukkit.Location l) || !(a1 instanceof org.bukkit.util.Vector v) ? null : l.clone().add(v));
        }));
        registry.registerExpression("%location%[ ]~[~][ ]%object%", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            Object a1 = a.get(1).getSingle(ctx);
            return (!(a0 instanceof org.bukkit.Location l) || !(a1 instanceof org.bukkit.util.Vector v) ? null : l.clone().add(v));
        }));
        registry.registerExpression("[the] break speed[s] [of %object%] [for %object%]", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            Object a1 = a.get(1).getSingle(ctx);
            return (!(a0 instanceof org.bukkit.block.Block b) || !(a1 instanceof org.bukkit.entity.Player p) ? null : b.getBreakSpeed(p));
        }));
        registry.registerExpression("%object%'[s] break speed[s] [for %object%]", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            Object a1 = a.get(1).getSingle(ctx);
            return (!(a0 instanceof org.bukkit.block.Block b) || !(a1 instanceof org.bukkit.entity.Player p) ? null : b.getBreakSpeed(p));
        }));
        registry.registerExpression("%object% with [a] yaw [of] %object%", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            Object a1 = a.get(1).getSingle(ctx);
            return (!(a0 instanceof org.bukkit.Location loc) || !(a1 instanceof java.lang.Number n) ? null : new org.bukkit.Location(loc.getWorld(), loc.getX(), loc.getY(), loc.getZ(), n.floatValue(), loc.getPitch()));
        }));
        registry.registerExpression("%object% with [a] pitch [of] %object%", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            Object a1 = a.get(1).getSingle(ctx);
            return (!(a0 instanceof org.bukkit.Location loc) || !(a1 instanceof java.lang.Number n) ? null : new org.bukkit.Location(loc.getWorld(), loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), n.floatValue()));
        }));
        registry.registerExpression("%object% with [a] yaw [of] %object% and [a] pitch [of] %object%", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            Object a1 = a.get(1).getSingle(ctx);
            Object a2 = a.get(2).getSingle(ctx);
            return (!(a0 instanceof org.bukkit.Location loc) || !(a1 instanceof java.lang.Number ny) || !(a2 instanceof java.lang.Number np) ? null : new org.bukkit.Location(loc.getWorld(), loc.getX(), loc.getY(), loc.getZ(), ny.floatValue(), np.floatValue()));
        }));
        registry.registerExpression("[a] [new] vector [(from|at|to)] %object%,[ ]%object%(,[ ]| and )%object%", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            Object a1 = a.get(1).getSingle(ctx);
            Object a2 = a.get(2).getSingle(ctx);
            return (!(a0 instanceof java.lang.Number x) || !(a1 instanceof java.lang.Number y) || !(a2 instanceof java.lang.Number z) ? null : new org.bukkit.util.Vector(x.doubleValue(), y.doubleValue(), z.doubleValue()));
        }));
        registry.registerExpression("[the] angle between [[the] vectors] %object% and %object%", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            Object a1 = a.get(1).getSingle(ctx);
            return (!(a0 instanceof org.bukkit.util.Vector v0) || !(a1 instanceof org.bukkit.util.Vector v1) ? null : (v0.angle(v1) * (float) (180.0 / Math.PI)));
        }));
        registry.registerExpression("%object% percent of %object%", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            Object a1 = a.get(1).getSingle(ctx);
            return (!(a0 instanceof Number n0) || !(a1 instanceof Number n1) ? null : n1.doubleValue() * n0.doubleValue() / 100.0);
        }));
        registry.registerExpression("%object% (otherwise|?) %object%", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            Object a1 = a.get(1).getSingle(ctx);
            return (a0 != null ? a0 : a1);
        }));
        registry.registerExpression("%object% cross %object%", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            Object a1 = a.get(1).getSingle(ctx);
            return (!(a0 instanceof org.bukkit.util.Vector v0) || !(a1 instanceof org.bukkit.util.Vector v1) ? null : v0.clone().crossProduct(v1));
        }));
        registry.registerExpression("[the] (location|position) [at] [\\(][x[ ][=[ ]]]%object%, [y[ ][=[ ]]]%object%, [and] [z[ ][=[ ]]]%object%[\\)] [[(in|of) [[the] world]] %object%]", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            Object a1 = a.get(1).getSingle(ctx);
            Object a2 = a.get(2).getSingle(ctx);
            Object a3 = a.get(3).getSingle(ctx);
            return (!(a0 instanceof java.lang.Number nx) || !(a1 instanceof java.lang.Number ny) || !(a2 instanceof java.lang.Number nz) || !(a3 instanceof org.bukkit.World w) ? null : new org.bukkit.Location(w, nx.doubleValue(), ny.doubleValue(), nz.doubleValue()));
        }));
        registry.registerExpression("[a] [new] chest inventory (named|with name) %object% [with %object% row[s]]", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            Object a1 = a.get(1).getSingle(ctx);
            return (org.bukkit.Bukkit.createInventory(null, java.lang.Math.max(0, java.lang.Math.min(54, (a1 instanceof java.lang.Number rn ? rn.intValue()*9 : 27))), (a0 instanceof net.kyori.adventure.text.Component nm ? nm : org.bukkit.event.inventory.InventoryType.CHEST.defaultTitle())));
        }));
        registry.registerExpression("[a] [new] chest inventory with %object% row[s] [(named|with name) %object%]", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            Object a1 = a.get(1).getSingle(ctx);
            return (org.bukkit.Bukkit.createInventory(null, java.lang.Math.max(0, java.lang.Math.min(54, (a0 instanceof java.lang.Number rn ? rn.intValue()*9 : 27))), (a1 instanceof net.kyori.adventure.text.Component nm ? nm : org.bukkit.event.inventory.InventoryType.CHEST.defaultTitle())));
        }));
        registry.registerExpression("%object% to location in %object%", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            Object a1 = a.get(1).getSingle(ctx);
            return (!(a0 instanceof org.bukkit.util.Vector v) || !(a1 instanceof org.bukkit.World w) ? null : v.toLocation(w));
        }));
        registry.registerExpression("location from %object% in %object%", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            Object a1 = a.get(1).getSingle(ctx);
            return (!(a0 instanceof org.bukkit.util.Vector v) || !(a1 instanceof org.bukkit.World w) ? null : v.toLocation(w));
        }));
        registry.registerExpression("location of %object% in %object%", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            Object a1 = a.get(1).getSingle(ctx);
            return (!(a0 instanceof org.bukkit.util.Vector v) || !(a1 instanceof org.bukkit.World w) ? null : v.toLocation(w));
        }));
        registry.registerExpression("%object% [to location] in %object% with yaw %object% and pitch %object%", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            Object a1 = a.get(1).getSingle(ctx);
            Object a2 = a.get(2).getSingle(ctx);
            Object a3 = a.get(3).getSingle(ctx);
            return (!(a0 instanceof org.bukkit.util.Vector v) || !(a1 instanceof org.bukkit.World w) || !(a2 instanceof java.lang.Number y) || !(a3 instanceof java.lang.Number p) ? null : v.toLocation(w, y.floatValue(), p.floatValue()));
        }));
        registry.registerExpression("location from %object% in %object% with yaw %object% and pitch %object%", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            Object a1 = a.get(1).getSingle(ctx);
            Object a2 = a.get(2).getSingle(ctx);
            Object a3 = a.get(3).getSingle(ctx);
            return (!(a0 instanceof org.bukkit.util.Vector v) || !(a1 instanceof org.bukkit.World w) || !(a2 instanceof java.lang.Number y) || !(a3 instanceof java.lang.Number p) ? null : v.toLocation(w, y.floatValue(), p.floatValue()));
        }));
        registry.registerExpression("location of %object% in %object% with yaw %object% and pitch %object%", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            Object a1 = a.get(1).getSingle(ctx);
            Object a2 = a.get(2).getSingle(ctx);
            Object a3 = a.get(3).getSingle(ctx);
            return (!(a0 instanceof org.bukkit.util.Vector v) || !(a1 instanceof org.bukkit.World w) || !(a2 instanceof java.lang.Number y) || !(a3 instanceof java.lang.Number p) ? null : v.toLocation(w, y.floatValue(), p.floatValue()));
        }));
        // event-restricted syntax
        registry.registerExpression("(old|unequipped) armo[u]r item", Object.class, a -> new ComputedExpression(ctx -> {
            org.bukkit.event.Event ev = ctx.event().orElse(null);
            return ev == null ? null : (ev instanceof com.destroystokyo.paper.event.player.PlayerArmorChangeEvent _e ? _e.getOldItem() : null);
        }));
        registry.registerExpression("[the] breeding mother", Object.class, a -> new ComputedExpression(ctx -> {
            org.bukkit.event.Event ev = ctx.event().orElse(null);
            return ev == null ? null : (ev instanceof org.bukkit.event.entity.EntityBreedEvent _e ? _e.getMother() : null);
        }));
        registry.registerExpression("[the] breeding father", Object.class, a -> new ComputedExpression(ctx -> {
            org.bukkit.event.Event ev = ctx.event().orElse(null);
            return ev == null ? null : (ev instanceof org.bukkit.event.entity.EntityBreedEvent _e ? _e.getFather() : null);
        }));
        registry.registerExpression("[the] [bred] (offspring|child)", Object.class, a -> new ComputedExpression(ctx -> {
            org.bukkit.event.Event ev = ctx.event().orElse(null);
            return ev == null ? null : (ev instanceof org.bukkit.event.entity.EntityBreedEvent _e ? _e.getEntity() : null);
        }));
        registry.registerExpression("[the] breeder", Object.class, a -> new ComputedExpression(ctx -> {
            org.bukkit.event.Event ev = ctx.event().orElse(null);
            return ev == null ? null : (ev instanceof org.bukkit.event.entity.EntityBreedEvent _e ? _e.getBreeder() : null);
        }));
        registry.registerExpression("[all [of]] [the] enchant[ment] offers", Object.class, a -> new ComputedListExpression(ctx -> {
            org.bukkit.event.Event ev = ctx.event().orElse(null);
            if (ev == null) return new Object[0];
            Object _c = (ev instanceof org.bukkit.event.enchantment.PrepareItemEnchantEvent _e ? _e.getOffers() : null);
            if (_c == null) return new Object[0];
            if (_c instanceof java.util.Collection<?> _col) return _col.toArray();
            if (_c instanceof Object[] _arr) return _arr;
            return new Object[]{
                _c
            };
        }));
        registry.registerExpression("[the] (spawned|dropped|) [e]xp[erience] [orb[s]]", Object.class, a -> new ComputedExpression(ctx -> {
            org.bukkit.event.Event ev = ctx.event().orElse(null);
            return ev == null ? null : ((ev instanceof org.bukkit.event.block.BlockBreakEvent _bb) ? (Object) Integer.valueOf(_bb.getExpToDrop()) : (ev instanceof org.bukkit.event.player.PlayerExpChangeEvent _ec) ? (Object) Integer.valueOf(_ec.getAmount()) : (ev instanceof org.bukkit.event.entity.EntityBreedEvent _eb) ? (Object) Integer.valueOf(_eb.getExperience()) : (ev instanceof org.bukkit.event.player.PlayerFishEvent _pf) ? (Object) Integer.valueOf(_pf.getExpToDrop()) : null);
        }));
        registry.registerExpression("(experience|[e]xp) cooldown change (reason|cause|type)", Object.class, a -> new ComputedExpression(ctx -> {
            org.bukkit.event.Event ev = ctx.event().orElse(null);
            return ev == null ? null : ((ev instanceof org.bukkit.event.player.PlayerExpCooldownChangeEvent _e) ? _e.getReason() : null);
        }));
        registry.registerExpression("[all] [the] fertilized blocks", Object.class, a -> new ComputedListExpression(ctx -> {
            org.bukkit.event.Event ev = ctx.event().orElse(null);
            if (ev == null) return new Object[0];
            Object _c = ((ev instanceof org.bukkit.event.block.BlockFertilizeEvent _e) ? _e.getBlocks() : null);
            if (_c == null) return new Object[0];
            if (_c instanceof java.util.Collection<?> _col) return _col.toArray();
            if (_c instanceof Object[] _arr) return _arr;
            return new Object[]{
                _c
            };
        }));
        registry.registerExpression("min[imum] fish[ing] approach[ing] angle", Object.class, a -> new ComputedExpression(ctx -> {
            org.bukkit.event.Event ev = ctx.event().orElse(null);
            return ev == null ? null : ((ev instanceof org.bukkit.event.player.PlayerFishEvent _e) ? Float.valueOf(_e.getHook().getMinLureAngle()) : null);
        }));
        registry.registerExpression("max[imum] fish[ing] approach[ing] angle", Object.class, a -> new ComputedExpression(ctx -> {
            org.bukkit.event.Event ev = ctx.event().orElse(null);
            return ev == null ? null : ((ev instanceof org.bukkit.event.player.PlayerFishEvent _e) ? Float.valueOf(_e.getHook().getMaxLureAngle()) : null);
        }));
        registry.registerExpression("[the] hanging entity", Object.class, a -> new ComputedExpression(ctx -> {
            org.bukkit.event.Event ev = ctx.event().orElse(null);
            return ev == null ? null : (ev instanceof org.bukkit.event.hanging.HangingEvent _e ? _e.getEntity() : null);
        }));
        registry.registerExpression("item", Object.class, a -> new ComputedExpression(ctx -> {
            org.bukkit.event.Event ev = ctx.event().orElse(null);
            return ev == null ? null : (ev instanceof org.bukkit.event.block.BlockDispenseEvent _d ? _d.getItem() : ev instanceof org.bukkit.event.player.PlayerItemConsumeEvent _c ? _c.getItem() : ev instanceof org.bukkit.event.player.PlayerItemBreakEvent _b ? _b.getBrokenItem() : ev instanceof org.bukkit.event.entity.EntityPickupItemEvent _p ? _p.getItem().getItemStack() : ev instanceof org.bukkit.event.player.PlayerDropItemEvent _dr ? _dr.getItemDrop().getItemStack() : null);
        }));
        registry.registerExpression("[the] [event-](location|position)", Object.class, a -> new ComputedExpression(ctx -> {
            org.bukkit.event.Event ev = ctx.event().orElse(null);
            return ev == null ? null : (ev instanceof org.bukkit.event.entity.EntityEvent _ee && _ee.getEntity() != null ? _ee.getEntity().getLocation() : (ev instanceof org.bukkit.event.block.BlockEvent _be && _be.getBlock() != null ? _be.getBlock().getLocation().add(0.5, 0.5, 0.5) : null));
        }));
        registry.registerExpression("loot[ ]context", Object.class, a -> new ComputedExpression(ctx -> {
            org.bukkit.event.Event ev = ctx.event().orElse(null);
            return ev == null ? null : (ev instanceof org.bukkit.event.world.LootGenerateEvent _e ? _e.getLootContext() : null);
        }));
        registry.registerExpression("[the] picked block", Object.class, a -> new ComputedExpression(ctx -> {
            org.bukkit.event.Event ev = ctx.event().orElse(null);
            return ev == null ? null : (ev instanceof io.papermc.paper.event.player.PlayerPickBlockEvent _e ? _e.getBlock() : null);
        }));
        registry.registerExpression("[the] picked entity", Object.class, a -> new ComputedExpression(ctx -> {
            org.bukkit.event.Event ev = ctx.event().orElse(null);
            return ev == null ? null : (ev instanceof io.papermc.paper.event.player.PlayerPickEntityEvent _e ? _e.getEntity() : null);
        }));
        registry.registerExpression("(quit|disconnect) (cause|reason)", Object.class, a -> new ComputedExpression(ctx -> {
            org.bukkit.event.Event ev = ctx.event().orElse(null);
            return ev == null ? null : (ev instanceof org.bukkit.event.player.PlayerQuitEvent _e ? _e.getReason() : null);
        }));
        // final permissive expression recovery
        registry.registerExpression("%object%'s [elapsed] (item|tool) us[ag]e time", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            return (a0 instanceof org.bukkit.entity.LivingEntity ? co.xenastudios.neoskript.core.runtime.Timespan.ofTicks(((org.bukkit.entity.LivingEntity) a0).getHandRaisedTime()) : null);
        }));
        registry.registerExpression("%object%'s remaining (item|tool) us[ag]e time", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            return (a0 instanceof org.bukkit.entity.LivingEntity ? co.xenastudios.neoskript.core.runtime.Timespan.ofTicks(((org.bukkit.entity.LivingEntity) a0).getItemUseRemainingTime()) : null);
        }));
        registry.registerExpression("[the] (amount|number) of %object% (in|of) %object%", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            Object a1 = a.get(1).getSingle(ctx);
            return ((a0 instanceof org.bukkit.inventory.ItemStack && a1 instanceof org.bukkit.inventory.Inventory) ? Long.valueOf(java.util.Arrays.stream(((org.bukkit.inventory.Inventory) a1).getContents()).filter(java.util.Objects::nonNull).filter(it -> it.isSimilar((org.bukkit.inventory.ItemStack) a0)).mapToLong(org.bukkit.inventory.ItemStack::getAmount).sum()) : null);
        }));
        registry.registerExpression("[an] eternity", Object.class, a -> new ComputedExpression(ctx -> {
            return (co.xenastudios.neoskript.core.runtime.Timespan.ofTicks(Long.MAX_VALUE));
        }));
        registry.registerExpression("forever", Object.class, a -> new ComputedExpression(ctx -> {
            return (co.xenastudios.neoskript.core.runtime.Timespan.ofTicks(Long.MAX_VALUE));
        }));
        registry.registerExpression("[an] (indefinite|infinite) (duration|timespan)", Object.class, a -> new ComputedExpression(ctx -> {
            return (co.xenastudios.neoskript.core.runtime.Timespan.ofTicks(Long.MAX_VALUE));
        }));
        registry.registerExpression("[a] %object% colo[u]red %object%", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            Object a1 = a.get(1).getSingle(ctx);
            return ((a0 instanceof org.bukkit.block.banner.PatternType && a1 instanceof org.bukkit.DyeColor) ? new org.bukkit.block.banner.Pattern((org.bukkit.DyeColor) a1, (org.bukkit.block.banner.PatternType) a0) : null);
        }));
        registry.registerExpression("primary [beacon] effect of %object%", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            return ((a0 instanceof org.bukkit.block.Block && ((org.bukkit.block.Block) a0).getState() instanceof org.bukkit.block.Beacon && ((org.bukkit.block.Beacon) ((org.bukkit.block.Block) a0).getState()).getPrimaryEffect() != null) ? ((org.bukkit.block.Beacon) ((org.bukkit.block.Block) a0).getState()).getPrimaryEffect().getType() : null);
        }));
        registry.registerExpression("secondary [beacon] effect of %object%", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            return ((a0 instanceof org.bukkit.block.Block && ((org.bukkit.block.Block) a0).getState() instanceof org.bukkit.block.Beacon && ((org.bukkit.block.Beacon) ((org.bukkit.block.Block) a0).getState()).getSecondaryEffect() != null) ? ((org.bukkit.block.Beacon) ((org.bukkit.block.Block) a0).getState()).getSecondaryEffect().getType() : null);
        }));
        registry.registerExpression("[the] main command [label|name] [of [[the] command[s] %object%]]", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            return (((java.util.function.Function<org.bukkit.command.Command,String>)(c -> c == null ? null : c.getName())).apply(a0 instanceof String ? org.bukkit.Bukkit.getServer().getCommandMap().getCommand((String) a0) : null));
        }));
        registry.registerExpression("[the] description [of [[the] command[s] %object%]]", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            return (((java.util.function.Function<org.bukkit.command.Command,String>)(c -> c == null ? null : c.getDescription())).apply(a0 instanceof String ? org.bukkit.Bukkit.getServer().getCommandMap().getCommand((String) a0) : null));
        }));
        registry.registerExpression("[the] label [of [[the] command[s] %object%]]", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            return (((java.util.function.Function<org.bukkit.command.Command,String>)(c -> c == null ? null : c.getLabel())).apply(a0 instanceof String ? org.bukkit.Bukkit.getServer().getCommandMap().getCommand((String) a0) : null));
        }));
        registry.registerExpression("[the] usage [of [[the] command[s] %object%]]", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            return (((java.util.function.Function<org.bukkit.command.Command,String>)(c -> c == null ? null : c.getUsage())).apply(a0 instanceof String ? org.bukkit.Bukkit.getServer().getCommandMap().getCommand((String) a0) : null));
        }));
        registry.registerExpression("[(all|the|all [of] the)] aliases [of [[the] command[s] %object%]]", Object.class, a -> new ComputedListExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            Object _c = (((java.util.function.Function<org.bukkit.command.Command,java.util.List<String>>)(c -> c == null ? null : c.getAliases())).apply(a0 instanceof String ? org.bukkit.Bukkit.getServer().getCommandMap().getCommand((String) a0) : null));
            if (_c == null) return new Object[0];
            if (_c instanceof java.util.Collection<?> _col) return _col.toArray();
            if (_c instanceof Object[] _arr) return _arr;
            return new Object[]{
                _c
            };
        }));
        registry.registerExpression("[the] permission [of [[the] command[s] %object%]]", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            return (((java.util.function.Function<org.bukkit.command.Command,String>)(c -> c == null ? null : c.getPermission())).apply(a0 instanceof String ? org.bukkit.Bukkit.getServer().getCommandMap().getCommand((String) a0) : null));
        }));
        registry.registerExpression("[the] permission message [of [[the] command[s] %object%]]", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            return (((java.util.function.Function<org.bukkit.command.Command,String>)(c -> c == null ? null : c.getPermissionMessage())).apply(a0 instanceof String ? org.bukkit.Bukkit.getServer().getCommandMap().getCommand((String) a0) : null));
        }));
        registry.registerExpression("[the] plugin [owner] [of [[the] command[s] %object%]]", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            return (((java.util.function.Function<org.bukkit.command.Command,String>)(c -> c == null ? null : c instanceof org.bukkit.command.PluginCommand ? ((org.bukkit.command.PluginCommand) c).getPlugin().getName() : c instanceof org.bukkit.command.defaults.BukkitCommand ? "Bukkit" : c.getClass().getPackage().getName().startsWith("org.spigot") ? "Spigot" : c.getClass().getPackage().getName().startsWith("com.destroystokyo.paper") ? "Paper" : "Unknown")).apply(a0 instanceof String ? org.bukkit.Bukkit.getServer().getCommandMap().getCommand((String) a0) : null));
        }));
        registry.registerExpression("%object% with (damage|data) [value] %object%", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            Object a1 = a.get(1).getSingle(ctx);
            return ((a0 instanceof org.bukkit.inventory.ItemStack && a1 instanceof Number) ? java.util.stream.Stream.of(((org.bukkit.inventory.ItemStack) a0).clone()).peek(_i -> _i.setDurability((short) ((Number) a1).intValue())).findFirst().orElse(null) : null);
        }));
        registry.registerExpression("%object% damaged by %object%", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            Object a1 = a.get(1).getSingle(ctx);
            return ((a0 instanceof org.bukkit.inventory.ItemStack && a1 instanceof Number) ? java.util.stream.Stream.of(((org.bukkit.inventory.ItemStack) a0).clone()).peek(_i -> _i.setDurability((short) ((Number) a1).intValue())).findFirst().orElse(null) : null);
        }));
        registry.registerExpression("interpolation delay[s] of %object%", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            return (a0 instanceof org.bukkit.entity.Display ? co.xenastudios.neoskript.core.runtime.Timespan.ofTicks((long) ((org.bukkit.entity.Display) a0).getInterpolationDelay()) : null);
        }));
        registry.registerExpression("interpolation duration[s] of %object%", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            return (a0 instanceof org.bukkit.entity.Display ? co.xenastudios.neoskript.core.runtime.Timespan.ofTicks((long) ((org.bukkit.entity.Display) a0).getInterpolationDuration()) : null);
        }));
        registry.registerExpression("exact item[s] of %object%", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            return (!(a0 instanceof org.bukkit.block.Block) || !((org.bukkit.block.Block) a0).getType().isItem() ? null : java.util.stream.Stream.of(new org.bukkit.inventory.ItemStack(((org.bukkit.block.Block) a0).getType())).peek(s -> s.editMeta(org.bukkit.inventory.meta.BlockStateMeta.class, m -> m.setBlockState(((org.bukkit.block.Block) a0).getState()))).findFirst().get());
        }));
        registry.registerExpression("%object% [firework [effect]] colo[u]red %object%", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            Object a1 = a.get(1).getSingle(ctx);
            return (!(a0 instanceof org.bukkit.FireworkEffect.Type) || !((a1 instanceof java.util.Collection && !((java.util.Collection<?>) a1).isEmpty()) || (a1 instanceof Object[] && ((Object[]) a1).length > 0) || a1 instanceof org.bukkit.Color) ? null : org.bukkit.FireworkEffect.builder().with((org.bukkit.FireworkEffect.Type) a0).withColor((java.lang.Iterable<?>) (a1 instanceof java.util.Collection ? (java.util.Collection<?>) a1 : a1 instanceof Object[] ? java.util.Arrays.asList((Object[]) a1) : java.util.List.of(a1))).flicker(false).trail(false).build());
        }));
        registry.registerExpression("flickering %object% [firework [effect]] colo[u]red %object%", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            Object a1 = a.get(1).getSingle(ctx);
            return (!(a0 instanceof org.bukkit.FireworkEffect.Type) || !((a1 instanceof java.util.Collection && !((java.util.Collection<?>) a1).isEmpty()) || (a1 instanceof Object[] && ((Object[]) a1).length > 0) || a1 instanceof org.bukkit.Color) ? null : org.bukkit.FireworkEffect.builder().with((org.bukkit.FireworkEffect.Type) a0).withColor((java.lang.Iterable<?>) (a1 instanceof java.util.Collection ? (java.util.Collection<?>) a1 : a1 instanceof Object[] ? java.util.Arrays.asList((Object[]) a1) : java.util.List.of(a1))).flicker(true).trail(false).build());
        }));
        registry.registerExpression("trailing %object% [firework [effect]] colo[u]red %object%", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            Object a1 = a.get(1).getSingle(ctx);
            return (!(a0 instanceof org.bukkit.FireworkEffect.Type) || !((a1 instanceof java.util.Collection && !((java.util.Collection<?>) a1).isEmpty()) || (a1 instanceof Object[] && ((Object[]) a1).length > 0) || a1 instanceof org.bukkit.Color) ? null : org.bukkit.FireworkEffect.builder().with((org.bukkit.FireworkEffect.Type) a0).withColor((java.lang.Iterable<?>) (a1 instanceof java.util.Collection ? (java.util.Collection<?>) a1 : a1 instanceof Object[] ? java.util.Arrays.asList((Object[]) a1) : java.util.List.of(a1))).flicker(false).trail(true).build());
        }));
        registry.registerExpression("flickering trailing %object% [firework [effect]] colo[u]red %object%", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            Object a1 = a.get(1).getSingle(ctx);
            return (!(a0 instanceof org.bukkit.FireworkEffect.Type) || !((a1 instanceof java.util.Collection && !((java.util.Collection<?>) a1).isEmpty()) || (a1 instanceof Object[] && ((Object[]) a1).length > 0) || a1 instanceof org.bukkit.Color) ? null : org.bukkit.FireworkEffect.builder().with((org.bukkit.FireworkEffect.Type) a0).withColor((java.lang.Iterable<?>) (a1 instanceof java.util.Collection ? (java.util.Collection<?>) a1 : a1 instanceof Object[] ? java.util.Arrays.asList((Object[]) a1) : java.util.List.of(a1))).flicker(true).trail(true).build());
        }));
        registry.registerExpression("trailing flickering %object% [firework [effect]] colo[u]red %object%", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            Object a1 = a.get(1).getSingle(ctx);
            return (!(a0 instanceof org.bukkit.FireworkEffect.Type) || !((a1 instanceof java.util.Collection && !((java.util.Collection<?>) a1).isEmpty()) || (a1 instanceof Object[] && ((Object[]) a1).length > 0) || a1 instanceof org.bukkit.Color) ? null : org.bukkit.FireworkEffect.builder().with((org.bukkit.FireworkEffect.Type) a0).withColor((java.lang.Iterable<?>) (a1 instanceof java.util.Collection ? (java.util.Collection<?>) a1 : a1 instanceof Object[] ? java.util.Arrays.asList((Object[]) a1) : java.util.List.of(a1))).flicker(true).trail(true).build());
        }));
        registry.registerExpression("%object% [firework [effect]] colo[u]red %object% fad(e|ing) [to] %object%", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            Object a1 = a.get(1).getSingle(ctx);
            Object a2 = a.get(2).getSingle(ctx);
            return (!(a0 instanceof org.bukkit.FireworkEffect.Type) || !((a1 instanceof java.util.Collection && !((java.util.Collection<?>) a1).isEmpty()) || (a1 instanceof Object[] && ((Object[]) a1).length > 0) || a1 instanceof org.bukkit.Color) || !((a2 instanceof java.util.Collection && !((java.util.Collection<?>) a2).isEmpty()) || (a2 instanceof Object[] && ((Object[]) a2).length > 0) || a2 instanceof org.bukkit.Color) ? null : org.bukkit.FireworkEffect.builder().with((org.bukkit.FireworkEffect.Type) a0).withColor((java.lang.Iterable<?>) (a1 instanceof java.util.Collection ? (java.util.Collection<?>) a1 : a1 instanceof Object[] ? java.util.Arrays.asList((Object[]) a1) : java.util.List.of(a1))).withFade((java.lang.Iterable<?>) (a2 instanceof java.util.Collection ? (java.util.Collection<?>) a2 : a2 instanceof Object[] ? java.util.Arrays.asList((Object[]) a2) : java.util.List.of(a2))).flicker(false).trail(false).build());
        }));
        registry.registerExpression("flickering %object% [firework [effect]] colo[u]red %object% fad(e|ing) [to] %object%", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            Object a1 = a.get(1).getSingle(ctx);
            Object a2 = a.get(2).getSingle(ctx);
            return (!(a0 instanceof org.bukkit.FireworkEffect.Type) || !((a1 instanceof java.util.Collection && !((java.util.Collection<?>) a1).isEmpty()) || (a1 instanceof Object[] && ((Object[]) a1).length > 0) || a1 instanceof org.bukkit.Color) || !((a2 instanceof java.util.Collection && !((java.util.Collection<?>) a2).isEmpty()) || (a2 instanceof Object[] && ((Object[]) a2).length > 0) || a2 instanceof org.bukkit.Color) ? null : org.bukkit.FireworkEffect.builder().with((org.bukkit.FireworkEffect.Type) a0).withColor((java.lang.Iterable<?>) (a1 instanceof java.util.Collection ? (java.util.Collection<?>) a1 : a1 instanceof Object[] ? java.util.Arrays.asList((Object[]) a1) : java.util.List.of(a1))).withFade((java.lang.Iterable<?>) (a2 instanceof java.util.Collection ? (java.util.Collection<?>) a2 : a2 instanceof Object[] ? java.util.Arrays.asList((Object[]) a2) : java.util.List.of(a2))).flicker(true).trail(false).build());
        }));
        registry.registerExpression("trailing %object% [firework [effect]] colo[u]red %object% fad(e|ing) [to] %object%", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            Object a1 = a.get(1).getSingle(ctx);
            Object a2 = a.get(2).getSingle(ctx);
            return (!(a0 instanceof org.bukkit.FireworkEffect.Type) || !((a1 instanceof java.util.Collection && !((java.util.Collection<?>) a1).isEmpty()) || (a1 instanceof Object[] && ((Object[]) a1).length > 0) || a1 instanceof org.bukkit.Color) || !((a2 instanceof java.util.Collection && !((java.util.Collection<?>) a2).isEmpty()) || (a2 instanceof Object[] && ((Object[]) a2).length > 0) || a2 instanceof org.bukkit.Color) ? null : org.bukkit.FireworkEffect.builder().with((org.bukkit.FireworkEffect.Type) a0).withColor((java.lang.Iterable<?>) (a1 instanceof java.util.Collection ? (java.util.Collection<?>) a1 : a1 instanceof Object[] ? java.util.Arrays.asList((Object[]) a1) : java.util.List.of(a1))).withFade((java.lang.Iterable<?>) (a2 instanceof java.util.Collection ? (java.util.Collection<?>) a2 : a2 instanceof Object[] ? java.util.Arrays.asList((Object[]) a2) : java.util.List.of(a2))).flicker(false).trail(true).build());
        }));
        registry.registerExpression("flickering trailing %object% [firework [effect]] colo[u]red %object% fad(e|ing) [to] %object%", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            Object a1 = a.get(1).getSingle(ctx);
            Object a2 = a.get(2).getSingle(ctx);
            return (!(a0 instanceof org.bukkit.FireworkEffect.Type) || !((a1 instanceof java.util.Collection && !((java.util.Collection<?>) a1).isEmpty()) || (a1 instanceof Object[] && ((Object[]) a1).length > 0) || a1 instanceof org.bukkit.Color) || !((a2 instanceof java.util.Collection && !((java.util.Collection<?>) a2).isEmpty()) || (a2 instanceof Object[] && ((Object[]) a2).length > 0) || a2 instanceof org.bukkit.Color) ? null : org.bukkit.FireworkEffect.builder().with((org.bukkit.FireworkEffect.Type) a0).withColor((java.lang.Iterable<?>) (a1 instanceof java.util.Collection ? (java.util.Collection<?>) a1 : a1 instanceof Object[] ? java.util.Arrays.asList((Object[]) a1) : java.util.List.of(a1))).withFade((java.lang.Iterable<?>) (a2 instanceof java.util.Collection ? (java.util.Collection<?>) a2 : a2 instanceof Object[] ? java.util.Arrays.asList((Object[]) a2) : java.util.List.of(a2))).flicker(true).trail(true).build());
        }));
        registry.registerExpression("trailing flickering %object% [firework [effect]] colo[u]red %object% fad(e|ing) [to] %object%", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            Object a1 = a.get(1).getSingle(ctx);
            Object a2 = a.get(2).getSingle(ctx);
            return (!(a0 instanceof org.bukkit.FireworkEffect.Type) || !((a1 instanceof java.util.Collection && !((java.util.Collection<?>) a1).isEmpty()) || (a1 instanceof Object[] && ((Object[]) a1).length > 0) || a1 instanceof org.bukkit.Color) || !((a2 instanceof java.util.Collection && !((java.util.Collection<?>) a2).isEmpty()) || (a2 instanceof Object[] && ((Object[]) a2).length > 0) || a2 instanceof org.bukkit.Color) ? null : org.bukkit.FireworkEffect.builder().with((org.bukkit.FireworkEffect.Type) a0).withColor((java.lang.Iterable<?>) (a1 instanceof java.util.Collection ? (java.util.Collection<?>) a1 : a1 instanceof Object[] ? java.util.Arrays.asList((Object[]) a1) : java.util.List.of(a1))).withFade((java.lang.Iterable<?>) (a2 instanceof java.util.Collection ? (java.util.Collection<?>) a2 : a2 instanceof Object[] ? java.util.Arrays.asList((Object[]) a2) : java.util.List.of(a2))).flicker(true).trail(true).build());
        }));
        registry.registerExpression("%object% formatted [human-readable]", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            return (a0 instanceof java.util.Date ? new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss z").format((java.util.Date) a0) : null);
        }));
        registry.registerExpression("[human-readable] formatted %object%", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            return (a0 instanceof java.util.Date ? new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss z").format((java.util.Date) a0) : null);
        }));
        registry.registerExpression("%object% formatted [human-readable] (with|as) %object%", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            Object a1 = a.get(1).getSingle(ctx);
            return ((a0 instanceof java.util.Date && a1 instanceof String) ? new java.text.SimpleDateFormat((String) a1).format((java.util.Date) a0) : null);
        }));
        registry.registerExpression("[human-readable] formatted %object% (with|as) %object%", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            Object a1 = a.get(1).getSingle(ctx);
            return ((a0 instanceof java.util.Date && a1 instanceof String) ? new java.text.SimpleDateFormat((String) a1).format((java.util.Date) a0) : null);
        }));
        registry.registerExpression("[the] [furnace] cook[ing] time [of %object%]", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            return (a0 instanceof org.bukkit.block.Block b && b.getState() instanceof org.bukkit.block.Furnace f ? co.xenastudios.neoskript.core.runtime.Timespan.ofTicks(f.getCookTime()) : null);
        }));
        registry.registerExpression("%object%'[s]cook[ing] time", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            return (a0 instanceof org.bukkit.block.Block b && b.getState() instanceof org.bukkit.block.Furnace f ? co.xenastudios.neoskript.core.runtime.Timespan.ofTicks(f.getCookTime()) : null);
        }));
        registry.registerExpression("[the] [furnace] total cook[ing] time [of %object%]", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            return (a0 instanceof org.bukkit.block.Block b && b.getState() instanceof org.bukkit.block.Furnace f ? co.xenastudios.neoskript.core.runtime.Timespan.ofTicks(f.getCookTimeTotal()) : null);
        }));
        registry.registerExpression("%object%'[s]total cook[ing] time", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            return (a0 instanceof org.bukkit.block.Block b && b.getState() instanceof org.bukkit.block.Furnace f ? co.xenastudios.neoskript.core.runtime.Timespan.ofTicks(f.getCookTimeTotal()) : null);
        }));
        registry.registerExpression("[the] [furnace] fuel burn[ing] time [of %object%]", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            return (a0 instanceof org.bukkit.block.Block b && b.getState() instanceof org.bukkit.block.Furnace f ? co.xenastudios.neoskript.core.runtime.Timespan.ofTicks(f.getBurnTime()) : null);
        }));
        registry.registerExpression("%object%'[s]fuel burn[ing] time", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            return (a0 instanceof org.bukkit.block.Block b && b.getState() instanceof org.bukkit.block.Furnace f ? co.xenastudios.neoskript.core.runtime.Timespan.ofTicks(f.getBurnTime()) : null);
        }));
        registry.registerExpression("%object% with [a|the] lore %object%", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            Object a1 = a.get(1).getSingle(ctx);
            return ((a0 instanceof org.bukkit.inventory.ItemStack && a1 instanceof String) ? java.util.stream.Stream.of(((org.bukkit.inventory.ItemStack)a0).clone()).map(s -> s.editMeta(m -> m.setLore(java.util.Collections.singletonList((String)a1))) ? s : s).findFirst().orElse(null) : null);
        }));
        registry.registerExpression("%object% with [entire] tool[ ]tip[s]", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            return (a0 instanceof org.bukkit.inventory.ItemStack ? java.util.stream.Stream.of(((org.bukkit.inventory.ItemStack)a0).clone()).map(s -> s.editMeta(m -> m.setHideTooltip(false)) ? s : s).findFirst().orElse(null) : null);
        }));
        registry.registerExpression("%object% without [entire] tool[ ]tip[s]", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            return (a0 instanceof org.bukkit.inventory.ItemStack ? java.util.stream.Stream.of(((org.bukkit.inventory.ItemStack)a0).clone()).map(s -> s.editMeta(m -> m.setHideTooltip(true)) ? s : s).findFirst().orElse(null) : null);
        }));
        registry.registerExpression("%object% with additional tool[ ]tip[s]", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            return (a0 instanceof org.bukkit.inventory.ItemStack ? java.util.stream.Stream.of(((org.bukkit.inventory.ItemStack)a0).clone()).map(s -> s.editMeta(m -> m.removeItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ADDITIONAL_TOOLTIP)) ? s : s).findFirst().orElse(null) : null);
        }));
        registry.registerExpression("%object% without additional tool[ ]tip[s]", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            return (a0 instanceof org.bukkit.inventory.ItemStack ? java.util.stream.Stream.of(((org.bukkit.inventory.ItemStack)a0).clone()).map(s -> s.editMeta(m -> m.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ADDITIONAL_TOOLTIP)) ? s : s).findFirst().orElse(null) : null);
        }));
        registry.registerExpression("[all [[of] the]|the] block[[ ]type]s", Object.class, a -> new ComputedListExpression(ctx -> {
            Object _c = (java.util.Arrays.stream(org.bukkit.Material.values()).filter(m -> !m.isLegacy() && m.isBlock()).map(org.bukkit.inventory.ItemStack::new).collect(java.util.stream.Collectors.toList()));
            if (_c == null) return new Object[0];
            if (_c instanceof java.util.Collection<?> _col) return _col.toArray();
            if (_c instanceof Object[] _arr) return _arr;
            return new Object[]{
                _c
            };
        }));
        registry.registerExpression("every block[[ ]type]", Object.class, a -> new ComputedListExpression(ctx -> {
            Object _c = (java.util.Arrays.stream(org.bukkit.Material.values()).filter(m -> !m.isLegacy() && m.isBlock()).map(org.bukkit.inventory.ItemStack::new).collect(java.util.stream.Collectors.toList()));
            if (_c == null) return new Object[0];
            if (_c instanceof java.util.Collection<?> _col) return _col.toArray();
            if (_c instanceof Object[] _arr) return _arr;
            return new Object[]{
                _c
            };
        }));
        registry.registerExpression("[all [[of] the]|the|every] block[s] of type[s] %object%", Object.class, a -> new ComputedListExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            Object _c = (a0 instanceof org.bukkit.inventory.ItemStack ? (((org.bukkit.inventory.ItemStack)a0).getType().isBlock() ? java.util.List.of(((org.bukkit.inventory.ItemStack)a0).clone()) : java.util.List.of()) : null);
            if (_c == null) return new Object[0];
            if (_c instanceof java.util.Collection<?> _col) return _col.toArray();
            if (_c instanceof Object[] _arr) return _arr;
            return new Object[]{
                _c
            };
        }));
        registry.registerExpression("[all [[of] the]|the|every] item[s] of type[s] %object%", Object.class, a -> new ComputedListExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            Object _c = (a0 instanceof org.bukkit.inventory.ItemStack ? java.util.List.of(((org.bukkit.inventory.ItemStack)a0).clone()) : null);
            if (_c == null) return new Object[0];
            if (_c instanceof java.util.Collection<?> _col) return _col.toArray();
            if (_c instanceof Object[] _arr) return _arr;
            return new Object[]{
                _c
            };
        }));
        registry.registerExpression("[the] last attacker of %object%", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            return (!(a0 instanceof org.bukkit.entity.Entity ent) || !(ent.getLastDamageCause() instanceof org.bukkit.event.entity.EntityDamageByEntityEvent ev) ? null : (ev.getDamager() instanceof org.bukkit.entity.Projectile pr && pr.getShooter() instanceof org.bukkit.entity.Entity sh ? sh : ev.getDamager()));
        }));
        registry.registerExpression("[the] loot of %object% (with|using) [[loot] context] %object%", Object.class, a -> new ComputedListExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            Object a1 = a.get(1).getSingle(ctx);
            Object _c = ((a0 instanceof org.bukkit.loot.LootTable && a1 instanceof org.bukkit.loot.LootContext) ? ((org.bukkit.loot.LootTable) a0).populateLoot(java.util.concurrent.ThreadLocalRandom.current(), (org.bukkit.loot.LootContext) a1) : null);
            if (_c == null) return new Object[0];
            if (_c instanceof java.util.Collection<?> _col) return _col.toArray();
            if (_c instanceof Object[] _arr) return _arr;
            return new Object[]{
                _c
            };
        }));
        registry.registerExpression("[the] loot of %object%", Object.class, a -> new ComputedListExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            Object _c = ((a0 instanceof org.bukkit.loot.LootTable) ? ((org.bukkit.loot.LootTable) a0).populateLoot(java.util.concurrent.ThreadLocalRandom.current(), new org.bukkit.loot.LootContext.Builder(org.bukkit.Bukkit.getWorlds().get(0).getSpawnLocation()).build()) : null);
            if (_c == null) return new Object[0];
            if (_c instanceof java.util.Collection<?> _col) return _col.toArray();
            if (_c instanceof Object[] _arr) return _arr;
            return new Object[]{
                _c
            };
        }));
        registry.registerExpression("%object%'[s] loot (with|using) [[loot] context] %object%", Object.class, a -> new ComputedListExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            Object a1 = a.get(1).getSingle(ctx);
            Object _c = ((a0 instanceof org.bukkit.loot.LootTable && a1 instanceof org.bukkit.loot.LootContext) ? ((org.bukkit.loot.LootTable) a0).populateLoot(java.util.concurrent.ThreadLocalRandom.current(), (org.bukkit.loot.LootContext) a1) : null);
            if (_c == null) return new Object[0];
            if (_c instanceof java.util.Collection<?> _col) return _col.toArray();
            if (_c instanceof Object[] _arr) return _arr;
            return new Object[]{
                _c
            };
        }));
        registry.registerExpression("%object%'[s] loot", Object.class, a -> new ComputedListExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            Object _c = ((a0 instanceof org.bukkit.loot.LootTable) ? ((org.bukkit.loot.LootTable) a0).populateLoot(java.util.concurrent.ThreadLocalRandom.current(), new org.bukkit.loot.LootContext.Builder(org.bukkit.Bukkit.getWorlds().get(0).getSpawnLocation()).build()) : null);
            if (_c == null) return new Object[0];
            if (_c instanceof java.util.Collection<?> _col) return _col.toArray();
            if (_c instanceof Object[] _arr) return _arr;
            return new Object[]{
                _c
            };
        }));
        registry.registerExpression("[the] love[d] time of %object%", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            return (a0 instanceof org.bukkit.entity.Animals ? co.xenastudios.neoskript.core.runtime.Timespan.ofTicks(((org.bukkit.entity.Animals) a0).getLoveModeTicks()) : (a0 instanceof org.bukkit.entity.LivingEntity ? co.xenastudios.neoskript.core.runtime.Timespan.ofTicks(0L) : null));
        }));
        registry.registerExpression("%object%'[s] love[d] time", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            return (a0 instanceof org.bukkit.entity.Animals ? co.xenastudios.neoskript.core.runtime.Timespan.ofTicks(((org.bukkit.entity.Animals) a0).getLoveModeTicks()) : (a0 instanceof org.bukkit.entity.LivingEntity ? co.xenastudios.neoskript.core.runtime.Timespan.ofTicks(0L) : null));
        }));
        registry.registerExpression("metadata [(value|tag)[s]] %object% of %object%", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            Object a1 = a.get(1).getSingle(ctx);
            return ((a0 instanceof String && a1 instanceof org.bukkit.metadata.Metadatable && !((org.bukkit.metadata.Metadatable) a1).getMetadata((String) a0).isEmpty()) ? ((org.bukkit.metadata.Metadatable) a1).getMetadata((String) a0).get(((org.bukkit.metadata.Metadatable) a1).getMetadata((String) a0).size() - 1).value() : null);
        }));
        registry.registerExpression("%object%'[s] metadata [(value|tag)[s]] %object%", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            Object a1 = a.get(1).getSingle(ctx);
            return ((a1 instanceof String && a0 instanceof org.bukkit.metadata.Metadatable && !((org.bukkit.metadata.Metadatable) a0).getMetadata((String) a1).isEmpty()) ? ((org.bukkit.metadata.Metadatable) a0).getMetadata((String) a1).get(((org.bukkit.metadata.Metadatable) a0).getMetadata((String) a1).size() - 1).value() : null);
        }));
        registry.registerExpression("[a[n]] potion effect of %object% [[of tier] %object%] [for %object%]", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            Object a1 = a.get(1).getSingle(ctx);
            Object a2 = a.get(2).getSingle(ctx);
            return ((a0 instanceof org.bukkit.potion.PotionEffectType && a2 instanceof co.xenastudios.neoskript.core.runtime.Timespan && (a1 == null || a1 instanceof Number)) ? new org.bukkit.potion.PotionEffect((org.bukkit.potion.PotionEffectType) a0, (int) Math.max(0L, Math.min(((co.xenastudios.neoskript.core.runtime.Timespan) a2).ticks(), (long) Integer.MAX_VALUE)), a1 == null ? 0 : ((Number) a1).intValue() - 1, false, true, true) : null);
        }));
        registry.registerExpression("[a[n]] ambient potion effect of %object% [[of tier] %object%] [for %object%]", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            Object a1 = a.get(1).getSingle(ctx);
            Object a2 = a.get(2).getSingle(ctx);
            return ((a0 instanceof org.bukkit.potion.PotionEffectType && a2 instanceof co.xenastudios.neoskript.core.runtime.Timespan && (a1 == null || a1 instanceof Number)) ? new org.bukkit.potion.PotionEffect((org.bukkit.potion.PotionEffectType) a0, (int) Math.max(0L, Math.min(((co.xenastudios.neoskript.core.runtime.Timespan) a2).ticks(), (long) Integer.MAX_VALUE)), a1 == null ? 0 : ((Number) a1).intValue() - 1, true, true, true) : null);
        }));
        registry.registerExpression("[an] (infinite|permanent) potion effect of %object% [[of tier] %object%]", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            Object a1 = a.get(1).getSingle(ctx);
            return ((a0 instanceof org.bukkit.potion.PotionEffectType && (a1 == null || a1 instanceof Number)) ? new org.bukkit.potion.PotionEffect((org.bukkit.potion.PotionEffectType) a0, -1, a1 == null ? 0 : ((Number) a1).intValue() - 1, false, true, true) : null);
        }));
        registry.registerExpression("[an] (infinite|permanent) ambient potion effect of %object% [[of tier] %object%]", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            Object a1 = a.get(1).getSingle(ctx);
            return ((a0 instanceof org.bukkit.potion.PotionEffectType && (a1 == null || a1 instanceof Number)) ? new org.bukkit.potion.PotionEffect((org.bukkit.potion.PotionEffectType) a0, -1, a1 == null ? 0 : ((Number) a1).intValue() - 1, true, true, true) : null);
        }));
        registry.registerExpression("[an] (infinite|permanent) %object% [[of tier] %object%] [potion [effect]]", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            Object a1 = a.get(1).getSingle(ctx);
            return ((a0 instanceof org.bukkit.potion.PotionEffectType && (a1 == null || a1 instanceof Number)) ? new org.bukkit.potion.PotionEffect((org.bukkit.potion.PotionEffectType) a0, -1, a1 == null ? 0 : ((Number) a1).intValue() - 1, false, true, true) : null);
        }));
        registry.registerExpression("[an] (infinite|permanent) ambient %object% [[of tier] %object%] [potion [effect]]", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            Object a1 = a.get(1).getSingle(ctx);
            return ((a0 instanceof org.bukkit.potion.PotionEffectType && (a1 == null || a1 instanceof Number)) ? new org.bukkit.potion.PotionEffect((org.bukkit.potion.PotionEffectType) a0, -1, a1 == null ? 0 : ((Number) a1).intValue() - 1, true, true, true) : null);
        }));
        registry.registerExpression("[a] potion effect [of %object%] (from|using|based on) %object%", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            Object a1 = a.get(1).getSingle(ctx);
            return (!(a1 instanceof org.bukkit.potion.PotionEffect) ? null : (a0 == null ? (org.bukkit.potion.PotionEffect) a1 : (a0 instanceof org.bukkit.potion.PotionEffectType ? new org.bukkit.potion.PotionEffect((org.bukkit.potion.PotionEffectType) a0, ((org.bukkit.potion.PotionEffect) a1).getDuration(), ((org.bukkit.potion.PotionEffect) a1).getAmplifier(), ((org.bukkit.potion.PotionEffect) a1).isAmbient(), ((org.bukkit.potion.PotionEffect) a1).hasParticles(), ((org.bukkit.potion.PotionEffect) a1).hasIcon()) : null)));
        }));
        registry.registerExpression("[(all [[of] the]|the)] numbers (between|from) %object% (and|to) %object%", Object.class, a -> new ComputedListExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            Object a1 = a.get(1).getSingle(ctx);
            Object _c = ((a0 instanceof Number && a1 instanceof Number) ? java.util.stream.IntStream.range(0, (int) Math.floor(Math.abs(((Number) a1).doubleValue() - ((Number) a0).doubleValue()) + 1)).mapToObj(k -> (Number) Double.valueOf(Math.min(((Number) a0).doubleValue(), ((Number) a1).doubleValue()) + (((Number) a0).doubleValue() > ((Number) a1).doubleValue() ? (int) Math.floor(Math.abs(((Number) a1).doubleValue() - ((Number) a0).doubleValue()) + 1) - 1 - k : k))).toArray(Number[]::new) : null);
            if (_c == null) return new Object[0];
            if (_c instanceof java.util.Collection<?> _col) return _col.toArray();
            if (_c instanceof Object[] _arr) return _arr;
            return new Object[]{
                _c
            };
        }));
        registry.registerExpression("[(all [[of] the]|the)] integers (between|from) %object% (and|to) %object%", Object.class, a -> new ComputedListExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            Object a1 = a.get(1).getSingle(ctx);
            Object _c = ((a0 instanceof Number && a1 instanceof Number) ? java.util.stream.IntStream.range(0, (int) (Math.floor(Math.max(((Number) a0).doubleValue(), ((Number) a1).doubleValue())) - Math.ceil(Math.min(((Number) a0).doubleValue(), ((Number) a1).doubleValue())) + 1)).mapToObj(k -> (Number) Long.valueOf((long) Math.ceil(Math.min(((Number) a0).doubleValue(), ((Number) a1).doubleValue())) + (((Number) a0).doubleValue() > ((Number) a1).doubleValue() ? (int) (Math.floor(Math.max(((Number) a0).doubleValue(), ((Number) a1).doubleValue())) - Math.ceil(Math.min(((Number) a0).doubleValue(), ((Number) a1).doubleValue())) + 1) - 1 - k : k))).toArray(Number[]::new) : null);
            if (_c == null) return new Object[0];
            if (_c instanceof java.util.Collection<?> _col) return _col.toArray();
            if (_c instanceof Object[] _arr) return _arr;
            return new Object[]{
                _c
            };
        }));
        registry.registerExpression("[a[n]] (plain|unmodified) %object%", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            return (a0 instanceof org.bukkit.inventory.ItemStack ? new org.bukkit.inventory.ItemStack(((org.bukkit.inventory.ItemStack) a0).getType(), ((org.bukkit.inventory.ItemStack) a0).getAmount()) : null);
        }));
        registry.registerExpression("skull of %object%", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            return ((a0 instanceof org.bukkit.OfflinePlayer op) ? java.util.stream.Stream.of(new org.bukkit.inventory.ItemStack(org.bukkit.Material.PLAYER_HEAD)).peek(it -> it.editMeta(org.bukkit.inventory.meta.SkullMeta.class, m -> m.setOwningPlayer(op))).findFirst().orElse(null) : null);
        }));
        registry.registerExpression("active %object% [potion] effect of %object%", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            Object a1 = a.get(1).getSingle(ctx);
            return ((a1 instanceof org.bukkit.entity.LivingEntity le && a0 instanceof org.bukkit.potion.PotionEffectType pet) ? le.getPotionEffect(pet) : null);
        }));
        registry.registerExpression("(raw|minecraft|vanilla) name[s] of %object%", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            return ((a0 instanceof org.bukkit.inventory.ItemStack is) ? is.getType().getKey().toString() : null);
        }));
        registry.registerExpression("%object% rotated around [the] [global] x(-| )axis by %object%", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            Object a1 = a.get(1).getSingle(ctx);
            return (a1 instanceof Number __n && !Double.isInfinite(__n.doubleValue()*Math.PI/180d) && !Double.isNaN(__n.doubleValue()*Math.PI/180d) ? (a0 instanceof org.bukkit.util.Vector __v ? (Object) __v.clone().rotateAroundX(__n.doubleValue()*Math.PI/180d) : (a0 instanceof org.joml.Quaternionf __q ? (Object) __q.rotateLocalX((float)(__n.doubleValue()*Math.PI/180d), new org.joml.Quaternionf()) : null)) : null);
        }));
        registry.registerExpression("%object% rotated around [the] [global] y(-| )axis by %object%", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            Object a1 = a.get(1).getSingle(ctx);
            return (a1 instanceof Number __n && !Double.isInfinite(__n.doubleValue()*Math.PI/180d) && !Double.isNaN(__n.doubleValue()*Math.PI/180d) ? (a0 instanceof org.bukkit.util.Vector __v ? (Object) __v.clone().rotateAroundY(__n.doubleValue()*Math.PI/180d) : (a0 instanceof org.joml.Quaternionf __q ? (Object) __q.rotateLocalY((float)(__n.doubleValue()*Math.PI/180d), new org.joml.Quaternionf()) : null)) : null);
        }));
        registry.registerExpression("%object% rotated around [the] [global] z(-| )axis by %object%", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            Object a1 = a.get(1).getSingle(ctx);
            return (a1 instanceof Number __n && !Double.isInfinite(__n.doubleValue()*Math.PI/180d) && !Double.isNaN(__n.doubleValue()*Math.PI/180d) ? (a0 instanceof org.bukkit.util.Vector __v ? (Object) __v.clone().rotateAroundZ(__n.doubleValue()*Math.PI/180d) : (a0 instanceof org.joml.Quaternionf __q ? (Object) __q.rotateLocalZ((float)(__n.doubleValue()*Math.PI/180d), new org.joml.Quaternionf()) : null)) : null);
        }));
        registry.registerExpression("%object% rotated around [the|its|their] local x(-| )ax(i|e)s by %object%", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            Object a1 = a.get(1).getSingle(ctx);
            return (a0 instanceof org.joml.Quaternionf __q && a1 instanceof Number __n && !Double.isInfinite(__n.doubleValue()*Math.PI/180d) && !Double.isNaN(__n.doubleValue()*Math.PI/180d) ? __q.rotateX((float)(__n.doubleValue()*Math.PI/180d), new org.joml.Quaternionf()) : null);
        }));
        registry.registerExpression("%object% rotated around [the|its|their] local y(-| )ax(i|e)s by %object%", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            Object a1 = a.get(1).getSingle(ctx);
            return (a0 instanceof org.joml.Quaternionf __q && a1 instanceof Number __n && !Double.isInfinite(__n.doubleValue()*Math.PI/180d) && !Double.isNaN(__n.doubleValue()*Math.PI/180d) ? __q.rotateY((float)(__n.doubleValue()*Math.PI/180d), new org.joml.Quaternionf()) : null);
        }));
        registry.registerExpression("%object% rotated around [the|its|their] local z(-| )ax(i|e)s by %object%", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            Object a1 = a.get(1).getSingle(ctx);
            return (a0 instanceof org.joml.Quaternionf __q && a1 instanceof Number __n && !Double.isInfinite(__n.doubleValue()*Math.PI/180d) && !Double.isNaN(__n.doubleValue()*Math.PI/180d) ? __q.rotateZ((float)(__n.doubleValue()*Math.PI/180d), new org.joml.Quaternionf()) : null);
        }));
        registry.registerExpression("%object% rotated around [the] %object% by %object%", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            Object a1 = a.get(1).getSingle(ctx);
            Object a2 = a.get(2).getSingle(ctx);
            return (a2 instanceof Number __n && a1 instanceof org.bukkit.util.Vector __ax && !__ax.isZero() && !Double.isInfinite(__n.doubleValue()*Math.PI/180d) && !Double.isNaN(__n.doubleValue()*Math.PI/180d) ? (a0 instanceof org.bukkit.util.Vector __v ? (Object) __v.clone().rotateAroundNonUnitAxis(__ax.clone().normalize(), __n.doubleValue()*Math.PI/180d) : (a0 instanceof org.joml.Quaternionf __q ? (Object) __q.rotateAxis((float)(__n.doubleValue()*Math.PI/180d), __ax.clone().normalize().toVector3f(), new org.joml.Quaternionf()) : null)) : null);
        }));
        registry.registerExpression("%object% rotated by x %object%, y %object%(, [and]| and) z %object%", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            Object a1 = a.get(1).getSingle(ctx);
            Object a2 = a.get(2).getSingle(ctx);
            Object a3 = a.get(3).getSingle(ctx);
            return (a0 instanceof org.joml.Quaternionf __q && a1 instanceof Number __x && a2 instanceof Number __y && a3 instanceof Number __z ? new org.joml.Quaternionf(__q).rotateZYX((float)(__z.floatValue()*Math.PI/180), (float)(__y.floatValue()*Math.PI/180), (float)(__x.floatValue()*Math.PI/180)) : null);
        }));
        registry.registerExpression("ticks of %object%", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            return (a0 instanceof co.xenastudios.neoskript.core.runtime.Timespan ? ((co.xenastudios.neoskript.core.runtime.Timespan) a0).millis() / 50L : null);
        }));
        registry.registerExpression("seconds of %object%", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            return (a0 instanceof co.xenastudios.neoskript.core.runtime.Timespan ? ((co.xenastudios.neoskript.core.runtime.Timespan) a0).millis() / 1000L : null);
        }));
        registry.registerExpression("minutes of %object%", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            return (a0 instanceof co.xenastudios.neoskript.core.runtime.Timespan ? ((co.xenastudios.neoskript.core.runtime.Timespan) a0).millis() / 60000L : null);
        }));
        registry.registerExpression("hours of %object%", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            return (a0 instanceof co.xenastudios.neoskript.core.runtime.Timespan ? ((co.xenastudios.neoskript.core.runtime.Timespan) a0).millis() / 3600000L : null);
        }));
        registry.registerExpression("days of %object%", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            return (a0 instanceof co.xenastudios.neoskript.core.runtime.Timespan ? ((co.xenastudios.neoskript.core.runtime.Timespan) a0).millis() / 86400000L : null);
        }));
        registry.registerExpression("weeks of %object%", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            return (a0 instanceof co.xenastudios.neoskript.core.runtime.Timespan ? ((co.xenastudios.neoskript.core.runtime.Timespan) a0).millis() / 604800000L : null);
        }));
        registry.registerExpression("months of %object%", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            return (a0 instanceof co.xenastudios.neoskript.core.runtime.Timespan ? ((co.xenastudios.neoskript.core.runtime.Timespan) a0).millis() / 2592000000L : null);
        }));
        registry.registerExpression("years of %object%", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            return (a0 instanceof co.xenastudios.neoskript.core.runtime.Timespan ? ((co.xenastudios.neoskript.core.runtime.Timespan) a0).millis() / 31536000000L : null);
        }));
        registry.registerExpression("unbreakable %object%", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            return (a0 instanceof org.bukkit.inventory.ItemStack ? ((java.util.function.Function<org.bukkit.inventory.ItemStack, org.bukkit.inventory.ItemStack>) (i -> i.editMeta(m -> m.setUnbreakable(true)) ? i : i)).apply(((org.bukkit.inventory.ItemStack) a0).clone()) : null);
        }));
        registry.registerExpression("breakable %object%", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            return (a0 instanceof org.bukkit.inventory.ItemStack ? ((java.util.function.Function<org.bukkit.inventory.ItemStack, org.bukkit.inventory.ItemStack>) (i -> i.editMeta(m -> m.setUnbreakable(false)) ? i : i)).apply(((org.bukkit.inventory.ItemStack) a0).clone()) : null);
        }));
        registry.registerExpression("unix timestamp of %object%", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            return ((a0 instanceof java.util.Date) ? (java.lang.Object) (((java.util.Date) a0).getTime() / 1000.0) : null);
        }));
        registry.registerExpression("[a] [new] cylindrical vector [from|with] [radius] %object%, [yaw] %object%(,[ and]| and) [height] %object%", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            Object a1 = a.get(1).getSingle(ctx);
            Object a2 = a.get(2).getSingle(ctx);
            return ((a0 instanceof java.lang.Number && a1 instanceof java.lang.Number && a2 instanceof java.lang.Number) ? new org.bukkit.util.Vector(java.lang.Math.abs(((java.lang.Number) a0).doubleValue()) * java.lang.Math.cos((((java.lang.Number) a1).floatValue() > 270f ? ((java.lang.Number) a1).floatValue() - 270f : ((java.lang.Number) a1).floatValue() + 90f) * (java.lang.Math.PI / 180)), ((java.lang.Number) a2).doubleValue(), java.lang.Math.abs(((java.lang.Number) a0).doubleValue()) * java.lang.Math.sin((((java.lang.Number) a1).floatValue() > 270f ? ((java.lang.Number) a1).floatValue() - 270f : ((java.lang.Number) a1).floatValue() + 90f) * (java.lang.Math.PI / 180))) : null);
        }));
        registry.registerExpression("[a] [new] spherical vector [(from|with)] [radius] %object%, [yaw] %object%(,[ and]| and) [pitch] %object%", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            Object a1 = a.get(1).getSingle(ctx);
            Object a2 = a.get(2).getSingle(ctx);
            return ((a0 instanceof java.lang.Number && a1 instanceof java.lang.Number && a2 instanceof java.lang.Number) ? new org.bukkit.util.Vector(java.lang.Math.abs(((java.lang.Number) a0).doubleValue()) * java.lang.Math.sin((((java.lang.Number) a2).floatValue() + 90f) * (java.lang.Math.PI / 180)) * java.lang.Math.cos((((java.lang.Number) a1).floatValue() > 270f ? ((java.lang.Number) a1).floatValue() - 270f : ((java.lang.Number) a1).floatValue() + 90f) * (java.lang.Math.PI / 180)), java.lang.Math.abs(((java.lang.Number) a0).doubleValue()) * java.lang.Math.cos((((java.lang.Number) a2).floatValue() + 90f) * (java.lang.Math.PI / 180)), java.lang.Math.abs(((java.lang.Number) a0).doubleValue()) * java.lang.Math.sin((((java.lang.Number) a2).floatValue() + 90f) * (java.lang.Math.PI / 180)) * java.lang.Math.sin((((java.lang.Number) a1).floatValue() > 270f ? ((java.lang.Number) a1).floatValue() - 270f : ((java.lang.Number) a1).floatValue() + 90f) * (java.lang.Math.PI / 180))) : null);
        }));
        registry.registerExpression("%object% with fire[ ]resistance", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            return (a0 instanceof org.bukkit.inventory.ItemStack ? java.util.Optional.of(((org.bukkit.inventory.ItemStack) a0).clone()).filter(c -> c.editMeta(m -> m.setFireResistant(true))).orElse(null) : null);
        }));
        registry.registerExpression("%object% without fire[ ]resistance", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            return (a0 instanceof org.bukkit.inventory.ItemStack ? java.util.Optional.of(((org.bukkit.inventory.ItemStack) a0).clone()).filter(c -> c.editMeta(m -> m.setFireResistant(false))).orElse(null) : null);
        }));
        registry.registerExpression("fire resistant %object%", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            return (a0 instanceof org.bukkit.inventory.ItemStack ? java.util.Optional.of(((org.bukkit.inventory.ItemStack) a0).clone()).filter(c -> c.editMeta(m -> m.setFireResistant(true))).orElse(null) : null);
        }));
        registry.registerExpression("%object% of %object%", Object.class, a -> new ComputedExpression(ctx -> {
            Object a0 = a.get(0).getSingle(ctx);
            Object a1 = a.get(1).getSingle(ctx);
            return ((a0 instanceof Number && a1 instanceof org.bukkit.inventory.ItemStack) ? ((org.bukkit.inventory.ItemStack) a1).asQuantity((int) Math.max(((Number) a0).longValue(), 0L)) : null);
        }));
    }
}
