package co.xenastudios.neoskript.core.expression;

import co.xenastudios.neoskript.api.runtime.TriggerContext;
import co.xenastudios.neoskript.api.syntax.Expression;
import co.xenastudios.neoskript.core.parser.ExpressionParser;
import co.xenastudios.neoskript.core.parser.ParseException;
import co.xenastudios.neoskript.core.runtime.Renderer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A variable reference: {@code {name}} (global) or {@code {_name}} (trigger-local). Settable, so the
 * {@code set} effect can assign into it.
 *
 * <p>Names may interpolate expressions ({@code {deaths::%player%}}) and use {@code ::} separators for
 * list entries. A trailing {@code *} ({@code {scores::*}}) denotes a list: reading yields all direct
 * children. Phase 2 supports flat list storage; nested list trees and persistence-aware serialization
 * deepen in later phases.
 */
public final class VariableExpression implements Expression<Object> {

    private final boolean local;
    private final boolean list;
    private final String rawName;
    /** Parts are either {@link String} literals or {@link Expression} interpolations. */
    private final List<Object> nameParts;

    /**
     * The reserved trigger-local name a section expression stores its in-progress value under; the
     * {@code created <thing>} expressions read and write it. Starts with {@code §} so it never
     * collides with a user-written variable name.
     */
    public static final String SECTION_VALUE = "§section-value";

    private VariableExpression(boolean local, boolean list, String rawName, List<Object> nameParts) {
        this.local = local;
        this.list = list;
        this.rawName = rawName;
        this.nameParts = nameParts;
    }

    /** @return a plain (non-interpolated) trigger-local variable reference for {@code name} */
    public static VariableExpression local(String name) {
        return new VariableExpression(true, false, name, List.of(name));
    }

    /**
     * Parses a variable from its inner text (between {@code {} and {@code }}).
     *
     * @param rawInner the inner text, e.g. {@code "_count"} or {@code "deaths::%player%"}
     * @param parser   the expression parser, for interpolated name segments
     * @return the variable expression
     */
    public static VariableExpression parse(String rawInner, ExpressionParser parser) {
        String trimmed = rawInner.trim();
        boolean local = trimmed.startsWith("_");
        String body = local ? trimmed.substring(1) : trimmed;
        boolean list = body.endsWith("*");
        return new VariableExpression(local, list, body, parseParts(body, parser));
    }

    private static List<Object> parseParts(String body, ExpressionParser parser) {
        List<Object> parts = new ArrayList<>();
        StringBuilder literal = new StringBuilder();
        int i = 0;
        while (i < body.length()) {
            char c = body.charAt(i);
            if (c == '%') {
                int end = body.indexOf('%', i + 1);
                if (end < 0) {
                    throw new ParseException("Unbalanced '%' in variable name: {" + body + "}");
                }
                if (literal.length() > 0) {
                    parts.add(literal.toString());
                    literal.setLength(0);
                }
                parts.add(parser.parse(body.substring(i + 1, end)));
                i = end + 1;
            } else {
                literal.append(c);
                i++;
            }
        }
        if (literal.length() > 0) {
            parts.add(literal.toString());
        }
        return parts;
    }

    /** @return {@code true} if this is a trigger-local variable */
    public boolean isLocal() {
        return local;
    }

    /** @return {@code true} if this is a list variable ({@code ::*}) */
    public boolean isList() {
        return list;
    }

    /** @return the raw inner name as written (without delimiters or the local underscore) */
    public String name() {
        return rawName;
    }

    /**
     * Resolves the variable's effective name for the current context (interpolating any {@code %...%}
     * segments).
     */
    public String resolveName(TriggerContext ctx) {
        if (nameParts.size() == 1 && nameParts.get(0) instanceof String only) {
            return only;
        }
        StringBuilder sb = new StringBuilder();
        for (Object part : nameParts) {
            if (part instanceof String literal) {
                sb.append(literal);
            } else {
                sb.append(Renderer.toDisplay(((Expression<?>) part).getSingle(ctx)));
            }
        }
        return sb.toString();
    }

    /** @return the list prefix (resolved name without the trailing {@code *}) */
    public String listPrefix(TriggerContext ctx) {
        String resolved = resolveName(ctx);
        return resolved.endsWith("*") ? resolved.substring(0, resolved.length() - 1) : resolved;
    }

    /**
     * Assigns a value to this (scalar) variable.
     *
     * @param ctx   the execution context
     * @param value the value to store ({@code null} clears it)
     */
    public void set(TriggerContext ctx, Object value) {
        String name = resolveName(ctx);
        if (local) {
            ctx.setLocal(name, value);
        } else {
            ctx.setGlobal(name, value);
        }
    }

    /**
     * Appends a value to this list variable under the next free integer index.
     *
     * @param ctx   the execution context
     * @param value the value to append
     */
    public void addToList(TriggerContext ctx, Object value) {
        String prefix = listPrefix(ctx);
        Map<String, Object> children = local ? ctx.listLocal(prefix) : ctx.listGlobal(prefix);
        long max = 0;
        for (String key : children.keySet()) {
            try {
                max = Math.max(max, Long.parseLong(key.substring(prefix.length())));
            } catch (NumberFormatException ignored) {
                // non-integer index; skip
            }
        }
        String name = prefix + (max + 1);
        if (local) {
            ctx.setLocal(name, value);
        } else {
            ctx.setGlobal(name, value);
        }
    }

    /**
     * Removes the first child of this list variable whose value equals {@code value}.
     *
     * @param ctx   the execution context
     * @param value the value to remove
     * @return {@code true} if an entry was removed
     */
    public boolean removeFromList(TriggerContext ctx, Object value) {
        String prefix = listPrefix(ctx);
        Map<String, Object> children = local ? ctx.listLocal(prefix) : ctx.listGlobal(prefix);
        for (Map.Entry<String, Object> entry : children.entrySet()) {
            if (valueEquals(entry.getValue(), value)) {
                if (local) {
                    ctx.setLocal(entry.getKey(), null);
                } else {
                    ctx.setGlobal(entry.getKey(), null);
                }
                return true;
            }
        }
        return false;
    }

    private static boolean valueEquals(Object a, Object b) {
        if (a instanceof Number x && b instanceof Number y) {
            return x.doubleValue() == y.doubleValue();
        }
        return java.util.Objects.equals(a, b);
    }

    /**
     * Deletes this variable: a single entry, or every child of a list.
     *
     * @param ctx the execution context
     */
    public void delete(TriggerContext ctx) {
        if (list) {
            String prefix = listPrefix(ctx);
            Map<String, Object> children = local ? ctx.listLocal(prefix) : ctx.listGlobal(prefix);
            for (String key : children.keySet()) {
                if (local) {
                    ctx.setLocal(key, null);
                } else {
                    ctx.setGlobal(key, null);
                }
            }
        } else {
            set(ctx, null);
        }
    }

    @Override
    public Object[] getAll(TriggerContext ctx) {
        if (list) {
            String prefix = listPrefix(ctx);
            Map<String, Object> children = local ? ctx.listLocal(prefix) : ctx.listGlobal(prefix);
            return children.values().toArray();
        }
        Object value = getSingle(ctx);
        return value == null ? new Object[0] : new Object[]{value};
    }

    /** @return the direct index keys of a list variable (relative to the list prefix), or empty. */
    public Object[] listKeys(TriggerContext ctx) {
        if (!list) {
            return new Object[0];
        }
        String prefix = listPrefix(ctx);
        Map<String, Object> children = local ? ctx.listLocal(prefix) : ctx.listGlobal(prefix);
        return children.keySet().stream()
                .map(key -> key.startsWith(prefix) ? key.substring(prefix.length()) : key)
                .toArray();
    }

    @Override
    public Object getSingle(TriggerContext ctx) {
        if (list) {
            Object[] all = getAll(ctx);
            return all.length > 0 ? all[0] : null;
        }
        String name = resolveName(ctx);
        return local ? ctx.getLocal(name) : ctx.getGlobal(name);
    }

    @Override
    public Class<Object> returnType() {
        return Object.class;
    }

    @Override
    public boolean isSingle() {
        return !list;
    }
}
