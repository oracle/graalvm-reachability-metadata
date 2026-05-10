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
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.http.HttpFields;
import org.junit.jupiter.api.Test;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.reactive.ClientHttpResponse;
import org.springframework.http.client.reactive.JettyClientHttpConnector;

import static org.assertj.core.api.Assertions.assertThat;

public class JettyClientHttpResponseInnerJetty10HttpFieldsHelperTest {
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final String RESPONSE_BODY = "jetty 10 helper response body";

    @Test
    void adaptsJetty10HttpFieldsToSpringHttpHeaders() throws Exception {
        assertThat(HttpFields.class.isInterface()).isTrue();

        ExecutorService serverExecutor = Executors.newSingleThreadExecutor();
        ServerSocket serverSocket = new ServerSocket();
        HttpClient httpClient = new HttpClient();

        try {
            serverSocket.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            Future<?> servedResponse = serverExecutor.submit(() -> respondOnce(serverSocket));

            JettyClientHttpConnector connector = new JettyClientHttpConnector(httpClient);
            ClientHttpResponse response = connector.connect(
                    HttpMethod.GET,
                    new URI("http://127.0.0.1:" + serverSocket.getLocalPort() + "/jetty10-headers"),
                    request -> request.setComplete())
                    .block(REQUEST_TIMEOUT);

            assertThat(response).isNotNull();
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
            assertThat(response.getHeaders().get("X-Jetty10-Header"))
                    .containsExactly("first", "second");
            assertThat(response.getHeaders().getContentType().toString()).isEqualTo("text/plain;charset=UTF-8");
            assertThat(readBody(response)).isEqualTo(RESPONSE_BODY);
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
            throw new IllegalStateException("Failed to serve Jetty 10 helper test response", ex);
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
        output.write(("HTTP/1.1 202 Accepted\r\n"
                + "Content-Type: text/plain;charset=UTF-8\r\n"
                + "X-Jetty10-Header: first\r\n"
                + "X-Jetty10-Header: second\r\n"
                + "Content-Length: " + body.length + "\r\n"
                + "Connection: close\r\n"
                + "\r\n").getBytes(StandardCharsets.ISO_8859_1));
        output.write(body);
        output.flush();
    }

    private static String readBody(ClientHttpResponse response) {
        List<String> chunks = response.getBody()
                .map(JettyClientHttpResponseInnerJetty10HttpFieldsHelperTest::readBuffer)
                .collectList()
                .block(REQUEST_TIMEOUT);
        assertThat(chunks).isNotNull();
        return String.join("", chunks);
    }

    private static String readBuffer(DataBuffer buffer) {
        byte[] bytes = new byte[buffer.readableByteCount()];
        buffer.read(bytes);
        DataBufferUtils.release(buffer);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
