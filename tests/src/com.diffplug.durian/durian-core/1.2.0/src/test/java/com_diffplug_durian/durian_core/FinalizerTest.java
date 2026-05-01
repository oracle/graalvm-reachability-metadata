/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_diffplug_durian.durian_core;

import com.diffplug.common.base.FinalizableReferenceQueue;
import com.diffplug.common.base.FinalizableWeakReference;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

public class FinalizerTest {
    @Test
    public void finalizableWeakReferenceIsCleanedByFinalizerThread() throws Exception {
        CountDownLatch finalized = new CountDownLatch(1);
        AtomicInteger finalizeCalls = new AtomicInteger();

        try (FinalizableReferenceQueue queue = new FinalizableReferenceQueue()) {
            TestFinalizableWeakReference reference =
                    new TestFinalizableWeakReference(new Object(), queue, finalized, finalizeCalls);

            assertThat(reference.enqueue()).isTrue();

            // If the background finalizer could not start, constructing another finalizable
            // reference triggers the queue's documented synchronous cleanup fallback.
            new TestFinalizableWeakReference(new Object(), queue, new CountDownLatch(1), new AtomicInteger());

            assertThat(finalized.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(finalizeCalls).hasValue(1);
        }
    }

    private static final class TestFinalizableWeakReference extends FinalizableWeakReference<Object> {
        private final CountDownLatch finalized;
        private final AtomicInteger finalizeCalls;

        private TestFinalizableWeakReference(
                Object referent,
                FinalizableReferenceQueue queue,
                CountDownLatch finalized,
                AtomicInteger finalizeCalls) {
            super(referent, queue);
            this.finalized = finalized;
            this.finalizeCalls = finalizeCalls;
        }

        @Override
        public void finalizeReferent() {
            finalizeCalls.incrementAndGet();
            finalized.countDown();
        }
    }
}
