/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jetty.jetty_client;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.ContentResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JettyClientTest {

    private static final String DOCKER_HOST = "127.0.0.1";

    private static int hostPort;

    private static Process process;

    @BeforeAll
    static void beforeAll() throws IOException {
        hostPort = findAvailablePort();
        System.out.printf("Starting nginx on port %d ...%n", hostPort);
        process = new ProcessBuilder(
                "docker", "run", "--rm", "-p", DOCKER_HOST + ":" + hostPort + ":80",
                "nginx:1-alpine-slim")
                .redirectOutput(new File("nginx-stdout.txt"))
                .redirectError(new File("nginx-stderr.txt"))
                .start();

        // Wait until connection can be established
        waitUntilContainerStarted(60);

        System.out.printf("nginx started on port %d%n", hostPort);
    }

    private static int findAvailablePort() throws IOException {
        try (ServerSocket serverSocket = new ServerSocket()) {
            serverSocket.bind(null);
            return serverSocket.getLocalPort();
        }
    }

    private static void waitUntilContainerStarted(int startupTimeoutSeconds) {
        System.out.println("Waiting for nginx container to become available");

        Exception lastConnectionException = null;

        long end = System.currentTimeMillis() + startupTimeoutSeconds * 1000L;
        while (System.currentTimeMillis() < end) {
            if (!process.isAlive()) {
                throw new IllegalStateException("Process has already exited with code %d".formatted(process.exitValue()));
            }
            try {
                Thread.sleep(100L);
            } catch (InterruptedException e) {
                // continue
            }
            try (Socket socket = new Socket()) {
                socket.setSoTimeout(100);
                socket.connect(new InetSocketAddress(DOCKER_HOST, hostPort), 100);
                if (!check(socket)) {
                    continue;
                }
                return;
            } catch (Exception e) {
                lastConnectionException = e;
            }
        }
        throw new IllegalStateException("nginx container cannot be accessed on %s:%d".formatted(DOCKER_HOST, hostPort), lastConnectionException);
    }

    private static boolean check(Socket socket) throws IOException {
        try {
            // -1 indicates the socket has been closed immediately
            // Other responses or a timeout are considered as success
            if (socket.getInputStream().read() == -1) {
                return false;
            }
        } catch (SocketTimeoutException ex) {
            // Ignore
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
            ContentResponse response = client.GET("http://%s:%d/".formatted(DOCKER_HOST, hostPort));
            assertThat(response.getStatus()).isEqualTo(200);
        } finally {
            client.stop();
        }
    }
}
