package co.xenastudios.neoskript.lang.type;

import co.xenastudios.neoskript.core.type.TypeRegistry;

/** Class/enum/registry-backed value types (source-grounded). One registration per line for compile-drop. */
public final class GenTypes3 {
    private GenTypes3() {}

    public static void register(TypeRegistry types) {
        types.register(new ClassType<>("block", org.bukkit.block.Block.class, b -> b.getType().name().toLowerCase()));
        types.register(new ClassType<>("blockdata", org.bukkit.block.data.BlockData.class, org.bukkit.block.data.BlockData::getAsString));
        types.register(new ClassType<>("chunk", org.bukkit.Chunk.class));
        types.register(new ClassType<>("commandsender", org.bukkit.command.CommandSender.class, org.bukkit.command.CommandSender::getName));
        types.register(new ClassType<>("damagesource", org.bukkit.damage.DamageSource.class));
        types.register(new ClassType<>("display", org.bukkit.entity.Display.class));
        types.register(new ClassType<>("entity", org.bukkit.entity.Entity.class, org.bukkit.entity.Entity::getName));
        types.register(new ClassType<>("entitysnapshot", org.bukkit.entity.EntitySnapshot.class));
        types.register(new ClassType<>("enchantmentoffer", org.bukkit.enchantments.EnchantmentOffer.class));
        types.register(new ClassType<>("enchantmenttype", org.bukkit.enchantments.Enchantment.class, e -> String.valueOf(e.getKey())));
        types.register(new ClassType<>("fireworkeffect", org.bukkit.FireworkEffect.class));
        types.register(new ClassType<>("inventory", org.bukkit.inventory.Inventory.class));
        types.register(new ClassType<>("inventoryholder", org.bukkit.inventory.InventoryHolder.class));
        types.register(new ClassType<>("itementity", org.bukkit.entity.Item.class));
        types.register(new ClassType<>("livingentity", org.bukkit.entity.LivingEntity.class, org.bukkit.entity.LivingEntity::getName));
        types.register(new ClassType<>("lootcontext", org.bukkit.loot.LootContext.class));
        types.register(new ClassType<>("loottable", org.bukkit.loot.LootTable.class, l -> String.valueOf(l.getKey())));
        types.register(new ClassType<>("metadataholder", org.bukkit.metadata.Metadatable.class));
        types.register(new ClassType<>("nameable", org.bukkit.Nameable.class));
        types.register(new ClassType<>("offlineplayer", org.bukkit.OfflinePlayer.class, p -> String.valueOf(p.getName())));
        types.register(new ClassType<>("potioneffect", org.bukkit.potion.PotionEffect.class));
        types.register(new ClassType<>("projectile", org.bukkit.entity.Projectile.class));
        types.register(new ClassType<>("teleportflag", io.papermc.paper.entity.TeleportFlag.class));
        types.register(new ClassType<>("textcomponent", net.kyori.adventure.text.Component.class));
        types.register(new ClassType<>("uuid", java.util.UUID.class, java.util.UUID::toString, java.util.UUID::fromString));
        types.register(new ClassType<>("vehicle", org.bukkit.entity.Vehicle.class));
        types.register(new ClassType<>("worldborder", org.bukkit.WorldBorder.class));
        types.register(new ClassType<>("bannerpattern", org.bukkit.block.banner.Pattern.class));
        types.register(new ClassType<>("cachedservericon", org.bukkit.util.CachedServerIcon.class));

        types.register(new ClassType<>("minecrafttag", org.bukkit.Tag.class));
        types.register(new ClassType<>("quaternion", org.joml.Quaternionf.class));
        types.register(new ClassType<>("timespan", co.xenastudios.neoskript.core.runtime.Timespan.class,
                co.xenastudios.neoskript.core.runtime.Timespan::toString,
                s -> co.xenastudios.neoskript.core.runtime.Timespan.parse(s).orElse(null)));
        types.register(new ClassType<>("integer", java.lang.Integer.class, String::valueOf, Integer::parseInt));
        types.register(new ClassType<>("long", java.lang.Long.class, String::valueOf, Long::parseLong));
        types.register(new ClassType<>("short", java.lang.Short.class, String::valueOf, Short::parseShort));
        types.register(new ClassType<>("byte", java.lang.Byte.class, String::valueOf, Byte::parseByte));
        types.register(new ClassType<>("double", java.lang.Double.class, String::valueOf, Double::parseDouble));
        types.register(new ClassType<>("float", java.lang.Float.class, String::valueOf, Float::parseFloat));
        types.register(new EnumType<>("gene", org.bukkit.entity.Panda.Gene.class));
        types.register(new EnumType<>("entitydata", org.bukkit.entity.EntityType.class));
        types.register(new EnumType<>("particle", org.bukkit.Particle.class));
        types.register(new EnumType<>("treetype", org.bukkit.TreeType.class));
        types.register(new ClassType<>("skriptpotioneffect", org.bukkit.potion.PotionEffect.class));
        types.register(new ClassType<>("object", java.lang.Object.class));
        types.register(new ClassType<>("direction", Direction.class));
        types.register(new ClassType<>("gamerule", org.bukkit.GameRule.class));
        types.register(new ClassType<>("equippablecomponent", io.papermc.paper.datacomponent.item.Equippable.class));
        types.register(new ClassType<>("audience", net.kyori.adventure.audience.Audience.class));
    }
}
