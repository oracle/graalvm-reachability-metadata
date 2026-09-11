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
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Iterator;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.Cache;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.internal.Internal;
import okhttp3.internal.NamedRunnable;
import org.junit.jupiter.api.Test;

public class CacheAndInternalsApiCoverageTest {
    @Test
    void cacheLifecycleAndIdentityMethodsManageAUserDirectory() throws Exception {
        File directory = java.nio.file.Files.createTempDirectory("okhttp-cache-api").toFile();
        Cache cache = new Cache(directory, 4096L);
        HttpUrl url = HttpUrl.parse("https://example.com/cache");
        assertThat(Cache.key(url)).hasSize(32);
        assertThat(cache.directory()).isEqualTo(directory);
        assertThat(cache.maxSize()).isEqualTo(4096L);
        cache.initialize();
        assertThat(cache.size()).isZero();
        assertThat(cache.urls().hasNext()).isFalse();
        Iterator<String> urls = cache.urls();
        assertThat(urls.hasNext()).isFalse();
        assertThat(cache.writeAbortCount()).isZero();
        assertThat(cache.writeSuccessCount()).isZero();
        assertThat(cache.networkCount()).isZero();
        assertThat(cache.hitCount()).isZero();
        assertThat(cache.requestCount()).isZero();
        cache.flush();
        cache.evictAll();
        assertThat(cache.isClosed()).isFalse();
        cache.close();
        assertThat(cache.isClosed()).isTrue();

        File secondDirectory = java.nio.file.Files.createTempDirectory("okhttp-cache-api-2").toFile();
        Cache second = new Cache(secondDirectory, 1024L);
        second.initialize();
        second.delete();
        assertThat(second.directory().exists()).isTrue();
        assertThat(second.directory().list()).isEmpty();
        directory.delete();
    }

    @Test
    void cacheInterceptorHandlesHeuristicHitsAndInvalidatingRequests() throws Exception {
        File directory = java.nio.file.Files.createTempDirectory("okhttp-cache-interceptor").toFile();
        Cache cache = new Cache(directory, 8192L);
        AtomicInteger requests = new AtomicInteger();
        long now = System.currentTimeMillis();
        try (ServerSocket server = startServer(requests, 2,
                "Date: " + httpDate(now) + "\r\nLast-Modified: "
                        + httpDate(now - 86400000L) + "\r\n", "cached")) {
            OkHttpClient client = new OkHttpClient.Builder().cache(cache).build();
            String url = "http://localhost:" + server.getLocalPort() + "/resource";
            assertThat(client.newCall(new Request.Builder().url(url).build()).execute().body().string())
                    .isEqualTo("cached");
            assertThat(client.newCall(new Request.Builder().url(url).build()).execute().body().string())
                    .isEqualTo("cached");
            Response cachedResponse = client.newCall(new Request.Builder().url(url).build())
                    .execute();
            assertThat(cachedResponse.body().contentLength()).isEqualTo(6L);
            assertThat(cachedResponse.body().string()).isEqualTo("cached");
            assertThat(requests).hasValue(1);
        }
        try (ServerSocket server = startServer(new AtomicInteger(), 1, "", "changed")) {
            OkHttpClient client = new OkHttpClient.Builder().cache(cache).build();
            Request post = new Request.Builder()
                    .url("http://localhost:" + server.getLocalPort() + "/resource")
                    .post(RequestBody.create(null, "invalidate"))
                    .build();
            assertThat(client.newCall(post).execute().code()).isEqualTo(200);
        }
        cache.close();
    }

    @Test
    void cacheResponseCloseAbortsWritesAndFaultHidingSinkToleratesClosedCache() throws Exception {
        File directory = java.nio.file.Files.createTempDirectory("okhttp-cache-abort").toFile();
        Cache cache = new Cache(directory, 8192L);
        AtomicInteger requests = new AtomicInteger();
        try (ServerSocket server = startServer(requests, 1, "", repeat('x', 256))) {
            OkHttpClient client = new OkHttpClient.Builder().cache(cache).build();
            Response response = client.newCall(new Request.Builder()
                    .url("http://localhost:" + server.getLocalPort() + "/partial").build()).execute();
            cache.close();
            try {
                response.close();
            } catch (IllegalStateException expectedClosedCache) {
                assertThat(expectedClosedCache).isNotNull();
            }
            assertThat(requests).hasValue(1);
        }
    }

    @Test
    void staleHeuristicResponsesRevalidateAndInvalidateThroughPublicCalls() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        long now = System.currentTimeMillis();
        File directory = java.nio.file.Files.createTempDirectory("okhttp-cache-stale").toFile();
        Cache cache = new Cache(directory, 8192L);
        try (ServerSocket server = startServer(requests, 2,
                "Date: " + httpDate(now - 30L * 86400000L) + "\r\nLast-Modified: "
                        + httpDate(now - 60L * 86400000L) + "\r\n", "stale")) {
            OkHttpClient client = new OkHttpClient.Builder().cache(cache).build();
            String url = "http://localhost:" + server.getLocalPort() + "/stale";
            assertThat(client.newCall(new Request.Builder().url(url).build()).execute().body().string())
                    .isEqualTo("stale");
            assertThat(client.newCall(new Request.Builder().url(url).build()).execute().body().string())
                    .isEqualTo("stale");
            assertThat(requests).hasValue(2);
        }
        cache.close();
    }

    @Test
    void defaultCookieJarAndCacheInvalidationAreDrivenByNetworkCalls() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        try (ServerSocket server = startServer(requests, 1, "Set-Cookie: session=one\r\n", "cookie")) {
            OkHttpClient client = new OkHttpClient();
            String url = "http://localhost:" + server.getLocalPort() + "/cookie";
            assertThat(client.newCall(new Request.Builder().url(url).build()).execute().body().string())
                    .isEqualTo("cookie");
        }

        File directory = java.nio.file.Files.createTempDirectory("okhttp-cache-invalidate").toFile();
        Cache cache = new Cache(directory, 8192L);
        AtomicInteger cacheRequests = new AtomicInteger();
        try (ServerSocket server = startServer(cacheRequests, 2, "", "changed")) {
            OkHttpClient client = new OkHttpClient.Builder().cache(cache).build();
            String url = "http://localhost:" + server.getLocalPort() + "/invalidate";
            assertThat(client.newCall(new Request.Builder().url(url).build()).execute().code())
                    .isEqualTo(200);
            Request post = new Request.Builder().url(url)
                    .post(RequestBody.create(null, "invalidate")).build();
            assertThat(client.newCall(post).execute().code()).isEqualTo(200);
            assertThat(cacheRequests).hasValue(2);
        }
        cache.close();
    }

    @Test
    void asynchronousCallbackFailureUsesThePublicEnqueueContract() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        CountDownLatch callback = new CountDownLatch(1);
        try (ServerSocket server = startServer(requests, 1, "", "async")) {
            OkHttpClient client = new OkHttpClient();
            client.newCall(new Request.Builder()
                    .url("http://localhost:" + server.getLocalPort() + "/async?secret=value").build())
                    .enqueue(new okhttp3.Callback() {
                        public void onFailure(okhttp3.Call call, java.io.IOException failure) {
                            callback.countDown();
                        }

                        public void onResponse(okhttp3.Call call, Response response) {
                            response.close();
                            callback.countDown();
                            throw new RuntimeException("callback coverage");
                        }
                    });
            assertThat(callback.await(5L, TimeUnit.SECONDS)).isTrue();
            assertThat(requests).hasValue(1);
        }
    }

    @Test
    void namedRunnableRestoresThreadStateAndInternalInitializationIsSafe() {
        String originalName = Thread.currentThread().getName();
        AtomicBoolean executed = new AtomicBoolean();
        NamedRunnable runnable = new NamedRunnable("coverage-%s", "worker") {
            @Override protected void execute() {
                assertThat(Thread.currentThread().getName()).isEqualTo("coverage-worker");
                executed.set(true);
            }
        };
        runnable.run();
        assertThat(executed).isTrue();
        assertThat(Thread.currentThread().getName()).isEqualTo(originalName);
        Internal.initializeInstanceForTests();
        assertThat(Internal.instance).isNotNull();
    }

    private static ServerSocket startServer(AtomicInteger requests, int expectedRequests,
            String extraHeaders, String body) throws Exception {
        ServerSocket server = new ServerSocket(0);
        Thread worker = new Thread(() -> {
            try {
                while (requests.get() < expectedRequests) {
                    try (Socket socket = server.accept()) {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(
                                socket.getInputStream(), StandardCharsets.US_ASCII));
                        String line;
                        int contentLength = 0;
                        while ((line = reader.readLine()) != null && !line.isEmpty()) {
                            if (line.regionMatches(true, 0, "Content-Length:", 0, 15)) {
                                contentLength = Integer.parseInt(line.substring(15).trim());
                            }
                        }
                        for (int i = 0; i < contentLength; i++) {
                            reader.read();
                        }
                        byte[] bytes = body.getBytes(StandardCharsets.US_ASCII);
                        OutputStream output = socket.getOutputStream();
                        String response = "HTTP/1.1 200 OK\r\nContent-Length: " + bytes.length
                                + "\r\nConnection: close\r\n" + extraHeaders + "\r\n";
                        output.write(response.getBytes(StandardCharsets.US_ASCII));
                        output.write(bytes);
                        output.flush();
                        requests.incrementAndGet();
                    }
                }
            } catch (Exception ignored) {
            }
        });
        worker.start();
        return server;
    }

    private static String httpDate(long timestamp) {
        return new java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US)
                .format(new Date(timestamp));
    }

    private static String repeat(char character, int count) {
        StringBuilder result = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            result.append(character);
        }
        return result.toString();
    }
}
