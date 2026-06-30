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
        registry.registerCondition("%object% is from a mob spawner", a -> { Expression<?> src = a.get(0); return ctx -> { Object o = src.getSingle(ctx); return o instanceof org.bukkit.entity.Entity h && h.fromMobSpawner(); }; });
    }
}
