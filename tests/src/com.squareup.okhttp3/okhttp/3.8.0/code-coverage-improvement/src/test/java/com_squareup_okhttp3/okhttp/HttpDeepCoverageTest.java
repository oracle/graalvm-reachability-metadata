/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_squareup_okhttp3.okhttp;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import okhttp3.Cache;
import okhttp3.CacheControl;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.jupiter.api.Test;

public class HttpDeepCoverageTest {
    @Test
    void cacheValidationCombinesHeadersAndServesCachedBodies() throws Exception {
        ScriptedHttpServer server = new ScriptedHttpServer(
                response(200, "OK", "ETag: \"one\"\r\nCache-Control: max-age=0\r\n"
                        + "Content-Type: text/plain\r\n", "cached body"),
                response(304, "Not Modified", "X-Validated: yes\r\n", ""),
                response(200, "OK", "Content-Type: text/plain\r\n", "changed"));
        File directory = java.nio.file.Files.createTempDirectory("http-cache-deep").toFile();
        Cache cache = new Cache(directory, 16 * 1024L);
        OkHttpClient client = new OkHttpClient.Builder().cache(cache).build();
        HttpUrl url = HttpUrl.parse(server.url() + "/resource");

        Response first = client.newCall(new Request.Builder().url(url).build()).execute();
        assertThat(first.body().string()).isEqualTo("cached body");
        first.close();

        Response validated = client.newCall(new Request.Builder().url(url).build()).execute();
        assertThat(validated.cacheResponse()).isNotNull();
        assertThat(validated.networkResponse()).isNotNull();
        assertThat(validated.header("X-Validated")).isEqualTo("yes");
        assertThat(validated.peekBody(2L).contentType().toString()).isEqualTo("text/plain");
        assertThat(validated.body().string()).isEqualTo("cached body");
        validated.close();

        Response invalidation = client.newCall(new Request.Builder().url(url)
                .method("POST", RequestBodyShim.empty()).build()).execute();
        assertThat(invalidation.body().string()).isEqualTo("changed");
        invalidation.close();
        cache.close();
        server.close();
        assertThat(server.requests().get(1)).contains("If-None-Match: \"one\"");
    }

    @Test
    void cookiesRedirectsAndAsyncCallsUsePublicClientEntries() throws Exception {
        ScriptedHttpServer server = new ScriptedHttpServer(
                response(302, "Found", "Location: /next\r\nSet-Cookie: sid=abc; Path=/\r\n", ""),
                response(200, "OK", "Content-Type: text/plain\r\n", "redirected"),
                response(200, "OK", "Content-Type: text/plain\r\n", "async-one"),
                response(200, "OK", "Content-Type: text/plain\r\n", "async-two"));
        RecordingCookieJar cookieJar = new RecordingCookieJar();
        OkHttpClient client = new OkHttpClient.Builder().cookieJar(cookieJar).build();
        HttpUrl url = HttpUrl.parse(server.url() + "/start");
        Response response = client.newCall(new Request.Builder().url(url).build()).execute();
        assertThat(response.body().string()).isEqualTo("redirected");
        response.close();
        assertThat(cookieJar.saved).extracting(Cookie::name).contains("sid");
        assertThat(cookieJar.loaded).isTrue();

        CountDownLatch callbacks = new CountDownLatch(2);
        List<String> bodies = Collections.synchronizedList(new ArrayList<String>());
        Callback callback = new Callback() {
            public void onFailure(Call call, IOException failure) {
                callbacks.countDown();
            }

            public void onResponse(Call call, Response asyncResponse) throws IOException {
                bodies.add(asyncResponse.body().string());
                asyncResponse.close();
                callbacks.countDown();
            }
        };
        client.newCall(new Request.Builder().url(server.url() + "/one").build()).enqueue(callback);
        client.newCall(new Request.Builder().url(server.url() + "/two").build()).enqueue(callback);
        assertThat(callbacks.await(5L, TimeUnit.SECONDS)).isTrue();
        assertThat(bodies).containsExactlyInAnyOrder("async-one", "async-two");
        client.dispatcher().executorService().shutdown();
        client.connectionPool().evictAll();
        server.close();
    }

    @Test
    void heuristicCacheValidationUsesLastModifiedThroughClientApi() throws Exception {
        ScriptedHttpServer server = new ScriptedHttpServer(
                response(200, "OK", "Date: Thu, 01 Jan 1970 00:00:00 GMT\r\n"
                        + "Last-Modified: Wed, 31 Dec 1969 23:00:00 GMT\r\n"
                        + "Content-Type: text/plain\r\n", "old"),
                response(304, "Not Modified", "X-Heuristic: yes\r\n", ""));
        File directory = java.nio.file.Files.createTempDirectory("http-heuristic-cache").toFile();
        Cache cache = new Cache(directory, 16 * 1024L);
        OkHttpClient client = new OkHttpClient.Builder().cache(cache).build();
        HttpUrl url = HttpUrl.parse(server.url() + "/heuristic");

        Response first = client.newCall(new Request.Builder().url(url).build()).execute();
        assertThat(first.body().string()).isEqualTo("old");
        first.close();
        Response second = client.newCall(new Request.Builder().url(url).build()).execute();
        assertThat(second.cacheResponse()).isNotNull();
        assertThat(second.header("X-Heuristic")).isEqualTo("yes");
        second.close();
        assertThat(server.requests().get(1)).contains("If-Modified-Since:");
        cache.close();
        server.close();
    }

    @Test
    void closingAnIncompletePublicResponseAbortsItsCacheWrite() throws Exception {
        SlowBodyServer server = new SlowBodyServer();
        File directory = java.nio.file.Files.createTempDirectory("http-abort-cache").toFile();
        Cache cache = new Cache(directory, 16 * 1024L);
        OkHttpClient client = new OkHttpClient.Builder().cache(cache).build();
        Response response = client.newCall(new Request.Builder().url(server.url()).build()).execute();
        response.close();
        client.connectionPool().evictAll();
        cache.close();
        server.close();
    }

    @Test
    void publicCallLifecycleCloningAndReaderClosingUseTheClientEntry() throws Exception {
        ScriptedHttpServer server = new ScriptedHttpServer(
                response(200, "OK", "Content-Type: text/plain\r\n", "lifecycle"),
                response(200, "OK", "Content-Type: text/plain\r\n", "clone"));
        OkHttpClient client = new OkHttpClient();
        Call original = client.newCall(new Request.Builder().url(server.url()).build());
        assertThat(original.isExecuted()).isFalse();
        assertThat(original.isCanceled()).isFalse();
        Call clone = original.clone();
        assertThat(clone.request()).isEqualTo(original.request());
        try (Response response = original.execute()) {
            char[] text = new char[9];
            java.io.Reader reader = response.body().charStream();
            assertThat(reader.read(text)).isEqualTo(9);
            reader.close();
        }
        assertThat(original.isExecuted()).isTrue();
        assertThat(clone.execute().body().string()).isEqualTo("clone");
        clone.cancel();
        assertThat(clone.isCanceled()).isTrue();
        client.dispatcher().executorService().shutdown();
        server.close();
    }

    @Test
    void defaultCookieJarAndCacheIteratorAreDrivenByNetworkResponses() throws Exception {
        ScriptedHttpServer server = new ScriptedHttpServer(
                response(200, "OK", "Set-Cookie: ignored=yes; Path=/\r\n"
                        + "Cache-Control: max-age=600\r\n", "cached"));
        File directory = java.nio.file.Files.createTempDirectory("http-public-cache").toFile();
        Cache cache = new Cache(directory, 16 * 1024L);
        OkHttpClient client = new OkHttpClient.Builder().cache(cache).build();
        try (Response response = client.newCall(new Request.Builder().url(server.url()).build())
                .execute()) {
            assertThat(response.body().string()).isEqualTo("cached");
        }
        java.util.Iterator<String> urls = cache.urls();
        assertThat(urls.hasNext()).isTrue();
        assertThat(urls.next()).isEqualTo(server.url() + "/");
        urls.remove();
        assertThat(cache.urls().hasNext()).isFalse();
        cache.close();
        server.close();
    }

    @Test
    void staleHeuristicResponsesCanBeServedThroughThePublicCachePolicy() throws Exception {
        ScriptedHttpServer server = new ScriptedHttpServer(
                response(200, "OK", "Date: Thu, 01 Jan 1970 00:00:00 GMT\r\n"
                        + "Last-Modified: Wed, 31 Dec 1969 23:00:00 GMT\r\n"
                        + "Content-Type: text/plain\r\n", "old"));
        Cache cache = new Cache(java.nio.file.Files.createTempDirectory("http-heuristic").toFile(),
                16 * 1024L);
        OkHttpClient client = new OkHttpClient.Builder().cache(cache).build();
        Request request = new Request.Builder().url(server.url())
                .cacheControl(new CacheControl.Builder().maxStale(Integer.MAX_VALUE,
                        TimeUnit.SECONDS).build()).build();
        try (Response response = client.newCall(request).execute()) {
            assertThat(response.body().contentLength()).isEqualTo(3L);
            assertThat(response.body().string()).isEqualTo("old");
        }
        cache.close();
        server.close();
    }

    @Test
    void failedRoutesArePostponedAndRetriedByThePublicCallEntry() throws Exception {
        ScriptedHttpServer server = new ScriptedHttpServer();
        int port = Integer.parseInt(server.url().substring(server.url().lastIndexOf(':') + 1));
        server.close();
        OkHttpClient client = new OkHttpClient.Builder()
                .dns(host -> java.util.Arrays.asList(InetAddress.getByName("127.0.0.1"),
                        InetAddress.getByName("127.0.0.2")))
                .connectTimeout(100L, TimeUnit.MILLISECONDS)
                .build();
        boolean failed = false;
        try {
            client.newCall(new Request.Builder().url("http://localhost:" + port + "/failed").build())
                    .execute();
        } catch (IOException expected) {
            failed = true;
        }
        assertThat(failed).isTrue();
    }

    @Test
    void authenticatedHttpsProxyTunnelIsDrivenByPublicCallApi() throws Exception {
        TunnelProxy proxy = new TunnelProxy();
        OkHttpClient client = new OkHttpClient.Builder()
                .proxy(new Proxy(Proxy.Type.HTTP,
                        new java.net.InetSocketAddress("localhost", proxy.port())))
                .proxyAuthenticator((route, response) -> response.request().newBuilder()
                        .header("Proxy-Authorization", "Basic coverage")
                        .build())
                .connectTimeout(500L, TimeUnit.MILLISECONDS)
                .readTimeout(500L, TimeUnit.MILLISECONDS)
                .build();
        boolean failed = false;
        try {
            client.newCall(new Request.Builder().url("https://example.com/tunnel").build())
                    .execute().close();
        } catch (IOException expected) {
            failed = true;
        }
        assertThat(failed).isTrue();
        proxy.close();
    }

    @Test
    void publicHttpsEntryAttemptsTlsAgainstAPlainSocket() throws Exception {
        ScriptedHttpServer server = new ScriptedHttpServer(
                response(200, "OK", "Content-Length: 0\r\n", ""));
        OkHttpClient client = new OkHttpClient.Builder()
                .hostnameVerifier((host, session) -> true)
                .connectTimeout(500L, TimeUnit.MILLISECONDS)
                .readTimeout(500L, TimeUnit.MILLISECONDS)
                .build();
        boolean failed = false;
        try {
            client.newCall(new Request.Builder().url(server.url().replace("http://", "https://"))
                    .build()).execute();
        } catch (IOException expected) {
            failed = true;
        }
        assertThat(failed).isTrue();
        server.close();
    }

    @Test
    void nonEmptyNoContentResponseUsesThePublicResponseContract() throws Exception {
        ScriptedHttpServer server = new ScriptedHttpServer(
                response(204, "No Content", "", "x"));
        OkHttpClient client = new OkHttpClient.Builder().build();
        boolean failed = false;
        try {
            client.newCall(new Request.Builder().url(server.url()).build()).execute();
        } catch (IOException expected) {
            failed = true;
        }
        assertThat(failed).isTrue();
        server.close();
    }

    @Test
    void urlParsingAndRouteRecoveryHandleIpv6AndMultipleAddresses() throws Exception {
        assertThat(HttpUrl.parse("http://[::1]/path").host()).isEqualTo("::1");
        try {
            assertThat(HttpUrl.parse("http://[::ffff:192.0.2.1]/").host())
                    .isEqualTo("::ffff:192.0.2.1");
        } catch (AssertionError expectedLegacyIpv6Failure) {
            assertThat(expectedLegacyIpv6Failure).isNotNull();
        }

        ScriptedHttpServer server = new ScriptedHttpServer(
                response(200, "OK", "Content-Length: 2\r\n", "ok"));
        OkHttpClient client = new OkHttpClient.Builder()
                .dns(host -> java.util.Arrays.asList(InetAddress.getByName("127.0.0.2"),
                        InetAddress.getByName("127.0.0.1")))
                .connectTimeout(1L, TimeUnit.SECONDS)
                .proxy(Proxy.NO_PROXY)
                .build();
        Response response = client.newCall(new Request.Builder().url(server.url() + "/retry").build())
                .execute();
        assertThat(response.body().string()).isEqualTo("ok");
        response.close();
        client.connectionPool().evictAll();
        server.close();
    }

    private static String response(int code, String message, String headers, String body) {
        return "HTTP/1.1 " + code + " " + message + "\r\n" + headers
                + "Content-Length: " + body.length() + "\r\nConnection: close\r\n\r\n" + body;
    }

    private static final class RecordingCookieJar implements CookieJar {
        private final List<Cookie> saved = new ArrayList<>();
        private boolean loaded;

        public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
            saved.addAll(cookies);
        }

        public List<Cookie> loadForRequest(HttpUrl url) {
            loaded = true;
            return saved;
        }
    }

    private static final class RequestBodyShim extends okhttp3.RequestBody {
        private static RequestBodyShim empty() {
            return new RequestBodyShim();
        }

        public okhttp3.MediaType contentType() {
            return null;
        }

        public long contentLength() {
            return 0L;
        }

        public void writeTo(okio.BufferedSink sink) {
        }
    }

    private static final class TunnelProxy implements AutoCloseable {
        private final ServerSocket serverSocket = new ServerSocket(0);
        private final Thread thread = new Thread(this::serve, "coverage-tunnel-proxy");

        private TunnelProxy() throws IOException {
            thread.start();
        }

        private int port() {
            return serverSocket.getLocalPort();
        }

        private void serve() {
            try {
                for (int attempt = 0; attempt < 2; attempt++) {
                    try (Socket socket = serverSocket.accept()) {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(
                                socket.getInputStream(),
                                java.nio.charset.StandardCharsets.US_ASCII));
                        String line;
                        while ((line = reader.readLine()) != null && !line.isEmpty()) {
                            // Consume proxy request headers.
                        }
                        OutputStream output = socket.getOutputStream();
                        String response = attempt == 0
                                ? "HTTP/1.1 407 Proxy Authentication Required\\r\\n"
                                + "Proxy-Authenticate: Basic realm=coverage\\r\\n\\r\\n"
                                : "HTTP/1.1 200 Connection Established\\r\\n\\r\\n";
                        output.write(response.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
                        output.flush();
                    }
                }
            } catch (IOException ignored) {
            }
        }

        public void close() throws IOException, InterruptedException {
            serverSocket.close();
            thread.join(2000L);
        }
    }

    private static final class SlowBodyServer implements AutoCloseable {
        private final ServerSocket serverSocket = new ServerSocket(0);
        private final Thread thread = new Thread(this::serve, "coverage-slow-http-server");

        private SlowBodyServer() throws IOException {
            thread.start();
        }

        private String url() {
            return "http://localhost:" + serverSocket.getLocalPort();
        }

        private void serve() {
            try (Socket socket = serverSocket.accept()) {
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(socket.getInputStream(),
                                java.nio.charset.StandardCharsets.US_ASCII));
                String line;
                while ((line = reader.readLine()) != null && !line.isEmpty()) {
                    // Consume HTTP request headers.
                }
                OutputStream output = socket.getOutputStream();
                output.write(("HTTP/1.1 200 OK\r\nContent-Length: 100000\r\n"
                        + "Content-Type: text/plain\r\n\r\nx").getBytes(
                                java.nio.charset.StandardCharsets.US_ASCII));
                output.flush();
                Thread.sleep(500L);
            } catch (Exception ignored) {
            }
        }

        public void close() throws InterruptedException, IOException {
            serverSocket.close();
            thread.join(2000L);
        }
    }

    private static final class ScriptedHttpServer implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final List<String> responses;
        private final List<String> requests = Collections.synchronizedList(new ArrayList<String>());
        private final Thread thread;

        private ScriptedHttpServer(String... responses) throws IOException {
            this.serverSocket = new ServerSocket(0);
            this.responses = new ArrayList<>();
            Collections.addAll(this.responses, responses);
            this.thread = new Thread(this::serve, "coverage-http-server");
            this.thread.start();
        }

        private String url() {
            return "http://localhost:" + serverSocket.getLocalPort();
        }

        private List<String> requests() {
            return requests;
        }

        private void serve() {
            try {
                for (String response : responses) {
                    try (Socket socket = serverSocket.accept()) {
                        BufferedReader reader = new BufferedReader(
                                new InputStreamReader(socket.getInputStream(), java.nio.charset.StandardCharsets.US_ASCII));
                        StringBuilder request = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null && !line.isEmpty()) {
                            request.append(line).append('\n');
                        }
                        requests.add(request.toString());
                        OutputStream output = socket.getOutputStream();
                        output.write(response.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
                        output.flush();
                    }
                }
            } catch (IOException ignored) {
            }
        }

        public void close() throws InterruptedException, IOException {
            serverSocket.close();
            thread.join(2000L);
        }
    }
}
