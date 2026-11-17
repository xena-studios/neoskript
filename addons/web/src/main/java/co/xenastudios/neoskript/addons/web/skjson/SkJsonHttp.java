/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.addons.web.skjson;

import co.xenastudios.neoskript.addons.web.WebAddon.Web;
import co.xenastudios.neoskript.addons.web.http.WebException;
import co.xenastudios.neoskript.addons.web.http.WebRequest;
import co.xenastudios.neoskript.addons.web.http.WebResponse;
import co.xenastudios.neoskript.addons.web.json.Json;
import co.xenastudios.neoskript.api.NeoSkript;
import co.xenastudios.neoskript.api.element.Changeable;
import co.xenastudios.neoskript.api.element.Expression;
import co.xenastudios.neoskript.api.element.TriggerContext;
import co.xenastudios.neoskript.api.registration.Patterns;
import co.xenastudios.neoskript.api.registration.Registrar;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;

/**
 * skJson 6.0.0's requests: {@code prepare GET request on %text%}, its body, headers and query parameters,
 * {@code execute {_request}} (which waits for the answer) and {@code execute {_request} as non blocking} (which
 * doesn't, and fires {@code on received http response} when it's answered), and its last response's status, status
 * code, body and headers (those shared with Reqn's are in {@link co.xenastudios.neoskript.addons.web.WebSyntax}).
 * Multipart attachments aren't supported yet.
 */
public final class SkJsonHttp {

    private final Web web;

    private SkJsonHttp(Web web) {
        this.web = web;
    }

    /**
     * Registers skJson's request syntax.
     *
     * @param registrar the registrar
     * @param web the addon
     */
    public static void register(Registrar registrar, Web web) {
        SkJsonHttp http = new SkJsonHttp(web);
        registrar.expression("NeoSkriptWebExprSkJsonRequest", "request",
                info -> (context, args) -> args[0] instanceof RequestMethod method && args[1] instanceof String url
                        ? new SkRequest(url, method) : null,
                "prepare %requestmethod% request on %string%",
                "prepare [a] %requestmethod% request [to [url]] %string%",
                "create [a] %requestmethod% request [for [url]] %string%");
        registrar.pausingEffect("NeoSkriptWebEffSkJsonExecute", info -> {
            boolean waits = !(info.hasTag("non") || info.hasTag("not"));
            return (context, args) -> http.execute(args[0], waits);
        }, "execute %request% [as [(:non|:not)(-| )blocking]]",
                "send %request% [as [(:non|:not)(-| )blocking]]",
                "run %request% [as [(:non|:not)(-| )blocking]]");
        registrar.timeout("NeoSkriptWebEffSkJsonExecute", web.config().timeout().plusSeconds(5));
        registrar.event("NeoSkriptWebEvtSkJsonResponse", List.of(WebResponseEvent.class), null,
                "received [http] response", "http response received");
        registrar.eventValue(WebResponseEvent.class, "response", WebResponseEvent::response);
        registrar.expression("NeoSkriptWebExprSkJsonLastResponse", "response",
                info -> (context, args) -> each(args[0], SkRequest.class, SkRequest::response),
                Patterns.property("[last] response", "requests"));
        registrar.expression("NeoSkriptWebExprSkJsonQueryParams", "jsonelement", info -> new QueryParams(),
                Patterns.property("query param(s|meters)", "requests"));
    }

    private static <T> Object each(Object value, Class<T> type, java.util.function.Function<T, ?> getter) {
        if (value instanceof Object[] array) {
            List<Object> out = new ArrayList<>();
            for (Object each : array) {
                if (type.isInstance(each)) {
                    Object got = getter.apply(type.cast(each));
                    if (got != null) {
                        out.add(got);
                    }
                }
            }
            return out.toArray();
        }
        return type.isInstance(value) ? getter.apply(type.cast(value)) : null;
    }

    /** skJson's {@code query params of %requests%}: {@code "key:value"} pairs, as JSON when read. */
    private static final class QueryParams implements Expression, Changeable {
        @Override
        public Object get(TriggerContext context, Object[] arguments) {
            return each(arguments[0], SkRequest.class, request -> {
                JsonObject json = new JsonObject();
                request.queryParams().forEach(json::addProperty);
                return json;
            });
        }

        @Override
        public Class<?>[] acceptChange(Mode mode) {
            return switch (mode) {
                case SET, ADD -> new Class<?>[] {String.class};
                case RESET -> new Class<?>[0];
                default -> null;
            };
        }

        @Override
        public void change(TriggerContext context, Object[] arguments, Object[] delta, Mode mode) {
            Object first = arguments[0] instanceof Object[] array ? (array.length == 0 ? null : array[0]) : arguments[0];
            if (!(first instanceof SkRequest request)) {
                return;
            }
            Map<String, String> params = new HashMap<>();
            if (delta != null) {
                for (Object value : delta) {
                    if (value instanceof String text) {
                        for (String part : text.split(",")) {
                            String pair = part.trim();
                            int colon = pair.indexOf(':');
                            if (colon > 0) {
                                params.put(pair.substring(0, colon).trim(), pair.substring(colon + 1).trim());
                            }
                        }
                    }
                }
            }
            request.queryParams(params, mode != Mode.ADD);
        }
    }

    /** Sends a request: waits for its answer, or fires the event when it comes. */
    private CompletableFuture<Void> execute(Object value, boolean waits) {
        if (!(value instanceof SkRequest request)) {
            return CompletableFuture.failedFuture(new WebException("There's no request to send"));
        }
        CompletableFuture<SkResponse> answer;
        if (request.method() == RequestMethod.MOCK) {
            String body = waits ? "{\"mock\":true}" : "{\"mock\":true,\"url\":\"" + request.uri() + "\"}";
            JsonObject headers = new JsonObject();
            JsonArray type = new JsonArray();
            type.add("application/json");
            headers.add("Content-Type", type);
            answer = CompletableFuture.completedFuture(new SkResponse(200, Json.parseOrText(body), headers,
                    RequestStatus.OK));
        } else {
            WebRequest built;
            try {
                built = build(request);
            } catch (WebException invalid) {
                request.answered(SkResponse.EMPTY, RequestStatus.FAILED);
                return CompletableFuture.failedFuture(invalid);
            }
            answer = web.engine().send(built).thenApply(SkJsonHttp::response);
        }
        if (waits) {
            return answer.handle((response, failure) -> {
                if (failure != null) {
                    request.answered(SkResponse.EMPTY, RequestStatus.FAILED);
                    throw WebException.unwrap(failure);
                }
                request.answered(response, response.status());
                return null;
            });
        }
        answer.whenComplete((response, failure) -> {
            if (failure != null) {
                web.console().warn(WebException.unwrap(failure).getMessage());
            }
            SkResponse got = failure == null ? response : SkResponse.EMPTY;
            RequestStatus status = failure == null ? response.status() : RequestStatus.FAILED;
            try {
                NeoSkript.get().threads().runGlobal(() -> {
                    request.answered(got, status);
                    Bukkit.getPluginManager().callEvent(new WebResponseEvent(got));
                });
            } catch (RuntimeException stopped) {
                // The addon or the server is stopping: there's no one left to tell.
            }
        });
        return null;
    }

    /** The request skJson would send: its address, query, body and headers. */
    private WebRequest build(SkRequest request) {
        String uri = uri(request);
        List<Map.Entry<String, String>> headers = new ArrayList<>();
        List<Map.Entry<String, String>> set = request.headerList();
        if (set != null) {
            for (Map.Entry<String, String> header : set) {
                if (header.getKey() != null && !header.getKey().isEmpty()) {
                    headers.add(Map.entry(header.getKey(), header.getValue() == null ? "" : header.getValue()));
                }
            }
        }
        JsonElement content = request.content();
        byte[] body = null;
        if (request.method() != RequestMethod.GET && content != null && !content.isJsonNull()) {
            body = Json.body(content).getBytes(StandardCharsets.UTF_8);
            if (headers.stream().noneMatch(h -> h.getKey().equalsIgnoreCase("Content-Type"))) {
                headers.addFirst(Map.entry("Content-Type", "application/json"));
            }
        } else if (request.method() == RequestMethod.GET && content != null) {
            web.console().warn("GET request to " + uri + " ignores body content");
        }
        return new WebRequest(request.method().name(), uri, headers, body);
    }

    /** skJson's address: {@code http://} if it has no scheme, then the query parameters, URL-encoded. */
    static String uri(SkRequest request) {
        StringBuilder uri = new StringBuilder(request.uri());
        if (!uri.toString().matches("^[a-zA-Z]+://.*")) {
            uri.insert(0, "http://");
        }
        Map<String, String> params = request.queryParams();
        if (!params.isEmpty()) {
            uri.append(uri.indexOf("?") >= 0 ? '&' : '?');
            params.forEach((key, value) -> uri.append(URLEncoder.encode(key, StandardCharsets.UTF_8)).append('=')
                    .append(URLEncoder.encode(value, StandardCharsets.UTF_8)).append('&'));
            uri.setLength(uri.length() - 1);
        }
        try {
            return URI.create(uri.toString()).normalize().toString().replace("§", "&");
        } catch (IllegalArgumentException invalid) {
            throw new WebException("'" + request.uri() + "' isn't a valid web address");
        }
    }

    /** What skJson keeps of an answer: the body as JSON where it parses, and the headers as JSON. */
    private static SkResponse response(WebResponse response) {
        String text = new String(response.body(), response.charset());
        JsonElement parsed = Json.parse(text);
        JsonObject headers = new JsonObject();
        response.headers().forEach((name, values) -> {
            JsonArray list = new JsonArray();
            values.forEach(list::add);
            headers.add(name, list);
        });
        RequestStatus status = response.status() >= 200 && response.status() < 500 ? RequestStatus.OK
                : RequestStatus.FAILED;
        return new SkResponse(response.status(), parsed != null ? parsed : text, headers, status);
    }

    /**
     * Headers as skJson's {@code set headers of {_request} to ...} reads them: JSON objects' entries, and texts that
     * are {@code Name: value}. skJson split a text at every colon, so a value with one in it (a URL, a token) was
     * dropped, and its own example, {@code "{'Content-Type':'application/json'}"}, sent a header named with quotes;
     * here a text that's a JSON object is read as one, and a text is split at its first colon.
     *
     * @param delta the values given
     * @return the headers
     */
    public static List<Map.Entry<String, String>> headers(Object[] delta) {
        List<Map.Entry<String, String>> headers = new ArrayList<>();
        for (Object value : delta) {
            if (value instanceof JsonObject object) {
                addAll(headers, object);
            } else if (value instanceof String text) {
                JsonElement json = text.strip().startsWith("{") ? Json.parse(text) : null;
                if (json instanceof JsonObject object) {
                    addAll(headers, object);
                    continue;
                }
                int colon = text.indexOf(':');
                if (colon > 0) {
                    String name = strip(text.substring(0, colon));
                    String content = strip(text.substring(colon + 1));
                    if (!name.isEmpty() && !content.isEmpty()) {
                        headers.add(Map.entry(name, content));
                    }
                }
            }
        }
        return headers;
    }

    private static void addAll(List<Map.Entry<String, String>> headers, JsonObject object) {
        Map<String, String> seen = new LinkedHashMap<>();
        object.entrySet().forEach(entry -> seen.put(entry.getKey(), entry.getValue() instanceof JsonPrimitive p
                ? p.getAsString() : entry.getValue().toString()));
        seen.forEach((name, value) -> headers.add(Map.entry(name, value)));
    }

    private static String strip(String part) {
        return part.replaceAll("[{\\[\\]}]", "").strip().replaceAll("^['\"]|['\"]$", "").strip();
    }

    /**
     * A request's headers as skJson gives them: a JSON object of each name and value.
     *
     * @param request the request
     * @return the JSON, or {@code null} if none were set
     */
    static JsonObject headersOf(SkRequest request) {
        List<Map.Entry<String, String>> list = request.headerList();
        if (list == null) {
            return null;
        }
        JsonObject json = new JsonObject();
        list.forEach(h -> json.addProperty(h.getKey(), h.getValue()));
        return json;
    }

    /**
     * What {@code body of} a request or a response is, for the shared properties.
     *
     * @param owner a request or a response
     * @return its body, or {@code null}
     */
    public static Object bodyOf(Object owner) {
        if (owner instanceof SkRequest request) {
            return request.content();
        }
        if (owner instanceof SkResponse response) {
            return response.content();
        }
        return null;
    }

    /**
     * What {@code headers of} a request or a response is, for the shared properties.
     *
     * @param owner a request or a response
     * @return its headers as JSON, or {@code null}
     */
    public static Object headersOfOwner(Object owner) {
        if (owner instanceof SkRequest request) {
            return headersOf(request);
        }
        if (owner instanceof SkResponse response) {
            return response.headers();
        }
        return null;
    }
}
