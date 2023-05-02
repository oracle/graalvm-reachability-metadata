/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jetty.jetty_client;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

class JettyClientTest {

    private static final int HOST_PORT = 12345;

    private static Process process;

    @BeforeAll
    static void beforeAll() throws IOException {
        System.out.println("Starting nginx ...");
        process = new ProcessBuilder(
                "docker", "run", "--rm", "-p", HOST_PORT + ":80",
                "nginx:1-alpine-slim")
                .redirectOutput(new File("nginx-stdout.txt"))
                .redirectError(new File("nginx-stderr.txt"))
                .start();

        // Wait until connection can be established
        waitUntilContainerStarted(60);

        System.out.println("nginx started");
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
                socket.connect(new InetSocketAddress("localhost", HOST_PORT), 100);
                if (!check(socket)) {
                    continue;
                }
                return;
            } catch (Exception e) {
                lastConnectionException = e;
            }
        }
        throw new IllegalStateException("nginx container cannot be accessed on localhost:" + HOST_PORT, lastConnectionException);
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
    void test() throws Exception {
        HttpClient client = new HttpClient();
        client.start();
        try {
            ContentResponse response = client.GET("http://localhost:%d/".formatted(HOST_PORT));
            assertThat(response.getStatus()).isEqualTo(200);
        } finally {
            client.stop();
        }
    }
}
