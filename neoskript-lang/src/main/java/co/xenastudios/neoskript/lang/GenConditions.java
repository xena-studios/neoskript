package co.xenastudios.neoskript.lang;

import co.xenastudios.neoskript.api.syntax.Expression;
import co.xenastudios.neoskript.api.registry.SyntaxRegistry;
import co.xenastudios.neoskript.core.expression.ComputedExpression;

/** Generated bulk registration (cond). One registration per line for compile-drop. */
public final class GenConditions {
    private GenConditions() {}

    public static void register(SyntaxRegistry registry) {
        registry.registerCondition("%object% can duplicate", a -> { Expression<?> src = a.get(0); return ctx -> { Object o = src.getSingle(ctx); return o instanceof org.bukkit.entity.Allay h && h.canDuplicate(); }; });
        registry.registerCondition("%object% is dashing", a -> { Expression<?> src = a.get(0); return ctx -> { Object o = src.getSingle(ctx); return o instanceof org.bukkit.entity.Camel h && h.isDashing(); }; });
        registry.registerCondition("%object% can breed", a -> { Expression<?> src = a.get(0); return ctx -> { Object o = src.getSingle(ctx); return o instanceof org.bukkit.entity.Breedable h && h.canBreed(); }; });
        registry.registerCondition("%object% can despawn when far away", a -> { Expression<?> src = a.get(0); return ctx -> { Object o = src.getSingle(ctx); return o instanceof org.bukkit.entity.LivingEntity h && h.getRemoveWhenFarAway(); }; });
        registry.registerCondition("%object% can pick up items", a -> { Expression<?> src = a.get(0); return ctx -> { Object o = src.getSingle(ctx); return o instanceof org.bukkit.entity.LivingEntity h && h.getCanPickupItems(); }; });
        registry.registerCondition("%object% scales damage with difficulty", a -> { Expression<?> src = a.get(0); return ctx -> { Object o = src.getSingle(ctx); return o instanceof org.bukkit.damage.DamageSource h && h.scalesWithDifficulty(); }; });
        registry.registerCondition("respawn anchors work in %object%", a -> { Expression<?> src = a.get(0); return ctx -> { Object o = src.getSingle(ctx); return o instanceof org.bukkit.World h && h.isRespawnAnchorWorks(); }; });
        registry.registerCondition("%object% is wet", a -> { Expression<?> src = a.get(0); return ctx -> { Object o = src.getSingle(ctx); return o instanceof org.bukkit.entity.Entity h && h.isInWaterOrRainOrBubbleColumn(); }; });
        registry.registerCondition("%object% has AI", a -> { Expression<?> src = a.get(0); return ctx -> { Object o = src.getSingle(ctx); return o instanceof org.bukkit.entity.LivingEntity h && h.hasAI(); }; });
        registry.registerCondition("%object% has a resource pack loaded", a -> { Expression<?> src = a.get(0); return ctx -> { Object o = src.getSingle(ctx); return o instanceof org.bukkit.entity.Player h && h.hasResourcePack(); }; });
        registry.registerCondition("%object% is climbing", a -> { Expression<?> src = a.get(0); return ctx -> { Object o = src.getSingle(ctx); return o instanceof org.bukkit.entity.LivingEntity h && h.isClimbing(); }; });
        registry.registerCondition("%object%'s custom name is visible", a -> { Expression<?> src = a.get(0); return ctx -> { Object o = src.getSingle(ctx); return o instanceof org.bukkit.entity.Entity h && h.isCustomNameVisible(); }; });
        registry.registerCondition("%object% is from a mob spawner", a -> { Expression<?> src = a.get(0); return ctx -> { Object o = src.getSingle(ctx); return o instanceof org.bukkit.entity.Entity h && h.fromMobSpawner(); }; });
        registry.registerCondition("%object% is jumping", a -> { Expression<?> src = a.get(0); return ctx -> { Object o = src.getSingle(ctx); return o instanceof org.bukkit.entity.LivingEntity h && h.isJumping(); }; });
        registry.registerCondition("%object% is normalized", a -> { Expression<?> src = a.get(0); return ctx -> { Object o = src.getSingle(ctx); return o instanceof org.bukkit.util.Vector h && h.isNormalized(); }; });
        registry.registerCondition("%object% is passable", a -> { Expression<?> src = a.get(0); return ctx -> { Object o = src.getSingle(ctx); return o instanceof org.bukkit.block.Block h && h.isPassable(); }; });
        registry.registerCondition("%object% is riptiding", a -> { Expression<?> src = a.get(0); return ctx -> { Object o = src.getSingle(ctx); return o instanceof org.bukkit.entity.LivingEntity h && h.isRiptiding(); }; });
        registry.registerCondition("%object% is a slime chunk", a -> { Expression<?> src = a.get(0); return ctx -> { Object o = src.getSingle(ctx); return o instanceof org.bukkit.Chunk h && h.isSlimeChunk(); }; });
        registry.registerCondition("%object% is ticking", a -> { Expression<?> src = a.get(0); return ctx -> { Object o = src.getSingle(ctx); return o instanceof org.bukkit.entity.Entity h && h.isTicking(); }; });
        registry.registerCondition("%object% is on its back", a -> { Expression<?> src = a.get(0); return ctx -> { Object o = src.getSingle(ctx); return o instanceof org.bukkit.entity.Panda h && h.isOnBack(); }; });
        registry.registerCondition("%object% is rolling", a -> { Expression<?> src = a.get(0); return ctx -> { Object o = src.getSingle(ctx); return o instanceof org.bukkit.entity.Panda h && h.isRolling(); }; });
        registry.registerCondition("%object% is scared", a -> { Expression<?> src = a.get(0); return ctx -> { Object o = src.getSingle(ctx); return o instanceof org.bukkit.entity.Panda h && h.isScared(); }; });
        registry.registerCondition("%object% is sneezing", a -> { Expression<?> src = a.get(0); return ctx -> { Object o = src.getSingle(ctx); return o instanceof org.bukkit.entity.Panda h && h.isSneezing(); }; });
        registry.registerCondition("%object% is instant", a -> { Expression<?> src = a.get(0); return ctx -> { Object o = src.getSingle(ctx); return o instanceof org.bukkit.potion.PotionEffectType h && h.isInstant(); }; });
        registry.registerCondition("%object% will despawn naturally", a -> { Expression<?> src = a.get(0); return ctx -> { Object o = src.getSingle(ctx); return o instanceof org.bukkit.entity.Item h && !h.isUnlimitedLifetime(); }; });
    }
}
