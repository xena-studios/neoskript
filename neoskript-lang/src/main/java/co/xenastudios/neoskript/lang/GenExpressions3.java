package co.xenastudios.neoskript.lang;

import co.xenastudios.neoskript.api.syntax.Expression;
import co.xenastudios.neoskript.api.registry.SyntaxRegistry;
import co.xenastudios.neoskript.core.expression.ComputedExpression;
import co.xenastudios.neoskript.core.expression.ComputedListExpression;

/** Generated bulk registration (expr). One registration per line for compile-drop. */
public final class GenExpressions3 {
    private GenExpressions3() {}

    public static void register(SyntaxRegistry registry) {
        registry.registerExpression("duplication cooldown of %object%", Object.class, a -> { Expression<?> src = a.get(0); return new ComputedExpression(ctx -> { Object o = src.getSingle(ctx); if (!(o instanceof org.bukkit.entity.Allay h)) return null; return h.getDuplicationCooldown(); }); });
        registry.registerExpression("food level of %object%", Object.class, a -> { Expression<?> src = a.get(0); return new ComputedExpression(ctx -> { Object o = src.getSingle(ctx); if (!(o instanceof org.bukkit.entity.Player h)) return null; return h.getFoodLevel(); }); });
        registry.registerExpression("hidden players of %object%", Object.class, a -> { Expression<?> src = a.get(0); return new ComputedListExpression(ctx -> { Object o = src.getSingle(ctx); if (!(o instanceof org.bukkit.entity.Player h)) return new Object[0]; var c = h.spigot().getHiddenPlayers(); return c == null ? new Object[0] : c.toArray(); }); });
        registry.registerExpression("leash holder of %object%", Object.class, a -> { Expression<?> src = a.get(0); return new ComputedExpression(ctx -> { Object o = src.getSingle(ctx); if (!(o instanceof org.bukkit.entity.LivingEntity h)) return null; return h.getLeashHolder(); }); });
        registry.registerExpression("looter of %object%", Object.class, a -> { Expression<?> src = a.get(0); return new ComputedExpression(ctx -> { Object o = src.getSingle(ctx); if (!(o instanceof org.bukkit.loot.LootContext h)) return null; return h.getKiller(); }); });
        registry.registerExpression("arrows stuck in %object%", Object.class, a -> { Expression<?> src = a.get(0); return new ComputedExpression(ctx -> { Object o = src.getSingle(ctx); if (!(o instanceof org.bukkit.entity.LivingEntity h)) return null; return h.getArrowsStuck(); }); });
        registry.registerExpression("passengers of %object%", Object.class, a -> { Expression<?> src = a.get(0); return new ComputedListExpression(ctx -> { Object o = src.getSingle(ctx); if (!(o instanceof org.bukkit.entity.Entity h)) return new Object[0]; var c = h.getPassengers(); return c == null ? new Object[0] : c.toArray(); }); });
        registry.registerExpression("ping of %object%", Object.class, a -> { Expression<?> src = a.get(0); return new ComputedExpression(ctx -> { Object o = src.getSingle(ctx); if (!(o instanceof org.bukkit.entity.Player h)) return null; return h.spigot().getPing(); }); });
        registry.registerExpression("scoreboard tags of %object%", Object.class, a -> { Expression<?> src = a.get(0); return new ComputedListExpression(ctx -> { Object o = src.getSingle(ctx); if (!(o instanceof org.bukkit.entity.Entity h)) return new Object[0]; var c = h.getScoreboardTags(); return c == null ? new Object[0] : c.toArray(); }); });
        registry.registerExpression("tag contents of %object%", Object.class, a -> { Expression<?> src = a.get(0); return new ComputedListExpression(ctx -> { Object o = src.getSingle(ctx); if (!(o instanceof org.bukkit.Tag h)) return new Object[0]; var c = h.getValues(); return c == null ? new Object[0] : c.toArray(); }); });
        registry.registerExpression("text opacity of %object%", Object.class, a -> { Expression<?> src = a.get(0); return new ComputedExpression(ctx -> { Object o = src.getSingle(ctx); if (!(o instanceof org.bukkit.entity.TextDisplay h)) return null; return h.getTextOpacity(); }); });
        registry.registerExpression("time lived of %object%", Object.class, a -> { Expression<?> src = a.get(0); return new ComputedExpression(ctx -> { Object o = src.getSingle(ctx); if (!(o instanceof org.bukkit.entity.Entity h)) return null; return h.getTicksLived(); }); });
        registry.registerExpression("vector of %object%", Object.class, a -> { Expression<?> src = a.get(0); return new ComputedExpression(ctx -> { Object o = src.getSingle(ctx); if (!(o instanceof org.bukkit.Location h)) return null; return h.toVector(); }); });
        registry.registerExpression("vehicle of %object%", Object.class, a -> { Expression<?> src = a.get(0); return new ComputedExpression(ctx -> { Object o = src.getSingle(ctx); if (!(o instanceof org.bukkit.entity.Entity h)) return null; return h.getVehicle(); }); });
        registry.registerExpression("most angered entity of %object%", Object.class, a -> { Expression<?> src = a.get(0); return new ComputedExpression(ctx -> { Object o = src.getSingle(ctx); if (!(o instanceof org.bukkit.entity.Warden h)) return null; return h.getEntityAngryAt(); }); });
        registry.registerExpression("world border warning time of %object%", Object.class, a -> { Expression<?> src = a.get(0); return new ComputedExpression(ctx -> { Object o = src.getSingle(ctx); if (!(o instanceof org.bukkit.WorldBorder h)) return null; return h.getWarningTime(); }); });
        registry.registerExpression("fuel level of %object%", Object.class, a -> { Expression<?> src = a.get(0); return new ComputedExpression(ctx -> { Object o = src.getSingle(ctx); if (!(o instanceof org.bukkit.block.Block h)) return null; return ((org.bukkit.block.BrewingStand) h.getState()).getFuelLevel(); }); });
        registry.registerExpression("seed of %object%", Object.class, a -> { Expression<?> src = a.get(0); return new ComputedExpression(ctx -> { Object o = src.getSingle(ctx); if (!(o instanceof org.bukkit.World h)) return null; return h.getSeed(); }); });
        registry.registerExpression("brewing time of %object%", Object.class, a -> { Expression<?> src = a.get(0); return new ComputedExpression(ctx -> { Object o = src.getSingle(ctx); if (!(o instanceof org.bukkit.block.Block h)) return null; return ((org.bukkit.block.BrewingStand) h.getState()).getBrewingTime(); }); });
        registry.registerExpression("buried item of %object%", Object.class, a -> { Expression<?> src = a.get(0); return new ComputedExpression(ctx -> { Object o = src.getSingle(ctx); if (!(o instanceof org.bukkit.block.Block h)) return null; return ((org.bukkit.block.BrushableBlock) h.getState()).getItem(); }); });
        registry.registerExpression("codepoint of %object%", Object.class, a -> { Expression<?> src = a.get(0); return new ComputedExpression(ctx -> { Object o = src.getSingle(ctx); if (!(o instanceof java.lang.String h)) return null; return h.codePointAt(0); }); });
        registry.registerExpression("max fuse ticks of %object%", Object.class, a -> { Expression<?> src = a.get(0); return new ComputedExpression(ctx -> { Object o = src.getSingle(ctx); if (!(o instanceof org.bukkit.entity.Creeper h)) return null; return h.getMaxFuseTicks(); }); });
        registry.registerExpression("glow color override of %object%", Object.class, a -> { Expression<?> src = a.get(0); return new ComputedExpression(ctx -> { Object o = src.getSingle(ctx); if (!(o instanceof org.bukkit.entity.Display h)) return null; return h.getGlowColorOverride(); }); });
    }
}
