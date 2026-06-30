package co.xenastudios.neoskript.lang;

import co.xenastudios.neoskript.api.syntax.Expression;
import co.xenastudios.neoskript.api.registry.SyntaxRegistry;
import co.xenastudios.neoskript.core.expression.ComputedExpression;

/** Generated bulk registration (expr). One registration per line for compile-drop. */
public final class GenExpressions {
    private GenExpressions() {}

    public static void register(SyntaxRegistry registry) {
        registry.registerExpression("active item of %object%", Object.class, a -> { Expression<?> src = a.get(0); return new ComputedExpression(ctx -> { Object o = src.getSingle(ctx); if (!(o instanceof org.bukkit.entity.LivingEntity h)) return null; return h.getActiveItem(); }); });
        registry.registerExpression("anvil text input of %object%", Object.class, a -> { Expression<?> src = a.get(0); return new ComputedExpression(ctx -> { Object o = src.getSingle(ctx); if (!(o instanceof org.bukkit.inventory.AnvilInventory h)) return null; return h.getRenameText(); }); });
        registry.registerExpression("arrow knockback strength of %object%", Object.class, a -> { Expression<?> src = a.get(0); return new ComputedExpression(ctx -> { Object o = src.getSingle(ctx); if (!(o instanceof org.bukkit.entity.AbstractArrow h)) return null; return h.getKnockbackStrength(); }); });
        registry.registerExpression("arrow pierce level of %object%", Object.class, a -> { Expression<?> src = a.get(0); return new ComputedExpression(ctx -> { Object o = src.getSingle(ctx); if (!(o instanceof org.bukkit.entity.Arrow h)) return null; return h.getPierceLevel(); }); });
        registry.registerExpression("attack cooldown of %object%", Object.class, a -> { Expression<?> src = a.get(0); return new ComputedExpression(ctx -> { Object o = src.getSingle(ctx); if (!(o instanceof org.bukkit.entity.HumanEntity h)) return null; return h.getAttackCooldown(); }); });
        registry.registerExpression("bed location of %object%", Object.class, a -> { Expression<?> src = a.get(0); return new ComputedExpression(ctx -> { Object o = src.getSingle(ctx); if (!(o instanceof org.bukkit.OfflinePlayer h)) return null; return h.getBedSpawnLocation(); }); });
        registry.registerExpression("world border center of %object%", Object.class, a -> { Expression<?> src = a.get(0); return new ComputedExpression(ctx -> { Object o = src.getSingle(ctx); if (!(o instanceof org.bukkit.WorldBorder h)) return null; return h.getCenter(); }); });
        registry.registerExpression("compass target of %object%", Object.class, a -> { Expression<?> src = a.get(0); return new ComputedExpression(ctx -> { Object o = src.getSingle(ctx); if (!(o instanceof org.bukkit.entity.Player h)) return null; return h.getCompassTarget(); }); });
        registry.registerExpression("world border damage amount of %object%", Object.class, a -> { Expression<?> src = a.get(0); return new ComputedExpression(ctx -> { Object o = src.getSingle(ctx); if (!(o instanceof org.bukkit.WorldBorder h)) return null; return h.getDamageAmount(); }); });
        registry.registerExpression("world border damage buffer of %object%", Object.class, a -> { Expression<?> src = a.get(0); return new ComputedExpression(ctx -> { Object o = src.getSingle(ctx); if (!(o instanceof org.bukkit.WorldBorder h)) return null; return h.getDamageBuffer(); }); });
        registry.registerExpression("causing entity of %object%", Object.class, a -> { Expression<?> src = a.get(0); return new ComputedExpression(ctx -> { Object o = src.getSingle(ctx); if (!(o instanceof org.bukkit.damage.DamageSource h)) return null; return h.getCausingEntity(); }); });
        registry.registerExpression("damage location of %object%", Object.class, a -> { Expression<?> src = a.get(0); return new ComputedExpression(ctx -> { Object o = src.getSingle(ctx); if (!(o instanceof org.bukkit.damage.DamageSource h)) return null; return h.getDamageLocation(); }); });
        registry.registerExpression("damage type of %object%", Object.class, a -> { Expression<?> src = a.get(0); return new ComputedExpression(ctx -> { Object o = src.getSingle(ctx); if (!(o instanceof org.bukkit.damage.DamageSource h)) return null; return h.getDamageType(); }); });
        registry.registerExpression("direct entity of %object%", Object.class, a -> { Expression<?> src = a.get(0); return new ComputedExpression(ctx -> { Object o = src.getSingle(ctx); if (!(o instanceof org.bukkit.damage.DamageSource h)) return null; return h.getDirectEntity(); }); });
        registry.registerExpression("food exhaustion of %object%", Object.class, a -> { Expression<?> src = a.get(0); return new ComputedExpression(ctx -> { Object o = src.getSingle(ctx); if (!(o instanceof org.bukkit.damage.DamageSource h)) return null; return h.getFoodExhaustion(); }); });
        registry.registerExpression("source location of %object%", Object.class, a -> { Expression<?> src = a.get(0); return new ComputedExpression(ctx -> { Object o = src.getSingle(ctx); if (!(o instanceof org.bukkit.damage.DamageSource h)) return null; return h.getSourceLocation(); }); });
        registry.registerExpression("difficulty of %object%", Object.class, a -> { Expression<?> src = a.get(0); return new ComputedExpression(ctx -> { Object o = src.getSingle(ctx); if (!(o instanceof org.bukkit.World h)) return null; return h.getDifficulty(); }); });
        registry.registerExpression("billboard of %object%", Object.class, a -> { Expression<?> src = a.get(0); return new ComputedExpression(ctx -> { Object o = src.getSingle(ctx); if (!(o instanceof org.bukkit.entity.Display h)) return null; return h.getBillboard(); }); });
        registry.registerExpression("teleport duration of %object%", Object.class, a -> { Expression<?> src = a.get(0); return new ComputedExpression(ctx -> { Object o = src.getSingle(ctx); if (!(o instanceof org.bukkit.entity.Display h)) return null; return h.getTeleportDuration(); }); });
        registry.registerExpression("view range of %object%", Object.class, a -> { Expression<?> src = a.get(0); return new ComputedExpression(ctx -> { Object o = src.getSingle(ctx); if (!(o instanceof org.bukkit.entity.Display h)) return null; return h.getViewRange(); }); });
        registry.registerExpression("uuid of dropped item owner of %object%", Object.class, a -> { Expression<?> src = a.get(0); return new ComputedExpression(ctx -> { Object o = src.getSingle(ctx); if (!(o instanceof org.bukkit.entity.Item h)) return null; return h.getOwner(); }); });
        registry.registerExpression("uuid of dropped item thrower of %object%", Object.class, a -> { Expression<?> src = a.get(0); return new ComputedExpression(ctx -> { Object o = src.getSingle(ctx); if (!(o instanceof org.bukkit.entity.Item h)) return null; return h.getThrower(); }); });
        registry.registerExpression("enchantment cost of %object%", Object.class, a -> { Expression<?> src = a.get(0); return new ComputedExpression(ctx -> { Object o = src.getSingle(ctx); if (!(o instanceof org.bukkit.enchantments.EnchantmentOffer h)) return null; return h.getCost(); }); });
        registry.registerExpression("ender chest of %object%", Object.class, a -> { Expression<?> src = a.get(0); return new ComputedExpression(ctx -> { Object o = src.getSingle(ctx); if (!(o instanceof org.bukkit.entity.Player h)) return null; return h.getEnderChest(); }); });
    }
}
