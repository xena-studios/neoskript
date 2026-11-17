/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.plugin;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import co.xenastudios.neoskript.plugin.config.ConfigException;
import co.xenastudios.neoskript.plugin.config.ConfigLoader;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Whatever config.yml holds, loading and reloading it never throws anything but a {@link ConfigException} for a
 * file that isn't YAML, never leaves a setting without a usable value, and never puts a setting back to its default
 * without a warning.
 */
class ConfigRobustnessTest {

    @TempDir
    Path folder;

    /** Values of every wrong kind: the wrong type, out of range, empty, huge, unicode, nothing. */
    private static final List<Object> BAD = Arrays.asList(
            List.of("a", "b"), Map.of("nested", 1), "", " ", -1, -2147483648L, 2147483648L, 1e308, Double.NaN,
            "99999999999999999999999999", "x".repeat(100_000), "\u4e2d\u6587 \ud83d\ude00 \u202e", null, true,
            "<red", "</gradient>", "5 eons", "0 seconds", "-5 minutes", "1000000000 years", "../../../../etc/passwd",
            "/dev/null", "\u0000");

    @Test
    void everyBadValueIsUsableAndWarnedAbout() throws Exception {
        String defaults = ConfigLoader.defaultText(NeoSkriptConfig.SPEC);
        YamlConfiguration base = new YamlConfiguration();
        base.loadFromString(defaults);
        List<String> leaves = base.getKeys(true).stream()
                .filter(path -> !base.isConfigurationSection(path) && !path.equals("config-version")).toList();
        assertTrue(leaves.size() > 20, leaves.toString());
        Path file = folder.resolve("config.yml");
        for (String path : leaves) {
            for (Object bad : BAD) {
                YamlConfiguration yaml = new YamlConfiguration();
                yaml.loadFromString(defaults);
                yaml.set(path, bad);
                Files.writeString(file, yaml.saveToString());
                String label = path + " = " + preview(bad);
                ConfigLoader.Result<NeoSkriptConfig> result;
                try {
                    result = ConfigLoader.load(NeoSkriptConfig.SPEC, file);
                } catch (ConfigException | RuntimeException e) {
                    throw new AssertionError(label + " threw " + e, e);
                }
                complete(result.config(), label);
                try {
                    // What scripts read takes whatever loaded.
                    ScriptSettings.apply(result.config(), folder);
                } catch (RuntimeException e) {
                    throw new AssertionError(label + ": applying it threw " + e, e);
                }
                Object value = valueAt(result.config(), path);
                Object fallback = valueAt(NeoSkriptConfig.DEFAULTS, path);
                if (result.warnings().isEmpty() && bad != null && value.equals(fallback)
                        && !String.valueOf(bad).strip().equalsIgnoreCase(String.valueOf(base.get(path)))) {
                    fail(label + " silently became the default " + fallback);
                }
            }
        }
    }

    @org.junit.jupiter.api.AfterEach
    void defaults() {
        ScriptSettings.apply(NeoSkriptConfig.DEFAULTS, folder);
    }

    @Test
    void aFileThatIsNotYamlIsOnlyAConfigExceptionAndIsLeftAlone() throws Exception {
        Random random = new Random(812);
        byte[] noise = new byte[64 * 1024];
        random.nextBytes(noise);
        List<byte[]> files = new ArrayList<>();
        for (String text : List.of("metrics: \"unclosed", "\tmetrics: true", "a: b: c", "[", "{", "- - - [", "&a [*a, *a]",
                "metrics: true\nmetrics: false", "%YAML 9.9\n---", "!!java.lang.Runtime {}", "[".repeat(20_000),
                "theme:\n" + "  x:\n".repeat(2_000), "key: " + "a".repeat(5_000_000))) {
            files.add(text.getBytes(StandardCharsets.UTF_8));
        }
        files.add(noise);
        Path file = folder.resolve("config.yml");
        for (byte[] bytes : files) {
            Files.write(file, bytes);
            try {
                complete(ConfigLoader.load(NeoSkriptConfig.SPEC, file).config(), "a broken file");
            } catch (ConfigException expected) {
                assertArrayEquals(bytes, Files.readAllBytes(file), "a file that couldn't be read was changed");
            } catch (RuntimeException | StackOverflowError e) {
                throw new AssertionError("A broken file threw " + e + ": " + preview(new String(bytes,
                        StandardCharsets.UTF_8)), e);
            }
        }
    }

    @Test
    void reloadingInALoopWhileTheFileChangesUnderIt() throws Exception {
        Path file = folder.resolve("config.yml");
        String good = ConfigLoader.defaultText(NeoSkriptConfig.SPEC).replace("update-checker: true", "update-checker: false");
        Files.writeString(file, good);
        List<String> failures = new ArrayList<>();
        List<ConfigLoader.Result<NeoSkriptConfig>> applied = new ArrayList<>();
        SettingsReloader reloader = new SettingsReloader(file, ConfigLoader.load(NeoSkriptConfig.SPEC, file).config(),
                new SettingsReloader.Effects() {
                    @Override
                    public void accept(ConfigLoader.Result<NeoSkriptConfig> result) {
                        applied.add(result);
                    }

                    @Override
                    public void playerUuids(boolean uuids) {}

                    @Override
                    public void scripts(NeoSkriptConfig settings) {
                        // What scripts read is applied for real: it must take whatever loaded.
                        ScriptSettings.apply(settings, folder);
                    }

                    @Override
                    public void effectCommands(EffectCommands.Settings settings) {}

                    @Override
                    public void runtimeErrors(NeoSkriptConfig.RuntimeErrors limits) {}

                    @Override
                    public void saveInterval(NeoSkriptConfig.Interval interval) {}

                    @Override
                    public void updateChecker(boolean enabled, boolean turnedOn) {}

                    @Override
                    public void failed(String message) {
                        failures.add(message);
                    }
                });
        for (int i = 0; i < 300; i++) {
            switch (i % 5) {
                case 0 -> Files.writeString(file, good.replace("total-errors-per-frame: 8", "total-errors-per-frame: " + i));
                case 1 -> Files.writeString(file, "metrics: \"unclosed");
                case 2 -> Files.deleteIfExists(file);
                case 3 -> Files.writeString(file, good.replace("token: '!'", "token: [a, b]"));
                default -> {
                    Files.writeString(file, good);
                    file.toFile().setWritable(false);
                }
            }
            SettingsReloader.Outcome outcome = reloader.reload();
            file.toFile().setWritable(true);
            if (i % 5 == 1) {
                assertNotNull(outcome.failure(), "a file that isn't YAML keeps the running settings");
            } else {
                assertNull(outcome.failure(), "round " + i + ": " + outcome.failure());
            }
            if (i % 5 == 2) {
                // Not silent: the defaults written are announced.
                assertTrue(applied.getLast().notices().stream().anyMatch(notice -> notice.contains("default")),
                        applied.getLast().notices().toString());
            }
            if (i % 5 == 0) {
                assertEquals(i, applied.getLast().config().runtimeErrors().totalErrorsPerFrame());
            }
        }
        assertEquals(60, failures.size());
    }

    /** Every setting has a value, none of them null, and counts aren't negative. */
    private static void complete(Record config, String label) throws Exception {
        for (RecordComponent component : config.getClass().getRecordComponents()) {
            Object value = component.getAccessor().invoke(config);
            assertNotNull(value, label + ": " + component.getName() + " is null");
            if (value instanceof Record nested) {
                complete(nested, label);
            } else if (value instanceof Integer number) {
                assertTrue(number >= 0, label + ": " + component.getName() + " is " + number);
            }
        }
    }

    /** The setting at a config.yml path, as the loaded config has it. */
    private static Object valueAt(Record config, String path) throws Exception {
        Object at = config;
        for (String key : path.split("\\.")) {
            Object next = null;
            for (RecordComponent component : at.getClass().getRecordComponents()) {
                if (kebab(component.getName()).equals(key)) {
                    next = component.getAccessor().invoke(at);
                }
            }
            if (next == null) {
                throw new AssertionError("No setting at " + path);
            }
            at = next;
        }
        return at;
    }

    private static String kebab(String name) {
        return name.replaceAll("([A-Z])", "-$1").toLowerCase(java.util.Locale.ROOT);
    }

    private static String preview(Object value) {
        String text = String.valueOf(value);
        return text.length() > 60 ? text.substring(0, 60) + "... (" + text.length() + ")" : text;
    }
}
