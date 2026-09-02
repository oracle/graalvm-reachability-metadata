/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_web;

import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.eclipse.jetty.client.HttpClient;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.client.reactive.ClientHttpRequest;
import org.springframework.http.client.reactive.ClientHttpResponse;
import org.springframework.http.client.reactive.JettyClientHttpConnector;

import static org.assertj.core.api.Assertions.assertThat;

public class Jetty10HttpFieldsHelperTest {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);

    @Test
    void jettyConnectorExchangesRequestAndResponseHeaders() throws Exception {
        MockWebServer server = new MockWebServer();
        HttpClient httpClient = new HttpClient();
        AtomicReference<ClientHttpRequest> sentRequest = new AtomicReference<>();

        try {
            server.start(InetAddress.getByName("127.0.0.1"), 0);
            server.enqueue(new MockResponse()
                    .setResponseCode(HttpStatus.CREATED.value())
                    .setHeader("Content-Type", "text/plain;charset=UTF-8")
                    .setHeader(
                            "Set-Cookie", "flavor=vanilla; Path=/; HttpOnly; SameSite=Lax")
                    .setBody("hello jetty"));

            httpClient.setConnectTimeout(10_000);
            httpClient.setIdleTimeout(10_000);
            JettyClientHttpConnector connector = new JettyClientHttpConnector(httpClient);

            ClientHttpResponse response = connector.connect(
                    HttpMethod.GET,
                    URI.create("http://127.0.0.1:" + server.getPort() + "/greeting"),
                    request -> completeRequest(request, sentRequest))
                    .block(REQUEST_TIMEOUT);

            assertThat(response).isNotNull();
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getHeaders().getFirst("Content-Type"))
                    .isEqualTo("text/plain;charset=UTF-8");
            ResponseCookie cookie = response.getCookies().getFirst("flavor");
            assertThat(cookie).isNotNull();
            assertThat(cookie.getValue()).isEqualTo("vanilla");
            assertThat(cookie.getSameSite()).isEqualTo("Lax");
            assertThat(readBody(response)).isEqualTo("hello jetty");

            RecordedRequest recordedRequest = server.takeRequest(10, TimeUnit.SECONDS);
            assertThat(recordedRequest).isNotNull();
            assertThat(recordedRequest.getMethod()).isEqualTo("GET");
            assertThat(recordedRequest.getPath()).isEqualTo("/greeting");
            assertThat(recordedRequest.getHeader("X-Test-Request"))
                    .isEqualTo("jetty-fields");

            ClientHttpRequest completedRequest = sentRequest.get();
            assertThat(completedRequest).isNotNull();
            assertThat(completedRequest.getHeaders().getFirst("X-Test-Request"))
                    .isEqualTo("jetty-fields");
        } finally {
            try {
                if (!httpClient.isStopped()) {
                    httpClient.stop();
                }
            } finally {
                server.shutdown();
            }
        }
    }

    private static Mono<Void> completeRequest(
            ClientHttpRequest request, AtomicReference<ClientHttpRequest> sentRequest) {
        sentRequest.set(request);
        request.getHeaders().add("X-Test-Request", "jetty-fields");
        return request.setComplete();
    }

    private static String readBody(ClientHttpResponse response) {
        return response.getBody()
                .map(Jetty10HttpFieldsHelperTest::readAndRelease)
                .collectList()
                .map(parts -> String.join("", parts))
                .block(REQUEST_TIMEOUT);
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
