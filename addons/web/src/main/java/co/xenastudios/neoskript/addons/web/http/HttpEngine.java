/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.addons.web.http;

import co.xenastudios.neoskript.addons.web.WebConfig;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPInputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;
import javax.net.ssl.SSLException;

/**
 * Sends requests for scripts, off the server's threads, with the JDK's HTTP client, and never lets one hang, grow
 * without bound or outlive the addon:
 *
 * <ul>
 *   <li>a whole request, redirects and the answer's last byte included, has {@code timeout-seconds}, and connecting
 *       {@code connect-timeout-seconds};</li>
 *   <li>an answer bigger than {@code max-response-kilobytes}, packed or unpacked, is cut off and fails;</li>
 *   <li>redirects are followed here, up to {@code max-redirects}, never from https to http, so each address is
 *       checked; credentials aren't sent on to another host;</li>
 *   <li>with {@code block-private-addresses}, every address a host name has, at each hop, must be public;</li>
 *   <li>at most {@code max-requests-in-flight} wait at once, and {@code max-requests-per-minute} start in a
 *       minute;</li>
 *   <li>{@link #close} stops every request still running, and its threads.</li>
 * </ul>
 *
 * <p>A failure is a {@link WebException} whose message is the script author's runtime error.
 */
public final class HttpEngine implements AutoCloseable {

    /** Headers the JDK's client sets itself and won't take. */
    private static final Set<String> RESTRICTED = Set.of("connection", "content-length", "expect", "host", "upgrade");
    /** Headers not sent on when a redirect goes to another host. */
    private static final Set<String> CREDENTIALS = Set.of("authorization", "cookie", "proxy-authorization");

    private final WebConfig config;
    private final ThreadPoolExecutor executor;
    /** Looks host names up for block-private-addresses, so a slow DNS server holds these threads, not the client's. */
    private final ThreadPoolExecutor resolver;
    /** Gives up on requests that take too long; its tasks go with the engine. */
    private final ScheduledThreadPoolExecutor timer;
    private final HttpClient client;
    private final AnswerBuffer.Budget budget;
    private final AtomicInteger inFlight = new AtomicInteger();
    private final Set<Exchange> running = ConcurrentHashMap.newKeySet();
    private final Object bucketLock = new Object();
    private double tokens;
    private long refilled = System.nanoTime();
    private volatile boolean closed;

    /**
     * An engine with its own threads.
     *
     * @param config the settings
     */
    public HttpEngine(WebConfig config) {
        this.config = config;
        AtomicInteger threads = new AtomicInteger();
        // The client's work is short (its I/O is non-blocking), and so is checking an address; a few threads do.
        this.executor = new ThreadPoolExecutor(4, 4, 30, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), runnable -> {
            Thread thread = new Thread(runnable, "NeoSkriptWeb-http-" + threads.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
        executor.allowCoreThreadTimeOut(true);
        this.resolver = new ThreadPoolExecutor(4, 4, 30, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), runnable -> {
            Thread thread = new Thread(runnable, "NeoSkriptWeb-dns-" + threads.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
        resolver.allowCoreThreadTimeOut(true);
        this.timer = new ScheduledThreadPoolExecutor(1, runnable -> {
            Thread thread = new Thread(runnable, "NeoSkriptWeb-timeouts");
            thread.setDaemon(true);
            return thread;
        });
        timer.setRemoveOnCancelPolicy(true);
        this.budget = new AnswerBuffer.Budget(config.maxResponseMemoryBytes());
        this.client = HttpClient.newBuilder()
                .executor(executor)
                .connectTimeout(config.connectTimeout())
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        this.tokens = config.maxPerMinute();
    }

    /**
     * How many requests are waiting for an answer.
     *
     * @return how many
     */
    public int inFlight() {
        return inFlight.get();
    }

    /**
     * Sends a request.
     *
     * @param request the request
     * @return its answer, or a {@link WebException}
     */
    public CompletableFuture<WebResponse> send(WebRequest request) {
        if (closed) {
            return CompletableFuture.failedFuture(new WebException("NeoSkriptWeb has stopped, so the request to "
                    + request.url() + " wasn't sent"));
        }
        URI uri;
        try {
            uri = address(request.url(), null);
        } catch (WebException invalid) {
            return CompletableFuture.failedFuture(invalid);
        }
        // The in-flight limit first: a request it refuses doesn't use up the minute's allowance.
        if (inFlight.incrementAndGet() > config.maxInFlight()) {
            inFlight.decrementAndGet();
            return CompletableFuture.failedFuture(new WebException(config.maxInFlight() + " requests are already"
                    + " waiting for an answer (max-requests-in-flight in NeoSkriptWeb's config.yml), so the request to "
                    + request.url() + " wasn't sent"));
        }
        if (!takeToken()) {
            inFlight.decrementAndGet();
            return CompletableFuture.failedFuture(new WebException("More than " + config.maxPerMinute()
                    + " requests were started in the last minute (max-requests-per-minute in NeoSkriptWeb's config.yml),"
                    + " so the request to " + request.url() + " wasn't sent"));
        }
        Exchange exchange = new Exchange(request.url());
        running.add(exchange);
        exchange.result.whenComplete((response, failure) -> {
            inFlight.decrementAndGet();
            running.remove(exchange);
            exchange.finish();
        });
        long seconds = config.timeout().toSeconds();
        try {
            exchange.timeout = timer.schedule(() -> exchange.fail(new WebException("The request to " + request.url()
                    + " took longer than " + seconds + " seconds (timeout-seconds in NeoSkriptWeb's config.yml)")),
                    config.timeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException stopped) {
            exchange.fail(new WebException("NeoSkriptWeb has stopped, so the request to " + request.url()
                    + " wasn't sent"));
        }
        if (closed) {
            exchange.fail(new WebException("NeoSkriptWeb has stopped, so the request to " + request.url()
                    + " wasn't sent"));
        } else {
            hop(exchange, request.method(), uri, request.headers(), request.body(), 0);
        }
        return exchange.result;
    }

    /** A token from the per-minute bucket, which refills smoothly over a minute. */
    private boolean takeToken() {
        synchronized (bucketLock) {
            long now = System.nanoTime();
            double perNano = config.maxPerMinute() / 60e9;
            tokens = Math.min(config.maxPerMinute(), tokens + (now - refilled) * perNano);
            refilled = now;
            if (tokens < 1) {
                return false;
            }
            tokens -= 1;
            return true;
        }
    }

    /** {@code url}, resolved against {@code base} if it's relative, if it's an http or https address. */
    private static URI address(String url, URI base) {
        URI uri;
        try {
            uri = base == null ? new URI(url) : base.resolve(new URI(url));
        } catch (URISyntaxException | IllegalArgumentException invalid) {
            throw new WebException("'" + url + "' isn't a valid web address");
        }
        String scheme = uri.getScheme() == null ? null : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new WebException("'" + url + "' isn't a web address: it has to start with http:// or https://");
        }
        if (uri.getHost() == null || uri.getHost().isEmpty()) {
            throw new WebException("'" + url + "' has no host");
        }
        return uri;
    }

    /** One request and the hops its redirects take. */
    private final class Exchange {
        final String url;
        final CompletableFuture<WebResponse> result = new CompletableFuture<>();
        volatile CompletableFuture<?> current;
        volatile ScheduledFuture<?> timeout;
        /** The buffers its answers are read into, released from the budget when it's over. */
        private final List<AnswerBuffer> buffers = new ArrayList<>();
        private boolean finished;

        Exchange(String url) {
            this.url = url;
        }

        void fail(Throwable failure) {
            if (result.completeExceptionally(failure)) {
                cancelCurrent();
            }
        }

        void cancelCurrent() {
            CompletableFuture<?> hop = current;
            if (hop != null) {
                // The JDK's client stops the exchange and closes its connection.
                hop.cancel(true);
            }
        }

        /** A buffer for an answer, released with the exchange. */
        AnswerBuffer buffer(String what, long expected) {
            AnswerBuffer buffer = new AnswerBuffer(config.maxResponseBytes(), budget, what, expected);
            synchronized (this) {
                if (!finished) {
                    buffers.add(buffer);
                    return buffer;
                }
            }
            buffer.release();
            throw new WebException("The request to " + url + " was stopped");
        }

        /** It's over: what it read is released from the budget, and its timeout cancelled. */
        void finish() {
            cancelCurrent();
            ScheduledFuture<?> pending = timeout;
            if (pending != null) {
                pending.cancel(false);
            }
            List<AnswerBuffer> held;
            synchronized (this) {
                finished = true;
                held = new ArrayList<>(buffers);
                buffers.clear();
            }
            held.forEach(AnswerBuffer::release);
        }
    }

    private void hop(Exchange exchange, String method, URI uri, List<Map.Entry<String, String>> headers, byte[] body,
            int redirects) {
        if (!config.blockPrivateAddresses()) {
            send(exchange, method, uri, headers, body, redirects);
            return;
        }
        try {
            // A host name is looked up on threads of its own, so a slow DNS server holds them, not the client's.
            resolver.execute(() -> {
                try {
                    checkPublic(uri);
                } catch (WebException e) {
                    exchange.fail(e);
                    return;
                } catch (RuntimeException e) {
                    exchange.fail(new WebException("The request to " + uri + " failed: " + e));
                    return;
                }
                send(exchange, method, uri, headers, body, redirects);
            });
        } catch (RejectedExecutionException stopped) {
            exchange.fail(new WebException("NeoSkriptWeb has stopped, so the request to " + uri + " wasn't sent"));
        }
    }

    private void send(Exchange exchange, String method, URI uri, List<Map.Entry<String, String>> headers, byte[] body,
            int redirects) {
        if (exchange.result.isDone()) {
            return;
        }
        try {
            HttpRequest built = build(method, uri, headers, body);
            CompletableFuture<HttpResponse<AnswerBuffer>> sent = client.sendAsync(built, info -> {
                long expected = info.headers().firstValueAsLong("content-length").orElse(-1);
                return new CappedBody(exchange.buffer(uri.toString(), expected));
            });
            exchange.current = sent;
            if (exchange.result.isDone()) {
                sent.cancel(true);
                return;
            }
            sent.whenComplete((response, failure) -> {
                if (failure != null) {
                    exchange.fail(describe(failure, uri));
                    return;
                }
                try {
                    answered(exchange, method, uri, headers, body, redirects, response);
                } catch (WebException e) {
                    exchange.fail(e);
                } catch (RuntimeException e) {
                    exchange.fail(new WebException("The request to " + uri + " failed: " + e));
                }
            });
        } catch (WebException e) {
            exchange.fail(e);
        } catch (IllegalArgumentException e) {
            exchange.fail(new WebException("The request to " + uri + " couldn't be sent: " + e.getMessage()));
        } catch (RuntimeException e) {
            exchange.fail(new WebException("The request to " + uri + " failed: " + e));
        }
    }

    /** Whether two addresses are the same origin: scheme, host and port, as curl decides where credentials go. */
    static boolean sameOrigin(URI a, URI b) {
        return a.getScheme().equalsIgnoreCase(b.getScheme()) && a.getHost().equalsIgnoreCase(b.getHost())
                && port(a) == port(b);
    }

    private static int port(URI uri) {
        return uri.getPort() >= 0 ? uri.getPort() : uri.getScheme().equalsIgnoreCase("https") ? 443 : 80;
    }

    private void answered(Exchange exchange, String method, URI uri, List<Map.Entry<String, String>> headers,
            byte[] body, int redirects, HttpResponse<AnswerBuffer> response) {
        int status = response.statusCode();
        String location = response.headers().firstValue("location").orElse(null);
        if (location != null && (status == 301 || status == 302 || status == 303 || status == 307 || status == 308)) {
            URI next = address(location, uri);
            boolean downgrade = uri.getScheme().equalsIgnoreCase("https") && next.getScheme().equalsIgnoreCase("http");
            if (!downgrade) {
                if (redirects >= config.maxRedirects()) {
                    throw new WebException("The request to " + exchange.url + " was redirected more than "
                            + config.maxRedirects() + " times (max-redirects in NeoSkriptWeb's config.yml)");
                }
                String nextMethod = method;
                byte[] nextBody = body;
                if (status == 303 && !method.equals("HEAD") || (status == 301 || status == 302) && method.equals("POST")) {
                    nextMethod = "GET";
                    nextBody = null;
                }
                List<Map.Entry<String, String>> nextHeaders = headers;
                if (nextBody == null && body != null) {
                    // The body isn't sent on, so neither is what it was.
                    nextHeaders = nextHeaders.stream()
                            .filter(h -> !h.getKey().equalsIgnoreCase("content-type")).toList();
                }
                if (!sameOrigin(next, uri)) {
                    // Another scheme, host or port: credentials aren't sent on (as curl, after CVE-2022-27776).
                    nextHeaders = nextHeaders.stream()
                            .filter(h -> !CREDENTIALS.contains(h.getKey().toLowerCase(Locale.ROOT))).toList();
                }
                hop(exchange, nextMethod, next, nextHeaders, nextBody, redirects + 1);
                return;
            }
        }
        Map<String, List<String>> names = new TreeMap<>();
        response.headers().map().forEach((name, values) -> names.put(name.toLowerCase(Locale.ROOT), List.copyOf(values)));
        AnswerBuffer content = unpack(exchange, response.body(),
                response.headers().firstValue("content-encoding").orElse(null), uri);
        exchange.result.complete(new WebResponse(status, response.uri(), Collections.unmodifiableMap(names),
                content.array(), content.size()));
    }

    private static void checkPublic(URI uri) {
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(uri.getHost());
        } catch (UnknownHostException unknown) {
            throw new WebException("Unknown host: " + uri.getHost());
        }
        for (InetAddress address : addresses) {
            if (Addresses.isPrivate(address)) {
                throw new WebException("The request to " + uri + " wasn't sent: " + uri.getHost() + " is "
                        + address.getHostAddress() + ", a private address, and block-private-addresses is on in"
                        + " NeoSkriptWeb's config.yml");
            }
        }
    }

    private HttpRequest build(String method, URI uri, List<Map.Entry<String, String>> headers, byte[] body) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri).timeout(config.timeout())
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofByteArray(body));
        for (Map.Entry<String, String> header : headers) {
            if (!RESTRICTED.contains(header.getKey().toLowerCase(Locale.ROOT))) {
                builder.header(header.getKey(), header.getValue());
            }
        }
        return builder.build();
    }

    /**
     * Unpacks a gzip or deflate body, as Reqn did, into a buffer of its own that's held to the limit and the budget;
     * the packed one is released at once.
     */
    private AnswerBuffer unpack(Exchange exchange, AnswerBuffer packed, String encoding, URI uri) {
        if (encoding == null || packed.size() == 0) {
            return packed;
        }
        String coding = encoding.strip().toLowerCase(Locale.ROOT);
        try {
            InputStream source = new ByteArrayInputStream(packed.array(), 0, packed.size());
            InputStream in;
            if (coding.equals("gzip") || coding.equals("x-gzip")) {
                in = new GZIPInputStream(source);
            } else if (coding.equals("deflate")) {
                in = new InflaterInputStream(source, new Inflater(true));
            } else {
                return packed;
            }
            AnswerBuffer unpacked = exchange.buffer(uri.toString(), -1);
            try (in) {
                byte[] chunk = new byte[8192];
                int read;
                while ((read = in.read(chunk)) > 0) {
                    unpacked.write(chunk, 0, read);
                }
            }
            packed.release();
            return unpacked;
        } catch (IOException broken) {
            throw new WebException("The answer from " + uri + " says it's " + coding + "-compressed, but it isn't: "
                    + broken.getMessage());
        }
    }

    static WebException tooBig(long max, Object what) {
        return new WebException("The answer from " + what + " is bigger than " + size(max)
                + " (max-response-kilobytes in NeoSkriptWeb's config.yml), so it was cut off");
    }

    private static String size(long bytes) {
        return bytes % (1024 * 1024) == 0 ? bytes / (1024 * 1024) + " MB" : bytes / 1024 + " KB";
    }

    /** A failure from the JDK's client, worded for the script's author. */
    private WebException describe(Throwable failure, URI uri) {
        Throwable cause = failure;
        while ((cause instanceof CompletionException || cause instanceof ExecutionException) && cause.getCause() != null) {
            cause = cause.getCause();
        }
        for (Throwable each = cause; each != null; each = each.getCause()) {
            if (each instanceof WebException web) {
                return web;
            }
            if (each instanceof HttpConnectTimeoutException) {
                return new WebException("Connecting to " + uri.getHost() + " took longer than "
                        + config.connectTimeout().toSeconds() + " seconds (connect-timeout-seconds in NeoSkriptWeb's"
                        + " config.yml)");
            }
            if (each instanceof HttpTimeoutException) {
                return new WebException(uri + " didn't answer within " + config.timeout().toSeconds()
                        + " seconds (timeout-seconds in NeoSkriptWeb's config.yml)");
            }
            if (each instanceof UnknownHostException) {
                return new WebException("Unknown host: " + uri.getHost());
            }
            if (each instanceof ConnectException) {
                return new WebException("Couldn't connect to " + uri.getHost() + portText(uri) + ": the connection was"
                        + " refused");
            }
            if (each instanceof SSLException ssl) {
                return new WebException("The secure connection (HTTPS) to " + uri.getHost() + " failed: "
                        + ssl.getMessage());
            }
            if (each instanceof CancellationException) {
                return new WebException("The request to " + uri + " was stopped");
            }
        }
        String message = cause.getMessage();
        return new WebException("The request to " + uri + " failed: "
                + (message == null || message.isBlank() ? cause.getClass().getSimpleName() : message));
    }

    private static String portText(URI uri) {
        return uri.getPort() < 0 ? "" : ":" + uri.getPort();
    }

    /** Stops every request still running, and the engine's threads, its timeouts included. */
    @Override
    public void close() {
        closed = true;
        for (Exchange exchange : new ArrayList<>(running)) {
            exchange.fail(new WebException("NeoSkriptWeb stopped before " + exchange.url + " answered"));
        }
        timer.shutdownNow();
        client.shutdownNow();
        executor.shutdownNow();
        resolver.shutdownNow();
        try {
            client.awaitTermination(Duration.ofSeconds(2));
            executor.awaitTermination(2, TimeUnit.SECONDS);
            resolver.awaitTermination(2, TimeUnit.SECONDS);
            timer.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * How many bytes the answers in flight hold.
     *
     * @return the bytes
     */
    public long bufferedBytes() {
        return budget.used();
    }

    /** Reads an answer's body into one buffer, which stops it at the limit or the budget. */
    static final class CappedBody implements HttpResponse.BodySubscriber<AnswerBuffer> {
        private final AnswerBuffer buffer;
        private final CompletableFuture<AnswerBuffer> body = new CompletableFuture<>();
        private Flow.Subscription subscription;

        CappedBody(AnswerBuffer buffer) {
            this.buffer = buffer;
        }

        @Override
        public CompletionStage<AnswerBuffer> getBody() {
            return body;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(List<ByteBuffer> buffers) {
            if (body.isDone()) {
                return;
            }
            try {
                for (ByteBuffer each : buffers) {
                    buffer.write(each);
                }
            } catch (WebException tooMuch) {
                subscription.cancel();
                buffer.release();
                body.completeExceptionally(tooMuch);
            }
        }

        @Override
        public void onError(Throwable failure) {
            buffer.release();
            body.completeExceptionally(failure);
        }

        @Override
        public void onComplete() {
            body.complete(buffer);
        }
    }
}
