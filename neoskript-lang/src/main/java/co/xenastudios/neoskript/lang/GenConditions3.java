package co.xenastudios.neoskript.lang;

import co.xenastudios.neoskript.api.syntax.Expression;
import co.xenastudios.neoskript.api.registry.SyntaxRegistry;
import co.xenastudios.neoskript.core.expression.ComputedExpression;
import co.xenastudios.neoskript.core.expression.ComputedListExpression;

/** Generated bulk registration (cond). One registration per line for compile-drop. */
public final class GenConditions3 {
    private GenConditions3() {}

    public static void register(SyntaxRegistry registry) {
        registry.registerCondition("%object% can see chat colors", a -> { Expression<?> src = a.get(0); return ctx -> { Object o = src.getSingle(ctx); return o instanceof org.bukkit.entity.Player h && h.getClientOption(com.destroystokyo.paper.ClientOption.CHAT_COLORS_ENABLED); }; });
        registry.registerCondition("%object% has been stared at", a -> { Expression<?> src = a.get(0); return ctx -> { Object o = src.getSingle(ctx); return o instanceof org.bukkit.entity.Enderman h && h.hasBeenStaredAt(); }; });
        registry.registerCondition("%object% is in open water", a -> { Expression<?> src = a.get(0); return ctx -> { Object o = src.getSingle(ctx); return o instanceof org.bukkit.entity.FishHook h && h.isInOpenWater(); }; });
        registry.registerCondition("%object% is in love", a -> { Expression<?> src = a.get(0); return ctx -> { Object o = src.getSingle(ctx); return o instanceof org.bukkit.entity.Animals h && h.isLoveMode(); }; });
    }
}
