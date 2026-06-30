package co.xenastudios.neoskript.lang;

import co.xenastudios.neoskript.api.syntax.Expression;
import co.xenastudios.neoskript.api.registry.SyntaxRegistry;
import co.xenastudios.neoskript.core.expression.ComputedExpression;

/** Generated bulk registration (eff). One registration per line for compile-drop. */
public final class GenEffects {
    private GenEffects() {}

    public static void register(SyntaxRegistry registry) {
        registry.registerEffect("allow %object% to duplicate", a -> { Expression<?> src = a.get(0); return ctx -> { Object o = src.getSingle(ctx); if (o instanceof org.bukkit.entity.Allay h) { h.setCanDuplicate(true); } }; });
        registry.registerEffect("prevent %object% from duplicating", a -> { Expression<?> src = a.get(0); return ctx -> { Object o = src.getSingle(ctx); if (o instanceof org.bukkit.entity.Allay h) { h.setCanDuplicate(false); } }; });
        registry.registerEffect("lock age of %object%", a -> { Expression<?> src = a.get(0); return ctx -> { Object o = src.getSingle(ctx); if (o instanceof org.bukkit.entity.Breedable h) { h.setAgeLock(true); } }; });
        registry.registerEffect("allow %object% to age", a -> { Expression<?> src = a.get(0); return ctx -> { Object o = src.getSingle(ctx); if (o instanceof org.bukkit.entity.Breedable h) { h.setAgeLock(false); } }; });
        registry.registerEffect("cancel usage of %object%'s active item", a -> { Expression<?> src = a.get(0); return ctx -> { Object o = src.getSingle(ctx); if (o instanceof org.bukkit.entity.LivingEntity h) { h.clearActiveItem(); } }; });
        registry.registerEffect("make %object% teleport randomly", a -> { Expression<?> src = a.get(0); return ctx -> { Object o = src.getSingle(ctx); if (o instanceof org.bukkit.entity.Enderman h) { h.teleport(); } }; });
        registry.registerEffect("make %object% despawn on chunk unload", a -> { Expression<?> src = a.get(0); return ctx -> { Object o = src.getSingle(ctx); if (o instanceof org.bukkit.entity.LivingEntity h) { h.setRemoveWhenFarAway(true); } }; });
        registry.registerEffect("prevent %object% from despawning", a -> { Expression<?> src = a.get(0); return ctx -> { Object o = src.getSingle(ctx); if (o instanceof org.bukkit.entity.LivingEntity h) { h.setRemoveWhenFarAway(false); } }; });
        registry.registerEffect("prevent %object% from naturally despawning", a -> { Expression<?> src = a.get(0); return ctx -> { Object o = src.getSingle(ctx); if (o instanceof org.bukkit.entity.Item h) { h.setUnlimitedLifetime(true); } }; });
        registry.registerEffect("unleash %object%", a -> { Expression<?> src = a.get(0); return ctx -> { Object o = src.getSingle(ctx); if (o instanceof org.bukkit.entity.LivingEntity h) { h.setLeashHolder(null); } }; });
        registry.registerEffect("make %object% an adult", a -> { Expression<?> src = a.get(0); return ctx -> { Object o = src.getSingle(ctx); if (o instanceof org.bukkit.entity.Ageable h) { h.setAdult(); } }; });
        registry.registerEffect("make %object% a baby", a -> { Expression<?> src = a.get(0); return ctx -> { Object o = src.getSingle(ctx); if (o instanceof org.bukkit.entity.Ageable h) { h.setBaby(); } }; });
        registry.registerEffect("make %object% duplicate", a -> { Expression<?> src = a.get(0); return ctx -> { Object o = src.getSingle(ctx); if (o instanceof org.bukkit.entity.Allay h) { h.duplicateAllay(); } }; });
        registry.registerEffect("make %object% breedable", a -> { Expression<?> src = a.get(0); return ctx -> { Object o = src.getSingle(ctx); if (o instanceof org.bukkit.entity.Breedable h) { h.setBreed(true); } }; });
        registry.registerEffect("sterilize %object%", a -> { Expression<?> src = a.get(0); return ctx -> { Object o = src.getSingle(ctx); if (o instanceof org.bukkit.entity.Breedable h) { h.setBreed(false); } }; });
        registry.registerEffect("save %object%", a -> { Expression<?> src = a.get(0); return ctx -> { Object o = src.getSingle(ctx); if (o instanceof org.bukkit.World h) { h.save(); } }; });
        registry.registerEffect("make %object% start sprinting", a -> { Expression<?> src = a.get(0); return ctx -> { Object o = src.getSingle(ctx); if (o instanceof org.bukkit.entity.Player h) { h.setSprinting(true); } }; });
        registry.registerEffect("make %object% stop sprinting", a -> { Expression<?> src = a.get(0); return ctx -> { Object o = src.getSingle(ctx); if (o instanceof org.bukkit.entity.Player h) { h.setSprinting(false); } }; });
        registry.registerEffect("make %object% start shivering", a -> { Expression<?> src = a.get(0); return ctx -> { Object o = src.getSingle(ctx); if (o instanceof org.bukkit.entity.Strider h) { h.setShivering(true); } }; });
        registry.registerEffect("make %object% stop shivering", a -> { Expression<?> src = a.get(0); return ctx -> { Object o = src.getSingle(ctx); if (o instanceof org.bukkit.entity.Strider h) { h.setShivering(false); } }; });
        registry.registerEffect("make %object% swing their main hand", a -> { Expression<?> src = a.get(0); return ctx -> { Object o = src.getSingle(ctx); if (o instanceof org.bukkit.entity.LivingEntity h) { h.swingMainHand(); } }; });
        registry.registerEffect("make %object% swing their off hand", a -> { Expression<?> src = a.get(0); return ctx -> { Object o = src.getSingle(ctx); if (o instanceof org.bukkit.entity.LivingEntity h) { h.swingOffHand(); } }; });
        registry.registerEffect("tame %object%", a -> { Expression<?> src = a.get(0); return ctx -> { Object o = src.getSingle(ctx); if (o instanceof org.bukkit.entity.Tameable h) { h.setTamed(true); } }; });
        registry.registerEffect("untame %object%", a -> { Expression<?> src = a.get(0); return ctx -> { Object o = src.getSingle(ctx); if (o instanceof org.bukkit.entity.Tameable h) { h.setTamed(false); } }; });
        registry.registerEffect("apply drop shadow to %object%", a -> { Expression<?> src = a.get(0); return ctx -> { Object o = src.getSingle(ctx); if (o instanceof org.bukkit.entity.TextDisplay h) { h.setShadowed(true); } }; });
        registry.registerEffect("remove drop shadow from %object%", a -> { Expression<?> src = a.get(0); return ctx -> { Object o = src.getSingle(ctx); if (o instanceof org.bukkit.entity.TextDisplay h) { h.setShadowed(false); } }; });
        registry.registerEffect("make %object% visible through blocks", a -> { Expression<?> src = a.get(0); return ctx -> { Object o = src.getSingle(ctx); if (o instanceof org.bukkit.entity.TextDisplay h) { h.setSeeThrough(true); } }; });
        registry.registerEffect("prevent %object% from being visible through blocks", a -> { Expression<?> src = a.get(0); return ctx -> { Object o = src.getSingle(ctx); if (o instanceof org.bukkit.entity.TextDisplay h) { h.setSeeThrough(false); } }; });
        registry.registerEffect("clear the title of %object%", a -> { Expression<?> src = a.get(0); return ctx -> { Object o = src.getSingle(ctx); if (o instanceof net.kyori.adventure.audience.Audience h) { h.clearTitle(); } }; });
        registry.registerEffect("reset the title of %object%", a -> { Expression<?> src = a.get(0); return ctx -> { Object o = src.getSingle(ctx); if (o instanceof net.kyori.adventure.audience.Audience h) { h.resetTitle(); } }; });
        registry.registerEffect("show the custom name of %object%", a -> { Expression<?> src = a.get(0); return ctx -> { Object o = src.getSingle(ctx); if (o instanceof org.bukkit.entity.Entity h) { h.setCustomNameVisible(true); } }; });
        registry.registerEffect("hide the custom name of %object%", a -> { Expression<?> src = a.get(0); return ctx -> { Object o = src.getSingle(ctx); if (o instanceof org.bukkit.entity.Entity h) { h.setCustomNameVisible(false); } }; });
        registry.registerEffect("allow %object% to pick up items", a -> { Expression<?> src = a.get(0); return ctx -> { Object o = src.getSingle(ctx); if (o instanceof org.bukkit.entity.LivingEntity h) { h.setCanPickupItems(true); } }; });
        registry.registerEffect("forbid %object% from picking up items", a -> { Expression<?> src = a.get(0); return ctx -> { Object o = src.getSingle(ctx); if (o instanceof org.bukkit.entity.LivingEntity h) { h.setCanPickupItems(false); } }; });
    }
}
