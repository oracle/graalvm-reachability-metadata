/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_diffplug_durian.durian_core;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.diffplug.common.base.FinalizableReferenceQueue;
import com.diffplug.common.base.FinalizableWeakReference;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FinalizerTest {
    @Test
    void finalizerThreadInvokesFinalizableReference() throws InterruptedException {
        CountDownLatch finalized = new CountDownLatch(1);

        try (FinalizableReferenceQueue queue = new FinalizableReferenceQueue()) {
            Object referent = new Object();
            TestFinalizableWeakReference reference = new TestFinalizableWeakReference(referent, queue, finalized);

            assertThat(reference.enqueue()).isTrue();
            assertThat(referent).isNotNull();
            assertThat(finalized.await(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static final class TestFinalizableWeakReference extends FinalizableWeakReference<Object> {
        private final CountDownLatch finalized;

        private TestFinalizableWeakReference(
                Object referent,
                FinalizableReferenceQueue queue,
                CountDownLatch finalized) {
            super(referent, queue);
            this.finalized = finalized;
        }

        @Override
        public void finalizeReferent() {
            finalized.countDown();
        }
    }
}
