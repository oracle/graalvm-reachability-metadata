/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micrometer.micrometer_core;

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import io.micrometer.core.instrument.binder.jetty.JettyConnectionMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JettyConnectionMetricsTest {

    @Test
    void installsNetworkTrafficListenerAndRecordsHttpBytes() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        try (LocalHttpServer http = new LocalHttpServer()) {
            http.connector.setName("public");
            JettyConnectionMetrics.addToAllConnectors(http.server, registry);
            http.start();
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress("127.0.0.1", http.connector.getLocalPort()), 30000);
                socket.setSoTimeout(30000);
                socket.getOutputStream().write("""
                        GET /greeting HTTP/1.1\r
                        Host: localhost\r
                        Connection: close\r
                        \r
                        """.getBytes(StandardCharsets.US_ASCII));
                InputStream input = socket.getInputStream();
                String response = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                assertThat(response).contains("200 OK", "hello metrics");
            }
            http.close();

            assertThat(registry.get("jetty.connections.bytes.in").tag("connector.name", "public")
                    .summary().totalAmount()).isGreaterThan(0.0);
            assertThat(registry.get("jetty.connections.bytes.out").summary().totalAmount()).isGreaterThan(0.0);
            assertThat(registry.get("jetty.connections.request").timer().count()).isGreaterThanOrEqualTo(1);
            assertThat(registry.get("jetty.connections.current").gauge().value()).isZero();
        } finally {
            registry.close();
        }
    }
}
