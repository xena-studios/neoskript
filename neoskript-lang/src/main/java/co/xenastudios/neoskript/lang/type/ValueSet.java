package co.xenastudios.neoskript.lang.type;

import java.util.List;

/**
 * Implemented by {@link co.xenastudios.neoskript.api.type.Type Type}s that can enumerate all of their
 * values (enum- and registry-backed ones). Backs the {@code [all [of the]|every] %*classinfo%}
 * expression, which lists every value of a type ({@code all game modes}, {@code every attribute type}).
 */
public interface ValueSet {

    /** @return every value of this type, or an empty list if it cannot be enumerated on this server */
    List<?> allValues();
}
