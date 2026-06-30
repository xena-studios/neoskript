package co.xenastudios.neoskript.lang.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class EventValueResolverTest {

    /** A minimal event exposing values of distinct types for the resolver to find. */
    public static final class FakeEvent extends Event {
        private static final HandlerList HANDLERS = new HandlerList();

        public String getLabel() {
            return "hello";
        }

        public Integer getCount() {
            return 42;
        }

        @Override
        public HandlerList getHandlers() {
            return HANDLERS;
        }

        public static HandlerList getHandlerList() {
            return HANDLERS;
        }
    }

    @Test
    void resolvesValuesByType() {
        FakeEvent event = new FakeEvent();
        assertEquals("hello", EventValueResolver.resolve(event, String.class));
        assertEquals(42, EventValueResolver.resolve(event, Integer.class));
    }

    @Test
    void returnsNullForMissingTypeOrNullEvent() {
        assertNull(EventValueResolver.resolve(new FakeEvent(), java.util.List.class));
        assertNull(EventValueResolver.resolve(null, String.class));
    }
}
