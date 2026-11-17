/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package ch.njol.skript.classes;

import ch.njol.skript.lang.DefaultExpression;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.bukkit.event.Event;

/** A type, as Skript describes one: its class, its code name, and how its values are read, saved and changed. */
public class ClassInfo<T> implements ch.njol.skript.lang.Debuggable {

    public static final String NO_DOC = new String();

    private final Class<T> c;
    private final String codeName;
    private Parser<? extends T> parser;
    private Cloner<T> cloner;
    private Pattern[] userInputPatterns;
    private String[] userInput;
    private DefaultExpression<T> defaultExpression;
    private Supplier<Iterator<T>> supplier;
    private Serializer<? super T> serializer;
    private Class<?> serializeAs;
    private Changer<? super T> changer;
    private String docName;
    private String[] description;
    private String[] usage;
    private String[] examples;
    private String since;
    private String[] requiredPlugins;
    private String documentationId;
    private final Set<String> before = new LinkedHashSet<>();
    private final Set<String> after = new LinkedHashSet<>();

    public ClassInfo(final Class<T> c, final String codeName) {
        if (!isValidCodeName(codeName)) {
            throw new IllegalArgumentException("Code names for classes must be lowercase and only consist of latin"
                    + " letters and arabic numbers: " + codeName);
        }
        this.c = c;
        this.codeName = codeName;
    }

    private ClassInfo(Class<T> c, String codeName, boolean neoskript) {
        this.c = c;
        this.codeName = codeName;
        this.neoskript = neoskript;
    }

    private boolean neoskript;

    /**
     * One of NeoSkript's types as a class info, for addons that look types up: not one of Skript's methods. Its code
     * name is NeoSkript's id for the type, which may not be a valid Skript code name.
     */
    public static <T> ClassInfo<T> ofNeoSkript(Class<T> c, String id) {
        return new ClassInfo<>(c, id, true);
    }

    /** Whether this is one of NeoSkript's types, rather than one an addon registered: not one of Skript's methods. */
    public boolean isNeoSkript() {
        return neoskript;
    }

    public static boolean isValidCodeName(final String name) {
        return name.matches("(?:any-)?[a-z0-9]+");
    }

    public ClassInfo<T> parser(final Parser<? extends T> parser) {
        this.parser = parser;
        return this;
    }

    public ClassInfo<T> cloner(Cloner<T> cloner) {
        this.cloner = cloner;
        return this;
    }

    public ClassInfo<T> user(final String... userInputPatterns) {
        this.userInput = userInputPatterns;
        this.userInputPatterns = new Pattern[userInputPatterns.length];
        for (int i = 0; i < userInputPatterns.length; i++) {
            this.userInputPatterns[i] = Pattern.compile(userInputPatterns[i]);
        }
        return this;
    }

    public ClassInfo<T> defaultExpression(final DefaultExpression<T> defaultExpression) {
        this.defaultExpression = defaultExpression;
        return this;
    }

    public ClassInfo<T> supplier(Supplier<Iterator<T>> supplier) {
        this.supplier = supplier;
        return this;
    }

    public ClassInfo<T> supplier(T[] values) {
        this.supplier = () -> java.util.Arrays.asList(values).iterator();
        return this;
    }

    public ClassInfo<T> serializer(final Serializer<? super T> serializer) {
        this.serializer = serializer;
        return this;
    }

    public ClassInfo<T> serializeAs(final Class<?> serializeAs) {
        this.serializeAs = serializeAs;
        return this;
    }

    public ClassInfo<T> changer(final Changer<? super T> changer) {
        this.changer = changer;
        return this;
    }

    public ClassInfo<T> name(final String name) {
        this.docName = name;
        return this;
    }

    public ClassInfo<T> description(final String... description) {
        this.description = description;
        return this;
    }

    public ClassInfo<T> usage(final String... usage) {
        this.usage = usage;
        return this;
    }

    public ClassInfo<T> examples(final String... examples) {
        this.examples = examples;
        return this;
    }

    public ClassInfo<T> since(final String since) {
        this.since = since;
        return this;
    }

    public ClassInfo<T> requiredPlugins(final String... pluginNames) {
        this.requiredPlugins = pluginNames;
        return this;
    }

    public ClassInfo<T> documentationId(String id) {
        this.documentationId = id;
        return this;
    }

    public Class<T> getC() {
        return c;
    }

    public String getCodeName() {
        return codeName;
    }

    public DefaultExpression<T> getDefaultExpression() {
        return defaultExpression;
    }

    public Parser<? extends T> getParser() {
        return parser;
    }

    public Cloner<? extends T> getCloner() {
        return cloner;
    }

    public T clone(T t) {
        return cloner == null ? t : cloner.clone(t);
    }

    public Pattern[] getUserInputPatterns() {
        return userInputPatterns;
    }

    /** The user input patterns as given, for NeoSkriptCompat. */
    public String[] userInput() {
        return userInput;
    }

    public boolean matchesUserInput(String input) {
        if (userInputPatterns == null) {
            return false;
        }
        for (Pattern pattern : userInputPatterns) {
            if (pattern.matcher(input).matches()) {
                return true;
            }
        }
        return false;
    }

    public Changer<? super T> getChanger() {
        return changer;
    }

    public Supplier<Iterator<T>> getSupplier() {
        return supplier;
    }

    public Serializer<? super T> getSerializer() {
        return serializer;
    }

    public Class<?> getSerializeAs() {
        return serializeAs;
    }

    public String[] getDescription() {
        return description;
    }

    public String[] getUsage() {
        return usage;
    }

    public String[] getExamples() {
        return examples;
    }

    public String getSince() {
        return since;
    }

    public String getDocName() {
        return docName;
    }

    public String[] getRequiredPlugins() {
        return requiredPlugins;
    }

    public String getDocumentationID() {
        return documentationId;
    }

    public boolean hasDocs() {
        return docName != null && docName != NO_DOC;
    }

    public ClassInfo<T> before(final String... before) {
        this.before.addAll(java.util.List.of(before));
        return this;
    }

    public ClassInfo<T> after(final String... after) {
        this.after.addAll(java.util.List.of(after));
        return this;
    }

    public Set<String> before() {
        return before;
    }

    public Set<String> after() {
        return after;
    }

    @Override
    public String toString() {
        return codeName;
    }

    public String toString(final int flags) {
        return codeName;
    }

    @Override
    public String toString(final Event event, final boolean debug) {
        return codeName;
    }

    private final java.util.Map<org.skriptlang.skript.lang.properties.Property<?>, Object> properties =
            new java.util.LinkedHashMap<>();

    /**
     * A property of the type's values, as {@code name of x}. NeoSkriptCompat records it, but NeoSkript's syntax
     * doesn't use addons' properties yet.
     */
    public <H extends org.skriptlang.skript.lang.properties.handlers.base.PropertyHandler<?>> ClassInfo<T> property(
            org.skriptlang.skript.lang.properties.Property<?> property, String description,
            org.skriptlang.skript.addon.SkriptAddon addon, H handler) {
        properties.put(property, handler);
        return this;
    }

    public boolean hasProperty(org.skriptlang.skript.lang.properties.Property<?> property) {
        return properties.containsKey(property);
    }

    public java.util.Collection<org.skriptlang.skript.lang.properties.Property<?>> getAllProperties() {
        return java.util.List.copyOf(properties.keySet());
    }
}
