package co.xenastudios.neoskript.plugin;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import java.io.IOException;import java.nio.charset.StandardCharsets;import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
class BlockSoundTest {
    ServerMock server; NeoSkriptPlugin plugin;
    @BeforeEach void a(){server=MockBukkit.mock();plugin=MockBukkit.load(NeoSkriptPlugin.class);}
    @AfterEach void b(){MockBukkit.unmock();}
    // Parse-verify only: MockBukkit doesn't implement BlockData#getSoundGroup, so evaluating the
    // expression throws at runtime. We confirm the trigger PARSED by asserting a broadcast placed
    // before the block-sound usage fires (a parse error would disable the whole trigger).
    @Test void parses() throws IOException {
        Path s=plugin.getDataFolder().toPath().resolve("scripts");Files.createDirectories(s);
        Files.writeString(s.resolve("snd.sk"), """
                command /snd:
                    trigger:
                        broadcast "STARTED"
                        set {_a} to break sound of stone
                        set {_b} to step sound of dirt
                        broadcast "GOT"
                """, StandardCharsets.UTF_8);
        server.dispatchCommand(server.getConsoleSender(),"neoskript reload");
        PlayerMock p=server.addPlayer();
        server.dispatchCommand(p,"snd");
        boolean started=false; String x;
        while((x=p.nextMessage())!=null) if(x.equals("STARTED")) started=true;
        assertTrue(started, "trigger parsed and ran (block-sound expression compiled)");
    }
}
