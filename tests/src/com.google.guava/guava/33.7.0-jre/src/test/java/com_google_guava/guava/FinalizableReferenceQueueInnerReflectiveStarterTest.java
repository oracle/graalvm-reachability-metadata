/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_guava.guava;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.base.FinalizablePhantomReference;
import com.google.common.base.FinalizableReferenceQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

public class FinalizableReferenceQueueInnerReflectiveStarterTest {
    @Test
    void queueStartsFinalizerThroughReflectiveStarter() throws Exception {
        CountDownLatch finalized = new CountDownLatch(1);

        try (FinalizableReferenceQueue queue = new FinalizableReferenceQueue()) {
            EnqueuedFinalizableReference reference =
                    new EnqueuedFinalizableReference(new Object(), queue, finalized);

            assertThat(reference.enqueue()).isTrue();
            assertThat(finalized.await(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static final class EnqueuedFinalizableReference
            extends FinalizablePhantomReference<Object> {
        private final CountDownLatch finalized;

        EnqueuedFinalizableReference(
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
