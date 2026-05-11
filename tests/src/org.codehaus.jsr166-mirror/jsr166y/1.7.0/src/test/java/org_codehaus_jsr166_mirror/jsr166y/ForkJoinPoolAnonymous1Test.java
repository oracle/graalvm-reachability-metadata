/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_jsr166_mirror.jsr166y;

import java.util.concurrent.TimeUnit;

import jsr166y.ForkJoinPool;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ForkJoinPoolAnonymous1Test {
    @Test
    void constructsPoolUsingDefaultUnsafeLookup() throws Exception {
        ForkJoinPool pool = new ForkJoinPool(1);
        try {
            assertThat(pool.getParallelism()).isEqualTo(1);
            assertThat(pool.getQueuedSubmissionCount()).isZero();
            assertThat(pool.hasQueuedSubmissions()).isFalse();
            assertThat(pool.toString()).contains("parallelism = 1", "submissions = 0");
        } finally {
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }
}
