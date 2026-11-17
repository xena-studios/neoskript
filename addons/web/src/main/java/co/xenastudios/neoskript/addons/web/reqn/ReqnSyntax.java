/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.addons.web.reqn;

import co.xenastudios.neoskript.addons.web.WebAddon.Web;
import co.xenastudios.neoskript.addons.web.http.WebException;
import co.xenastudios.neoskript.addons.web.http.WebRequest;
import co.xenastudios.neoskript.api.element.TriggerContext;
import co.xenastudios.neoskript.api.registration.Registrar;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reqn 1.2.3's syntax: {@code send a "POST" request to %text% with headers ... and body ...}, the last response, and
 * {@code url encoded} and {@code json encoded} text. Its response's properties are shared with skJson's
 * ({@link co.xenastudios.neoskript.addons.web.WebSyntax}).
 *
 * <p>Where Reqn differs: the request pauses the trigger as Reqn's did, but has a timeout and a size limit, and a
 * failure is a runtime error after which the trigger carries on (Reqn printed a stack trace, and stopped the trigger
 * for some failures). The last response is the trigger's own, so another trigger's request can't replace it; a
 * trigger that hasn't sent one sees the last one any trigger got, as with Reqn. Only http and https addresses are
 * sent, and any method can be used (Reqn's Java client refused PATCH).
 */
public final class ReqnSyntax {

    /** Reqn's header format: {@code Name: value}. */
    private static final Pattern HEADER = Pattern.compile("(.*?):(.+)");
    /** What Reqn's Java client sent without being asked, so servers see the same request. */
    private static final String USER_AGENT = "Java/" + System.getProperty("java.version");
    private static final String ACCEPT = "text/html, image/gif, image/jpeg, *; q=.2, */*; q=.2";

    /** Each trigger's last response; {@link #NONE} after a failed request. */
    private final Map<TriggerContext, Object> lastByTrigger = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Object NONE = new Object();
    /** The last response any trigger got, for triggers that haven't sent one, as Reqn's one static field. */
    private volatile ReqnResponse last;
    private final Web web;

    private ReqnSyntax(Web web) {
        this.web = web;
    }

    /**
     * Registers Reqn's syntax, other than its response's properties.
     *
     * @param registrar the registrar
     * @param web the addon
     */
    public static void register(Registrar registrar, Web web) {
        ReqnSyntax reqn = new ReqnSyntax(web);
        registrar.pausingEffect("NeoSkriptWebEffReqnRequest", info -> (context, args) -> reqn.send(context, info.mark(), args),
                "send [a[n]] [http] [%-string%] [web] request to [the] [url] %string% [with "
                        + "(0¦[the] header[s] %-strings% [and [the] body %-strings%]"
                        + "|1¦[the] body %-strings% [and [the] header[s] %-strings%])]");
        // Ours gives up a little after the engine, so scripts see the engine's error.
        registrar.timeout("NeoSkriptWebEffReqnRequest", web.config().timeout().plusSeconds(5));
        registrar.expression("NeoSkriptWebExprReqnLastResponse", "httpresponse",
                info -> (context, args) -> reqn.last(context),
                "[the] [last[ly]] [received] [http] [web] response");
        registrar.expression("NeoSkriptWebExprReqnUrlEncode", "string",
                info -> (context, args) -> map(args[0], ReqnSyntax::urlEncode),
                "(http|ur(i|l)) (safe|encoded|escaped) %strings%");
        registrar.expression("NeoSkriptWebExprReqnJsonEncode", "string",
                info -> (context, args) -> map(args[0], ReqnSyntax::jsonEncode),
                "json (safe|encoded|escaped) %strings%");
    }

    private ReqnResponse last(TriggerContext context) {
        Object own = lastByTrigger.get(context);
        if (own == NONE) {
            return null;
        }
        return own instanceof ReqnResponse response ? response : last;
    }

    private void remember(TriggerContext context, ReqnResponse response) {
        lastByTrigger.put(context, response == null ? NONE : response);
        last = response;
    }

    private CompletableFuture<Void> send(TriggerContext context, int mark, Object[] args) {
        String method = args[0] instanceof String m ? m.toUpperCase(Locale.ROOT) : null;
        Object[] headerValues = strings(mark == 1 ? args[5] : args[2]);
        Object[] bodyValues = strings(mark == 1 ? args[4] : args[3]);
        if (!(args[1] instanceof String written)) {
            remember(context, null);
            return CompletableFuture.failedFuture(new WebException("The request has no address"));
        }
        String url = written.replace('§', '&');
        List<Map.Entry<String, String>> headers = new ArrayList<>();
        for (Object header : headerValues) {
            Matcher matcher = HEADER.matcher(String.valueOf(header));
            String name = matcher.matches() ? matcher.group(1).trim() : "";
            if (name.isEmpty()) {
                web.console().warn(String.format("Malformed header during request to %s: %s", url, header));
            } else {
                headers.add(Map.entry(name, matcher.group(2).trim()));
            }
        }
        StringBuilder joined = new StringBuilder();
        for (Object part : bodyValues) {
            if (!joined.isEmpty()) {
                joined.append('\n');
            }
            joined.append(part);
        }
        String body = bodyValues.length == 0 ? "" : joined.toString();
        byte[] sent = null;
        if (method != null && !method.equals("GET")) {
            sent = body.getBytes(StandardCharsets.UTF_8);
            if (!method.equals("PUT") && !has(headers, "content-type")) {
                // What Reqn's Java client sent with a body without a type, for every method but PUT.
                headers.add(Map.entry("Content-Type", "application/x-www-form-urlencoded"));
            }
        } else if (!body.isEmpty()) {
            web.console().warn("Get requests should not have a body");
        }
        if (!has(headers, "user-agent")) {
            headers.add(Map.entry("User-Agent", USER_AGENT));
        }
        if (!has(headers, "accept")) {
            headers.add(Map.entry("Accept", ACCEPT));
        }
        WebRequest request = new WebRequest(method == null ? "GET" : method, url, headers, sent);
        return web.engine().send(request).handle((response, failure) -> {
            if (failure != null) {
                remember(context, null);
                throw WebException.unwrap(failure);
            }
            remember(context, ReqnResponse.of(response));
            return null;
        });
    }

    private static boolean has(List<Map.Entry<String, String>> headers, String name) {
        return headers.stream().anyMatch(h -> h.getKey().equalsIgnoreCase(name));
    }

    private static Object[] strings(Object value) {
        if (value == null) {
            return new Object[0];
        }
        return value instanceof Object[] array ? array : new Object[] {value};
    }

    private static Object map(Object value, java.util.function.UnaryOperator<String> change) {
        if (value instanceof Object[] array) {
            Object[] out = new Object[array.length];
            for (int i = 0; i < array.length; i++) {
                out[i] = change.apply(String.valueOf(array[i]));
            }
            return out;
        }
        return value == null ? null : change.apply(String.valueOf(value));
    }

    private static String urlEncode(String text) {
        return URLEncoder.encode(text, StandardCharsets.UTF_8);
    }

    /** Reqn's JSON escaping, character for character. */
    static String jsonEncode(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '\\', '"' -> out.append('\\').append(c);
                case '\b' -> out.append("\\b");
                case '\t' -> out.append("\\t");
                case '\n' -> out.append("\\n");
                case '\f' -> out.append("\\f");
                case '\r' -> out.append("\\r");
                default -> {
                    if (c < ' ') {
                        String hex = "000" + Integer.toHexString(c);
                        out.append("\\u").append(hex.substring(hex.length() - 4));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }
}
