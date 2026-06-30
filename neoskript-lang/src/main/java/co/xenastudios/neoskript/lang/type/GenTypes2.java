package co.xenastudios.neoskript.lang.type;
import co.xenastudios.neoskript.core.type.TypeRegistry;

/** Generated bulk type registrations (round 2). */
public final class GenTypes2 {
    private GenTypes2() {}

    public static void register(TypeRegistry types) {
        types.register(new EnumType<>("experiencecooldownchangereason", org.bukkit.event.player.PlayerExpCooldownChangeEvent.ChangeReason.class));
        types.register(new EnumType<>("villagercareerchangereason", org.bukkit.event.entity.VillagerCareerChangeEvent.ChangeReason.class));
    }
}
