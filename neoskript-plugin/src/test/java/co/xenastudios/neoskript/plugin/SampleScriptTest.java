package co.xenastudios.neoskript.plugin;

import co.xenastudios.neoskript.core.parser.ParseException;
import co.xenastudios.neoskript.core.parser.ScriptParser;
import co.xenastudios.neoskript.core.registry.DefaultSyntaxRegistry;
import co.xenastudios.neoskript.core.runtime.CommandRegistry;
import co.xenastudios.neoskript.core.runtime.EventRegistry;
import co.xenastudios.neoskript.core.runtime.FunctionRegistry;
import co.xenastudios.neoskript.core.runtime.Trigger;
import co.xenastudios.neoskript.lang.BuiltinFunctions;
import co.xenastudios.neoskript.lang.BuiltinModule;
import co.xenastudios.neoskript.lang.event.BuiltinEvents;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Loads the canonical Skript sample end-to-end and asserts every structure parses (no gaps). */
class SampleScriptTest {

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        MockBukkit.load(NeoSkriptPlugin.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void sampleScriptParsesFully() throws Exception {
        DefaultSyntaxRegistry registry = new DefaultSyntaxRegistry();
        BuiltinModule.registerAll(registry);
        EventRegistry events = new EventRegistry();
        BuiltinEvents.registerAll(events);
        FunctionRegistry functions = new FunctionRegistry();
        BuiltinFunctions.registerAll(functions);
        CommandRegistry commands = new CommandRegistry();

        String source;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("sample.sk")) {
            source = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        ScriptParser parser = new ScriptParser(registry, events, functions, commands);
        List<Trigger> triggers = parser.parse(source);
        StringBuilder sb = new StringBuilder("\nSAMPLE.SK parse errors (" + parser.errors().size() + "):\n");
        for (ParseException e : parser.errors()) {
            sb.append("  line ").append(e.line()).append(": ").append(e.getMessage()).append('\n');
        }
        // Every canonical sample structure must load; the whole point is drop-in compatibility.
        assertTrue(parser.errors().isEmpty(), sb.toString());
    }

    @Test
    void sampleScriptRunsEndToEnd() throws Exception {
        NeoSkriptPlugin plugin = (NeoSkriptPlugin) server.getPluginManager().getPlugin("NeoSkript");
        java.nio.file.Path scripts = plugin.getDataFolder().toPath().resolve("scripts");
        java.nio.file.Files.createDirectories(scripts);
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("sample.sk")) {
            java.nio.file.Files.write(scripts.resolve("sample.sk"), in.readAllBytes());
        }
        server.dispatchCommand(server.getConsoleSender(), "neoskript reload");

        org.mockbukkit.mockbukkit.entity.PlayerMock player = server.addPlayer("Steve");
        player.addAttachment(plugin, "demo.use", true);

        server.dispatchCommand(player, "demo hello");
        server.dispatchCommand(player, "fntest");
        java.util.List<String> messages = new java.util.ArrayList<>();
        String m;
        while ((m = player.nextMessage()) != null) {
            messages.add(m);
        }
        String plain = String.join("\n", messages).replaceAll("§.", "");
        assertTrue(plain.contains("Hello, Steve"), "/demo hello + greet() reach the sender; got:\n" + plain);
        assertTrue(plain.contains("fib(10) = 55"), "/fntest computes fib(10)=55 recursively; got:\n" + plain);
        assertTrue(plain.contains("Welcome, Steve"), "on join welcome fired; got:\n" + plain);
    }

    /** Regression guard for the core fix: one bad structure never discards the rest of the file. */
    @Test
    void oneBadLineDoesNotBreakTheWholeScript() throws Exception {
        DefaultSyntaxRegistry registry = new DefaultSyntaxRegistry();
        BuiltinModule.registerAll(registry);
        EventRegistry events = new EventRegistry();
        BuiltinEvents.registerAll(events);
        FunctionRegistry functions = new FunctionRegistry();
        BuiltinFunctions.registerAll(functions);
        ScriptParser parser = new ScriptParser(registry, events, functions, new CommandRegistry());
        List<Trigger> triggers = parser.parse(
                "on join:\n    completely bogus effect here\n\non join:\n    send \"ok\" to player\n");
        assertTrue(triggers.size() == 1, "the valid trigger loads despite the bad one");
        assertTrue(parser.errors().size() == 1, "the bad trigger is reported, not fatal");
    }
}
