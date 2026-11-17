---
title: NeoSkriptWeb
description: "HTTP requests, webhooks and JSON for scripts. Scripts written for Reqn, or for skJson's requests and JSON, run unchanged, without blocking the server."
sidebar:
  label: NeoSkriptWeb
---

NeoSkriptWeb sends HTTP requests from scripts, so they can post to a Discord webhook, read an API, or turn JSON into list variables and back. It's an official addon, released with NeoSkript at the same version.

It takes the syntax of the two addons scripts use for this, so their scripts run unchanged:

- **[Reqn](https://github.com/btk5h/reqn) 1.2.3**, the classic one: `send a "POST" request to ...` and `last response`. It hasn't been updated since 2018.
- **[skJson](https://github.com/cooffeeRequired/skJson) 6.0.0**, the one most servers use now (about 50,000 downloads): its requests and the core of its JSON syntax.

A request never blocks the server. The trigger waits for the answer, as it would at a `wait`, and carries on when it arrives.

## Installing

1. Put `NeoSkriptWeb-<version>.jar` in `plugins/`, next to NeoSkript, using the same version as NeoSkript.
2. Remove Reqn's or skJson's jar if it's there. It needs Skript, so it can't load with NeoSkript anyway, and NeoSkriptWeb says so in the console when it starts.
3. Restart the server. `/sk info` lists NeoSkriptWeb.

Its settings are in `plugins/NeoSkriptWeb/config.yml` ([below](#settings)).

## Examples

A Discord webhook, with Reqn's syntax:

```skript nocheck
command /announce <text>:
	permission: server.announce
	trigger:
		set {_body} to "{""content"": ""%json encoded arg-1%""}"
		send a "POST" request to {webhook} with header "Content-Type: application/json" and body {_body}
		if response code of last response is not 204:
			send "Discord said %status of last response%" to sender
```

The same with skJson's, building the JSON from a list variable:

```skript nocheck
command /announce <text>:
	permission: server.announce
	trigger:
		set {_payload::content} to arg-1
		set {_payload::username} to "Server"
		set {_request} to prepare POST request on {webhook}
		set body of {_request} to {_payload::*}'s formatted json
		execute {_request}
		send "Discord said %status code of last response of {_request}%" to sender
```

Reading an API into list variables:

```skript nocheck
command /weather:
	trigger:
		set {_json} to json from url "https://api.example.com/weather?city=London"
		map {_json} to {_weather::*}
		send "It's %{_weather::current::temperature}% degrees in London" to sender
```

## Syntax

The [syntax reference](/syntax/) has every pattern, with a badge saying it's from NeoSkriptWeb. In short:

### Reqn

| Syntax | What it does |
|---|---|
| `send [a] [http] ["METHOD"] [web] request to %text% [with header[s] %texts% [and body %texts%]]` | Sends a request and waits for the answer. The method is GET unless given. Headers are `Name: value`, and several body texts are joined with line breaks. |
| `[the] last [received] [http] [web] response` | The answer to the last request this trigger sent, or none if it failed. |
| `body`, `status code`, `status` (line), `status message`, `headers`, `header keys`, `header values` `of %httpresponse%` | Parts of the answer. The body is text, each line ending in a line break, as Reqn read it. |
| `%text% header of %httpresponse%` | One header's value. |
| `url encoded %texts%`, `json encoded %texts%` | Text made safe for a URL's query, or for a JSON string. |

### skJson: requests

| Syntax | What it does |
|---|---|
| `prepare GET request on %text%` (or `POST`, `PUT`, `PATCH`, `DELETE`, `HEAD`, `MOCK`) | A request to configure, then send. `http://` is added to an address without a scheme. |
| `body of %request%`, `headers of %request%`, `query params of %request%` | Set, add to (query params) or reset before sending. Headers are `Name: value` texts or JSON. |
| `execute %request%` | Sends it and waits for the answer. |
| `execute %request% as non blocking` | Sends it and carries on at once. `on received http response` runs when it's answered. |
| `last response of %request%` | What it got: `status code`, `status` (`OK` below 500, else `FAILED`), `body` (JSON if it parses, else text), `headers` (JSON). |
| `json from url %text%` | The JSON a GET answers with. |

### skJson: JSON

| Syntax | What it does |
|---|---|
| `parse %text% as json`, `json from %objects%` | JSON from text, read leniently as skJson does (`{a: 'b'}` parses). Text that isn't JSON is a JSON string. |
| `map %json% to {_list::*}` | JSON into a list variable: keys and list positions (from 1) are indices, nested ones make lists under them, null is left out. |
| `{_list::*}'s formatted json` | A list variable, nested lists included, as JSON. |
| `value at path "home.x" in %json%`, `values of %json%`, `keys of %json%` | What's in JSON. Paths use `.`, and `items[0]`, with lists counted from 0. |
| `%json% as pretty printed`, `as uncolored pretty printed`, `as compact json` | JSON as text: coloured and indented, indented, or on one line. |
| `json size of %json%`, `%json% is a json object`, `json %json% is empty`, `%json% has key %text%` | Its size and type, and what it holds. |

## Threads and timing

A request, sent with Reqn's `send ... request` or skJson's `execute`, and `json from url`, pause the trigger the way `wait` does. That was true of both originals too. So after a request, the event has passed: `cancel event` can't be used there, as after a `wait`. Local variables and loops are kept.

The request runs on NeoSkriptWeb's own threads. The trigger carries on on its own thread: the main thread on Paper; on Folia, the thread of whatever started it (a player's region, or the global thread for commands from the console). `on received http response` runs on the main thread, or the global thread on Folia.

A trigger waiting on a request while its script is reloaded carries on with its old code, as after a `wait`. When the server stops, requests still running are stopped, and the triggers waiting on them don't carry on.

## Settings

`plugins/NeoSkriptWeb/config.yml`. Changes take effect when the server restarts. A value that isn't valid is replaced by its default, and the console says so.

| Setting | Default | What it does |
|---|---|---|
| `timeout-seconds` | 30 | How long a request may take, redirects and the whole answer included. After it, the trigger carries on with an error. |
| `connect-timeout-seconds` | 10 | How long connecting may take. |
| `max-response-kilobytes` | 8192 | The largest answer kept (8 MB), after unpacking a compressed one. A bigger one is cut off, with an error. |
| `max-response-memory-megabytes` | 128 | The memory all answers being read may take between them. A request whose answer would go past it fails, so many big answers at once can't fill the server's memory. |
| `max-redirects` | 5 | How many redirects a request follows. |
| `max-requests-in-flight` | 64 | How many requests can wait for an answer at once, from all scripts. Past it, a request fails at once. |
| `max-requests-per-minute` | 1200 | How many requests scripts can start in a minute, all together. Past it, a request fails at once. |
| `block-private-addresses` | false | Whether requests to this machine and private networks are refused ([below](#security)). |

## Security

- **Answers can't take the server down.** One bigger than `max-response-kilobytes`, packed or unpacked (a "gzip bomb"), is cut off as it arrives, and its connection closed. Each answer is read into one buffer, sized from its `Content-Length` when it has one, and all answers being read share `max-response-memory-megabytes`. One that never ends, or trickles in, stops at `timeout-seconds`. Neither Reqn nor skJson had a limit on either, so a hostile or broken server could fill the server's memory or hold a trigger forever.
- **Hostile JSON:** JSON nested more than 255 deep isn't parsed; it's left as text. Mapping JSON into variables stops 60 levels down.
- **Floods:** `max-requests-in-flight` and `max-requests-per-minute` stop a loop from hammering an API or the server's network. The console gets at most 10 of the addon's warnings every 10 seconds, then a count of the ones left out.
- **Requests to private addresses (SSRF)** are allowed by default. Scripts are written by the server's staff, and often talk to services on the same machine or network. If scripts send requests to addresses players choose, turn on `block-private-addresses`. Then requests to `localhost`, `10.x`, `172.16-31.x`, `192.168.x`, link-local addresses (`169.254.x`, where cloud servers keep their metadata), carrier-grade NAT and their IPv6 equivalents are refused, as are private IPv4 addresses inside IPv6 ones (`::ffff:`, NAT64's `64:ff9b::`, 6to4's `2002::`). Every address a host name has is checked, at every redirect, on threads of their own, so a slow DNS server doesn't hold up other requests. The check is made before connecting, so a host whose DNS answer changes in between could still get through. If players choose the addresses, keep them on a list of allowed hosts too.
- **Only `http://` and `https://`.** Any other address is a runtime error.
- **Redirects** never go from https to http, and `Authorization`, `Cookie` and `Proxy-Authorization` headers aren't sent on to another scheme, host or port, as curl decides since CVE-2022-27776.
- **TLS:** HTTPS certificates are checked against Java's trusted certificates, and there's no setting to turn that off. A certificate that doesn't check out, or any other TLS failure, is a runtime error saying why.

## Where it differs from the originals

NeoSkript's test harness runs the same scripts on Skript 2.17.0 with each original and on NeoSkript with NeoSkriptWeb, against a local test server, and compares every line (the `neoskript-web` scenario). Where they differ, it's on purpose, because the original was broken; every other line matches.

**Reqn**

- Reqn lost the trigger's local variables: after a request, every local variable set before it was gone. They're kept.
- A failed request (refused, unknown host, a bad address) printed a stack trace in the console. For an address that's none, the trigger stopped. A failure is a runtime error now, and the trigger carries on, with `last response` set to none.
- `last response` was one value shared by every trigger, so another trigger's request could replace it. It's the trigger's own now. A trigger that hasn't sent a request still sees the last one any trigger got.
- Headers were looked up by their names as written, so `"content-type" header` didn't find `Content-Type`. Header names have no case, so it finds it now. Header names are given in the usual way (`Content-Type`), not exactly as the server wrote them, and the status line and message use the usual reason phrase for the status code. The JDK's client doesn't keep either. Nearly every server sends these anyway.
- Any method works: Reqn's Java client refused `PATCH` and other methods it didn't know.

**skJson**

- `as compact json` and `as minified json` printed JSON indented. They print it on one line now.
- Numbers from JSON lost their fractions in arithmetic: `1.5 + 1` was 2. They're whole or decimal numbers, as scripts' numbers are.
- `on received http response` couldn't read the response: `event-response` was none on Skript 2.17. It works.
- `values of` gave nothing at all when the JSON held a null. The null is left out now.
- A header set as text was split at every colon, so one with a colon in its value (a URL, a token) was dropped. skJson's own example, `"{'Content-Type':'application/json'}"`, sent a header named with the quotes. Text is split at its first colon now, and text that's a JSON object is read as one.
- A gzip-compressed answer's body was the compressed bytes. It's unpacked now.
- `json from url` held the server's main thread while it waited for the answer. It pauses the trigger instead.
- `{_x::*}'s formatted json` of an empty list failed with an error. It's none now. A list index past 65,536 makes an object, not a list of nulls that could fill the server's memory.

**Both together**

- Reqn and skJson both have `body of`, `status of`, `status code of` and `headers of`. On Skript, with both installed, whichever loaded first took them, and the other's scripts got nothing: `{_response}'s body` was always Reqn's. Here each works for both.

## Not yet

These parts of skJson aren't in NeoSkriptWeb yet, so lines that use them don't load. They're tracked in [issue #18](https://github.com/xena-studios/neoskript/issues/18).

- JSON files: `json from file`, `new json file`, `json file %text% exists`, and the files in a folder.
- JSON caches and storages, file watchers and `on json file change`.
- NBT, and Minecraft objects (items, locations, inventories) as JSON. Here a value that isn't text, a number, a boolean or JSON becomes its text.
- Changing JSON in place: `set value at path ... to`, `remove path`, `merge`, `sort`, `copy of`, `count of`, and `%json%.key` literals and JSON loops (`json-key`).
- Multipart attachments (`attachment(...)`), and `ip of player`.

Receiving webhooks, with a small HTTP server of its own, is planned too. SkriptWebAPI has syntax for that, which may be the one to take.
