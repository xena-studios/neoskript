/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.compiler;

import static org.objectweb.asm.Opcodes.AASTORE;
import static org.objectweb.asm.Opcodes.ACC_FINAL;
import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.ACC_SUPER;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ANEWARRAY;
import static org.objectweb.asm.Opcodes.ASTORE;
import static org.objectweb.asm.Opcodes.ATHROW;
import static org.objectweb.asm.Opcodes.DUP;
import static org.objectweb.asm.Opcodes.GETSTATIC;
import static org.objectweb.asm.Opcodes.H_INVOKESTATIC;
import static org.objectweb.asm.Opcodes.IFNE;
import static org.objectweb.asm.Opcodes.INVOKEINTERFACE;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.objectweb.asm.Opcodes.ILOAD;
import static org.objectweb.asm.Opcodes.ISTORE;
import static org.objectweb.asm.Opcodes.NEW;
import static org.objectweb.asm.Opcodes.RETURN;
import static org.objectweb.asm.Opcodes.V21;

import co.xenastudios.neoskript.api.element.Condition;
import co.xenastudios.neoskript.api.element.Effect;
import co.xenastudios.neoskript.api.element.ElementFactory;
import co.xenastudios.neoskript.api.element.ElementInfo;
import co.xenastudios.neoskript.api.element.Expression;
import co.xenastudios.neoskript.api.element.TriggerContext;
import co.xenastudios.neoskript.frontend.parse.Expr;
import co.xenastudios.neoskript.frontend.parse.ParsedSyntax;
import co.xenastudios.neoskript.frontend.parse.Statement;
import co.xenastudios.neoskript.frontend.pattern.CompiledPattern;
import co.xenastudios.neoskript.frontend.pattern.PatternElement;
import co.xenastudios.neoskript.frontend.pattern.PlaceholderSpec;
import co.xenastudios.neoskript.frontend.registry.SyntaxRegistry;
import co.xenastudios.neoskript.frontend.registry.TypeRef;
import co.xenastudios.neoskript.runtime.ScriptRuntimeException;
import co.xenastudios.neoskript.runtime.Trigger;
import co.xenastudios.neoskript.runtime.Values;
import co.xenastudios.neoskript.runtime.generated.TriggerHost;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

/**
 * Compiles one trigger's statements into a hidden class implementing {@link Trigger}.
 *
 * <p>Elements are called through: the generated code evaluates their placeholders and calls the element
 * with the values. Each element instance is a constant of the class (loaded with
 * {@link MethodHandles#classDataAt}), so every call site has exactly one target and the JIT can inline it.
 *
 * <p>The JVM leaves hidden classes out of stack traces, so the generated code keeps the current script
 * line in a local variable and rethrows anything thrown as a {@link ScriptRuntimeException} with that
 * line. Storing an int per statement costs next to nothing.
 */
final class TriggerCompiler {

    private static final String HOST = Type.getInternalName(TriggerHost.class);
    private static final String CLASS_NAME = HOST.substring(0, HOST.lastIndexOf('/') + 1) + "Trigger";
    private static final String CONTEXT = Type.getDescriptor(TriggerContext.class);
    private static final String VALUES = Type.getInternalName(Values.class);
    private static final String OBJECT = "java/lang/Object";
    private static final String OBJECTS = "[Ljava/lang/Object;";
    private static final Handle CLASS_DATA_AT = new Handle(H_INVOKESTATIC, "java/lang/invoke/MethodHandles",
            "classDataAt", "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/Class;I)Ljava/lang/Object;",
            false);

    /** Local variable slots: 0 is this, 1 the trigger context. */
    private static final int LINE_SLOT = 2;

    private final Library library;
    private final SyntaxRegistry registry;
    private final List<Object> constants = new ArrayList<>();
    private MethodVisitor method;
    /** The text expression positions refer to: the current line, or a section header's key. */
    private String source;
    private int line;

    TriggerCompiler(Library library) {
        this.library = library;
        this.registry = library.registry();
    }

    Trigger compile(String scriptName, List<Statement> body) throws CompileException {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS) {
            @Override
            protected String getCommonSuperClass(String first, String second) {
                return OBJECT;
            }
        };
        writer.visit(V21, ACC_PUBLIC | ACC_FINAL | ACC_SUPER, CLASS_NAME, null, OBJECT,
                new String[] {Type.getInternalName(Trigger.class)});
        writer.visitSource(scriptName, null);

        MethodVisitor constructor = writer.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(ALOAD, 0);
        constructor.visitMethodInsn(INVOKESPECIAL, OBJECT, "<init>", "()V", false);
        constructor.visitInsn(RETURN);
        constructor.visitMaxs(0, 0);
        constructor.visitEnd();

        method = writer.visitMethod(ACC_PUBLIC, "execute", "(" + CONTEXT + ")V", null, null);
        method.visitCode();
        Label start = new Label();
        Label end = new Label();
        Label handler = new Label();
        method.visitTryCatchBlock(start, end, handler, "java/lang/Throwable");
        pushInt(0);
        method.visitVarInsn(ISTORE, LINE_SLOT);
        method.visitLabel(start);
        for (Statement statement : body) {
            statement(statement);
        }
        method.visitInsn(RETURN);
        method.visitLabel(end);
        // catch (Throwable t) { throw new ScriptRuntimeException(script, line, t); }
        method.visitLabel(handler);
        method.visitVarInsn(ASTORE, LINE_SLOT + 1);
        String exception = Type.getInternalName(ScriptRuntimeException.class);
        method.visitTypeInsn(NEW, exception);
        method.visitInsn(DUP);
        method.visitLdcInsn(scriptName);
        method.visitVarInsn(ILOAD, LINE_SLOT);
        method.visitVarInsn(ALOAD, LINE_SLOT + 1);
        method.visitMethodInsn(INVOKESPECIAL, exception, "<init>", "(Ljava/lang/String;ILjava/lang/Throwable;)V", false);
        method.visitInsn(ATHROW);
        method.visitMaxs(0, 0);
        method.visitEnd();
        writer.visitEnd();
        return define(writer.toByteArray());
    }

    private Trigger define(byte[] bytes) {
        try {
            MethodHandles.Lookup hidden =
                    TriggerHost.lookup().defineHiddenClassWithClassData(bytes, List.copyOf(constants), true);
            return (Trigger) hidden.findConstructor(hidden.lookupClass(), MethodType.methodType(void.class)).invoke();
        } catch (Throwable e) {
            throw new IllegalStateException("Couldn't load a compiled trigger", e);
        }
    }

    // ---------------------------------------------------------------- statements

    private void statement(Statement statement) throws CompileException {
        line = statement.line().number();
        Label label = new Label();
        method.visitLabel(label);
        method.visitLineNumber(line, label);
        pushInt(line);
        method.visitVarInsn(ISTORE, LINE_SLOT);
        source = statement.line().text();
        switch (statement) {
            case Statement.Effect effect -> {
                callThrough(effect.effect(), Effect.class);
                method.visitMethodInsn(INVOKEINTERFACE, Type.getInternalName(Effect.class), "execute",
                        "(" + CONTEXT + OBJECTS + ")V", true);
            }
            case Statement.Condition condition -> {
                callThrough(condition.condition(), Condition.class);
                method.visitMethodInsn(INVOKEINTERFACE, Type.getInternalName(Condition.class), "check",
                        "(" + CONTEXT + OBJECTS + ")Z", true);
                Label pass = new Label();
                method.visitJumpInsn(IFNE, pass);
                method.visitInsn(RETURN);
                method.visitLabel(pass);
            }
            default -> throw unsupported(describe(statement));
        }
    }

    private static String describe(Statement statement) {
        return switch (statement) {
            case Statement.Call call -> "calling functions";
            case Statement.Section section -> "sections like '" + section.line().key() + "'";
            case Statement.Conditions conditions -> "multi-line conditions";
            case Statement.Parse parse -> "parse sections";
            default -> statement.line().text();
        };
    }

    // ---------------------------------------------------------------- elements

    /** Pushes the element, the trigger context and the element's arguments, ready to call it. */
    private void callThrough(ParsedSyntax syntax, Class<?> type) throws CompileException {
        if (!(library.binding(syntax.syntax()) instanceof ElementFactory<?> factory)) {
            throw unsupported(syntax.syntax().id());
        }
        Object element = factory.create(
                new ElementInfo(syntax.pattern(), syntax.match().mark(), syntax.match().tags()));
        method.visitLdcInsn(new ConstantDynamic("_", Type.getDescriptor(type), CLASS_DATA_AT, constant(element)));
        method.visitVarInsn(ALOAD, 1);
        arguments(syntax);
    }

    private int constant(Object value) {
        constants.add(value);
        return constants.size() - 1;
    }

    private void arguments(ParsedSyntax syntax) throws CompileException {
        CompiledPattern pattern = syntax.syntax().patterns().get(syntax.pattern());
        List<Object> values = syntax.match().values();
        PlaceholderSpec[] specs = placeholderSpecs(pattern);
        pushInt(values.size());
        method.visitTypeInsn(ANEWARRAY, OBJECT);
        for (int i = 0; i < values.size(); i++) {
            if (!(values.get(i) instanceof Expr expr)) {
                continue;
            }
            method.visitInsn(DUP);
            pushInt(i);
            expression(expr);
            String convert = isPlural(specs[i]) ? "array" : "single";
            method.visitMethodInsn(INVOKESTATIC, VALUES, convert,
                    "(Ljava/lang/Object;)" + (convert.equals("array") ? OBJECTS : "Ljava/lang/Object;"), false);
            method.visitInsn(AASTORE);
        }
    }

    private boolean isPlural(PlaceholderSpec spec) {
        for (String name : spec.types()) {
            TypeRef ref = registry.resolveType(name);
            if (ref != null ? ref.plural() : name.strip().endsWith("s")) {
                return true;
            }
        }
        return false;
    }

    private static PlaceholderSpec[] placeholderSpecs(CompiledPattern pattern) {
        PlaceholderSpec[] specs = new PlaceholderSpec[pattern.placeholderCount()];
        collect(pattern.root(), specs);
        return specs;
    }

    private static void collect(PatternElement element, PlaceholderSpec[] specs) {
        switch (element) {
            case PatternElement.Placeholder placeholder -> specs[placeholder.index()] = placeholder.spec();
            case PatternElement.Sequence sequence -> sequence.elements().forEach(e -> collect(e, specs));
            case PatternElement.Choice choice -> choice.alternatives().forEach(a -> collect(a.body(), specs));
            case PatternElement.Optional optional -> collect(optional.choice(), specs);
            default -> {}
        }
    }

    // ---------------------------------------------------------------- expressions

    /** Pushes the expression's value as an {@code Object} (an {@code Object[]} for several values). */
    private void expression(Expr expr) throws CompileException {
        switch (expr) {
            case Expr.Literal literal -> literal(literal);
            case Expr.Text text -> text(text);
            case Expr.ListOf list -> {
                pushInt(list.items().size());
                method.visitTypeInsn(ANEWARRAY, OBJECT);
                for (int i = 0; i < list.items().size(); i++) {
                    method.visitInsn(DUP);
                    pushInt(i);
                    expression(list.items().get(i));
                    method.visitInsn(AASTORE);
                }
                method.visitMethodInsn(INVOKESTATIC, VALUES, "list", "(" + OBJECTS + ")" + OBJECTS, false);
            }
            case Expr.Syntax syntax -> {
                callThrough(syntax.syntax(), Expression.class);
                method.visitMethodInsn(INVOKEINTERFACE, Type.getInternalName(Expression.class), "get",
                        "(" + CONTEXT + OBJECTS + ")Ljava/lang/Object;", true);
            }
            case Expr.Variable variable -> throw unsupported("variables");
            case Expr.FunctionCall call -> throw unsupported("calling functions");
            case Expr.Unresolved unresolved -> throw unsupported("'" + unresolved.text() + "'");
        }
    }

    private void literal(Expr.Literal literal) throws CompileException {
        String text = literal.text().replace("_", "");
        switch (literal.type()) {
            case "integer" -> {
                method.visitLdcInsn(Long.parseLong(text.startsWith("+") ? text.substring(1) : text));
                method.visitMethodInsn(INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false);
            }
            case "number" -> {
                double value = text.endsWith("%")
                        ? Double.parseDouble(text.substring(0, text.length() - 1)) / 100
                        : Double.parseDouble(text);
                method.visitLdcInsn(value);
                method.visitMethodInsn(INVOKESTATIC, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;", false);
            }
            case "boolean" -> {
                boolean value = switch (text.toLowerCase(java.util.Locale.ROOT)) {
                    case "true", "yes", "on" -> true;
                    default -> false;
                };
                method.visitFieldInsn(GETSTATIC, "java/lang/Boolean", value ? "TRUE" : "FALSE", "Ljava/lang/Boolean;");
            }
            default -> throw unsupported(literal.type() + " literals");
        }
    }

    /** A string, with embedded {@code %expressions%} converted to text and joined in. */
    private void text(Expr.Text text) throws CompileException {
        List<String> pieces = new ArrayList<>();
        int pos = text.start() + 1;
        for (Expr embedded : text.embedded()) {
            pieces.add(unescape(source.substring(pos, embedded.start() - 1)));
            pos = embedded.end() + 1;
        }
        pieces.add(unescape(source.substring(pos, text.end() - 1)));
        if (text.embedded().isEmpty()) {
            method.visitLdcInsn(pieces.getFirst());
            return;
        }
        String builder = "java/lang/StringBuilder";
        method.visitTypeInsn(NEW, builder);
        method.visitInsn(DUP);
        method.visitMethodInsn(INVOKESPECIAL, builder, "<init>", "()V", false);
        for (int i = 0; i < pieces.size(); i++) {
            if (!pieces.get(i).isEmpty()) {
                method.visitLdcInsn(pieces.get(i));
                method.visitMethodInsn(INVOKEVIRTUAL, builder, "append",
                        "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
            }
            if (i < text.embedded().size()) {
                expression(text.embedded().get(i));
                method.visitMethodInsn(INVOKESTATIC, VALUES, "text", "(Ljava/lang/Object;)Ljava/lang/String;", false);
                method.visitMethodInsn(INVOKEVIRTUAL, builder, "append",
                        "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
            }
        }
        method.visitMethodInsn(INVOKEVIRTUAL, builder, "toString", "()Ljava/lang/String;", false);
    }

    private static String unescape(String text) {
        return text.replace("\"\"", "\"").replace("%%", "%");
    }

    private void pushInt(int value) {
        method.visitLdcInsn(value);
    }

    private CompileException unsupported(String what) {
        return new CompileException(line, "NeoSkript can't run this yet: " + what);
    }
}
