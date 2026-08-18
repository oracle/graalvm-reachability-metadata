/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_sonatype_sisu.sisu_guice;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.internal.util.FinalizableReferenceQueue;
import com.google.inject.internal.util.FinalizableWeakReference;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

public class FinalizerTest {
    @Test
    void invokesFinalizeReferentForQueuedReference() throws InterruptedException {
        CountDownLatch finalized = new CountDownLatch(1);
        FinalizableReferenceQueue queue = new FinalizableReferenceQueue();
        TestFinalizableWeakReference reference = new TestFinalizableWeakReference(new Object(), queue, finalized);

        assertThat(reference.enqueue()).isTrue();
        new TestFinalizableWeakReference(new Object(), queue, new CountDownLatch(0));

        assertThat(finalized.await(10, TimeUnit.SECONDS)).isTrue();
    }

    private static final class TestFinalizableWeakReference extends FinalizableWeakReference<Object> {
        private final CountDownLatch finalized;

        private TestFinalizableWeakReference(Object referent, FinalizableReferenceQueue queue, CountDownLatch finalized) {
            super(referent, queue);
            this.finalized = finalized;
        }

        @Override
        public void finalizeReferent() {
            finalized.countDown();
        }
    }
}
