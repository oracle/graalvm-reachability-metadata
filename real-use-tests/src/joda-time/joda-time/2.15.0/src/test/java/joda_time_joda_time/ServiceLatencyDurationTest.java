/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package joda_time_joda_time;

import org.joda.time.Duration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises Joda-Time's duration subsystem for combining service timeout budgets and calculating a retry backoff.
public class ServiceLatencyDurationTest {
    @Test
    void combinesTimeoutBudgetsAndReportsTheRetryBackoff() {
        Duration requestTimeout = Duration.parse("PT45S");
        Duration processingBudget = Duration.standardMinutes(2);
        Duration totalBudget = requestTimeout.plus(processingBudget);
        Duration retryBackoff = totalBudget.dividedBy(3);

        assertEquals(165_000L, totalBudget.getMillis());
        assertEquals(165, totalBudget.toStandardSeconds().getSeconds());
        assertEquals("PT165S", totalBudget.toString());
        assertEquals(55_000L, retryBackoff.getMillis());
        assertEquals("PT55S", retryBackoff.toString());
    }
}
