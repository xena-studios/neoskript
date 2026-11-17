/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.addons.web;

import co.xenastudios.neoskript.addons.web.WebAddon.Web;
import co.xenastudios.neoskript.addons.web.json.Json;
import co.xenastudios.neoskript.addons.web.reqn.ReqnResponse;
import co.xenastudios.neoskript.addons.web.reqn.ReqnSyntax;
import co.xenastudios.neoskript.addons.web.skjson.RequestMethod;
import co.xenastudios.neoskript.addons.web.skjson.SkJsonHttp;
import co.xenastudios.neoskript.addons.web.skjson.SkJsonJson;
import co.xenastudios.neoskript.addons.web.skjson.SkRequest;
import co.xenastudios.neoskript.addons.web.skjson.SkResponse;
import co.xenastudios.neoskript.api.element.Changeable;
import co.xenastudios.neoskript.api.element.Expression;
import co.xenastudios.neoskript.api.element.TriggerContext;
import co.xenastudios.neoskript.api.registration.Patterns;
import co.xenastudios.neoskript.api.registration.Registrar;
import co.xenastudios.neoskript.api.registration.SyntaxPriority;
import co.xenastudios.neoskript.api.registration.ThreadAffinity;
import co.xenastudios.neoskript.api.storage.BinaryReader;
import co.xenastudios.neoskript.api.storage.BinaryWriter;
import co.xenastudios.neoskript.api.storage.VariableCodec;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Registers NeoSkriptWeb's types and syntax: Reqn's ({@link ReqnSyntax}), skJson's requests ({@link SkJsonHttp}) and
 * JSON ({@link SkJsonJson}), and the properties both have, here.
 *
 * <p>Reqn and skJson both have {@code body of}, {@code headers of}, {@code status of} and {@code status code of}, for
 * their own responses. On Skript, with both installed, whichever loaded first took them, so the other's scripts got
 * nothing ({@code {_response}'s body} was Reqn's, and empty for an skJson response). Here each is one property that
 * works for both, by what it's given.
 */
public final class WebSyntax {

    private WebSyntax() {}

    /**
     * Registers everything.
     *
     * @param registrar the registrar
     * @param web the addon
     */
    public static void register(Registrar registrar, Web web) {
        types(registrar);
        ReqnSyntax.register(registrar, web);
        SkJsonHttp.register(registrar, web);
        SkJsonJson.register(registrar, web);
        shared(registrar);
        reqnProperties(registrar);
        priorities(registrar);
        // Nothing here touches what a region owns, so it all runs on any thread on Folia.
        for (String id : List.of("NeoSkriptWebEffReqnRequest", "NeoSkriptWebExprReqnLastResponse",
                "NeoSkriptWebExprReqnUrlEncode", "NeoSkriptWebExprReqnJsonEncode", "NeoSkriptWebExprSkJsonRequest",
                "NeoSkriptWebEffSkJsonExecute", "NeoSkriptWebExprSkJsonLastResponse", "NeoSkriptWebExprSkJsonQueryParams",
                "NeoSkriptWebExprSkJsonFromUrl", "NeoSkriptWebExprSkJsonNew", "NeoSkriptWebEffSkJsonMap",
                "NeoSkriptWebExprSkJsonFormatted", "NeoSkriptWebExprSkJsonPretty", "NeoSkriptWebExprSkJsonSize",
                "NeoSkriptWebExprSkJsonKeys", "NeoSkriptWebExprSkJsonValues", "NeoSkriptWebCondSkJsonType",
                "NeoSkriptWebCondSkJsonEmpty", "NeoSkriptWebCondSkJsonHas", "NeoSkriptWebExprBody",
                "NeoSkriptWebExprStatusCode", "NeoSkriptWebExprReqnStatusCodes", "NeoSkriptWebExprHeaders",
                "NeoSkriptWebExprStatus", "NeoSkriptWebExprReqnStatuses",
                "NeoSkriptWebExprReqnHeaderKeys", "NeoSkriptWebExprReqnHeaderValues", "NeoSkriptWebExprReqnMessage",
                "NeoSkriptWebExprReqnHeader")) {
            registrar.affinity(id, ThreadAffinity.ANY);
        }
    }

    /**
     * Each element's priority, as Reqn and skJson register theirs, so Skript's parser order is kept: where one of
     * theirs and a built-in element both match a line, the one Skript picks is picked here too (skJson's
     * {@code keys of %json%}, a simple expression, before Skript's {@code key of %minecraft tag%}, a property).
     */
    private static void priorities(Registrar registrar) {
        Map<String, SyntaxPriority> levels = new LinkedHashMap<>();
        // Reqn: its effect has Skript's estimate from its pattern; its expressions are simple, its properties
        // properties.
        levels.put("NeoSkriptWebEffReqnRequest", SyntaxPriority.COMBINED);
        levels.put("NeoSkriptWebExprReqnLastResponse", SyntaxPriority.SIMPLE);
        levels.put("NeoSkriptWebExprReqnUrlEncode", SyntaxPriority.SIMPLE);
        levels.put("NeoSkriptWebExprReqnJsonEncode", SyntaxPriority.SIMPLE);
        // skJson: as its Register gives them.
        levels.put("NeoSkriptWebExprSkJsonRequest", SyntaxPriority.SIMPLE);
        levels.put("NeoSkriptWebEffSkJsonExecute", SyntaxPriority.COMBINED);
        levels.put("NeoSkriptWebExprSkJsonFromUrl", SyntaxPriority.SIMPLE);
        levels.put("NeoSkriptWebExprSkJsonNew", SyntaxPriority.SIMPLE);
        levels.put("NeoSkriptWebEffSkJsonMap", SyntaxPriority.COMBINED);
        levels.put("NeoSkriptWebExprSkJsonPretty", SyntaxPriority.SIMPLE);
        levels.put("NeoSkriptWebExprSkJsonKeys", SyntaxPriority.SIMPLE);
        levels.put("NeoSkriptWebExprSkJsonValues", SyntaxPriority.COMBINED);
        levels.put("NeoSkriptWebCondSkJsonType", SyntaxPriority.COMBINED);
        levels.put("NeoSkriptWebCondSkJsonEmpty", SyntaxPriority.COMBINED);
        levels.put("NeoSkriptWebCondSkJsonHas", SyntaxPriority.COMBINED);
        for (String property : List.of("NeoSkriptWebExprSkJsonLastResponse", "NeoSkriptWebExprSkJsonQueryParams",
                "NeoSkriptWebExprSkJsonFormatted", "NeoSkriptWebExprSkJsonSize", "NeoSkriptWebExprBody",
                "NeoSkriptWebExprStatusCode", "NeoSkriptWebExprReqnStatusCodes", "NeoSkriptWebExprHeaders",
                "NeoSkriptWebExprStatus", "NeoSkriptWebExprReqnStatuses",
                "NeoSkriptWebExprReqnHeaderKeys", "NeoSkriptWebExprReqnHeaderValues", "NeoSkriptWebExprReqnMessage",
                "NeoSkriptWebExprReqnHeader")) {
            levels.put(property, SyntaxPriority.PROPERTY);
        }
        levels.forEach(registrar::priority);
        // Their properties are several values when their owners are, as Skript's property expressions.
        levels.forEach((id, level) -> {
            if (level == SyntaxPriority.PROPERTY) {
                registrar.pluralAsArguments(id);
            }
        });
    }

    private static void types(Registrar registrar) {
        registrar.type("httpresponse", "http response", ReqnResponse.class);
        registrar.text(ReqnResponse.class, ReqnResponse::toString);
        registrar.type("request", "request", SkRequest.class);
        registrar.text(SkRequest.class, SkRequest::toString);
        registrar.type("response", "response", SkResponse.class);
        registrar.text(SkResponse.class, SkResponse::toString);
        Map<String, RequestMethod> methods = new LinkedHashMap<>();
        for (RequestMethod method : RequestMethod.values()) {
            methods.put(method.name().toLowerCase(Locale.ROOT), method);
        }
        registrar.namedType("requestmethod", "request method", RequestMethod.class, methods);
        registrar.text(RequestMethod.class, RequestMethod::name);
        registrar.type("jsonelement", "json element", JsonElement.class);
        registrar.text(JsonElement.class, JsonElement::toString);
        // Saved as skJson saved it: its text in a field named 'json', under skJson's type name, so variables.csv
        // files carry over.
        registrar.storage("jsonelement", JsonElement.class, new VariableCodec<>() {
            @Override
            public void encode(JsonElement value, BinaryWriter out) {
                out.beginObject(1);
                out.stringField("json", value.toString());
            }

            @Override
            public JsonElement decode(BinaryReader in) {
                in.beginObject();
                return Json.toJson(in.stringField("json"));
            }
        });
    }

    /**
     * The properties Reqn's and skJson's responses share, for whichever they're given. skJson's {@code status} and
     * {@code status code} are of one response, so '(status of {_r} and body of {_r})' is two values, as on Skript, where
     * a list can't be one response and the parser splits it (issue #44). Reqn's are of several, as Reqn registers them
     * ({@code status codes of {_responses::*}}): its other spellings ({@code status codes}, {@code response code},
     * {@code status line}) take several here. Only the singular spelling of a list of Reqn's is refused, where Reqn took it.
     */
    private static void shared(Registrar registrar) {
        registrar.expression("NeoSkriptWebExprBody", "object", info -> new Body(),
                Patterns.property("[response] bod(y|ies)", "httpresponses/requests/responses"));
        // Of a Reqn response, text, as Reqn's body is: so '(response code of last response and body of last response)'
        // is two values, as on Skript, where text can't be a response and the parser splits the list.
        registrar.returnType("NeoSkriptWebExprBody", info -> "httpresponse".equals(info.argumentType(0)) ? "string" : null);
        Expression code = (context, args) -> each(args[0], owner ->
                owner instanceof ReqnResponse r ? (Object) (long) r.code()
                        : owner instanceof SkResponse s && s.statusCode() != null ? (Object) (long) s.statusCode()
                        : null);
        registrar.expression("NeoSkriptWebExprStatusCode", "integer", info -> code,
                Patterns.property("status code", "httpresponse/response"));
        registrar.expression("NeoSkriptWebExprReqnStatusCodes", "integer", info -> code,
                Patterns.property("(response [status] code[s]|status codes|code[s])", "httpresponses/responses"));
        registrar.expression("NeoSkriptWebExprHeaders", "object", info -> new Headers(),
                Patterns.property("[response] header[s]", "httpresponses/requests/responses"));
        Expression status = (context, args) -> each(args[0], owner ->
                owner instanceof ReqnResponse r ? r.statusLine()
                        : owner instanceof SkResponse s ? s.status().toString() : null);
        registrar.expression("NeoSkriptWebExprStatus", "string", info -> status,
                Patterns.property("status", "httpresponse/response"));
        registrar.expression("NeoSkriptWebExprReqnStatuses", "string", info -> status,
                Patterns.property("(response status[(es| line[s])]|status(es| line[s]))", "httpresponses/responses"));
    }

    private static void reqnProperties(Registrar registrar) {
        registrar.expression("NeoSkriptWebExprReqnHeaderKeys", "string", info -> (context, args) -> each(args[0],
                owner -> owner instanceof ReqnResponse r ? r.headers().keySet().toArray() : null),
                Patterns.property("[response] header key[s]", "httpresponses"));
        registrar.expression("NeoSkriptWebExprReqnHeaderValues", "string", info -> (context, args) -> each(args[0],
                owner -> owner instanceof ReqnResponse r ? r.headers().values().toArray() : null),
                Patterns.property("[response] header value[s]", "httpresponses"));
        registrar.expression("NeoSkriptWebExprReqnMessage", "string", info -> (context, args) -> each(args[0],
                owner -> owner instanceof ReqnResponse r ? r.message() : null),
                Patterns.property("[response] [status] (message|reason)[s]", "httpresponses"));
        registrar.expression("NeoSkriptWebExprReqnHeader", "string", info -> {
            boolean keyFirst = info.pattern() == 0;
            return (context, args) -> {
                Object key = keyFirst ? args[0] : args[1];
                Object owners = keyFirst ? args[1] : args[0];
                if (!(key instanceof String name)) {
                    return null;
                }
                // Reqn looked the name up as written; a header's name is the same whatever its case.
                return each(owners, owner -> owner instanceof ReqnResponse r ? r.header(name) : null);
            };
        }, Patterns.property("%string% [response] header[ value][s]", "httpresponses"));
    }

    /** {@code body of}: Reqn's text, skJson's JSON (or text); a request's can be set and reset. */
    private static final class Body implements Expression, Changeable {
        @Override
        public Object get(TriggerContext context, Object[] arguments) {
            return each(arguments[0], owner -> owner instanceof ReqnResponse r ? r.body() : SkJsonHttp.bodyOf(owner));
        }

        @Override
        public Class<?>[] acceptChange(Mode mode) {
            return switch (mode) {
                case SET -> new Class<?>[] {JsonElement.class, String.class};
                case RESET -> new Class<?>[0];
                default -> null;
            };
        }

        @Override
        public void change(TriggerContext context, Object[] arguments, Object[] delta, Mode mode) {
            if (!(first(arguments[0]) instanceof SkRequest request)) {
                return;
            }
            if (mode == Mode.RESET) {
                request.content(new JsonObject());
                return;
            }
            if (delta != null) {
                for (Object value : delta) {
                    if (value instanceof String text) {
                        request.content(Json.parseOrText(text));
                    } else if (value instanceof JsonElement json) {
                        request.content(json);
                    }
                }
            }
        }
    }

    /** {@code headers of}: Reqn's {@code Name: value} texts, skJson's JSON; a request's can be set and reset. */
    private static final class Headers implements Expression, Changeable {
        @Override
        public Object get(TriggerContext context, Object[] arguments) {
            return each(arguments[0], owner -> owner instanceof ReqnResponse r ? r.lines().toArray()
                    : SkJsonHttp.headersOfOwner(owner));
        }

        @Override
        public Class<?>[] acceptChange(Mode mode) {
            return switch (mode) {
                case SET -> new Class<?>[] {JsonElement.class, String.class};
                case RESET -> new Class<?>[0];
                default -> null;
            };
        }

        @Override
        public void change(TriggerContext context, Object[] arguments, Object[] delta, Mode mode) {
            if (first(arguments[0]) instanceof SkRequest request) {
                request.headers(mode == Mode.RESET || delta == null ? List.of() : SkJsonHttp.headers(delta));
            }
        }
    }

    private static Object first(Object value) {
        if (value instanceof Object[] array) {
            return array.length == 0 ? null : array[0];
        }
        return value;
    }

    /** Each owner's property, several values flattened, nothing left out as {@code null}. */
    private static Object each(Object owners, Function<Object, Object> property) {
        if (!(owners instanceof Object[] array)) {
            return owners == null ? null : property.apply(owners);
        }
        List<Object> out = new ArrayList<>();
        for (Object owner : array) {
            Object value = property.apply(owner);
            if (value instanceof Object[] several) {
                for (Object each : several) {
                    if (each != null) {
                        out.add(each);
                    }
                }
            } else if (value != null) {
                out.add(value);
            }
        }
        return out.toArray();
    }
}
