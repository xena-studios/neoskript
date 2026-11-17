/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.runtime.variables.storage;

import co.xenastudios.neoskript.api.storage.BinaryReader;
import co.xenastudios.neoskript.api.storage.BinaryWriter;
import co.xenastudios.neoskript.api.storage.VariableCodec;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * An addon's codecs, which {@link VariableStorageDetachTest} loads with a class loader of their own, as an addon's
 * jar is: one for its own type, and one for a JDK type it didn't load.
 */
public final class AddonCodecs {

    /** The addon's own type. */
    public record Ticket(long number) {}

    private AddonCodecs() {}

    public static Object ticket(long number) {
        return new Ticket(number);
    }

    /**
     * @param gone whether the addon's classes are gone, so encoding fails as it would
     * @param missingPlugin whether decoding needs a class that isn't there, as for an optional plugin
     */
    public static void register(VariableCodecs codecs, AtomicBoolean gone, AtomicBoolean missingPlugin) {
        codecs.register("test:ticket", Ticket.class, new VariableCodec<>() {
            @Override
            public void encode(Ticket value, BinaryWriter out) {
                if (gone.get()) {
                    throw new NoClassDefFoundError("the addon's classes are gone");
                }
                out.rawLong(value.number());
            }

            @Override
            public Ticket decode(BinaryReader in) {
                if (missingPlugin.get()) {
                    throw new NoClassDefFoundError("org/example/OptionalPlugin");
                }
                return new Ticket(in.rawLong());
            }
        });
        codecs.register("test:duration", Duration.class, new VariableCodec<>() {
            @Override
            public void encode(Duration value, BinaryWriter out) {
                if (gone.get()) {
                    throw new NoClassDefFoundError("the addon's classes are gone");
                }
                out.rawLong(value.toMillis());
            }

            @Override
            public Duration decode(BinaryReader in) {
                return Duration.ofMillis(in.rawLong());
            }
        });
    }
}
