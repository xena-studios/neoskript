package co.xenastudios.neoskript.core.variable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A simple line-based persistence backend for global variables.
 *
 * <p>Each line is {@code name<TAB>type<TAB>value}, where {@code type} is {@code string},
 * {@code number}, or {@code boolean}. Values are escaped so newlines and tabs round-trip. Variables
 * whose values are not one of these simple types are skipped on save (full serialization via the
 * type registry arrives with the broader type system). This is intentionally human-readable; the
 * async SQL backends described in the plan slot in behind the same load/save contract.
 */
public final class FlatFileVariableStore {

    private FlatFileVariableStore() {
    }

    /**
     * Loads variables from a file.
     *
     * @param file the storage file
     * @return the loaded variables (empty if the file does not exist)
     * @throws IOException if reading fails
     */
    public static Map<String, Object> load(Path file) throws IOException {
        Map<String, Object> variables = new LinkedHashMap<>();
        if (!Files.exists(file)) {
            return variables;
        }
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            if (line.isBlank()) {
                continue;
            }
            String[] parts = line.split("\t", 3);
            if (parts.length != 3) {
                continue;
            }
            String name = unescape(parts[0]);
            Object value = switch (parts[1]) {
                case "number" -> parseNumber(parts[2]);
                case "boolean" -> Boolean.valueOf(parts[2]);
                case "string" -> unescape(parts[2]);
                default -> deserializeCustom(parts[1], unescape(parts[2]));
            };
            if (value != null) {
                variables.put(name, value);
            }
        }
        return variables;
    }

    /**
     * Saves the simple-typed entries of {@code variables} to a file, creating parent directories.
     *
     * @param file      the storage file
     * @param variables the variables to persist
     * @throws IOException if writing fails
     */
    public static void save(Path file, Map<String, Object> variables) throws IOException {
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        List<String> lines = new java.util.ArrayList<>(variables.size());
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            Object value = entry.getValue();
            String type;
            String serialized;
            if (value instanceof Number number) {
                type = "number";
                serialized = Double.toString(number.doubleValue());
            } else if (value instanceof Boolean bool) {
                type = "boolean";
                serialized = bool.toString();
            } else if (value instanceof String text) {
                type = "string";
                serialized = escape(text);
            } else {
                ValueSerializer serializer = ValueSerializers.forValue(value);
                if (serializer == null) {
                    continue; // no serializer registered for this type
                }
                type = serializer.id();
                serialized = escape(serializer.serialize(value));
            }
            lines.add(escape(entry.getKey()) + "\t" + type + "\t" + serialized);
        }
        // Write to a temp file then move into place so a crash mid-write can't corrupt the store.
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.write(tmp, lines, StandardCharsets.UTF_8);
        try {
            Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static Object deserializeCustom(String typeId, String data) {
        ValueSerializer serializer = ValueSerializers.byId(typeId);
        return serializer == null ? null : serializer.deserialize(data);
    }

    private static Double parseNumber(String text) {
        try {
            return Double.valueOf(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n");
    }

    private static String unescape(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char next = s.charAt(++i);
                sb.append(switch (next) {
                    case 't' -> '\t';
                    case 'n' -> '\n';
                    default -> next;
                });
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
