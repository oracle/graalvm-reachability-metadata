/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_guava.guava;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.base.FinalizableReference;
import com.google.common.base.internal.Finalizer;
import java.lang.ref.PhantomReference;
import java.lang.ref.ReferenceQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

public class FinalizerTest {
    @Test
    void finalizerThreadInvokesFinalizeReferentWithoutInheritedThreadLocals() throws Exception {
        InheritableThreadLocal<String> inheritedValue = new InheritableThreadLocal<>();
        inheritedValue.set("parent-value");
        ReferenceQueue<Object> queue = new ReferenceQueue<>();
        PhantomReference<Object> finalizerQueueReference =
                new PhantomReference<>(new Object(), queue);
        CountDownLatch finalized = new CountDownLatch(1);
        AtomicReference<String> valueSeenByFinalizerThread = new AtomicReference<>();
        AtomicReference<Thread> finalizerThread = new AtomicReference<>();

        Finalizer.startFinalizer(FinalizableReference.class, queue, finalizerQueueReference);
        QueuedFinalizableReference reference =
                new QueuedFinalizableReference(
                        new Object(),
                        queue,
                        inheritedValue,
                        valueSeenByFinalizerThread,
                        finalizerThread,
                        finalized);

        try {
            assertThat(reference.enqueue()).isTrue();
            assertThat(finalized.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(valueSeenByFinalizerThread.get()).isNull();
            assertThat(finalizerThread.get()).isNotSameAs(Thread.currentThread());
        } finally {
            finalizerQueueReference.enqueue();
            Thread thread = finalizerThread.get();
            if (thread != null) {
                thread.join(TimeUnit.SECONDS.toMillis(10));
            }
            inheritedValue.remove();
        }
    }

    private static final class QueuedFinalizableReference extends PhantomReference<Object>
            implements FinalizableReference {
        private final InheritableThreadLocal<String> inheritedValue;
        private final AtomicReference<String> valueSeenByFinalizerThread;
        private final AtomicReference<Thread> finalizerThread;
        private final CountDownLatch finalized;

        QueuedFinalizableReference(
                Object referent,
                ReferenceQueue<Object> queue,
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
