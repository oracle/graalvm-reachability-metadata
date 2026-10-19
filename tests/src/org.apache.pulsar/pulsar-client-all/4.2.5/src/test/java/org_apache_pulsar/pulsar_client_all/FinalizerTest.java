/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.apache.pulsar.shade.com.google.common.base.FinalizableReferenceQueue;
import org.apache.pulsar.shade.com.google.common.base.FinalizableWeakReference;
import org.junit.jupiter.api.Test;

public class FinalizerTest {
    @Test
    void invokesFinalizableReferenceCallback() throws Exception {
        FinalizableReferenceQueue queue = new FinalizableReferenceQueue();
        CountDownLatch finalized = new CountDownLatch(1);
        TestReference reference = new TestReference(new Object(), queue, finalized);

        assertThat(reference.enqueue()).isTrue();
        queue.close();

        assertThat(finalized.await(10, TimeUnit.SECONDS)).isTrue();
    }

    private static final class TestReference extends FinalizableWeakReference<Object> {
        private final CountDownLatch finalized;

        private TestReference(
                Object referent, FinalizableReferenceQueue queue, CountDownLatch finalized) {
            super(referent, queue);
            this.finalized = finalized;
        }

        @Override
        public void finalizeReferent() {
            finalized.countDown();
        }
    }
}
