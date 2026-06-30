package co.xenastudios.neoskript.testkit;

import co.xenastudios.neoskript.core.lexer.Token;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConformanceHarnessTest {

    @Test
    void tokenizesSampleScript() {
        ConformanceHarness harness = new ConformanceHarness();
        List<Token> tokens = harness.tokenize("scripts/hello.sk");

        assertFalse(tokens.isEmpty(), "expected tokens from the sample script");
        assertTrue(
                tokens.stream().anyMatch(t -> t.text().equals("broadcast")),
                "expected the 'broadcast' keyword to be tokenized");
    }
}
