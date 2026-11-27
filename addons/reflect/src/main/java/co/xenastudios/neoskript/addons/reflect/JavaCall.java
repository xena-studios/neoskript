/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.addons.reflect;

import co.xenastudios.neoskript.api.NeoSkript;
import co.xenastudios.neoskript.api.element.Changeable;
import co.xenastudios.neoskript.api.element.ElementInfo;
import co.xenastudios.neoskript.api.element.Expression;
import co.xenastudios.neoskript.api.element.SyntaxError;
import co.xenastudios.neoskript.api.element.TriggerContext;
import java.lang.reflect.Executable;
import java.lang.reflect.Member;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A Java call written in a script, skript-reflect's {@code ExprJavaCall}: a field ({@code System.out}), a method
 * ({@code player.getName()}) or a constructor ({@code new Location(...)}), on an object or, on an imported class, a
 * static one. Used as an expression, an effect or a condition. What goes wrong while it runs is a runtime warning with
 * skript-reflect's words, unless it's written with {@code try}, and the call gives no value; the trigger carries on.
 */
final class JavaCall implements Expression, Changeable {

    /** The patterns, in skript-reflect's order: a descriptor in a text, then one written out, then a constructor. */
    // Not 'class' alone: 'ArrayList.class' is the class itself, as in skript-reflect.
    static final String DESCRIPTOR = "(?:\\[[\\w.$]*])?(?!class(?![^. \\[\\]()\"{}%,]))[^0-9. \\[\\]()\"{}%,][^. \\[\\]()\"{}%,]*(?:\\[[\\w.$, \\[\\]]*])?";
    static final String[] PATTERNS = {
        "[try:try] %object%..%string%",
        "[try:try] %object%..%string%\\([%-objects%]\\)",
        "[try:try] %object%.<" + DESCRIPTOR + ">",
        "[try:try] %object%.<" + DESCRIPTOR + ">\\([%-objects%]\\)",
        "[try:try] [a] new %javatype%\\([%-objects%]\\)"
    };

    private static final Object[] NONE = new Object[0];

    private final CallSite site;
    private final boolean quiet;
    private final Descriptor descriptor;
    private final int argumentsIndex;
    private final boolean singleArgument;
    /** The script's imports, as they were when the call was set up: a later reload doesn't change them. */
    private final java.util.Map<String, JavaType> imports;

    private JavaCall(CallSite site, boolean quiet, Descriptor descriptor, int argumentsIndex, boolean singleArgument,
            java.util.Map<String, JavaType> imports) {
        this.site = site;
        this.quiet = quiet;
        this.descriptor = descriptor;
        this.argumentsIndex = argumentsIndex;
        this.singleArgument = singleArgument;
        this.imports = imports;
    }

    /** Sets up the call where a script uses it, with skript-reflect's errors for what can't work. */
    static JavaCall create(ElementInfo info, Imports all) {
        java.util.Map<String, JavaType> imports = all.of(NeoSkript.get().scriptBeingLoaded());
        boolean quiet = info.hasTag("try");
        int pattern = info.pattern();
        CallSite.Kind kind = switch (pattern) {
            case 0, 2 -> CallSite.Kind.FIELD;
            case 1, 3 -> CallSite.Kind.METHOD;
            default -> CallSite.Kind.CONSTRUCTOR;
        };
        int arguments = switch (pattern) {
            case 1 -> 2;
            case 3, 4 -> 1;
            default -> -1;
        };
        boolean single = arguments >= 0 && (info.singleArguments() & (1L << arguments)) != 0;
        Descriptor descriptor = null;
        if (pattern == 4) {
            descriptor = Descriptor.CONSTRUCTOR;
        } else if (pattern == 2 || pattern == 3) {
            String text = descriptorText(info.text(), pattern == 3);
            if (text == null) {
                throw new SyntaxError("This Java call can't be read: " + info.text());
            }
            try {
                descriptor = Descriptor.parse(text, imports);
            } catch (Descriptor.NotFound missing) {
                throw new SyntaxError("The class " + missing.name + " could not be found.");
            }
            if (descriptor == null) {
                throw new SyntaxError(text + " is not a valid descriptor.");
            }
            // What the call is made on, as written: an imported class's name makes it a static call, checked here as
            // skript-reflect does. The words reach the factory unparsed, so they're read from the call's text.
            String target = receiverText(info.text(), pattern == 3, quiet);
            JavaType imported = target == null ? null : imports.get(target);
            if (descriptor.javaClass() == null && imported != null) {
                descriptor = descriptor.orClass(imported.javaClass());
            }
            if (descriptor.parameterTypes() != null && kind == CallSite.Kind.FIELD) {
                throw new SyntaxError("You can't pass parameter types to a field call.");
            }
            if (descriptor.javaClass() != null) {
                CallSite probe = new CallSite(kind);
                boolean isStatic = imported != null;
                boolean missing;
                try {
                    missing = probe.candidates(descriptor, isStatic, false).isEmpty()
                            && probe.candidates(descriptor, !isStatic, false).isEmpty()
                            && !nestedClass(descriptor.javaClass(), descriptor.name());
                } catch (LinkageError broken) {
                    // Its members can't be looked at now: the call says why when it runs.
                    missing = false;
                }
                if (missing) {
                    throw new SyntaxError(text + " refers to a non-existent " + kind);
                }
            }
        }
        return new JavaCall(new CallSite(kind), quiet, descriptor, arguments, single, imports);
    }

    /** The descriptor written in a call's text: what follows its last {@code .} outside brackets, quotes and calls. */
    static String descriptorText(String text, boolean method) {
        String[] parts = split(text, method);
        return parts == null ? null : parts[1];
    }

    /** What a call's text calls it on: the words before its last {@code .}, without {@code try}. */
    static String receiverText(String text, boolean method, boolean quiet) {
        String[] parts = split(text, method);
        if (parts == null) {
            return null;
        }
        String receiver = parts[0];
        if (quiet && receiver.regionMatches(true, 0, "try ", 0, 4)) {
            receiver = receiver.substring(4).strip();
        }
        return receiver;
    }

    /** A call's text as what it's called on and its descriptor, split at its last {@code .} outside brackets. */
    private static String[] split(String text, boolean method) {
        if (text == null) {
            return null;
        }
        String call = text.strip();
        if (method) {
            int open = matchingOpen(call);
            if (open < 0) {
                return null;
            }
            call = call.substring(0, open).strip();
        }
        int depth = 0;
        boolean quoted = false;
        for (int i = call.length() - 1; i >= 0; i--) {
            char c = call.charAt(i);
            if (c == '"') {
                quoted = !quoted;
            } else if (quoted) {
                continue;
            } else if (c == ']' || c == ')' || c == '}') {
                depth++;
            } else if (c == '[' || c == '(' || c == '{') {
                depth--;
            } else if (c == '.' && depth == 0) {
                return new String[] {call.substring(0, i).strip(), call.substring(i + 1).strip()};
            }
        }
        return null;
    }

    /** The index of the {@code (} the text's final {@code )} closes, or -1. */
    private static int matchingOpen(String text) {
        if (!text.endsWith(")")) {
            return -1;
        }
        int depth = 0;
        boolean quoted = false;
        for (int i = text.length() - 1; i >= 0; i--) {
            char c = text.charAt(i);
            if (c == '"') {
                quoted = !quoted;
            } else if (quoted) {
                continue;
            } else if (c == ')') {
                depth++;
            } else if (c == '(' && --depth == 0) {
                return i;
            }
        }
        return -1;
    }

    private static boolean nestedClass(Class<?> type, String name) {
        for (Class<?> nested : type.getClasses()) {
            if (nested.getSimpleName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Object get(TriggerContext context, Object[] arguments) {
        Object target = arguments[0];
        if (target == null) {
            return null;
        }
        Descriptor base = descriptor != null ? descriptor : dynamic(context, arguments[1]);
        if (base == null) {
            return null;
        }
        return invoke(context, target, base, values(arguments), false);
    }

    /** The call's arguments, one each, as skript-reflect passes them: an argument with no value is {@code null}. */
    private Object[] values(Object[] arguments) {
        if (argumentsIndex < 0) {
            return NONE;
        }
        Object raw = arguments[argumentsIndex];
        if (raw == null) {
            return singleArgument ? new Object[] {Null.NULL} : NONE;
        }
        if (raw instanceof Object[] several) {
            if (several.length == 0 && singleArgument) {
                return new Object[] {Null.NULL};
            }
            // The array is the call's own: nothing here changes it.
            return several;
        }
        return new Object[] {raw};
    }

    private Descriptor dynamic(TriggerContext context, Object text) {
        if (!(text instanceof String written)) {
            warn(context, new JavaCallException("Dynamic descriptor returned null"),
                    "Dynamic descriptor " + text + " returned null");
            return null;
        }
        try {
            Descriptor parsed = Descriptor.parse(written, imports);
            if (parsed == null) {
                warn(context, new JavaCallException("Invalid dynamic descriptor (" + written + ")"),
                        "Invalid dynamic descriptor (" + written + ")");
            }
            return parsed;
        } catch (Descriptor.NotFound missing) {
            warn(context, new JavaCallException("The class " + missing.name + " could not be found."),
                    "The class " + missing.name + " could not be found.");
            return null;
        }
    }

    /** Calls it, with skript-reflect's messages for what goes wrong. */
    private Object invoke(TriggerContext context, Object rawTarget, Descriptor base, Object[] arguments,
            boolean setter) {
        Object target = rawTarget instanceof ObjectWrapper wrapper ? wrapper.array() : rawTarget;
        boolean isStatic = target instanceof JavaType;
        Class<?> targetClass = isStatic ? ((JavaType) target).javaClass() : target.getClass();
        Descriptor described = base.orClass(targetClass);
        CallSite.Kind kind = site.kind();
        if (!described.javaClass().isAssignableFrom(targetClass)) {
            warn(context, null, "Incompatible " + kind + " call: " + described.text(false) + " on "
                    + Coercion.debugName(targetClass));
            return null;
        }
        boolean staticCall = isStatic || kind == CallSite.Kind.CONSTRUCTOR;
        CallSite.Target chosen;
        try {
            if (isStatic && kind == CallSite.Kind.FIELD && !setter) {
                for (Class<?> nested : targetClass.getClasses()) {
                    if (described.name().equals(nested.getSimpleName())) {
                        return new JavaType(nested);
                    }
                }
            }
            chosen = site.find(described, targetClass, staticCall, arguments, setter);
        } catch (LinkageError | RuntimeException broken) {
            // A member whose signature names a class that isn't there (a plugin built against another's API): the
            // class can't be looked at, so the call can't be made, as a call that threw.
            warn(context, broken, kind + " " + described.text(false) + argumentsMessage(kind, arguments)
                    + " couldn't be looked up: " + broken.getClass().getSimpleName() + ": " + broken.getMessage());
            return null;
        }
        if (chosen == null) {
            List<String> lines = new ArrayList<>();
            lines.add("No matching " + (isStatic ? "static" : "non-static") + " " + kind + ": "
                    + described.text(isStatic) + argumentsMessage(kind, arguments));
            try {
                suggestParameters(lines, described, isStatic);
                suggestTypo(lines, described, isStatic);
            } catch (LinkageError | RuntimeException broken) {
                // Its members can't all be looked at: no suggestions.
            }
            warn(context, null, String.join("\n", lines));
            return null;
        }
        Object returned;
        try {
            returned = CallSite.call(chosen, target, staticCall, arguments);
        } catch (Throwable thrown) {
            warn(context, thrown, kind + " " + described.text(false) + argumentsMessage(kind, arguments) + " threw a "
                    + thrown.getClass().getSimpleName() + ": " + thrown.getMessage());
            return null;
        }
        LastError.clear(context);
        return returned == null ? null : Coercion.fromJava(returned);
    }

    private static String argumentsMessage(CallSite.Kind kind, Object[] arguments) {
        if (kind == CallSite.Kind.FIELD) {
            return "";
        }
        if (arguments.length == 0) {
            return " called without arguments";
        }
        return " called with (" + argumentsText(arguments) + ")";
    }

    static String argumentsText(Object... arguments) {
        List<String> parts = new ArrayList<>(arguments.length);
        for (Object argument : arguments) {
            parts.add(text(argument) + " (" + Coercion.debugName(Coercion.classOf(argument)) + ")");
        }
        return String.join(", ", parts);
    }

    private static String text(Object value) {
        if (value instanceof ObjectWrapper wrapper) {
            return wrapper.text(JavaCall::text);
        }
        if (value == Null.NULL || value == null) {
            return "null";
        }
        try {
            return NeoSkript.get().values().text(value);
        } catch (RuntimeException e) {
            return String.valueOf(value);
        }
    }

    private void suggestParameters(List<String> lines, Descriptor described, boolean isStatic) {
        CallSite.Kind kind = site.kind();
        if (kind == CallSite.Kind.FIELD) {
            return;
        }
        List<String> matches = new ArrayList<>();
        for (Executable executable : CallSite.executables(kind, described.javaClass())) {
            String name = kind == CallSite.Kind.CONSTRUCTOR ? "<init>" : executable.getName();
            boolean executableStatic = kind == CallSite.Kind.CONSTRUCTOR || Members.isStatic(executable);
            if (name.equals(described.name()) && executableStatic == isStatic) {
                matches.add(java.util.Arrays.stream(executable.getParameterTypes()).map(Class::getTypeName)
                        .collect(Collectors.joining(",")));
            }
        }
        if (!matches.isEmpty()) {
            lines.add("Did you pass the wrong parameters? Here are the parameter signatures for the "
                    + (isStatic ? "static" : "non-static") + " " + kind + " " + described.name() + ":");
            matches.forEach(parameters -> lines.add("* " + described.name() + "(" + parameters + ")"));
        }
    }

    private void suggestTypo(List<String> lines, Descriptor described, boolean isStatic) {
        CallSite.Kind kind = site.kind();
        List<Member> members = new ArrayList<>();
        switch (kind) {
            case FIELD -> members.addAll(Members.fields(described.javaClass()));
            case METHOD -> members.addAll(Members.methods(described.javaClass()));
            case CONSTRUCTOR -> members.addAll(Members.constructors(described.javaClass()));
        }
        List<Member> close = new ArrayList<>();
        outer:
        for (Member member : members) {
            String name = member.getName();
            if (name.equals(described.name()) && Members.isStatic(member) == isStatic) {
                continue;
            }
            for (Member seen : close) {
                if (seen.getName().equals(name)) {
                    continue outer;
                }
            }
            if (editDistance(described.name(), name, 3) <= 3) {
                close.add(member);
            }
        }
        if (!close.isEmpty()) {
            lines.add("Did you misspell the " + kind + "? You may have meant to type one of the following:");
            String className = Coercion.debugName(described.javaClass());
            for (Member member : close) {
                lines.add("* " + (Members.isStatic(member) ? className : "%" + className + "%") + "."
                        + member.getName());
            }
        }
    }

    /** The edit distance between two names, or more than {@code limit} if it's over. */
    static int editDistance(String a, String b, int limit) {
        if (Math.abs(a.length() - b.length()) > limit) {
            return limit + 1;
        }
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()];
    }

    /** As skript-reflect: the error's kept for {@code the last java error}, and said unless the call is a {@code try}. */
    private void warn(TriggerContext context, Throwable cause, String message) {
        LastError.set(context, cause != null ? cause : new JavaCallException(message));
        if (!quiet) {
            // Each line indented as the first is, as Skript prints skript-reflect's.
            NeoSkript.get().runtimeWarning(message.replace("\n", "\n    "));
        }
    }

    @Override
    public Class<?>[] acceptChange(Mode mode) {
        if (site.kind() == CallSite.Kind.FIELD && (mode == Mode.SET || mode == Mode.DELETE)) {
            return new Class<?>[] {Object.class};
        }
        return null;
    }

    @Override
    public void change(TriggerContext context, Object[] arguments, Object[] delta, Mode mode) {
        Object target = arguments[0];
        if (target == null) {
            return;
        }
        Descriptor base = descriptor != null ? descriptor : dynamic(context, arguments[1]);
        if (base == null) {
            return;
        }
        Object value = mode == Mode.SET && delta != null && delta.length > 0 ? delta[0] : Null.NULL;
        invoke(context, target, base, new Object[] {value}, true);
    }

    /** The value as a condition: true unless it's {@code false}, {@code null} or zero, as in skript-reflect. */
    static boolean truthy(Object value) {
        if (value == null || value == Null.NULL || Boolean.FALSE.equals(value)) {
            return false;
        }
        return !(value instanceof Number number && number.doubleValue() == 0);
    }
}
