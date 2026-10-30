/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite_avatica.avatica_metrics;

import org.apache.calcite.avatica.metrics.MetricsSystemConfiguration;
import org.apache.calcite.avatica.metrics.noop.NoopMetricsSystemConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

public class NoopMetricsSystemConfigurationTest {
    @Test
    void suppliesAnEmptySharedConfiguration() {
        MetricsSystemConfiguration<Void> configuration = NoopMetricsSystemConfiguration.getInstance();

        assertNull(configuration.get());
        assertSame(configuration, NoopMetricsSystemConfiguration.getInstance());
    }
}
