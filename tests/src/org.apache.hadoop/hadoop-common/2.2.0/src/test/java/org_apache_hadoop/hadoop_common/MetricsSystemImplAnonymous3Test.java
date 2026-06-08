/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_hadoop.hadoop_common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.hadoop.metrics2.MetricsSystem.AbstractCallback;
import org.apache.hadoop.metrics2.impl.MetricsSystemImpl;
import org.junit.jupiter.api.Test;

public class MetricsSystemImplAnonymous3Test {
    @Test
    void registeredCallbackIsInvokedWhenMetricsSystemStartsAndStops() {
        MetricsSystemImpl metricsSystem = new MetricsSystemImpl();
        RecordingCallback callback = new RecordingCallback();
        metricsSystem.register(callback);

        try {
            metricsSystem.init("metricssystemimplanonymous3test");

            assertThat(callback.preStartCount).hasValue(1);
            assertThat(callback.postStartCount).hasValue(1);
        } finally {
            metricsSystem.shutdown();
        }

        assertThat(callback.preStopCount).hasValue(1);
        assertThat(callback.postStopCount).hasValue(1);
    }

    public static class RecordingCallback extends AbstractCallback {
        private final AtomicInteger preStartCount = new AtomicInteger();
        private final AtomicInteger postStartCount = new AtomicInteger();
        private final AtomicInteger preStopCount = new AtomicInteger();
        private final AtomicInteger postStopCount = new AtomicInteger();

        @Override
        public void preStart() {
            preStartCount.incrementAndGet();
        }

        @Override
        public void postStart() {
            postStartCount.incrementAndGet();
        }

        @Override
        public void preStop() {
            preStopCount.incrementAndGet();
        }

        @Override
        public void postStop() {
            postStopCount.incrementAndGet();
        }
    }
}
