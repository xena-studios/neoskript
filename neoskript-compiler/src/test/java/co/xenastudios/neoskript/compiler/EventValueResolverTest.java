/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.xenastudios.neoskript.api.registration.Registrar;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Event values resolved with Skript's steps: exact, subclasses, superclasses cast down, then converters. */
class EventValueResolverTest {

    static class Sender {}
    static class Mob extends Sender {}
    static class Human extends Mob {}
    static class Spot {}
    static class Brick {}

    static class Happening {}
    static class MobHappening extends Happening {
        final Object value;
        MobHappening(Object value) {
            this.value = value;
        }
    }
    static class Hurt extends MobHappening {
        Hurt(Object value) {
            super(value);
        }
    }

    private static EventValueResolver resolver(Converters converters, EventValueResolver.Value... values) {
        return new EventValueResolver(List.of(values), converters, c -> null);
    }

    private static EventValueResolver.Value value(Class<?> event, Class<?> type, int time) {
        return new EventValueResolver.Value(event, type, null, time, e -> ((MobHappening) e).value, null);
    }

    @Test
    void aSuperclassValueIsCastDownWhenItRuns() {
        EventValueResolver resolver = resolver(new Converters(), value(MobHappening.class, Mob.class, 0));
        var human = resolver.resolve(MobHappening.class, Human.class, null, 0, EventValueResolver.DEFAULT);
        assertTrue(human.successful());
        Human someone = new Human();
        assertEquals(someone, human.all().getFirst().getter().apply(new MobHappening(someone)));
        assertNull(human.all().getFirst().getter().apply(new MobHappening(new Mob())));
        // Without conversion, a value of a superclass isn't one.
        assertFalse(resolver.resolve(MobHappening.class, Human.class, null, 0, 0)
                .successful());
        // A subclass's value is one of its superclass: 'event-sender' is the mob.
        assertTrue(resolver.resolve(MobHappening.class, Sender.class, null, 0, 0).successful());
    }

    @Test
    void convertersAndTheirChainsGiveValuesOfOtherClasses() {
        Converters converters = new Converters();
        converters.register(Mob.class, Spot.class, mob -> new Spot(), 0);
        converters.register(Spot.class, Brick.class, spot -> new Brick(), 0);
        EventValueResolver resolver = resolver(converters, value(MobHappening.class, Mob.class, 0));
        var brick = resolver.resolve(MobHappening.class, Brick.class, null, 0, EventValueResolver.DEFAULT);
        assertTrue(brick.successful());
        assertTrue(brick.all().getFirst().getter().apply(new MobHappening(new Mob())) instanceof Brick);

        // A converter that isn't chained gives no brick.
        Converters unchained = new Converters();
        unchained.register(Mob.class, Spot.class, mob -> new Spot(), 0);
        unchained.register(Spot.class, Brick.class, spot -> new Brick(), Registrar.NO_CHAINING);
        assertFalse(resolver(unchained, value(MobHappening.class, Mob.class, 0))
                .resolve(MobHappening.class, Brick.class, null, 0, EventValueResolver.DEFAULT).successful());
    }

    @Test
    void anExcludedValueStopsTheSearchWithItsError() {
        EventValueResolver resolver = resolver(new Converters(), value(MobHappening.class, Mob.class, 0),
                new EventValueResolver.Value(Hurt.class, Mob.class, null, 0, null, "Use 'victim'"));
        assertEquals("Use 'victim'", resolver.resolve(Hurt.class, Mob.class, null, 0, EventValueResolver.DEFAULT)
                .error());
        assertTrue(resolver.resolve(MobHappening.class, Mob.class, null, 0, EventValueResolver.DEFAULT).successful());
    }

    @Test
    void pastValuesFallBackToTheValueNowAndTwoEqualOnesAreSeveral() {
        EventValueResolver resolver = resolver(new Converters(), value(MobHappening.class, Mob.class, 0),
                value(MobHappening.class, Mob.class, 0));
        assertTrue(resolver.resolve(MobHappening.class, Mob.class, null, -1, EventValueResolver.DEFAULT).successful());
        assertFalse(resolver.resolve(MobHappening.class, Mob.class, null, -1, EventValueResolver.CONVERSION)
                .successful());
        // Two as close are several, which Skript says to choose between.
        assertTrue(resolver.resolve(MobHappening.class, Mob.class, null, 0, 0).multiple());
        // The closest subclass wins: a mob over a human, for a sender.
        EventValueResolver closest = resolver(new Converters(), value(MobHappening.class, Human.class, 0),
                value(MobHappening.class, Mob.class, 0));
        var sender = closest.resolve(MobHappening.class, Sender.class, null, 0, 0);
        assertEquals(1, sender.all().size());
        assertEquals(Mob.class, sender.all().getFirst().valueClass());
    }
}
