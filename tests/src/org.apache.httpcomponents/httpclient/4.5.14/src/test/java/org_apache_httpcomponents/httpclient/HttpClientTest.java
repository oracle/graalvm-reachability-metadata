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

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

class HttpClientTest {

    private static int port;

    private static Process process;

    @BeforeAll
    static void beforeAll() throws IOException {
        System.out.println("Finding available port ...");
        port = findAvailablePort();
        System.out.println("Found port: " + port);

        System.out.println("Starting nginx ...");
        process = new ProcessBuilder(
                "docker", "run", "--rm", "-p", port + ":80", "nginx:1-alpine-slim")
                .inheritIO()
                .start();

        // Wait until connection can be established
        waitUntilContainerStarted(60);

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

    private static void waitUntilContainerStarted(int startupTimeoutSeconds) {
        System.out.println("Waiting for nginx container to become available");

        Exception lastConnectionException = null;

        long end = System.currentTimeMillis() + startupTimeoutSeconds * 1000L;
        while (System.currentTimeMillis() < end) {
            try {
                Thread.sleep(100L);
            } catch (InterruptedException e) {
                // continue
            }
            try (Socket socket = new Socket()) {
                socket.setSoTimeout(100);
                socket.connect(new InetSocketAddress("localhost", port), 100);
                if (!check(socket)) {
                    continue;
                }
                return;
            } catch (Exception e) {
                lastConnectionException = e;
            }
        }
        throw new IllegalStateException("nginx container cannot be accessed on localhost:" + port, lastConnectionException);
    }

    private static boolean check(Socket socket) throws IOException {
        try {
            // -1 indicates the socket has been closed immediately
            // Other responses or a timeout are considered as success
            if (socket.getInputStream().read() == -1) {
                return false;
            }
        } catch (SocketTimeoutException ex) {
        }
        return true;
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

        HttpGet httpGet = new HttpGet("http://localhost:%d/".formatted(port));
        try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
            try (CloseableHttpResponse response = httpclient.execute(httpGet, context)) {
                assertThat(response.getStatusLine().getStatusCode()).isEqualTo(200);
            }
        }
    }
}
