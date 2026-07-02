package co.xenastudios.neoskript.lang.type;

import java.util.Locale;

/**
 * A loaded script, identified by its name (relative path under the scripts directory, e.g.
 * {@code "foo.sk"}). Mirrors Skript's {@code script} type; equality and display are by name.
 */
public final class Script {

    private final String name;

    public Script(String name) {
        this.name = name;
    }

    /** @return the script's name (relative path) */
    public String name() {
        return name;
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Script script && script.name.equalsIgnoreCase(name);
    }

    @Override
    public int hashCode() {
        return name.toLowerCase(Locale.ROOT).hashCode();
    }
}
