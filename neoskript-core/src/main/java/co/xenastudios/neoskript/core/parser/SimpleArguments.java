package co.xenastudios.neoskript.core.parser;

import co.xenastudios.neoskript.api.syntax.Arguments;
import co.xenastudios.neoskript.api.syntax.Expression;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable {@link Arguments} backed by the list of expressions parsed from a pattern's slots.
 * Absent optional arguments are represented as {@code null} entries.
 */
public final class SimpleArguments implements Arguments {

    private final List<Expression<?>> arguments;

    public SimpleArguments(List<Expression<?>> arguments) {
        // A defensive, unmodifiable copy that PERMITS null elements (absent optional %-slots%).
        // List.copyOf would reject nulls, so use ArrayList + unmodifiableList.
        this.arguments = Collections.unmodifiableList(new ArrayList<>(arguments == null ? List.of() : arguments));
    }

    @Override
    public int count() {
        return arguments.size();
    }

    @Override
    public Expression<?> get(int index) {
        if (index < 0 || index >= arguments.size()) {
            return null;
        }
        return arguments.get(index);
    }

    @Override
    public boolean isPresent(int index) {
        return get(index) != null;
    }

    @Override
    public List<Expression<?>> all() {
        return arguments;
    }
}
