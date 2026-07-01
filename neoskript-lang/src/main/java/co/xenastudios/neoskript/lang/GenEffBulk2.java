package co.xenastudios.neoskript.lang;

import co.xenastudios.neoskript.api.syntax.Expression;
import co.xenastudios.neoskript.api.registry.SyntaxRegistry;

/** Generated bulk effects (source-grounded). One per line for compile-drop. */
public final class GenEffBulk2 {
    private GenEffBulk2() {}

    public static void register(SyntaxRegistry registry) {
        registry.registerEffect("ban %object%", a -> { Expression<?> src = a.get(0); return ctx -> { Object o = src.getSingle(ctx); if (o instanceof org.bukkit.OfflinePlayer h) { String n = h.getName(); if (n != null) org.bukkit.Bukkit.getBanList(org.bukkit.BanList.Type.NAME).addBan(n, null, (java.util.Date) null, "Skript ban effect"); } }; });
        registry.registerEffect("unban %object%", a -> { Expression<?> src = a.get(0); return ctx -> { Object o = src.getSingle(ctx); if (o instanceof org.bukkit.OfflinePlayer h) { String n = h.getName(); if (n != null) org.bukkit.Bukkit.getBanList(org.bukkit.BanList.Type.NAME).pardon(n); } }; });
        registry.registerEffect("transfer %object% to server %object%", a -> { Expression<?> src = a.get(0); Expression<?> ve = a.get(1); return ctx -> { Object o = src.getSingle(ctx); Object v = ve.getSingle(ctx); if (o instanceof org.bukkit.entity.Player h) { if (h.isOnline() && v instanceof String s) h.transfer(s, 25565); } }; });
        registry.registerEffect("knockback %object% %object%", a -> { Expression<?> src = a.get(0); Expression<?> ve = a.get(1); return ctx -> { Object o = src.getSingle(ctx); Object v = ve.getSingle(ctx); if (o instanceof org.bukkit.entity.LivingEntity h) { if (v instanceof org.bukkit.util.Vector vec) { h.knockback(1.0, -vec.getX(), -vec.getZ()); h.setVelocity(h.getVelocity()); }; } }; });
        registry.registerEffect("launch firework at %object% with effect %object%", a -> { Expression<?> src = a.get(0); Expression<?> ve = a.get(1); return ctx -> { Object o = src.getSingle(ctx); Object v = ve.getSingle(ctx); if (o instanceof org.bukkit.Location h) { org.bukkit.World w = h.getWorld(); if (w != null && v instanceof org.bukkit.FireworkEffect fe) { org.bukkit.entity.Firework f = w.spawn(h, org.bukkit.entity.Firework.class); org.bukkit.inventory.meta.FireworkMeta m = f.getFireworkMeta(); m.addEffect(fe); m.setPower(1); f.setFireworkMeta(m); }; } }; });
        registry.registerEffect("make %object% play [entity] effect %object%", a -> { Expression<?> src = a.get(0); Expression<?> ve = a.get(1); return ctx -> { Object o = src.getSingle(ctx); Object v = ve.getSingle(ctx); if (o instanceof org.bukkit.entity.Entity h) { if (v instanceof org.bukkit.EntityEffect ee && ee.isApplicableTo(h)) h.playEffect(ee); } }; });
    }
}
