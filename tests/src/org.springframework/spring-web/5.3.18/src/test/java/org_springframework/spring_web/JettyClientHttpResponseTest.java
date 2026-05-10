/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_web;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.eclipse.jetty.client.HttpClient;
import org.junit.jupiter.api.Test;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.client.reactive.ClientHttpResponse;
import org.springframework.http.client.reactive.JettyClientHttpConnector;

import static org.assertj.core.api.Assertions.assertThat;

public class JettyClientHttpResponseTest {
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final String RESPONSE_BODY = "jetty response body";

    @Test
    void receivesStatusHeadersCookiesAndBodyThroughJettyConnector() throws Exception {
        ExecutorService serverExecutor = Executors.newSingleThreadExecutor();
        ServerSocket serverSocket = new ServerSocket();
        HttpClient httpClient = new HttpClient();

        try {
            serverSocket.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            Future<?> servedResponse = serverExecutor.submit(() -> respondOnce(serverSocket));

            JettyClientHttpConnector connector = new JettyClientHttpConnector(httpClient);
            ClientHttpResponse response = connector.connect(
                    HttpMethod.GET,
                    new URI("http://127.0.0.1:" + serverSocket.getLocalPort() + "/response"),
                    request -> request.setComplete())
                    .block(REQUEST_TIMEOUT);

            assertThat(response).isNotNull();
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getRawStatusCode()).isEqualTo(201);
            assertThat(response.getHeaders().getFirst("X-Test")).isEqualTo("jetty-response");

            ResponseCookie cookie = response.getCookies().getFirst("session");
            assertThat(cookie).isNotNull();
            assertThat(cookie.getValue()).isEqualTo("abc");
            assertThat(cookie.getPath()).isEqualTo("/");
            assertThat(cookie.isHttpOnly()).isTrue();
            assertThat(cookie.getSameSite()).isEqualTo("Lax");

            String body = response.getBody()
                    .map(JettyClientHttpResponseTest::readBuffer)
                    .reduce("", String::concat)
                    .block(REQUEST_TIMEOUT);
            assertThat(body).isEqualTo(RESPONSE_BODY);
            servedResponse.get(10, TimeUnit.SECONDS);
        } finally {
            if (httpClient.isStarted()) {
                httpClient.stop();
            }
            serverSocket.close();
            serverExecutor.shutdownNow();
        }
    }

    private static void respondOnce(ServerSocket serverSocket) {
        try (Socket socket = serverSocket.accept()) {
            socket.setSoTimeout(5_000);
            drainRequestHeaders(socket);
            writeResponse(socket.getOutputStream());
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serve test response", ex);
        }
    }

    private static void drainRequestHeaders(Socket socket) throws Exception {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.ISO_8859_1));
        String line;
        int headerCount = 0;
        while ((line = reader.readLine()) != null && !line.isEmpty()) {
            headerCount++;
        }
        assertThat(headerCount).isGreaterThan(0);
    }

    private static void writeResponse(OutputStream output) throws Exception {
        byte[] body = RESPONSE_BODY.getBytes(StandardCharsets.UTF_8);
        output.write(("HTTP/1.1 201 Created\r\n"
                + "Content-Type: text/plain\r\n"
                + "X-Test: jetty-response\r\n"
                + "Set-Cookie: session=abc; Path=/; HttpOnly; SameSite=Lax\r\n"
                + "Content-Length: " + body.length + "\r\n"
                + "Connection: close\r\n"
                + "\r\n").getBytes(StandardCharsets.ISO_8859_1));
        output.write(body);
        output.flush();
    }

    private static String readBuffer(DataBuffer buffer) {
        byte[] bytes = new byte[buffer.readableByteCount()];
        buffer.read(bytes);
        DataBufferUtils.release(buffer);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
