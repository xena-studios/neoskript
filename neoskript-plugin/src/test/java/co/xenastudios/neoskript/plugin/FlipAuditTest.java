package co.xenastudios.neoskript.plugin;

import co.xenastudios.neoskript.core.parser.ScriptParser;
import co.xenastudios.neoskript.core.registry.DefaultSyntaxRegistry;
import co.xenastudios.neoskript.core.runtime.EventRegistry;
import co.xenastudios.neoskript.core.runtime.FunctionRegistry;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Audit: every inventory entry flipped to done/parse-verified (expression/condition/effect) must
 * actually parse in NeoSkript. Entries that fail are written to a report so they can be reverted —
 * this is the integrity gate against fuzzy mis-flips.
 */
class FlipAuditTest {

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
    void auditFlippedEntriesParse() throws Exception {
        DefaultSyntaxRegistry registry = new DefaultSyntaxRegistry();
        BuiltinModule.registerAll(registry);
        EventRegistry events = new EventRegistry();
        BuiltinEvents.registerAll(events);
        FunctionRegistry functions = new FunctionRegistry();
        BuiltinFunctions.registerAll(functions);

        String json;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("audit_lines.json")) {
            json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        // crude JSON parse of [{"id":..,"cat":..,"line":..}]
        Matcher m = Pattern.compile(
                "\\{\\s*\"id\":\\s*\"(.*?)\",\\s*\"cat\":\\s*\"(.*?)\",\\s*\"line\":\\s*\"(.*?)\"\\s*\\}")
                .matcher(json);
        List<String> failures = new ArrayList<>();
        int total = 0;
        while (m.find()) {
            total++;
            String id = m.group(1);
            String cat = m.group(2);
            String line = m.group(3).replace("\\\"", "\"").replace("\\\\", "\\");
            String script = switch (cat) {
                case "expression" -> "command /a:\n    trigger:\n        set {_x} to " + line + "\n";
                case "condition" -> "command /a:\n    trigger:\n        if " + line + ":\n            set {_r} to 1\n";
                default -> "command /a:\n    trigger:\n        " + line + "\n";
            };
            try {
                new ScriptParser(registry, events, functions).parse(script);
            } catch (Throwable t) {
                failures.add(id + "\t" + cat + "\t" + line + "\t" + t.getMessage());
            }
        }
        Path report = Path.of(System.getProperty("java.io.tmpdir"), "flip_audit_failures.txt");
        Files.write(report, failures);
        System.out.println("AUDIT total=" + total + " failures=" + failures.size() + " report=" + report);
        // Do not fail the build; this test produces a report consumed by the audit step.
    }
}
