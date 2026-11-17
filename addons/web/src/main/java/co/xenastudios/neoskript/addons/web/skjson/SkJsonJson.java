/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.addons.web.skjson;

import co.xenastudios.neoskript.addons.web.WebAddon.Web;
import co.xenastudios.neoskript.addons.web.http.WebRequest;
import co.xenastudios.neoskript.addons.web.json.Json;
import co.xenastudios.neoskript.api.element.Expression;
import co.xenastudios.neoskript.api.element.KeyAware;
import co.xenastudios.neoskript.api.element.Keyed;
import co.xenastudios.neoskript.api.element.SyntaxError;
import co.xenastudios.neoskript.api.element.TriggerContext;
import co.xenastudios.neoskript.api.element.Update;
import co.xenastudios.neoskript.api.registration.Patterns;
import co.xenastudios.neoskript.api.registration.Registrar;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * skJson 6.0.0's JSON: the {@code jsonelement} type, JSON from text and values, JSON into list variables and back,
 * printing it, its size, keys and values (at a path), and checking its type, emptiness and contents. JSON files,
 * caches, watchers, NBT, and changing JSON at a path aren't here yet; the addon's docs list them.
 */
public final class SkJsonJson {

    private SkJsonJson() {}

    /**
     * Registers skJson's JSON syntax.
     *
     * @param registrar the registrar
     * @param web the addon
     */
    public static void register(Registrar registrar, Web web) {
        // From the web: a GET, waited for without blocking the server (skJson blocked the main thread).
        registrar.pausingExpression("NeoSkriptWebExprSkJsonFromUrl", "jsonelement",
                info -> (context, args) -> fromUrl(web, first(args[0])),
                "json from [the] (website|url) %strings%",
                "json from url %strings%",
                "fetch json from [url] %strings%");
        registrar.timeout("NeoSkriptWebExprSkJsonFromUrl", web.config().timeout().plusSeconds(5));
        registrar.expression("NeoSkriptWebExprSkJsonNew", "jsonelement",
                info -> (context, args) -> map(args[0], Json::toJson),
                "json (from|of) %objects%",
                "parse %objects% as json",
                "json (parsed|created) from %objects%");
        registrar.effect("NeoSkriptWebEffSkJsonMap", info -> {
            if (!info.isList(1)) {
                throw new SyntaxError("JSON can only be mapped to a list variable, like {_data::*}");
            }
            return Update.of(1, (context, args) -> {
                Object value = first(args[0]);
                return value == null ? Map.of() : Json.toVariables(Json.toJson(value));
            });
        }, "[:async] (map|copy) %jsonelement% to %objects%",
                "[:async] store json %jsonelement% in %objects%",
                "[:async] copy json %jsonelement% to %objects%");
        registrar.expression("NeoSkriptWebExprSkJsonFormatted", "jsonelement", info -> {
            if (!info.isList(0)) {
                throw new SyntaxError("Only a list variable can be formatted as JSON, like {_data::*}");
            }
            return new Formatted();
        }, Patterns.property("form[atted [json]]", "objects"));
        registrar.expression("NeoSkriptWebExprSkJsonPretty", "string", info -> {
            int pattern = info.pattern();
            return (context, args) -> {
                if (!(first(args[0]) instanceof JsonElement json)) {
                    return null;
                }
                // skJson printed 'as compact json' indented too; here it's compact.
                return pattern == 0 ? Json.coloured(json) : pattern >= 4 ? json.toString() : Json.pretty(json);
            };
        }, "%jsonelement% as pretty[ printed]",
                "%jsonelement% as (pretty[ printed]|formatted) json",
                "%jsonelement% as uncolo[u]red pretty[ printed]",
                "%jsonelement% as unformatted pretty[ printed]",
                "%jsonelement% as (compact|minified) json",
                "%jsonelement% as compact json");
        registrar.expression("NeoSkriptWebExprSkJsonSize", "integer",
                info -> (context, args) -> map(args[0], json -> json instanceof JsonObject object ? (Object) (long) object.size()
                        : json instanceof JsonArray array ? (Object) (long) array.size() : null),
                Patterns.property("json size", "jsonelements"));
        registrar.expression("NeoSkriptWebExprSkJsonKeys", "string", info -> (context, args) -> {
            JsonElement json = first(args[1]) instanceof JsonElement j ? j : null;
            if (json == null) {
                return null;
            }
            if (args[0] instanceof String path) {
                json = Json.at(json, path);
            }
            if (!(json instanceof JsonObject object)) {
                web.console().warn("Only json objects are supported");
                return null;
            }
            return object.keySet().toArray();
        }, "[all] keys [%-string%] of %jsonelement%",
                "[every|all] key[s] (at|in) [path] [%-string%] (of|in) %jsonelement%",
                "keys (at|in) [path] [%-string%] (of|in) %jsonelement%");
        registerValues(registrar, web);
        registerConditions(registrar, web);
    }

    /** skJson's {@code value(s) [at path %string%] of %jsonelement%}, in all its forms. */
    private static void registerValues(Registrar registrar, Web web) {
        registrar.expression("NeoSkriptWebExprSkJsonValues", "object", info -> {
            boolean several = info.hasTag("values") || Set.of(0, 1, 3, 4, 6, 7).contains(info.pattern());
            return (context, args) -> {
                JsonElement json = null;
                String path = null;
                for (Object arg : args) {
                    Object value = first(arg);
                    if (value instanceof JsonElement j) {
                        json = j;
                    } else if (value instanceof String text) {
                        path = text;
                    }
                }
                if (json == null || json.isJsonNull()) {
                    return null;
                }
                if (several) {
                    JsonElement at = Json.at(json, path);
                    if (at == null) {
                        return null;
                    }
                    if (at.isJsonArray() || at.isJsonObject()) {
                        return Json.values(at);
                    }
                    return Json.fromJson(at);
                }
                if (path == null) {
                    web.console().warn("You cannot use \"value\" in root without specified path");
                    return null;
                }
                JsonElement at = Json.at(json, path);
                if (at instanceof JsonArray) {
                    web.console().warn("You didn't want to use \"value\" instead of \"values\", the plural"
                            + " expression will ensure that you get the array");
                }
                return at == null ? null : Json.fromJson(at);
            };
        }, "[json] :values [%-string%] of %jsonelement%",
                "[json] values [of] %jsonelement%",
                "[json] :value [%-string%] of %jsonelement%",
                ":values at path [%-string%] in %jsonelement%",
                "values at path [%-string%] in %jsonelement%",
                "value at path [%-string%] in %jsonelement%",
                ":values of path [%-string%] in %jsonelement%",
                "values of path [%-string%] in %jsonelement%",
                "value of path [%-string%] in %jsonelement%",
                "path %string% of %jsonelement%",
                "the path %string% in %jsonelement%",
                "%jsonelement%'s path %string%",
                "%jsonelement%'s value at path %string%");
    }

    private static void registerConditions(Registrar registrar, Web web) {
        registrar.condition("NeoSkriptWebCondSkJsonType", info -> {
            boolean negated = info.pattern() % 2 == 1;
            String type = info.hasTag("object") ? "object" : info.hasTag("array") ? "array"
                    : info.hasTag("primitive") ? "primitive" : "null";
            return (context, args) -> {
                if (!(first(args[0]) instanceof JsonElement json)) {
                    return false;
                }
                boolean is = switch (type) {
                    case "object" -> json.isJsonObject();
                    case "array" -> json.isJsonArray();
                    case "primitive" -> json.isJsonPrimitive();
                    default -> json.isJsonNull();
                };
                return negated != is;
            };
        }, "json type of %jsonelement% is (json[ ](:object)|json[ ](:array)|json[ ](:primitive)|json[ ](:null))",
                "json type of %jsonelement% (is(n't| not)) (json[ ](:object)|json[ ](:array)|json[ ](:primitive)|json[ ](:null))",
                "%jsonelement% is [a] json[ ](:object|:array|:primitive|:null)",
                "%jsonelement% is(n't| not) [a] json[ ](:object|:array|:primitive|:null)");
        registrar.condition("NeoSkriptWebCondSkJsonEmpty", info -> {
            boolean expectEmpty = info.pattern() % 2 == 0;
            return (context, args) -> {
                Object value = first(args[0]);
                boolean empty = !(value instanceof JsonElement json) || (json instanceof JsonObject object
                        ? object.isEmpty() : json instanceof JsonArray array && array.isEmpty());
                return expectEmpty == empty;
            };
        }, "json %jsonelement% is empty", "json %jsonelement% is(n't| not) empty",
                "%jsonelement% is empty json", "%jsonelement% is(n't| not) empty json");
        registrar.condition("NeoSkriptWebCondSkJsonHas", info -> {
            boolean negated = info.pattern() % 2 == 1;
            boolean values = info.hasTag("value") || info.hasTag("values");
            return (context, args) -> has(web, first(args[0]), args[1], values, negated);
        }, "[json] %jsonelement% has [:all] (:value[s]|:key[s]) %objects%",
                "[json] %jsonelement% does(n't| not) have [:all] (:value[s]|:key[s]) %objects%",
                "%jsonelement% contains [:all] (:value[s]|:key[s]) %objects%",
                "%jsonelement% does(n't| not) contain [:all] (:value[s]|:key[s]) %objects%");
    }

    /** skJson's {@code has key/value}: every one given must be there. */
    private static boolean has(Web web, Object value, Object wanted, boolean values, boolean negated) {
        if (!(value instanceof JsonElement json) || wanted == null) {
            return false;
        }
        Object[] all = wanted instanceof Object[] array ? array : new Object[] {wanted};
        boolean found = true;
        for (Object each : all) {
            if (values) {
                JsonElement needle = Json.toJson(each);
                if (json instanceof JsonArray array) {
                    found = array.contains(needle);
                } else if (json instanceof JsonObject object) {
                    found = object.entrySet().stream().anyMatch(e -> needle.equals(e.getValue()));
                }
            } else if (json instanceof JsonObject object) {
                found = object.keySet().contains(String.valueOf(each));
            } else if (json instanceof JsonArray) {
                web.console().warn("Keys are not supported for arrays");
                return false;
            }
            if (!found) {
                break;
            }
        }
        return found != negated;
    }

    /** skJson's {@code {_x::*}'s formatted json}: a list variable, nested lists included, as JSON. */
    private static final class Formatted implements Expression, KeyAware {
        @Override
        public Object get(TriggerContext context, Object[] arguments) {
            if (!(arguments[0] instanceof Keyed keyed)) {
                return null;
            }
            return Json.fromVariables(keyed.keys(), keyed.values());
        }

        @Override
        public boolean recursiveKeys() {
            return true;
        }
    }

    private static CompletableFuture<Object> fromUrl(Web web, Object url) {
        if (!(url instanceof String address)) {
            return null;
        }
        return web.engine().send(new WebRequest("GET", address, List.of(), null)).thenApply(response -> {
            if (response.status() > 400) {
                return null;
            }
            return Json.parseOrText(response.text(response.charset()));
        });
    }

    static Object first(Object value) {
        if (value instanceof Object[] array) {
            return array.length == 0 ? null : array[0];
        }
        return value;
    }

    private static Object map(Object value, java.util.function.Function<Object, Object> change) {
        if (value instanceof Object[] array) {
            List<Object> out = new ArrayList<>(array.length);
            for (Object each : array) {
                Object changed = change.apply(each);
                if (changed != null) {
                    out.add(changed);
                }
            }
            return out.toArray();
        }
        return value == null ? null : change.apply(value);
    }
}
