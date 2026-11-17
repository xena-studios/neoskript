/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.addons.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Every pattern of Reqn's and skJson's syntax that NeoSkriptWeb takes loads, written as their scripts write it. */
class SyntaxTest {

    private final Scripts scripts = new Scripts(WebConfig.DEFAULTS);

    @AfterEach
    void close() {
        scripts.close();
    }

    private void loads(String... lines) {
        StringBuilder script = new StringBuilder("on load:\n");
        for (String line : lines) {
            script.append('\t').append(line).append('\n');
        }
        assertEquals(List.of(), scripts.problems(script.toString()), script.toString());
    }

    @Test
    void reqn() {
        loads("send a \"GET\" request to \"http://x\"",
                "send request to \"http://x\"",
                "send an http \"POST\" web request to the url \"http://x\" with headers \"a: b\" and body \"c\"",
                "send a \"PUT\" request to \"http://x\" with the body \"x\" and the header \"a: b\"",
                "set {_r} to last response",
                "set {_r} to the last received http web response",
                "set {_r} to lastly received response",
                "set {_x} to body of {_r}",
                "set {_x} to {_r}'s response body",
                "set {_x} to response code of {_r}",
                "set {_x} to {_r}'s status code",
                "set {_x} to status of {_r}",
                "set {_x} to {_r}'s status line",
                "set {_x} to status message of {_r}",
                "set {_x} to {_r}'s reason",
                "set {_x::*} to headers of {_r}",
                "set {_x::*} to header keys of {_r}",
                "set {_x::*} to header values of {_r}",
                "set {_x} to \"Content-Type\" header of {_r}",
                "set {_x} to {_r}'s \"Content-Type\" header value",
                "set {_x} to url encoded \"a b\"",
                "set {_x} to http safe \"a b\"",
                "set {_x} to uri escaped \"a b\"",
                "set {_x} to json encoded \"a\"",
                "set {_x} to json safe \"a\"");
    }

    @Test
    void skJsonRequests() {
        loads("set {_r} to prepare GET request on \"http://x\"",
                "set {_r} to prepare a POST request to url \"http://x\"",
                "set {_r} to create a PATCH request for \"http://x\"",
                "set {_r} to prepare MOCK request on \"x\"",
                "execute {_r}",
                "execute {_r} as non blocking",
                "execute {_r} as non-blocking",
                "run {_r} as not blocking",
                "set {_x} to last response of {_r}",
                "set {_x} to {_r}'s response",
                "set {_x} to {_r}'s last response",
                "set {_x} to status of last response of {_r}",
                "set {_x} to status code of last response of {_r}",
                "set {_x} to body of last response of {_r}",
                "set {_x} to headers of last response of {_r}",
                "set {_r}'s headers to \"Content-Type: application/json\"",
                "set headers of {_r} to parse \"{}\" as json",
                "reset headers of {_r}",
                "set body of {_r} to \"{}\"",
                "set {_r}'s body to parse \"{}\" as json",
                "reset {_r}'s body",
                "set query params of {_r} to \"a:1\"",
                "add \"b:2\" to {_r}'s query params",
                "reset query params of {_r}");
    }

    @Test
    void skJsonJson() {
        loads("set {_j} to json from \"{}\"",
                "set {_j} to json of 12",
                "set {_j} to parse \"{}\" as json",
                "set {_j} to json parsed from \"[]\"",
                "set {_j} to json created from \"[]\"",
                "set {_j} to json from url \"http://x\"",
                "set {_j} to json from the website \"http://x\"",
                "set {_j} to fetch json from url \"http://x\"",
                "map {_j} to {_m::*}",
                "copy {_j} to {_m::*}",
                "async map {_j} to {_m::*}",
                "store json {_j} in {_m::*}",
                "copy json {_j} to {_m::*}",
                "set {_j} to {_m::*}'s formatted json",
                "set {_j} to {_m::*}'s form",
                "set {_j} to formatted json of {_m::*}",
                "set {_s} to {_j} as pretty printed",
                "set {_s} to {_j} as formatted json",
                "set {_s} to {_j} as uncolored pretty printed",
                "set {_s} to {_j} as unformatted pretty printed",
                "set {_s} to {_j} as compact json",
                "set {_s} to {_j} as minified json",
                "set {_n} to json size of {_j}",
                "set {_k::*} to keys of {_j}",
                "set {_k::*} to all keys \"a.b\" of {_j}",
                "set {_k::*} to keys in path \"a\" of {_j}",
                "set {_v::*} to values of {_j}",
                "set {_v::*} to json values \"a\" of {_j}",
                "set {_v} to value \"a\" of {_j}",
                "set {_v} to json value \"a[1]\" of {_j}",
                "set {_v::*} to values at path \"a\" in {_j}",
                "set {_v} to value at path \"a.1\" in {_j}",
                "set {_v::*} to values of path \"a\" in {_j}",
                "set {_v} to value of path \"a\" in {_j}",
                "set {_v} to path \"a\" of {_j}",
                "set {_v} to the path \"a\" in {_j}",
                "set {_v} to {_j}'s path \"a\"",
                "set {_v} to {_j}'s value at path \"a\"",
                "if {_j} is a json object:",
                "\tstop",
                "if json type of {_j} is json array:",
                "\tstop",
                "if {_j} isn't a json primitive:",
                "\tstop",
                "if json {_j} is empty:",
                "\tstop",
                "if {_j} is not empty json:",
                "\tstop",
                "if {_j} has key \"a\":",
                "\tstop",
                "if {_j} has all values 1 and 2:",
                "\tstop",
                "if {_j} doesn't contain keys \"a\" and \"b\":",
                "\tstop");
    }
}
