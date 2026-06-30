package co.xenastudios.neoskript.lang.type;

import co.xenastudios.neoskript.core.type.TypeRegistry;

/** Generated bulk registration of enum/registry-backed types (resolved from Skript source). */
public final class GenTypes {
    private GenTypes() {}

    public static void register(TypeRegistry types) {
        types.register(new RegistryType<>("bannerpatterntype", org.bukkit.block.banner.PatternType.class, () -> org.bukkit.Registry.BANNER_PATTERN));
        types.register(new EnumType<>("billboard", org.bukkit.entity.Display.Billboard.class));
        types.register(new EnumType<>("bukkitparticle", org.bukkit.Particle.class));
        types.register(new EnumType<>("experiencecooldownchangereason", org.bukkit.event.player.PlayerExpCooldownChangeEvent.ChangeReason.class));
        types.register(new EnumType<>("inventoryclosereason", org.bukkit.event.inventory.InventoryCloseEvent.Reason.class));
        types.register(new EnumType<>("itemdisplaytransform", org.bukkit.entity.ItemDisplay.ItemDisplayTransform.class));
        types.register(new EnumType<>("itemflag", org.bukkit.inventory.ItemFlag.class));
        types.register(new EnumType<>("moonphase", io.papermc.paper.world.MoonPhase.class));
        types.register(new EnumType<>("potionaction", org.bukkit.event.entity.EntityPotionEffectEvent.Action.class));
        types.register(new EnumType<>("potioncause", org.bukkit.event.entity.EntityPotionEffectEvent.Cause.class));
        types.register(new RegistryType<>("potioneffecttype", org.bukkit.potion.PotionEffectType.class, () -> org.bukkit.Registry.MOB_EFFECT));
        types.register(new EnumType<>("potioneffecttypecategory", org.bukkit.potion.PotionEffectTypeCategory.class));
        types.register(new EnumType<>("quitreason", org.bukkit.event.player.PlayerQuitEvent.QuitReason.class));
        types.register(new EnumType<>("resourcepackstate", org.bukkit.event.player.PlayerResourcePackStatusEvent.Status.class));
        types.register(new EnumType<>("respawnreason", org.bukkit.event.player.PlayerRespawnEvent.RespawnReason.class));
        types.register(new EnumType<>("textalignment", org.bukkit.entity.TextDisplay.TextAlignment.class));
        types.register(new EnumType<>("transformreason", org.bukkit.event.entity.EntityTransformEvent.TransformReason.class));
        types.register(new EnumType<>("unleashreason", org.bukkit.event.entity.EntityUnleashEvent.UnleashReason.class));
        types.register(new RegistryType<>("wolfvariant", org.bukkit.entity.Wolf.Variant.class, () -> org.bukkit.Registry.WOLF_VARIANT));
    }
}
