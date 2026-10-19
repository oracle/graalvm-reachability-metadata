/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_cloud.spring_cloud_consul_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import org.springframework.cloud.consul.ConsulAutoConfiguration;
import org.springframework.cloud.consul.ConsulClient;
import org.springframework.cloud.consul.model.http.event.Event;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;

@Timeout(value = 55, unit = TimeUnit.SECONDS)
public class EventTest {

    private HttpServer server;

    private ConsulClient client;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/events", this::sendEvents);
        server.start();

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        RestClient restClient = RestClient.builder()
            .baseUrl("http://127.0.0.1:" + server.getAddress().getPort())
            .requestFactory(requestFactory)
            .build();
        client = ConsulAutoConfiguration.createNewConsulClient(RestClientAdapter.create(restClient));
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void listsConsulEvents() {
        ResponseEntity<List<Event>> response = client.eventList();

        assertThat(response.getBody()).hasSize(1);
        Event event = response.getBody().get(0);
        assertThat(event.getId()).isEqualTo("12345678-1234-1234-1234-123456789abc");
        assertThat(event.getName()).isEqualTo("configuration-updated");
        assertThat(event.getPayload()).isEqualTo("cHJvZmlsZT10ZXN0");
        assertThat(event.getNodeFilter()).isEqualTo("node-a");
        assertThat(event.getServiceFilter()).isEqualTo("inventory");
        assertThat(event.getTagFilter()).isEqualTo("primary");
        assertThat(event.getVersion()).isEqualTo(1);
        assertThat(event.getlTime()).isEqualTo(73);
        assertThat(event.getWaitIndex()).isPositive();
    }

    private void sendEvents(HttpExchange exchange) throws IOException {
        assertThat(exchange.getRequestMethod()).isEqualTo("GET");
        byte[] body = """
            [{"ID":"12345678-1234-1234-1234-123456789abc",\
            "Name":"configuration-updated","Payload":"cHJvZmlsZT10ZXN0",\
            "NodeFilter":"node-a","ServiceFilter":"inventory","TagFilter":"primary",\
            "Version":1,"LTime":73}]
            """.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(body);
        }
    }
}
