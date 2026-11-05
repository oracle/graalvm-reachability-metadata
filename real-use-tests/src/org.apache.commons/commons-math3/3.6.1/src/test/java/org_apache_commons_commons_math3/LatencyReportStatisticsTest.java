/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons_commons_math3;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Summarizes service response times with Commons Math's descriptive statistics subsystem.
public class LatencyReportStatisticsTest {
    @Test
    public void reportsMetricsForTheLatestResponseTimes() {
        DescriptiveStatistics responseTimes = new DescriptiveStatistics(5);
        double[] observedLatencies = {120.0, 95.0, 110.0, 130.0, 100.0, 105.0, 115.0};
        for (double observedLatency : observedLatencies) {
            responseTimes.addValue(observedLatency);
        }

        assertEquals(5, responseTimes.getN());
        assertEquals(560.0, responseTimes.getSum(), 0.0);
        assertEquals(112.0, responseTimes.getMean(), 0.0);
        assertEquals(100.0, responseTimes.getMin(), 0.0);
        assertEquals(130.0, responseTimes.getMax(), 0.0);
        assertEquals(11.5108644332, responseTimes.getStandardDeviation(), 1.0e-10);
    }
}
