/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite_avatica.avatica_metrics;

import org.apache.calcite.avatica.metrics.MetricsSystem;
import org.apache.calcite.avatica.metrics.MetricsSystemFactory;
import org.apache.calcite.avatica.metrics.noop.NoopMetricsSystem;
import org.apache.calcite.avatica.metrics.noop.NoopMetricsSystemConfiguration;
import org.apache.calcite.avatica.metrics.noop.NoopMetricsSystemFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;

public class NoopMetricsSystemFactoryTest {
    @Test
    void createsTheSharedNoopSystemForRepeatedRequests() {
        MetricsSystemFactory factory = new NoopMetricsSystemFactory();
        NoopMetricsSystemConfiguration configuration = NoopMetricsSystemConfiguration.getInstance();

        MetricsSystem first = factory.create(configuration);
        MetricsSystem second = factory.create(configuration);

        assertSame(NoopMetricsSystem.getInstance(), first);
        assertSame(first, second);
        assertSame(first.getCounter("requests"), second.getCounter("responses"));
    }
}
