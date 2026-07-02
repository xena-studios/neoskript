package co.xenastudios.neoskript.plugin;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import java.io.IOException;import java.nio.charset.StandardCharsets;import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
class ArithmeticTest {
    ServerMock server; NeoSkriptPlugin plugin;
    @BeforeEach void a(){server=MockBukkit.mock();plugin=MockBukkit.load(NeoSkriptPlugin.class);}
    @AfterEach void b(){MockBukkit.unmock();}
    @Test void d() throws IOException {
        Path s=plugin.getDataFolder().toPath().resolve("scripts");Files.createDirectories(s);
        Files.writeString(s.resolve("ar.sk"), """
                command /ar:
                    trigger:
                        set {_x} to (5 + 3) * 2 - 4 / 2
                        broadcast "R:%{_x}%"
                        broadcast "P:%2 ^ 10%"
                        broadcast "RA:%2 ^ 3 ^ 2%"
                        broadcast "PR:%3 + 2 ^ 3%"
                """, StandardCharsets.UTF_8);
        server.dispatchCommand(server.getConsoleSender(),"neoskript reload");
        PlayerMock p=server.addPlayer();
        server.dispatchCommand(p,"ar");
        double r=Double.NaN,pw=Double.NaN,ra=Double.NaN,pr=Double.NaN;String x;
        while((x=p.nextMessage())!=null){
            if(x.startsWith("R:"))r=Double.parseDouble(x.substring(2));
            if(x.startsWith("P:"))pw=Double.parseDouble(x.substring(2));
            if(x.startsWith("RA:"))ra=Double.parseDouble(x.substring(3));
            if(x.startsWith("PR:"))pr=Double.parseDouble(x.substring(3));
        }
        assertEquals(14.0,r,1e-9,"(5+3)*2-4/2");
        assertEquals(1024.0,pw,1e-9,"2^10");
        assertEquals(512.0,ra,1e-9,"2^3^2 right-assoc = 2^(3^2)=512");
        assertEquals(11.0,pr,1e-9,"3 + 2^3 = 11 (^ binds tighter)");
    }
}
