/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package org.skriptlang.skript.lang.entry;

import ch.njol.skript.config.Node;
import ch.njol.skript.config.SectionNode;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

/** The entries a section or structure takes. NeoSkriptCompat records them; it doesn't read sections' entries yet. */
public class EntryValidator {

    private final List<EntryData<?>> entryData;
    private final Predicate<Node> unexpectedNodeTester;
    private final Function<String, String> unexpectedEntryMessage;
    private final Function<String, String> missingRequiredEntryMessage;

    protected EntryValidator(List<EntryData<?>> entryData, Predicate<Node> unexpectedNodeTester,
            Function<String, String> unexpectedEntryMessage, Function<String, String> missingRequiredEntryMessage) {
        this.entryData = List.copyOf(entryData);
        this.unexpectedNodeTester = unexpectedNodeTester;
        this.unexpectedEntryMessage = unexpectedEntryMessage;
        this.missingRequiredEntryMessage = missingRequiredEntryMessage;
    }

    public static EntryValidatorBuilder builder() {
        return new EntryValidatorBuilder();
    }

    public List<EntryData<?>> getEntryData() {
        return entryData;
    }

    public Predicate<Node> getUnexpectedNodeTester() {
        return unexpectedNodeTester;
    }

    public Function<String, String> getUnexpectedEntryMessage() {
        return unexpectedEntryMessage;
    }

    public Function<String, String> getMissingRequiredEntryMessage() {
        return missingRequiredEntryMessage;
    }

    public EntryContainer validate(SectionNode sectionNode) {
        throw new UnsupportedOperationException("NeoSkriptCompat doesn't read sections' entries yet");
    }

    /** Builds an entry validator. */
    public static class EntryValidatorBuilder {

        public static final String DEFAULT_ENTRY_SEPARATOR = ": ";

        private final List<EntryData<?>> entryData = new ArrayList<>();
        private String entrySeparator = DEFAULT_ENTRY_SEPARATOR;
        private Predicate<Node> unexpectedNodeTester;
        private Function<String, String> unexpectedEntryMessage = key -> "Unexpected entry '" + key + "'";
        private Function<String, String> missingRequiredEntryMessage = key -> "Required entry '" + key + "' is missing";

        protected EntryValidatorBuilder() {}

        public EntryValidatorBuilder entrySeparator(String separator) {
            this.entrySeparator = separator;
            return this;
        }

        public EntryValidatorBuilder unexpectedNodeTester(Predicate<Node> tester) {
            this.unexpectedNodeTester = tester;
            return this;
        }

        public EntryValidatorBuilder unexpectedEntryMessage(Function<String, String> message) {
            this.unexpectedEntryMessage = message;
            return this;
        }

        public EntryValidatorBuilder missingRequiredEntryMessage(Function<String, String> message) {
            this.missingRequiredEntryMessage = message;
            return this;
        }

        public EntryValidatorBuilder addEntry(String key, String defaultValue, boolean optional) {
            return addEntry(key, defaultValue, optional, false);
        }

        public EntryValidatorBuilder addEntry(String key, String defaultValue, boolean optional, boolean multiple) {
            entryData.add(new KeyValueEntryData<String>(key, defaultValue, optional, multiple) {
                @Override
                protected String getValue(String value) {
                    return value;
                }
            });
            return this;
        }

        public EntryValidatorBuilder addSection(String key, boolean optional) {
            return addSection(key, optional, false);
        }

        public EntryValidatorBuilder addSection(String key, boolean optional, boolean multiple) {
            entryData.add(new SectionEntryData(key, null, optional, multiple));
            return this;
        }

        public EntryValidatorBuilder addEntryData(EntryData<?> data) {
            entryData.add(data);
            return this;
        }

        public EntryValidator build() {
            return new EntryValidator(entryData, unexpectedNodeTester, unexpectedEntryMessage,
                    missingRequiredEntryMessage);
        }
    }
}
