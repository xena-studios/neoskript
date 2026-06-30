package co.xenastudios.neoskript.lang;

import co.xenastudios.neoskript.lang.type.EnumType;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.EntityType;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnumTypeTest {

    @Test
    void parsesAndDisplaysEnumConstants() {
        EnumType<EntityType> entities = new EnumType<>("entitytype", EntityType.class);
        assertEquals(EntityType.CREEPER, entities.parse("creeper").orElseThrow());
        assertEquals("creeper", entities.toDisplayString(EntityType.CREEPER));
        assertTrue(entities.parse("not_a_real_entity").isEmpty());
    }

    @Test
    void normalizesSpacesAndHyphens() {
        EnumType<DamageCause> causes = new EnumType<>("damagecause", DamageCause.class);
        assertEquals(DamageCause.FALL, causes.parse("fall").orElseThrow());
        // spaces and hyphens both map to the underscore in the constant name
        assertEquals(DamageCause.ENTITY_ATTACK, causes.parse("entity attack").orElseThrow());
        assertEquals(DamageCause.ENTITY_ATTACK, causes.parse("ENTITY-ATTACK").orElseThrow());
    }

    @Test
    void displayReplacesUnderscoresWithSpaces() {
        EnumType<BlockFace> faces = new EnumType<>("blockface", BlockFace.class);
        assertEquals("north east", faces.toDisplayString(BlockFace.NORTH_EAST));
    }
}
