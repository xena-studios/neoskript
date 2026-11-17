/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.addons.web.skjson;

import com.google.gson.JsonElement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * skJson's {@code request}: prepared by a script, then sent. Scripts can change its body, headers and query
 * parameters until then; its response arrives when it's answered, on another thread for one sent without waiting.
 */
public final class SkRequest {

    private final String uri;
    private final RequestMethod method;
    private volatile JsonElement content;
    /** Each header as a name and a value, or {@code null} if none were set. */
    private volatile List<Map.Entry<String, String>> headers;
    private final Map<String, String> queryParams = new HashMap<>();
    private volatile RequestStatus status = RequestStatus.UNKNOWN;
    private volatile SkResponse response = SkResponse.EMPTY;

    /**
     * A request.
     *
     * @param uri where it goes, as written
     * @param method its method
     */
    public SkRequest(String uri, RequestMethod method) {
        this.uri = uri;
        this.method = method;
    }

    public String uri() {
        return uri;
    }

    public RequestMethod method() {
        return method;
    }

    public JsonElement content() {
        return content;
    }

    public void content(JsonElement content) {
        this.content = content;
    }

    public List<Map.Entry<String, String>> headers() {
        return headers;
    }

    public void headers(List<Map.Entry<String, String>> headers) {
        this.headers = headers == null ? null : List.copyOf(headers);
    }

    /**
     * The query parameters, in the order skJson's {@code HashMap} gives them.
     *
     * @return a copy
     */
    public Map<String, String> queryParams() {
        synchronized (queryParams) {
            return new HashMap<>(queryParams);
        }
    }

    public void queryParams(Map<String, String> params, boolean replace) {
        synchronized (queryParams) {
            if (replace) {
                queryParams.clear();
            }
            queryParams.putAll(params);
        }
    }

    public RequestStatus status() {
        return status;
    }

    public SkResponse response() {
        return response;
    }

    /**
     * Sets what it got.
     *
     * @param response the response
     * @param status how it went
     */
    public void answered(SkResponse response, RequestStatus status) {
        this.response = response;
        this.status = status;
    }

    /**
     * The headers as skJson lists them, names and values.
     *
     * @return a copy, or {@code null}
     */
    public List<Map.Entry<String, String>> headerList() {
        List<Map.Entry<String, String>> current = headers;
        return current == null ? null : new ArrayList<>(current);
    }

    @Override
    public String toString() {
        return "prepared request for uri: " + uri + ", method: " + method + " and status: " + status;
    }
}
