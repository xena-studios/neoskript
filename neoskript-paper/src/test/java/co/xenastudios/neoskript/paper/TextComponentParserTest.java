/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TextComponentParserTest {

    private final TextComponentParser parser = TextComponentParser.instance();

    @AfterEach
    void defaults() {
        parser.colorsCauseReset(true);
        parser.safeTags(TextComponentParser.DEFAULT_SAFE_TAGS);
        parser.linkParseMode(TextComponentParser.LinkParseMode.DISABLED);
    }

    @Test
    void coloursResetFormattingUnlessTurnedOff() {
        assertEquals("<bold>a<red><!italic><!underlined><!strikethrough><!bold><!obfuscated>b",
                parser.toString(parser.parse("<bold>a<red>b")));
        parser.colorsCauseReset(false);
        assertEquals("<bold>a<red>b", parser.toString(parser.parse("<bold>a<red>b")));
    }

    @Test
    void theSafeParserReadsOnlyTheSafeTags() {
        parser.colorsCauseReset(false);
        assertEquals("<red>x", parser.toString(parser.parseSafe("<red>x")));
        parser.safeTags(List.of("decorations"));
        assertEquals("\\<red>x", parser.toString(parser.parseSafe("<red>x")));
        assertEquals("<bold>x", parser.toString(parser.parseSafe("<bold>x")));
        // The unsafe parser reads every tag whatever they are.
        assertEquals("<red>x", parser.toString(parser.parse("<red>x")));
    }

    @Test
    void linksAreParsedAsTheModeSays() {
        String text = "see https://example.com/a and example.org";
        assertEquals(List.of(), links(parser.parse(text)));
        parser.linkParseMode(TextComponentParser.LinkParseMode.STRICT);
        assertEquals(List.of("https://example.com/a"), links(parser.parse(text)));
        parser.linkParseMode(TextComponentParser.LinkParseMode.LENIENT);
        assertEquals(List.of("https://example.com/a", "example.org"), links(parser.parseSafe(text)));
        assertNull(parser.parse("no links here").clickEvent());
    }

    private static List<String> links(Component component) {
        List<String> links = new java.util.ArrayList<>();
        collect(component, links);
        return links;
    }

    private static void collect(Component component, List<String> links) {
        ClickEvent click = component.clickEvent();
        if (click != null && click.action() == ClickEvent.Action.OPEN_URL) {
            links.add(click.value());
        }
        component.children().forEach(child -> collect(child, links));
    }
}
