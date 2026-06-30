package co.xenastudios.neoskript.core.registry;

import co.xenastudios.neoskript.core.registry.DefaultSyntaxRegistry.EffectEntry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultSyntaxRegistryTest {

    @Test
    void candidatesAreFilteredByLeadingWord() {
        DefaultSyntaxRegistry registry = new DefaultSyntaxRegistry();
        registry.registerEffect("send %string%", arguments -> ctx -> {
        });
        registry.registerEffect("broadcast %string%", arguments -> ctx -> {
        });

        List<EffectEntry> sendCandidates = registry.effectCandidates("send \"hi\"");
        assertEquals(1, sendCandidates.size());
        assertEquals("send", sendCandidates.get(0).pattern().firstLiteral());

        assertTrue(registry.effectCandidates("teleport player").isEmpty(),
                "no effect starts with 'teleport', so there should be no candidates");
    }

    @Test
    void argumentLeadingPatternsAreAlwaysCandidates() {
        DefaultSyntaxRegistry registry = new DefaultSyntaxRegistry();
        registry.registerCondition("%object% (is|=) %object%", arguments -> ctx -> true);

        // The condition begins with an argument, so it must be a candidate regardless of first word.
        assertEquals(1, registry.conditionCandidates("{_x} is 5").size());
        assertEquals(1, registry.conditionCandidates("\"a\" is \"b\"").size());
    }

    @Test
    void mergedCandidatesKeepRegistrationOrder() {
        DefaultSyntaxRegistry registry = new DefaultSyntaxRegistry();
        registry.registerEffect("give %object%", arguments -> ctx -> {     // order 0, word "give"
        });
        registry.registerEffect("%object% wins", arguments -> ctx -> {     // order 1, wildcard
        });
        registry.registerEffect("give up", arguments -> ctx -> {          // order 2, word "give"
        });

        List<EffectEntry> candidates = registry.effectCandidates("give up");
        // Should include both "give" entries and the wildcard, ordered by registration.
        assertEquals(List.of(0, 1, 2), candidates.stream().map(EffectEntry::order).toList());
    }
}
