/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package co_elastic_clients.elasticsearch_java;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import co.elastic.clients.transport.rest5_client.low_level.Request;
import co.elastic.clients.transport.rest5_client.low_level.RequestOptions;
import co.elastic.clients.transport.rest5_client.low_level.Response;
import co.elastic.clients.transport.rest5_client.low_level.Rest5Client;
import com.sun.net.httpserver.HttpServer;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class Rest5ClientLowLevelLanguageRuntimeVersionsTest {
    @Test
    void metadataHeaderIncludesDetectedJvmLanguageVersions() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> metadataHeader = new AtomicReference<>();
        server.createContext("/", exchange -> {
            metadataHeader.set(exchange.getRequestHeaders().getFirst("X-Elastic-Client-Meta"));
            byte[] responseBody = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, responseBody.length);
            try (OutputStream body = exchange.getResponseBody()) {
                body.write(responseBody);
            }
        });
        server.setExecutor(executor);
        server.start();

        try {
            URI uri = URI.create("http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort());
            try (Rest5Client client = Rest5Client.builder(uri).build()) {
                Request request = new Request("GET", "/");
                request.setOptions(requestOptionsWithBoundedTimeouts());

                Response response = client.performRequest(request);

                assertThat(response.getStatusCode()).isEqualTo(200);
            }
        } finally {
            server.stop(0);
            executor.shutdownNow();
        }

        assertThat(metadataHeader.get())
            .containsPattern("(^|,)kt=\\d+\\.\\d+(,|$)")
            .containsPattern("(^|,)sc=\\d+\\.\\d+(,|$)");
    }

    private static RequestOptions requestOptionsWithBoundedTimeouts() {
        Timeout timeout = Timeout.ofSeconds(10);
        RequestConfig requestConfig = RequestConfig.custom()
            .setConnectionRequestTimeout(timeout)
            .setConnectTimeout(timeout)
            .setResponseTimeout(timeout)
            .build();
        return RequestOptions.DEFAULT.toBuilder()
            .setRequestConfig(requestConfig)
            .build();
    }
}
