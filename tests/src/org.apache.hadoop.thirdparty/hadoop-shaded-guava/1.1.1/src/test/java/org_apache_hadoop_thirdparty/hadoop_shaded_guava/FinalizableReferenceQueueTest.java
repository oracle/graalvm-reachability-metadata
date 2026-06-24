/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_hadoop_thirdparty.hadoop_shaded_guava;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.ref.Reference;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.apache.hadoop.thirdparty.com.google.common.base.FinalizableReferenceQueue;
import org.apache.hadoop.thirdparty.com.google.common.base.FinalizableWeakReference;
import org.junit.jupiter.api.Test;

public class FinalizableReferenceQueueTest {
    @Test
    void finalizableReferenceQueueStartsThreadThatFinalizesQueuedReferences() throws Exception {
        CountDownLatch finalized = new CountDownLatch(1);
        FinalizableReferenceQueue queue = new FinalizableReferenceQueue();
        TestFinalizableWeakReference reference = null;

        try {
            reference = new TestFinalizableWeakReference(new Object(), queue, finalized);

            assertThat(reference.enqueue()).isTrue();
            assertThat(finalized.await(10, TimeUnit.SECONDS)).isTrue();
        } finally {
            queue.close();
            Reference.reachabilityFence(reference);
        }
    }

    private static final class TestFinalizableWeakReference
            extends FinalizableWeakReference<Object> {
        private final CountDownLatch finalized;

        TestFinalizableWeakReference(
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
