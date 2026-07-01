package co.xenastudios.neoskript.lang.type;

import co.xenastudios.neoskript.api.type.Type;

import java.util.Optional;
import java.util.function.Function;

/**
 * A generic {@link Type} backed by a plain Java/Bukkit class. Used for value types that are
 * referenced by code name (e.g. {@code entity}, {@code chunk}, {@code inventory}) but are not
 * written as literals in scripts. An optional parser supports code-name literals where one exists
 * (e.g. {@code uuid}); otherwise {@link #parse} yields empty. Display defaults to {@code toString}.
 *
 * @param <T> the represented Java type
 */
public final class ClassType<T> implements Type<T> {

    private final String codeName;
    private final Class<T> typeClass;
    private final Function<T, String> display;
    private final Function<String, T> parser;

    public ClassType(String codeName, Class<T> typeClass) {
        this(codeName, typeClass, null, null);
    }

    public ClassType(String codeName, Class<T> typeClass, Function<T, String> display) {
        this(codeName, typeClass, display, null);
    }

    public ClassType(String codeName, Class<T> typeClass, Function<T, String> display,
                     Function<String, T> parser) {
        this.codeName = codeName;
        this.typeClass = typeClass;
        this.display = display;
        this.parser = parser;
    }

    @Override
    public Class<T> typeClass() {
        return typeClass;
    }

    @Override
    public String codeName() {
        return codeName;
    }

    @Override
    public Optional<T> parse(String input) {
        if (parser == null) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(parser.apply(input.trim()));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    @Override
    public String toDisplayString(T value) {
        if (value == null) {
            return "<none>";
        }
        return display != null ? display.apply(value) : String.valueOf(value);
    }
}
