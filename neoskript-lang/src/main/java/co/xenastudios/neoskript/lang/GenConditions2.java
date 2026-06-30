package co.xenastudios.neoskript.lang;

import co.xenastudios.neoskript.api.syntax.Expression;
import co.xenastudios.neoskript.api.registry.SyntaxRegistry;
import co.xenastudios.neoskript.core.expression.ComputedExpression;

/** Generated bulk registration (cond). One registration per line for compile-drop. */
public final class GenConditions2 {
    private GenConditions2() {}

    public static void register(SyntaxRegistry registry) {
        registry.registerCondition("%object% (is|are) normalized", a -> { Expression<?> src = a.get(0); return ctx -> { Object o = src.getSingle(ctx); return o instanceof org.bukkit.util.Vector h && h.isNormalized(); }; });
        registry.registerCondition("%object% (is|are) riptiding", a -> { Expression<?> src = a.get(0); return ctx -> { Object o = src.getSingle(ctx); return o instanceof org.bukkit.entity.LivingEntity h && h.isRiptiding(); }; });
        registry.registerCondition("%object% (is|are) flying", a -> { Expression<?> src = a.get(0); return ctx -> { Object o = src.getSingle(ctx); return o instanceof org.bukkit.entity.Player h && h.isFlying(); }; });
        registry.registerCondition("%object% (is|are) (invisible|visible)", a -> { Expression<?> src = a.get(0); return ctx -> { Object o = src.getSingle(ctx); return o instanceof org.bukkit.entity.LivingEntity h && h.isInvisible(); }; });
        registry.registerCondition("%object% (is|are) ticking", a -> { Expression<?> src = a.get(0); return ctx -> { Object o = src.getSingle(ctx); return o instanceof org.bukkit.entity.Entity h && h.isTicking(); }; });
        registry.registerCondition("%object% (is|are) passable", a -> { Expression<?> src = a.get(0); return ctx -> { Object o = src.getSingle(ctx); return o instanceof org.bukkit.block.Block h && h.isPassable(); }; });
        registry.registerCondition("%object% (is|are) swimming", a -> { Expression<?> src = a.get(0); return ctx -> { Object o = src.getSingle(ctx); return o instanceof org.bukkit.entity.LivingEntity h && h.isSwimming(); }; });
        registry.registerCondition("%object% (is|are) sprinting", a -> { Expression<?> src = a.get(0); return ctx -> { Object o = src.getSingle(ctx); return o instanceof org.bukkit.entity.Player h && h.isSprinting(); }; });
        registry.registerCondition("%object% (is|are) [[a] server|an] op[erator][s]", a -> { Expression<?> src = a.get(0); return ctx -> { Object o = src.getSingle(ctx); return o instanceof org.bukkit.OfflinePlayer h && h.isOp(); }; });
        registry.registerCondition("%object% (is|are) on [the] ground", a -> { Expression<?> src = a.get(0); return ctx -> { Object o = src.getSingle(ctx); return o instanceof org.bukkit.entity.Entity h && h.isOnGround(); }; });
        registry.registerCondition("%object% (is|are) gliding", a -> { Expression<?> src = a.get(0); return ctx -> { Object o = src.getSingle(ctx); return o instanceof org.bukkit.entity.LivingEntity h && h.isGliding(); }; });
        registry.registerCondition("%object% (is|are) silent", a -> { Expression<?> src = a.get(0); return ctx -> { Object o = src.getSingle(ctx); return o instanceof org.bukkit.entity.Entity h && h.isSilent(); }; });
        registry.registerCondition("%object% (is|are) climbing", a -> { Expression<?> src = a.get(0); return ctx -> { Object o = src.getSingle(ctx); return o instanceof org.bukkit.entity.LivingEntity h && h.isClimbing(); }; });
        registry.registerCondition("%object% (is|are) frozen", a -> { Expression<?> src = a.get(0); return ctx -> { Object o = src.getSingle(ctx); return o instanceof org.bukkit.entity.Entity h && h.isFrozen(); }; });
        registry.registerCondition("%object% (is|are) wet", a -> { Expression<?> src = a.get(0); return ctx -> { Object o = src.getSingle(ctx); return o instanceof org.bukkit.entity.Entity h && h.isInWaterOrRainOrBubbleColumn(); }; });
        registry.registerCondition("%object% (is|are) sleeping", a -> { Expression<?> src = a.get(0); return ctx -> { Object o = src.getSingle(ctx); return o instanceof org.bukkit.entity.LivingEntity h && h.isSleeping(); }; });
        registry.registerCondition("%object% (is|are) sneaking", a -> { Expression<?> src = a.get(0); return ctx -> { Object o = src.getSingle(ctx); return o instanceof org.bukkit.entity.Player h && h.isSneaking(); }; });
        registry.registerCondition("%object% (is|are) jumping", a -> { Expression<?> src = a.get(0); return ctx -> { Object o = src.getSingle(ctx); return o instanceof org.bukkit.entity.LivingEntity h && h.isJumping(); }; });
        registry.registerCondition("%object% (is|are) (blocking|defending) [with [a] shield]", a -> { Expression<?> src = a.get(0); return ctx -> { Object o = src.getSingle(ctx); return o instanceof org.bukkit.entity.Player h && h.isBlocking(); }; });
    }
}
