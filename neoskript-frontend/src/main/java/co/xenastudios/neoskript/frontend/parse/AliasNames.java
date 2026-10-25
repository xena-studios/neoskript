/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package co.xenastudios.neoskript.frontend.parse;

import co.xenastudios.neoskript.frontend.structure.Node;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** The names a script's {@code aliases:} define, as Skript writes them: {@code [any] boot¦s}. */
public final class AliasNames {

    private AliasNames() {}

    /** Every name the {@code name = items} lines of an {@code aliases:} section define. */
    static Set<String> of(Node.Section section) {
        Set<String> names = new HashSet<>();
        for (Node child : section.children()) {
            String line = child.line().text().strip();
            int equals = line.indexOf('=');
            if (!(child instanceof Node.Section) && equals > 0) {
                names.addAll(forms(line.substring(0, equals).strip().toLowerCase(Locale.ROOT)));
            }
        }
        return names;
    }

    /** The names an alias's name stands for: {@code [any] boot¦s} is any boot, any boots, boot and boots. */
    public static List<String> forms(String pattern) {
        List<String> forms = new ArrayList<>();
        expand(pattern, forms);
        List<String> clean = new ArrayList<>();
        for (String form : forms) {
            String normal = form.strip().replaceAll("\\s+", " ");
            if (!normal.isEmpty() && !clean.contains(normal)) {
                clean.add(normal);
            }
        }
        return clean;
    }

    private static void expand(String pattern, List<String> into) {
        // The first optional or choice group, expanded each way.
        int open = -1;
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (c == '[' || c == '(') {
                open = i;
                break;
            }
        }
        if (open < 0) {
            int bar = pattern.indexOf('\u00a6');
            if (bar < 0) {
                into.add(pattern);
                return;
            }
            // 'boot¦s': the word with and without its plural ending.
            int end = bar + 1;
            while (end < pattern.length() && Character.isLetter(pattern.charAt(end))) {
                end++;
            }
            String rest = pattern.substring(end);
            expand(pattern.substring(0, bar) + rest, into);
            expand(pattern.substring(0, bar) + pattern.substring(bar + 1, end) + rest, into);
            return;
        }
        int depth = 0;
        int close = -1;
        List<Integer> bars = new ArrayList<>();
        for (int i = open; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (c == '[' || c == '(') {
                depth++;
            } else if (c == ']' || c == ')') {
                depth--;
                if (depth == 0) {
                    close = i;
                    break;
                }
            } else if (c == '|' && depth == 1) {
                bars.add(i);
            }
        }
        if (close < 0) {
            into.add(pattern);
            return;
        }
        String before = pattern.substring(0, open);
        String after = pattern.substring(close + 1);
        List<String> choices = new ArrayList<>();
        int start = open + 1;
        for (int bar : bars) {
            choices.add(pattern.substring(start, bar));
            start = bar + 1;
        }
        choices.add(pattern.substring(start, close));
        if (pattern.charAt(open) == '[') {
            choices.add("");
        }
        for (String choice : choices) {
            expand(before + choice + after, into);
        }
    }
}
