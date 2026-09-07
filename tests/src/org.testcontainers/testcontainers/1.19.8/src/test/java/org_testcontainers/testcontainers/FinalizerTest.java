/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import java.lang.ref.Reference;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.com.google.common.base.FinalizableReferenceQueue;
import org.testcontainers.shaded.com.google.common.base.FinalizableWeakReference;

import static org.assertj.core.api.Assertions.assertThat;

public class FinalizerTest {
    @Test
    void invokesFinalizableReferencesFromTheQueueWorker() throws Exception {
        CountDownLatch finalized = new CountDownLatch(1);
        FinalizableReferenceQueue queue = new FinalizableReferenceQueue();
        Object referent = new Object();
        NotifyingReference reference = new NotifyingReference(referent, queue, finalized);

        try {
            assertThat(reference.enqueue()).isTrue();
            Reference.reachabilityFence(referent);
            assertThat(finalized.await(10, TimeUnit.SECONDS)).isTrue();
        } finally {
            queue.close();
        }
    }

    public static class NotifyingReference extends FinalizableWeakReference<Object> {
        private final CountDownLatch finalized;

        public NotifyingReference(Object referent, FinalizableReferenceQueue queue, CountDownLatch finalized) {
            super(referent, queue);
            this.finalized = finalized;
        }

        @Override
        public void finalizeReferent() {
            finalized.countDown();
        }
    }
}
