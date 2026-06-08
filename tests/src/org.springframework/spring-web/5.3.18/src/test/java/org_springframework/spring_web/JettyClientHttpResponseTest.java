/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_web;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
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
import org.springframework.http.client.reactive.ClientHttpRequest;
import org.springframework.http.client.reactive.ClientHttpResponse;
import org.springframework.http.client.reactive.JettyClientHttpConnector;

import static org.assertj.core.api.Assertions.assertThat;

public class JettyClientHttpResponseTest {

    @Test
    void jettyConnectorReceivesResponseStatusHeadersCookiesAndBody() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        HttpClient httpClient = new HttpClient();

        try (ServerSocket serverSocket = new ServerSocket(
                0, 1, InetAddress.getByName("127.0.0.1"))) {
            Future<?> serverExchange = executor.submit(() -> serveSingleHttpResponse(serverSocket));
            JettyClientHttpConnector connector = new JettyClientHttpConnector(httpClient);
            URI uri = new URI("http://127.0.0.1:" + serverSocket.getLocalPort() + "/greeting");

            ClientHttpResponse response = connector.connect(
                    HttpMethod.GET, uri, ClientHttpRequest::setComplete).block(Duration.ofSeconds(10));

            assertThat(response).isNotNull();
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getHeaders().getFirst("Content-Type"))
                    .isEqualTo("text/plain;charset=UTF-8");
            ResponseCookie cookie = response.getCookies().getFirst("flavor");
            assertThat(cookie).isNotNull();
            assertThat(cookie.getValue()).isEqualTo("vanilla");
            assertThat(cookie.getSameSite()).isEqualTo("Lax");
            assertThat(readBody(response)).isEqualTo("hello jetty");

            serverExchange.get(10, TimeUnit.SECONDS);
        } finally {
            if (httpClient.isStarted()) {
                httpClient.stop();
            }
            executor.shutdownNow();
        }
    }

    private static Void serveSingleHttpResponse(ServerSocket serverSocket) {
        try (Socket socket = serverSocket.accept()) {
            socket.setSoTimeout(10_000);
            readHttpRequest(socket.getInputStream());
            byte[] body = "hello jetty".getBytes(StandardCharsets.UTF_8);
            byte[] headers = ("HTTP/1.1 201 Created\r\n"
                    + "Content-Type: text/plain;charset=UTF-8\r\n"
                    + "Set-Cookie: flavor=vanilla; Path=/; HttpOnly; SameSite=Lax\r\n"
                    + "Content-Length: " + body.length + "\r\n"
                    + "Connection: close\r\n"
                    + "\r\n").getBytes(StandardCharsets.US_ASCII);
            OutputStream output = socket.getOutputStream();
            output.write(headers);
            output.write(body);
            output.flush();
            return null;
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to serve test HTTP response", ex);
        }
    }

    private static void readHttpRequest(InputStream input) throws IOException {
        int state = 0;
        while (state < 4) {
            int value = input.read();
            if (value == -1) {
                throw new EOFException("HTTP request ended before headers were complete");
            }
            if ((state == 0 || state == 2) && value == '\r') {
                state++;
            } else if ((state == 1 || state == 3) && value == '\n') {
                state++;
            } else {
                state = (value == '\r' ? 1 : 0);
            }
        }
    }

    private static String readBody(ClientHttpResponse response) {
        return response.getBody()
                .map(JettyClientHttpResponseTest::readAndRelease)
                .collectList()
                .map(parts -> String.join("", parts))
                .block(Duration.ofSeconds(10));
    }

    private static String readAndRelease(DataBuffer dataBuffer) {
        try {
            byte[] bytes = new byte[dataBuffer.readableByteCount()];
            dataBuffer.read(bytes);
            return new String(bytes, StandardCharsets.UTF_8);
        } finally {
            DataBufferUtils.release(dataBuffer);
        }
    }
}
