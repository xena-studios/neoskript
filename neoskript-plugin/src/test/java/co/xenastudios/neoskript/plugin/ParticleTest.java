package co.xenastudios.neoskript.plugin;

import co.xenastudios.neoskript.lang.type.ParticleEffect;
import co.xenastudios.neoskript.lang.type.ParticleEffectType;
import org.bukkit.Particle;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Behaviour tests for the particle configuration subsystem (count/offset/distribution/speed + builders). */
class ParticleTest {
    ServerMock server; NeoSkriptPlugin plugin;

    @BeforeEach void a() { server = MockBukkit.mock(); plugin = MockBukkit.load(NeoSkriptPlugin.class); }
    @AfterEach void b() { MockBukkit.unmock(); }

    @Test void particleProperties() throws IOException {
        Path s = plugin.getDataFolder().toPath().resolve("scripts");
        Files.createDirectories(s);
        Files.writeString(s.resolve("pt.sk"), """
                command /pt:
                    trigger:
                        set {_p} to cloud
                        send "COUNT:%particle count of {_p}%" to player
                        set particle count of {_p} to 5
                        send "COUNT2:%particle count of {_p}%" to player
                        set particle offset of {_p} to vector 2, 3, 4
                        set {_o} to particle offset of {_p}
                        send "OFFX:%vector x of {_o}%" to player
                        set extra value of {_p} to 2
                        send "EXTRA:%extra value of {_p}%" to player
                        set {_q} to {_p} with an offset of vector 9, 9, 9
                        set {_qo} to particle offset of {_q}
                        set {_po} to particle offset of {_p}
                        send "COPYX:%vector x of {_qo}%" to player
                        send "ORIGX:%vector x of {_po}%" to player
                        set {_d} to cloud with a velocity of vector 1, 0, 0
                        send "VELCOUNT:%particle count of {_d}%" to player
                        set {_c} to portal
                        send "CONV:%particle count of {_c}%" to player
                """, StandardCharsets.UTF_8);
        server.dispatchCommand(server.getConsoleSender(), "neoskript reload");
        PlayerMock p = server.addPlayer();
        server.dispatchCommand(p, "pt");
        Set<String> seen = new HashSet<>();
        String m;
        while ((m = p.nextMessage()) != null) seen.add(m);

        assertTrue(seen.contains("COUNT:1"), "cloud's default count is 1; got " + seen);
        assertTrue(seen.contains("COUNT2:5"), "set particle count to 5");
        assertTrue(seen.contains("OFFX:2"), "offset x is 2 after set to vector 2,3,4");
        assertTrue(seen.contains("EXTRA:2"), "extra value set to 2");
        assertTrue(seen.contains("COPYX:9"), "the 'with an offset' copy has offset x 9");
        assertTrue(seen.contains("ORIGX:2"), "the original is unchanged by the builder (offset x still 2)");
        assertTrue(seen.contains("VELCOUNT:0"), "setting a velocity zeroes the count");
        assertTrue(seen.contains("CONV:1"), "a converging particle (portal) parses with default count 1");
    }

    @Test void categorySubtypesRestrictParsing() {
        // flame is directional, explosion is scalable, portal is converging.
        assertTrue(ParticleEffectType.directional().parse("flame").isPresent(), "flame is directional");
        assertTrue(ParticleEffectType.directional().parse("portal").isEmpty(), "portal is not directional");
        assertTrue(ParticleEffectType.scalable().parse("explosion").isPresent(), "explosion is scalable");
        assertTrue(ParticleEffectType.scalable().parse("flame").isEmpty(), "flame is not scalable");
        assertTrue(ParticleEffectType.converging().parse("portal").isPresent(), "portal is converging");
        assertTrue(ParticleEffectType.converging().parse("explosion").isEmpty(), "explosion is not converging");
        // The base type accepts any particle and applies Bukkit defaults.
        ParticleEffect base = ParticleEffectType.particle().parse("flame").orElseThrow();
        assertEquals(Particle.FLAME, base.particle());
        assertEquals(1, base.count(), "default count is 1");
        assertEquals(0.0, base.extra(), "default extra is 0");
    }
}
