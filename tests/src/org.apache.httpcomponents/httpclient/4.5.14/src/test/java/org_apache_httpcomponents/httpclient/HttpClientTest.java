/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_httpcomponents.httpclient;

import org.apache.http.HttpHost;
import org.apache.http.client.AuthCache;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URL;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;

class HttpClientTest {

    private static int port;

    private static String uri;

    private static Process process;

    @BeforeAll
    static void beforeAll() throws IOException {
        System.out.println("Finding available port ...");
        port = findAvailablePort();
        System.out.println("Found port: " + port);

        uri = "http://localhost:%d/".formatted(port);

        System.out.println("Starting nginx ...");
        process = new ProcessBuilder(
                "docker", "run", "--rm", "-p", port + ":80", "nginx:1-alpine-slim")
                .inheritIO()
                .start();

        // Wait until connection can be established
        waitUntil(() -> openConnection(uri), 60, 1);

        System.out.println("nginx started");
    }

    private static int findAvailablePort() throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            assertThat(serverSocket).isNotNull();
            int localPort = serverSocket.getLocalPort();
            assertThat(localPort).isGreaterThan(0);
            return localPort;
        }
    }

    private static boolean openConnection(String uri) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(uri).openConnection();
        connection.setReadTimeout(1000);
        connection.setRequestMethod("GET");
        connection.connect();
        return HttpURLConnection.HTTP_OK == connection.getResponseCode();
    }

    private static void waitUntil(Callable<Boolean> conditionEvaluator, int timeoutSeconds, int sleepTimeSeconds) {
        Exception lastException = null;

        long end = System.currentTimeMillis() + timeoutSeconds * 1000L;
        while (System.currentTimeMillis() < end) {
            try {
                Thread.sleep(sleepTimeSeconds * 1000L);
            } catch (InterruptedException e) {
                // continue
            }
            try {
                if (conditionEvaluator.call()) {
                    return;
                }
            } catch (Exception e) {
                lastException = e;
            }
        }
        String errorMessage = "Condition was not fulfilled within " + timeoutSeconds + " seconds";
        throw lastException == null ? new IllegalStateException(errorMessage) : new IllegalStateException(errorMessage, lastException);
    }

    @AfterAll
    static void tearDown() {
        if (process != null && process.isAlive()) {
            System.out.println("Shutting down nginx");
            process.destroy();
        }
    }

    @Test
    void testBasicSchemeDeserialization() throws Exception {
        AuthCache authCache = new BasicAuthCache();
        authCache.put(new HttpHost("localhost", port, "http"), new BasicScheme());

        HttpClientContext context = HttpClientContext.create();
        context.setAuthCache(authCache);

        HttpGet httpGet = new HttpGet(uri);
        try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
            try (CloseableHttpResponse response = httpclient.execute(httpGet, context)) {
                assertThat(response.getStatusLine().getStatusCode()).isEqualTo(200);
            }
        }
    }
}
