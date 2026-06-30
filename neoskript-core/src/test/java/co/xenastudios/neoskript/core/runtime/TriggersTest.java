package co.xenastudios.neoskript.core.runtime;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TriggersTest {

    private static final List<Statement> BODY = List.of(ctx -> {
    });

    @Test
    void groupsOnlyEventTriggersByClassPreservingOrder() {
        Trigger join1 = new Trigger("join", String.class, BODY);
        Trigger join2 = new Trigger("join", String.class, BODY);
        Trigger quit = new Trigger("quit", Integer.class, BODY);
        Trigger periodic = Trigger.periodic(20, BODY);
        Trigger load = Trigger.onLoad(BODY);

        Map<Class<?>, List<Trigger>> grouped =
                Triggers.groupEventTriggers(List.of(join1, periodic, quit, join2, load));

        assertEquals(2, grouped.size());
        assertEquals(List.of(String.class, Integer.class), List.copyOf(grouped.keySet()));
        assertEquals(2, grouped.get(String.class).size());
        assertEquals(1, grouped.get(Integer.class).size());
        assertFalse(grouped.containsKey(null), "periodic/load triggers must be excluded");
    }
}
