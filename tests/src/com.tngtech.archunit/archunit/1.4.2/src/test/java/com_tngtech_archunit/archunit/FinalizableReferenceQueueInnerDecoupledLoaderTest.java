/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_tngtech_archunit.archunit;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.thirdparty.com.google.common.base.FinalizableReferenceQueue;
import com.tngtech.archunit.thirdparty.com.google.common.base.FinalizableSoftReference;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

public class FinalizableReferenceQueueInnerDecoupledLoaderTest {
    @Test
    void finalizesAnEnqueuedSoftReferenceWithTheLoadedFinalizer() throws Exception {
        CountDownLatch finalized = new CountDownLatch(1);

        try (FinalizableReferenceQueue queue = new FinalizableReferenceQueue()) {
            QueuedSoftReference reference =
                    new QueuedSoftReference(new Object(), queue, finalized);

            assertThat(reference.get()).isNotNull();
            assertThat(reference.enqueue()).isTrue();
            assertThat(finalized.await(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static final class QueuedSoftReference extends FinalizableSoftReference<Object> {
        private final CountDownLatch finalized;

        private QueuedSoftReference(
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
