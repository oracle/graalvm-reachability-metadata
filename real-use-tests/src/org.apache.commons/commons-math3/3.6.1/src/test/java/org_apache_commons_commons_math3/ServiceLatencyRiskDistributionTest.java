/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons_commons_math3;

import org.apache.commons.math3.distribution.NormalDistribution;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Probability distributions estimate service-latency risk for an application's SLA.
public class ServiceLatencyRiskDistributionTest {
    @Test
    public void setsAnSlaTimeoutFromObservedLatencyRisk() {
        NormalDistribution latencyDistribution = new NormalDistribution(100.0, 15.0);

        double probabilityOfMeetingTheSla = latencyDistribution.cumulativeProbability(130.0);
        double p95Latency = latencyDistribution.inverseCumulativeProbability(0.95);
        double probabilityInNormalOperatingRange = latencyDistribution.probability(85.0, 115.0);

        assertEquals(0.9772498681, probabilityOfMeetingTheSla, 1.0e-10);
        assertEquals(124.6728044, p95Latency, 1.0e-8);
        assertEquals(0.682689492137086, probabilityInNormalOperatingRange, 1.0e-12);
    }
}
