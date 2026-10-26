/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;
import static org.apache.pulsar.shade.com.google.common.util.concurrent.MoreExecutors.directExecutor;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.pulsar.shade.com.google.common.util.concurrent.ClosingFuture;
import org.apache.pulsar.shade.com.google.common.util.concurrent.FluentFuture;
import org.junit.jupiter.api.Test;

public class ClosingFutureTest {
    @Test
    void closesCapturedResourceAfterComputation() throws Exception {
        AtomicBoolean closed = new AtomicBoolean();

        FluentFuture<String> result = ClosingFuture.submit(closer -> {
            closer.eventuallyClose((AutoCloseable) () -> closed.set(true), directExecutor());
            return "persistent";
        }, directExecutor()).finishToFuture();

        assertThat(result.get(10, TimeUnit.SECONDS)).isEqualTo("persistent");
        assertThat(closed).isTrue();
    }
}
