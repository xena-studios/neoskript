package co.xenastudios.neoskript.lang;

import co.xenastudios.neoskript.api.syntax.Expression;
import co.xenastudios.neoskript.api.registry.SyntaxRegistry;

/** Generated bulk conditions (source-grounded). One per line for compile-drop. */
public final class GenCondBulk2 {
    private GenCondBulk2() {}

    public static void register(SyntaxRegistry registry) {
        registry.registerCondition("%object% can see all messages [in chat]", a -> { Expression<?> src = a.get(0); return ctx -> { Object o = src.getSingle(ctx); return o instanceof org.bukkit.entity.Player h && (h.getClientOption(com.destroystokyo.paper.ClientOption.CHAT_VISIBILITY) == com.destroystokyo.paper.ClientOption.ChatVisibility.FULL); }; });
        registry.registerCondition("%object% can only see (commands|system messages) [in chat]", a -> { Expression<?> src = a.get(0); return ctx -> { Object o = src.getSingle(ctx); return o instanceof org.bukkit.entity.Player h && (h.getClientOption(com.destroystokyo.paper.ClientOption.CHAT_VISIBILITY) == com.destroystokyo.paper.ClientOption.ChatVisibility.SYSTEM); }; });
        registry.registerCondition("%object% can('t|[ ]not) see any (command[s]|message[s]) [in chat]", a -> { Expression<?> src = a.get(0); return ctx -> { Object o = src.getSingle(ctx); return o instanceof org.bukkit.entity.Player h && (h.getClientOption(com.destroystokyo.paper.ClientOption.CHAT_VISIBILITY) == com.destroystokyo.paper.ClientOption.ChatVisibility.HIDDEN); }; });
        registry.registerCondition("%object% (was|were) (indirectly caused|caused indirectly)", a -> { Expression<?> src = a.get(0); return ctx -> { Object o = src.getSingle(ctx); return o instanceof org.bukkit.damage.DamageSource h && (h.isIndirect()); }; });
        registry.registerCondition("%object% (was|were) (directly caused|caused directly)", a -> { Expression<?> src = a.get(0); return ctx -> { Object o = src.getSingle(ctx); return o instanceof org.bukkit.damage.DamageSource h && (!h.isIndirect()); }; });
        registry.registerCondition("%object% (has|have) (any|a) horn", a -> { Expression<?> src = a.get(0); return ctx -> { Object o = src.getSingle(ctx); return o instanceof org.bukkit.entity.LivingEntity h && (h instanceof org.bukkit.entity.Goat goat && (goat.hasLeftHorn() || goat.hasRightHorn())); }; });
        registry.registerCondition("%object% (has|have) [a] left horn[s]", a -> { Expression<?> src = a.get(0); return ctx -> { Object o = src.getSingle(ctx); return o instanceof org.bukkit.entity.LivingEntity h && (h instanceof org.bukkit.entity.Goat goat && goat.hasLeftHorn()); }; });
        registry.registerCondition("%object% (has|have) [a] right horn[s]", a -> { Expression<?> src = a.get(0); return ctx -> { Object o = src.getSingle(ctx); return o instanceof org.bukkit.entity.LivingEntity h && (h instanceof org.bukkit.entity.Goat goat && goat.hasRightHorn()); }; });
        registry.registerCondition("%object% (has|have) both horns", a -> { Expression<?> src = a.get(0); return ctx -> { Object o = src.getSingle(ctx); return o instanceof org.bukkit.entity.LivingEntity h && (h instanceof org.bukkit.entity.Goat goat && goat.hasLeftHorn() && goat.hasRightHorn()); }; });
        registry.registerCondition("%object% (has|have) [custom] model data floats", a -> { Expression<?> src = a.get(0); return ctx -> { Object o = src.getSingle(ctx); return o instanceof org.bukkit.inventory.ItemStack h && (h.hasItemMeta() && !h.getItemMeta().getCustomModelDataComponent().getFloats().isEmpty()); }; });
        registry.registerCondition("%object% (has|have) [custom] model data flags", a -> { Expression<?> src = a.get(0); return ctx -> { Object o = src.getSingle(ctx); return o instanceof org.bukkit.inventory.ItemStack h && (h.hasItemMeta() && !h.getItemMeta().getCustomModelDataComponent().getFlags().isEmpty()); }; });
        registry.registerCondition("%object% (has|have) [custom] model data strings", a -> { Expression<?> src = a.get(0); return ctx -> { Object o = src.getSingle(ctx); return o instanceof org.bukkit.inventory.ItemStack h && (h.hasItemMeta() && !h.getItemMeta().getCustomModelDataComponent().getStrings().isEmpty()); }; });
        registry.registerCondition("%object% (has|have) [custom] model data colo[u]rs", a -> { Expression<?> src = a.get(0); return ctx -> { Object o = src.getSingle(ctx); return o instanceof org.bukkit.inventory.ItemStack h && (h.hasItemMeta() && !h.getItemMeta().getCustomModelDataComponent().getColors().isEmpty()); }; });
        registry.registerCondition("[the] [entire] tool[ ]tip[s] of %object% (is|are) shown", a -> { Expression<?> src = a.get(0); return ctx -> { Object o = src.getSingle(ctx); return o instanceof org.bukkit.inventory.ItemStack h && (!(h.hasItemMeta() && h.getItemMeta().isHideTooltip())); }; });
        registry.registerCondition("[the] [entire] tool[ ]tip[s] of %object% (is|are) hidden", a -> { Expression<?> src = a.get(0); return ctx -> { Object o = src.getSingle(ctx); return o instanceof org.bukkit.inventory.ItemStack h && (h.hasItemMeta() && h.getItemMeta().isHideTooltip()); }; });
        registry.registerCondition("[the] additional tool[ ]tip[s] of %object% (is|are) shown", a -> { Expression<?> src = a.get(0); return ctx -> { Object o = src.getSingle(ctx); return o instanceof org.bukkit.inventory.ItemStack h && (!(h.hasItemMeta() && h.getItemMeta().hasItemFlag(org.bukkit.inventory.ItemFlag.HIDE_ADDITIONAL_TOOLTIP))); }; });
        registry.registerCondition("[the] additional tool[ ]tip[s] of %object% (is|are) hidden", a -> { Expression<?> src = a.get(0); return ctx -> { Object o = src.getSingle(ctx); return o instanceof org.bukkit.inventory.ItemStack h && (h.hasItemMeta() && h.getItemMeta().hasItemFlag(org.bukkit.inventory.ItemFlag.HIDE_ADDITIONAL_TOOLTIP)); }; });




        registry.registerCondition("%object%'[s] main hand[s] (is|are) raised", a -> { Expression<?> src = a.get(0); return ctx -> { Object o = src.getSingle(ctx); return o instanceof org.bukkit.entity.LivingEntity h && (h.isHandRaised() && h.getHandRaised() == org.bukkit.inventory.EquipmentSlot.HAND); }; });
        registry.registerCondition("%object%'[s] off[ |-]hand[s] (is|are) raised", a -> { Expression<?> src = a.get(0); return ctx -> { Object o = src.getSingle(ctx); return o instanceof org.bukkit.entity.LivingEntity h && (h.isHandRaised() && h.getHandRaised() == org.bukkit.inventory.EquipmentSlot.OFF_HAND); }; });
        registry.registerCondition("%object%'[s] hand[s] (is|are) raised", a -> { Expression<?> src = a.get(0); return ctx -> { Object o = src.getSingle(ctx); return o instanceof org.bukkit.entity.LivingEntity h && (h.isHandRaised()); }; });
        registry.registerCondition("%object% (is|are) infinite", a -> { Expression<?> src = a.get(0); return ctx -> { Object o = src.getSingle(ctx); return o instanceof org.bukkit.potion.PotionEffect h && (h.isInfinite()); }; });
        registry.registerCondition("world[s] %object% (is|are) loaded", a -> { Expression<?> src = a.get(0); return ctx -> { Object o = src.getSingle(ctx); return o instanceof org.bukkit.World h && (org.bukkit.Bukkit.getWorld(h.getName()) != null); }; });
        registry.registerCondition("chunk[s] at %object% (is|are) loaded", a -> { Expression<?> src = a.get(0); return ctx -> { Object o = src.getSingle(ctx); return o instanceof org.bukkit.Location h && (h.getWorld() != null && h.getWorld().isChunkLoaded(h.getBlockX() >> 4, h.getBlockZ() >> 4)); }; });
    }
}
