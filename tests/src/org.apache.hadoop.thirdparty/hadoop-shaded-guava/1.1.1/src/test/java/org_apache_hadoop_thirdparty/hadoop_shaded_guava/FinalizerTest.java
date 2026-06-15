/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_hadoop_thirdparty.hadoop_shaded_guava;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.ref.PhantomReference;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.hadoop.thirdparty.com.google.common.base.FinalizableReference;
import org.apache.hadoop.thirdparty.com.google.common.base.internal.Finalizer;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

public class FinalizerTest {
    private static final Unsafe UNSAFE = unsafe();

    @Test
    void finalizerThreadInvokesFinalizeReferentWithoutInheritedThreadLocals() throws Exception {
        InheritableThreadLocal<String> inheritedValue = new InheritableThreadLocal<>();
        inheritedValue.set("parent-value");
        ReferenceQueue<Object> queue = new ReferenceQueue<>();
        Object finalizerQueueSentinel = new Object();
        PhantomReference<Object> finalizerQueueReference =
                new PhantomReference<>(finalizerQueueSentinel, queue);
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
            Reference.reachabilityFence(finalizerQueueSentinel);
            finalizerQueueReference.enqueue();
            Thread thread = finalizerThread.get();
            if (thread != null) {
                thread.join(TimeUnit.SECONDS.toMillis(10));
            }
            inheritedValue.remove();
        }
    }

    @Test
    void javaEightFallbackFinalizerClearsThreadLocalsWithFieldSetter() throws Exception {
        InheritableThreadLocal<String> inheritedValue = new InheritableThreadLocal<>();
        inheritedValue.set("parent-value");
        ReferenceQueue<Object> queue = new ReferenceQueue<>();
        Object finalizerQueueSentinel = new Object();
        PhantomReference<Object> finalizerQueueReference =
                new PhantomReference<>(finalizerQueueSentinel, queue);
        CountDownLatch finalized = new CountDownLatch(1);
        AtomicReference<String> valueSeenByFinalizerThread = new AtomicReference<>();
        AtomicReference<Thread> finalizerThread = new AtomicReference<>();
        Field bigThreadConstructor = accessibleFinalizerField("bigThreadConstructor");
        Field inheritableThreadLocals = accessibleFinalizerField("inheritableThreadLocals");
        Constructor<?> originalBigThreadConstructor =
                (Constructor<?>) bigThreadConstructor.get(null);
        Field originalInheritableThreadLocals = (Field) inheritableThreadLocals.get(null);

        synchronized (Finalizer.class) {
            try {
                setStaticField(bigThreadConstructor, null);
                setStaticField(inheritableThreadLocals, inheritableThreadLocalsField());
                Finalizer.startFinalizer(
                        FinalizableReference.class, queue, finalizerQueueReference);
            } finally {
                setStaticField(bigThreadConstructor, originalBigThreadConstructor);
                setStaticField(inheritableThreadLocals, originalInheritableThreadLocals);
            }
        }

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
            Reference.reachabilityFence(finalizerQueueSentinel);
            finalizerQueueReference.enqueue();
            Thread thread = finalizerThread.get();
            if (thread != null) {
                thread.join(TimeUnit.SECONDS.toMillis(10));
            }
            inheritedValue.remove();
        }
    }

    private static Field accessibleFinalizerField(String name) throws NoSuchFieldException {
        Field field = Finalizer.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static Field inheritableThreadLocalsField() throws NoSuchFieldException {
        Field field = Thread.class.getDeclaredField("inheritableThreadLocals");
        field.setAccessible(true);
        return field;
    }

    private static void setStaticField(Field field, Object value) {
        Object base = UNSAFE.staticFieldBase(field);
        long offset = UNSAFE.staticFieldOffset(field);
        UNSAFE.putObject(base, offset, value);
    }

    private static Unsafe unsafe() {
        try {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            return (Unsafe) field.get(null);
        } catch (ReflectiveOperationException exception) {
            throw new ExceptionInInitializerError(exception);
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
