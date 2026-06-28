/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_clickhouse.client_v2;

import com.clickhouse.client.api.Client;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class HttpAPIClientHelperTest {
    @Test
    void registersConnectionPoolMetricsWhenClientIsBuilt() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        try (Client client = new Client.Builder()
                .addEndpoint("http://localhost:8123")
                .setUsername("default")
                .setPassword("test")
                .registerClientMetrics(registry, "test-client")
                .build()) {
            assertThat(client).isNotNull();
            assertThat(registry.getMeters())
                    .extracting(Meter::getId)
                    .extracting(Meter.Id::getName)
                    .contains(
                            "httpcomponents.httpclient.pool.total.max",
                            "httpcomponents.httpclient.pool.total.connections",
                            "httpcomponents.httpclient.pool.total.pending",
                            "httpcomponents.httpclient.pool.route.max.default",
                            "httpcomponents.httpclient.connect.time");
        }
    }
}
