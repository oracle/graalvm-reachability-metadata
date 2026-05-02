/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_collections.google_collections;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.base.FinalizableReferenceQueue;
import com.google.common.base.FinalizableWeakReference;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

public class FinalizerTest {
    @Test
    void finalizableReferenceQueueInvokesFinalizeReferent() throws Exception {
        CountDownLatch finalized = new CountDownLatch(1);
        FinalizableReferenceQueue queue = new FinalizableReferenceQueue();
        LatchingFinalizableWeakReference reference = new LatchingFinalizableWeakReference(
                new Object(), queue, finalized);

        assertThat(reference.enqueue()).isTrue();

        assertThat(finalized.await(10, TimeUnit.SECONDS)).isTrue();
    }

    private static final class LatchingFinalizableWeakReference extends FinalizableWeakReference<Object> {
        private final CountDownLatch finalized;

        private LatchingFinalizableWeakReference(
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
