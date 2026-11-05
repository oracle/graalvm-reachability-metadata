/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_zookeeper.zookeeper;

import org.apache.zookeeper.metrics.Counter;
import org.apache.zookeeper.metrics.MetricsContext;
import org.apache.zookeeper.metrics.MetricsProvider;
import org.apache.zookeeper.metrics.impl.MetricsProviderBootstrap;
import org.apache.zookeeper.server.quorum.QuorumPeerConfig;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class MetricsProviderBootstrapTest {

    @Test
    void configuredDefaultProviderCollectsAndResetsMetrics() throws Exception {
        QuorumPeerConfig config = new QuorumPeerConfig();
        MetricsProvider provider = MetricsProviderBootstrap.startMetricsProvider(
                config.getMetricsProviderClassName(), config.getMetricsProviderConfiguration());
        try {
            MetricsContext context = provider.getRootContext();
            Counter requests = context.getCounter("completed_requests");
            requests.inc();
            requests.add(4);
            context.registerGauge("pending_requests", () -> 3);

            Map<String, Object> values = new HashMap<>();
            provider.dump(values::put);
            assertThat(requests.get()).isEqualTo(5);
            assertThat(values).containsEntry("completed_requests", 5L)
                    .containsEntry("pending_requests", 3);

            provider.resetAllValues();
            context.unregisterGauge("pending_requests");
            values.clear();
            provider.dump(values::put);
            assertThat(requests.get()).isZero();
            assertThat(values).containsEntry("completed_requests", 0L)
                    .doesNotContainKey("pending_requests");
        } finally {
            provider.stop();
        }
    }
}
