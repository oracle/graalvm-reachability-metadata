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
import java.lang.ref.WeakReference;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

public class FinalizerTest {
    @Test
    void finalizableReferenceQueueInvokesFinalizeReferentOnBackgroundFinalizer()
            throws Exception {
        InheritableThreadLocal<String> inheritedValue = new InheritableThreadLocal<>();
        inheritedValue.set("parent-value");

        FinalizationResult result = null;
        try {
            result = createFinalizableReference(inheritedValue);
            assertThat(result.finalized.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(result.valueSeenByFinalizerThread.get()).isNull();
            assertThat(result.finalizerThread.get()).isNotSameAs(Thread.currentThread());
        } finally {
            inheritedValue.remove();
            if (result != null) {
                awaitFinalizerShutdown(result);
            }
        }
    }

    private static FinalizationResult createFinalizableReference(
            InheritableThreadLocal<String> inheritedValue) {
        FinalizableReferenceQueue referenceQueue = new FinalizableReferenceQueue();
        WeakReference<FinalizableReferenceQueue> queueReference = new WeakReference<>(referenceQueue);
        CountDownLatch finalized = new CountDownLatch(1);
        AtomicReference<String> valueSeenByFinalizerThread = new AtomicReference<>();
        AtomicReference<Thread> finalizerThread = new AtomicReference<>();

        Object referent = new Object();
        AwaitedFinalizableWeakReference reference = new AwaitedFinalizableWeakReference(
                referent,
                referenceQueue,
                inheritedValue,
                valueSeenByFinalizerThread,
                finalizerThread,
                finalized);

        assertThat(reference.enqueue()).isTrue();
        assertThat(referent).isNotNull();
        return new FinalizationResult(
                queueReference, finalized, valueSeenByFinalizerThread, finalizerThread);
    }

    private static void awaitFinalizerShutdown(FinalizationResult result) throws InterruptedException {
        for (int attempt = 0; attempt < 10 && result.queueReference.get() != null; attempt++) {
            System.gc();
            Thread.sleep(100);
        }

        Thread thread = result.finalizerThread.get();
        if (result.queueReference.get() == null && thread != null) {
            thread.join(TimeUnit.SECONDS.toMillis(10));
        }
    }

    private static final class FinalizationResult {
        private final WeakReference<FinalizableReferenceQueue> queueReference;
        private final CountDownLatch finalized;
        private final AtomicReference<String> valueSeenByFinalizerThread;
        private final AtomicReference<Thread> finalizerThread;

        FinalizationResult(
                WeakReference<FinalizableReferenceQueue> queueReference,
                CountDownLatch finalized,
                AtomicReference<String> valueSeenByFinalizerThread,
                AtomicReference<Thread> finalizerThread) {
            this.queueReference = queueReference;
            this.finalized = finalized;
            this.valueSeenByFinalizerThread = valueSeenByFinalizerThread;
            this.finalizerThread = finalizerThread;
        }
    }

    private static final class AwaitedFinalizableWeakReference
            extends FinalizableWeakReference<Object> {
        private final InheritableThreadLocal<String> inheritedValue;
        private final AtomicReference<String> valueSeenByFinalizerThread;
        private final AtomicReference<Thread> finalizerThread;
        private final CountDownLatch finalized;

        AwaitedFinalizableWeakReference(
                Object referent,
                FinalizableReferenceQueue queue,
                InheritableThreadLocal<String> inheritedValue,
                AtomicReference<String> valueSeenByFinalizerThread,
                AtomicReference<Thread> finalizerThread,
                CountDownLatch finalized) {
            super(referent, queue);
            this.inheritedValue = inheritedValue;
            this.valueSeenByFinalizerThread = valueSeenByFinalizerThread;
            this.finalizerThread = finalizerThread;
            this.finalized = finalized;
        }

        @Override
        public void finalizeReferent() {
            valueSeenByFinalizerThread.set(inheritedValue.get());
            finalizerThread.set(Thread.currentThread());
            finalized.countDown();
        }
    }
}
