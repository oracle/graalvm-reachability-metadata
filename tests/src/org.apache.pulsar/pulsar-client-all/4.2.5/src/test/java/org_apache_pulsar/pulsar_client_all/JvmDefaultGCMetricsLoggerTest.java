/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.common.stats.JvmDefaultGCMetricsLogger;
import org.apache.pulsar.common.stats.JvmMetrics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(55)
public class JvmDefaultGCMetricsLoggerTest {
    @Test
    void refreshesDefaultGcStatisticsWhileGeneratingJvmMetrics() {
        JvmDefaultGCMetricsLogger logger = new JvmDefaultGCMetricsLogger();
        logger.refresh();
        JvmMetrics metrics = new JvmMetrics(null, "pulsar_client", logger);

        assertThat(metrics.generate()).hasSize(1);
    }
}
