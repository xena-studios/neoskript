/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.addons.yaml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class YamlTextTest {

    private static final FakeValues VALUES = new FakeValues();
    private static final String N = YamlText.LINE_BREAK;

    private static YamlFile load(String text) throws YamlText.Unreadable {
        YamlText.Read read = YamlText.read(text.getBytes(StandardCharsets.UTF_8), VALUES);
        YamlFile file = new YamlFile(Path.of("test.yml"));
        file.replace(read.root(), read.header(), read.comments());
        return file;
    }

    private static String save(YamlFile file, boolean extraLines) {
        return YamlText.write(file.snapshot(), extraLines, VALUES);
    }

    @Test
    void writesAsSkriptYamlDoes() {
        YamlFile file = new YamlFile(Path.of("test.yml"));
        file.set("a", 1L);
        file.set("b.c", "x");
        file.set("b.d", List.of(1L, 2L));
        file.set("e", "&ahi");
        file.header(false, "Hi");
        file.comment("b", false, "about b");
        assertEquals("## Hi" + N + N + "a: 1" + N + N + "# about b" + N + "b:" + N + "    c: x" + N + "    d:" + N
                + "    - 1" + N + "    - 2" + N + N + "e: \"&ahi\"" + N, save(file, true));
        assertEquals("## Hi" + N + "a: 1" + N + "# about b" + N + "b:" + N + "    c: x" + N + "    d:" + N + "    - 1" + N
                + "    - 2" + N + "e: \"&ahi\"" + N, save(file, false));
        file.indent(2);
        assertTrue(save(file, false).contains("b:" + N + "  c: x" + N));
    }

    @Test
    void aFileReadsBackAndIsWrittenTheSame() throws Exception {
        String text = "## Header line" + N + "## second" + N + "# about a" + N + "a: 1" + N + "b:" + N + "    c: true" + N
                + "    d: 1.5" + N + "    e:" + N + "    - x" + N + "    - y" + N + "'1': one" + N + "f: \"&cred\"" + N;
        YamlFile file = load(text);
        assertEquals("## Header line" + N + "## second", file.header());
        assertEquals("# about a", file.comment("a").text());
        assertEquals(List.of("a", "b", "b.c", "b.d", "b.e", "1", "f"), file.paths());
        assertEquals(text, save(file, false));
        assertEquals(text, save(load(save(file, false)), false));
    }

    @Test
    void valuesOfNoYamlTypeAreSavedAsSkriptSavesThem() throws Exception {
        YamlFile file = new YamlFile(Path.of("test.yml"));
        file.set("wait", Duration.ofSeconds(5));
        file.set("thing", new FakeValues.Thing("abc"));
        file.set("nothing", new Object());
        String text = save(file, false);
        assertEquals("wait: !skripttimespan '5 seconds'" + N + "thing: !skriptclass" + N + "    type: thing" + N
                + "    data: YWJj" + N + "nothing: null" + N, text.replace("nothing:" + N, "nothing: null" + N));
        YamlFile read = load(text);
        assertEquals(Duration.ofSeconds(5), read.get("wait"));
        assertEquals(new FakeValues.Thing("abc"), read.get("thing"));
        // What's stored isn't replaced by what's written, as skript-yaml replaces it.
        assertEquals(new FakeValues.Thing("abc"), file.get("thing"));
    }

    @Test
    void aValueThatCantBeReadIsKeptAsWritten() throws Exception {
        String text = "home: !location" + N + "    world: gone" + N + "    x: 1.0" + N + "    y: 2.0" + N + "    z: 3.0" + N
                + "    yaw: 0.0" + N + "    pitch: 0.0" + N + "odd: !unknowntag 'value'" + N + "other: !skriptclass" + N
                + "    type: nosuchtype" + N + "    data: AAAA" + N;
        YamlFile file = load(text);
        assertInstanceOf(YamlValues.Kept.class, file.get("home"));
        assertNull(YamlValues.toScript(file.get("odd")));
        assertEquals(text, save(file, false));
    }

    @Test
    void aFileEndingInACommentLoads() throws Exception {
        // skript-yaml appends the text "null" after it, and the file fails to load.
        YamlFile file = load("a: 1" + N + "# the end");
        assertEquals(1, file.get("a"));
    }

    @Test
    void brokenFilesArentRead() {
        assertThrows(YamlText.Unreadable.class, () -> load("a: [1, 2" + N));
        assertThrows(YamlText.Unreadable.class, () -> load("a: 1" + N + "  b: 2" + N + " c" + N));
        assertThrows(YamlText.Unreadable.class, () -> load("- 1" + N + "- 2" + N));
        assertThrows(YamlText.Unreadable.class, () -> load("just text"));
        assertThrows(YamlText.Unreadable.class, () -> load("a: &a [*a]" + N));
        assertThrows(YamlText.Unreadable.class, () -> load("---" + N + "a: 1" + N + "---" + N + "b: 2" + N));
    }

    @Test
    void aliasesThatMultiplyArentRead() {
        StringBuilder bomb = new StringBuilder("a0: &a0 [x, x, x, x, x, x, x, x, x, x]" + N);
        for (int i = 1; i < 9; i++) {
            bomb.append("a").append(i).append(": &a").append(i).append(" [");
            for (int j = 0; j < 10; j++) {
                bomb.append(j == 0 ? "" : ", ").append("*a").append(i - 1);
            }
            bomb.append("]").append(N);
        }
        YamlText.Unreadable error = assertThrows(YamlText.Unreadable.class, () -> load(bomb.toString()));
        assertTrue(error.getMessage().contains("values"), error.getMessage());
    }

    @Test
    void anEmptyFileIsEmpty() throws Exception {
        YamlFile file = load("");
        assertTrue(file.isEmpty());
        assertEquals("", save(file, true));
    }

    @Test
    void aliasesDontShareWhatTheyPointAt() throws Exception {
        YamlFile file = load("a: &x" + N + "    k: 1" + N + "b: *x" + N);
        file.set("a.k", 2L);
        assertEquals(1, file.get("b.k"));
    }

    @Test
    void textSetFromScriptsIsReadAsSkriptYamlReadsIt() {
        assertEquals(true, YamlValues.fromScript("yes", false));
        assertEquals(false, YamlValues.fromScript("off", false));
        assertEquals(5L, YamlValues.fromScript("5", false));
        assertEquals(-2.5, YamlValues.fromScript("-2.5", false));
        assertEquals("5", YamlValues.fromScript("5", true));
        assertEquals("&ahi", YamlValues.fromScript("§ahi", false));
        assertEquals("99999999999999999999", YamlValues.fromScript("99999999999999999999", false));
        assertEquals("§ahi", YamlValues.colourCodes("&ahi"));
    }

    @Test
    void numbersReadAsScriptNumbers() {
        assertEquals(5L, YamlValues.toScript(5));
        assertEquals(1.5, YamlValues.toScript(1.5f));
        assertTrue(YamlValues.same(5, 5L));
        assertTrue(YamlValues.same(2.0, 2L));
        assertEquals(Map.of(), YamlValues.toScript(Map.of()));
    }

    @Test
    void datesAreWrittenAsSkriptYamlWritesThem() {
        long millis = YamlConstructor.date("2026-11-17T10:15:30.250Z");
        assertEquals(java.time.Instant.parse("2026-11-17T10:15:30.250Z").toEpochMilli(), millis);
        assertEquals(java.time.Instant.parse("2026-11-17T00:00:00Z").toEpochMilli(), YamlConstructor.date("2026-11-17"));
        java.util.TimeZone zone = java.util.TimeZone.getDefault();
        try {
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"));
            assertEquals("2026-11-17T10:15:30.250Z", YamlText.isoDate(millis));
        } finally {
            java.util.TimeZone.setDefault(zone);
        }
    }
}
