package co.xenastudios.neoskript.core.docs;

import co.xenastudios.neoskript.core.registry.DefaultSyntaxRegistry;
import co.xenastudios.neoskript.core.runtime.EventRegistry;

import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Generates a Markdown syntax reference from the live registries, so the documentation always
 * reflects exactly what is registered (built-ins plus any addons). This is the "docs generated from
 * syntax metadata" deliverable; richer per-syntax descriptions can be layered on as the metadata
 * model grows.
 */
public final class DocsGenerator {

    private DocsGenerator() {
    }

    /**
     * Renders the syntax reference.
     *
     * @param registry the syntax registry
     * @param events   the event registry
     * @return Markdown text
     */
    public static String generate(DefaultSyntaxRegistry registry, EventRegistry events) {
        StringBuilder sb = new StringBuilder();
        sb.append("# NeoSkript Syntax Reference\n\n");
        sb.append("_Generated from the registered syntax (")
                .append(registry.size()).append(" elements)._\n");

        sb.append("\n## Events\n\n");
        // Group event aliases by their target class for a tidy listing.
        Map<String, TreeSet<String>> byEvent = new TreeMap<>();
        events.aliases().forEach((alias, type) ->
                byEvent.computeIfAbsent(type.getSimpleName(), k -> new TreeSet<>()).add(alias));
        byEvent.forEach((type, aliases) ->
                sb.append("- `on ").append(String.join("`, `on ", aliases)).append("` — ").append(type).append('\n'));

        sb.append("\n## Effects\n\n");
        registry.allEffects().forEach(e -> sb.append("- `").append(e.pattern().raw()).append("`\n"));

        sb.append("\n## Conditions\n\n");
        registry.allConditions().forEach(c -> sb.append("- `").append(c.pattern().raw()).append("`\n"));

        sb.append("\n## Expressions\n\n");
        registry.allExpressions().forEach(x -> sb.append("- `").append(x.pattern().raw())
                .append("` → ").append(x.returnType().getSimpleName()).append('\n'));

        return sb.toString();
    }
}
