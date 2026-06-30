package co.xenastudios.neoskript.lang;

import co.xenastudios.neoskript.api.syntax.Expression;
import co.xenastudios.neoskript.api.registry.SyntaxRegistry;
import co.xenastudios.neoskript.core.expression.ComputedExpression;

/** Generated bulk registration (expr). One registration per line for compile-drop. */
public final class GenExpressions2 {
    private GenExpressions2() {}

    public static void register(SyntaxRegistry registry) {
        registry.registerExpression("level progress of %object%", Object.class, a -> { Expression<?> src = a.get(0); return new ComputedExpression(ctx -> { Object o = src.getSingle(ctx); if (!(o instanceof org.bukkit.entity.Player h)) return null; return h.getExp(); }); });
        registry.registerExpression("fall[en] (distance|height) of %object%", Object.class, a -> { Expression<?> src = a.get(0); return new ComputedExpression(ctx -> { Object o = src.getSingle(ctx); if (!(o instanceof org.bukkit.entity.Entity h)) return null; return h.getFallDistance(); }); });
        registry.registerExpression("saturation of %object%", Object.class, a -> { Expression<?> src = a.get(0); return new ComputedExpression(ctx -> { Object o = src.getSingle(ctx); if (!(o instanceof org.bukkit.entity.Player h)) return null; return h.getSaturation(); }); });
        registry.registerExpression("ender[ ]chest[s] of %object%", Object.class, a -> { Expression<?> src = a.get(0); return new ComputedExpression(ctx -> { Object o = src.getSingle(ctx); if (!(o instanceof org.bukkit.entity.Player h)) return null; return h.getEnderChest(); }); });
        registry.registerExpression("[world] environment of %object%", Object.class, a -> { Expression<?> src = a.get(0); return new ComputedExpression(ctx -> { Object o = src.getSingle(ctx); if (!(o instanceof org.bukkit.World h)) return null; return h.getEnvironment(); }); });
        registry.registerExpression("exhaustion of %object%", Object.class, a -> { Expression<?> src = a.get(0); return new ComputedExpression(ctx -> { Object o = src.getSingle(ctx); if (!(o instanceof org.bukkit.entity.Player h)) return null; return h.getExhaustion(); }); });
        registry.registerExpression("humidit(y|ies) of %object%", Object.class, a -> { Expression<?> src = a.get(0); return new ComputedExpression(ctx -> { Object o = src.getSingle(ctx); if (!(o instanceof org.bukkit.block.Block h)) return null; return h.getHumidity(); }); });
        registry.registerExpression("(ai|artificial intelligence) of %object%", Object.class, a -> { Expression<?> src = a.get(0); return new ComputedExpression(ctx -> { Object o = src.getSingle(ctx); if (!(o instanceof org.bukkit.entity.LivingEntity h)) return null; return h.hasAI(); }); });
        registry.registerExpression("(head|eye[s]) [location[s]] of %object%", Object.class, a -> { Expression<?> src = a.get(0); return new ComputedExpression(ctx -> { Object o = src.getSingle(ctx); if (!(o instanceof org.bukkit.entity.LivingEntity h)) return null; return h.getEyeLocation(); }); });
        registry.registerExpression("glowing of %object%", Object.class, a -> { Expression<?> src = a.get(0); return new ComputedExpression(ctx -> { Object o = src.getSingle(ctx); if (!(o instanceof org.bukkit.entity.Entity h)) return null; return h.isGlowing(); }); });
        registry.registerExpression("attack cooldown of %object%", Object.class, a -> { Expression<?> src = a.get(0); return new ComputedExpression(ctx -> { Object o = src.getSingle(ctx); if (!(o instanceof org.bukkit.entity.HumanEntity h)) return null; return h.getAttackCooldown(); }); });
        registry.registerExpression("squared length[s] of %object%", Object.class, a -> { Expression<?> src = a.get(0); return new ComputedExpression(ctx -> { Object o = src.getSingle(ctx); if (!(o instanceof org.bukkit.util.Vector h)) return null; return h.lengthSquared(); }); });
        registry.registerExpression("(lunar|moon) phase[s] of %object%", Object.class, a -> { Expression<?> src = a.get(0); return new ComputedExpression(ctx -> { Object o = src.getSingle(ctx); if (!(o instanceof org.bukkit.World h)) return null; return h.getMoonPhase(); }); });
        registry.registerExpression("gravity of %object%", Object.class, a -> { Expression<?> src = a.get(0); return new ComputedExpression(ctx -> { Object o = src.getSingle(ctx); if (!(o instanceof org.bukkit.entity.Entity h)) return null; return h.hasGravity(); }); });
        registry.registerExpression("temperature[s] of %object%", Object.class, a -> { Expression<?> src = a.get(0); return new ComputedExpression(ctx -> { Object o = src.getSingle(ctx); if (!(o instanceof org.bukkit.block.Block h)) return null; return h.getTemperature(); }); });
        registry.registerExpression("difficult(y|ies) of %object%", Object.class, a -> { Expression<?> src = a.get(0); return new ComputedExpression(ctx -> { Object o = src.getSingle(ctx); if (!(o instanceof org.bukkit.World h)) return null; return h.getDifficulty(); }); });
        registry.registerExpression("(vector|standard|normal) length[s] of %object%", Object.class, a -> { Expression<?> src = a.get(0); return new ComputedExpression(ctx -> { Object o = src.getSingle(ctx); if (!(o instanceof org.bukkit.util.Vector h)) return null; return h.length(); }); });
        registry.registerExpression("[last] resource pack response[s] of %object%", Object.class, a -> { Expression<?> src = a.get(0); return new ComputedExpression(ctx -> { Object o = src.getSingle(ctx); if (!(o instanceof org.bukkit.entity.Player h)) return null; return h.getResourcePackStatus(); }); });
        registry.registerExpression("compass target of %object%", Object.class, a -> { Expression<?> src = a.get(0); return new ComputedExpression(ctx -> { Object o = src.getSingle(ctx); if (!(o instanceof org.bukkit.entity.Player h)) return null; return h.getCompassTarget(); }); });
        registry.registerExpression("altitude[s] of %object%", Object.class, a -> { Expression<?> src = a.get(0); return new ComputedExpression(ctx -> { Object o = src.getSingle(ctx); if (!(o instanceof org.bukkit.Location h)) return null; return h.getY(); }); });
        registry.registerExpression("fl(y[ing]|ight) (mode|state) of %object%", Object.class, a -> { Expression<?> src = a.get(0); return new ComputedExpression(ctx -> { Object o = src.getSingle(ctx); if (!(o instanceof org.bukkit.entity.Player h)) return null; return h.getAllowFlight(); }); });
        registry.registerExpression("(gliding|glider) [state] of %object%", Object.class, a -> { Expression<?> src = a.get(0); return new ComputedExpression(ctx -> { Object o = src.getSingle(ctx); if (!(o instanceof org.bukkit.entity.LivingEntity h)) return null; return h.isGliding(); }); });
    }
}
