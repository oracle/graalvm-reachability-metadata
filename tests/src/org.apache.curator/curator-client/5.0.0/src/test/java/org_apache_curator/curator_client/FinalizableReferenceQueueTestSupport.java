/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_curator.curator_client;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.curator.shaded.com.google.common.base.FinalizablePhantomReference;
import org.apache.curator.shaded.com.google.common.base.FinalizableReferenceQueue;

import static org.assertj.core.api.Assertions.assertThat;

public final class FinalizableReferenceQueueTestSupport {
    private FinalizableReferenceQueueTestSupport() {
    }

    public static void assertEnqueuedReferenceIsFinalized() throws Exception {
        CountDownLatch finalized = new CountDownLatch(1);

        try (FinalizableReferenceQueue queue = new FinalizableReferenceQueue()) {
            EnqueuedFinalizableReference reference =
                    new EnqueuedFinalizableReference(new Object(), queue, finalized);

            assertThat(reference.enqueue()).isTrue();
            assertThat(finalized.await(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static final class EnqueuedFinalizableReference extends FinalizablePhantomReference<Object> {
        private final CountDownLatch finalized;

        private EnqueuedFinalizableReference(
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
