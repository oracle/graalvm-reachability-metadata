/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_github_resilience4j.resilience4j_core;

import io.github.resilience4j.core.metrics.FixedSizeSlidingWindowMetrics;
import io.github.resilience4j.core.metrics.Metrics.Outcome;
import io.github.resilience4j.core.metrics.Snapshot;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tracks recent operation health with Resilience4j's fixed-size sliding-window metrics subsystem.
 *
 * A payment service records each authorization outcome and exposes a health snapshot over only its
 * most recent calls. The service uses the snapshot to report failure and slow-call rates without
 * retaining an unbounded history of old authorizations.
 */
class SlidingWindowMetricsTest {

    @Test
    void reportsHealthForTheMostRecentAuthorizations() {
        FixedSizeSlidingWindowMetrics metrics = new FixedSizeSlidingWindowMetrics(3);

        metrics.record(10, TimeUnit.MILLISECONDS, Outcome.SUCCESS);
        metrics.record(20, TimeUnit.MILLISECONDS, Outcome.ERROR);
        metrics.record(30, TimeUnit.MILLISECONDS, Outcome.SLOW_SUCCESS);
        Snapshot snapshot = metrics.record(40, TimeUnit.MILLISECONDS, Outcome.SUCCESS);

        assertThat(snapshot.getTotalNumberOfCalls()).isEqualTo(3);
        assertThat(snapshot.getNumberOfSuccessfulCalls()).isEqualTo(2);
        assertThat(snapshot.getNumberOfFailedCalls()).isEqualTo(1);
        assertThat(snapshot.getTotalNumberOfSlowCalls()).isEqualTo(1);
        assertThat(snapshot.getNumberOfSlowSuccessfulCalls()).isEqualTo(1);
        assertThat(snapshot.getNumberOfSlowFailedCalls()).isZero();
        assertThat(snapshot.getTotalDuration()).isEqualTo(Duration.ofMillis(90));
        assertThat(snapshot.getAverageDuration()).isEqualTo(Duration.ofMillis(30));
        assertThat(snapshot.getFailureRate()).isEqualTo(100.0f / 3.0f);
        assertThat(snapshot.getSlowCallRate()).isEqualTo(100.0f / 3.0f);
    }
}
