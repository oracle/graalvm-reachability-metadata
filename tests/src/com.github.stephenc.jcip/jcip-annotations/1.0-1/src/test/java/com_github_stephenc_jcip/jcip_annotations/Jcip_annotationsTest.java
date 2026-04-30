/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_stephenc_jcip.jcip_annotations;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import net.jcip.annotations.GuardedBy;
import net.jcip.annotations.Immutable;
import net.jcip.annotations.NotThreadSafe;
import net.jcip.annotations.ThreadSafe;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class Jcip_annotationsTest {
    @Test
    void threadSafeAnnotatedCounterCoordinatesConcurrentAccess() throws InterruptedException {
        ThreadSafeCounter counter = new ThreadSafeCounter();
        int workerCount = 4;
        int incrementsPerWorker = 25;
        CountDownLatch startSignal = new CountDownLatch(1);
        CountDownLatch finishedSignal = new CountDownLatch(workerCount);
        ExecutorService executor = Executors.newFixedThreadPool(workerCount);

        try {
            for (int workerIndex = 0; workerIndex < workerCount; workerIndex++) {
                executor.execute(() -> {
                    try {
                        startSignal.await();
                        for (int incrementIndex = 0; incrementIndex < incrementsPerWorker; incrementIndex++) {
                            counter.incrementAndGet();
                        }
                    } catch (InterruptedException interruptedException) {
                        Thread.currentThread().interrupt();
                    } finally {
                        finishedSignal.countDown();
                    }
                });
            }

            startSignal.countDown();
            assertThat(finishedSignal.await(5, TimeUnit.SECONDS)).isTrue();
        } finally {
            executor.shutdownNow();
        }

        assertThat(counter.current()).isEqualTo(workerCount * incrementsPerWorker);
        assertThat(counter.guardedSnapshot()).isEqualTo(workerCount * incrementsPerWorker);
        assertThat(counter.describeState()).isEqualTo("count=" + workerCount * incrementsPerWorker);
    }

    @Test
    void guardedByAnnotatedMembersModelSeparateLockingStrategies() {
        ThreadSafeCounter counter = new ThreadSafeCounter();

        assertThat(counter.incrementAndGet()).isEqualTo(1);
        assertThat(counter.incrementAndGet()).isEqualTo(2);
        assertThat(counter.guardedSnapshot()).isEqualTo(2);
        assertThat(counter.describeState()).isEqualTo("count=2");
    }

    @Test
    void guardedBySupportsClassLevelLockForStaticState() {
        StaticGuardedRegistry.clear();

        assertThat(StaticGuardedRegistry.register("primary")).isEqualTo(1);
        assertThat(StaticGuardedRegistry.register("secondary")).isEqualTo(2);
        assertThat(StaticGuardedRegistry.snapshot()).containsExactly("primary", "secondary");
        assertThat(StaticGuardedRegistry.describe()).isEqualTo("registered=2");
    }

    @Test
    void guardedBySupportsSelfGuardedState() {
        SelfGuardedTaskQueue queue = new SelfGuardedTaskQueue();

        assertThat(queue.enqueueIfMissing("compile")).isTrue();
        assertThat(queue.enqueueIfMissing("compile")).isFalse();
        assertThat(queue.enqueueIfMissing("package")).isTrue();
        assertThat(queue.drain()).containsExactly("compile", "package");
        assertThat(queue.drain()).isEmpty();
    }

    @Test
    void notThreadSafeAnnotatedMutableTypeKeepsInsertionOrder() {
        MutableAuditTrail auditTrail = new MutableAuditTrail();

        auditTrail.append("created");
        auditTrail.append("validated");
        auditTrail.append("stored");

        assertThat(auditTrail.snapshot()).containsExactly("created", "validated", "stored");
        assertThat(auditTrail.joined()).isEqualTo("created -> validated -> stored");
        assertThat(auditTrail.size()).isEqualTo(3);
    }

    @Test
    void immutableAnnotatedValueTypesCreateNewValues() {
        ImmutablePoint origin = new ImmutablePoint(2, -3);
        ImmutablePoint translated = origin.translate(5, 7);
        ImmutableRange range = new ImmutableRange(-4, 8);

        assertThat(origin.asText()).isEqualTo("(2,-3)");
        assertThat(translated.asText()).isEqualTo("(7,4)");
        assertThat(origin.manhattanDistance()).isEqualTo(5);
        assertThat(translated.manhattanDistance()).isEqualTo(11);
        assertThat(origin).isNotSameAs(translated);

        assertThat(range.contains(-4)).isTrue();
        assertThat(range.contains(0)).isTrue();
        assertThat(range.contains(9)).isFalse();
        assertThat(range.length()).isEqualTo(12);
    }

    @Test
    void markerAnnotationsCanBeAppliedToDifferentTypeDeclarations() {
        ThreadSafeService service = new ThreadSafeService(new ThreadSafeCounter());
        LifecycleMode mode = LifecycleMode.SINGLE_WRITER;
        SnapshotState state = new SnapshotState() {
            @Override
            public String value() {
                return "warm";
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return SnapshotState.class;
            }
        };

        assertThat(service.incrementAndReport()).isEqualTo("count=1");
        assertThat(mode.requiresExternalSynchronization()).isTrue();
        assertThat(state.value()).isEqualTo("warm");
        assertThat(new ImmutableCoordinates(4, -1).translate(3, 2)).isEqualTo(new ImmutableCoordinates(7, 1));
    }

    @Test
    void annotationInterfacesCanBeUsedThroughTheirPublicAnnotationContracts() {
        ThreadSafe threadSafe = new ThreadSafeLiteral();
        NotThreadSafe notThreadSafe = new NotThreadSafeLiteral();
        Immutable immutable = new ImmutableLiteral();
        GuardedBy lockGuard = new GuardedByLiteral("lock");
        GuardedBy thisGuard = new GuardedByLiteral("this");

        assertThat(threadSafe.annotationType()).isSameAs(ThreadSafe.class);
        assertThat(notThreadSafe.annotationType()).isSameAs(NotThreadSafe.class);
        assertThat(immutable.annotationType()).isSameAs(Immutable.class);
        assertThat(lockGuard.annotationType()).isSameAs(GuardedBy.class);
        assertThat(lockGuard.value()).isEqualTo("lock");
        assertThat(thisGuard.value()).isEqualTo("this");
        assertThat(lockGuard).isNotEqualTo(thisGuard);
        assertThat(new GuardedByLiteral("lock")).isEqualTo(lockGuard);
        assertThat(new GuardedByLiteral("lock").hashCode()).isEqualTo(lockGuard.hashCode());
    }

    @ThreadSafe
    private interface ConcurrentService {
        String incrementAndReport();
    }

    @ThreadSafe
    private static final class ThreadSafeService implements ConcurrentService {
        private final ThreadSafeCounter counter;

        private ThreadSafeService(ThreadSafeCounter counter) {
            this.counter = counter;
        }

        @Override
        public String incrementAndReport() {
            counter.incrementAndGet();
            return counter.describeState();
        }
    }

    @NotThreadSafe
    private enum LifecycleMode {
        SINGLE_WRITER,
        BEST_EFFORT;

        private boolean requiresExternalSynchronization() {
            return this == SINGLE_WRITER;
        }
    }

    @Immutable
    private @interface SnapshotState {
        String value();
    }

    @Immutable
    private record ImmutableCoordinates(int x, int y) {
        private ImmutableCoordinates translate(int dx, int dy) {
            return new ImmutableCoordinates(x + dx, y + dy);
        }
    }

    @ThreadSafe
    private static final class ThreadSafeCounter {
        private final Object lock = new Object();

        @GuardedBy("lock")
        private int value;

        int incrementAndGet() {
            synchronized (lock) {
                value++;
                return value;
            }
        }

        int current() {
            synchronized (lock) {
                return value;
            }
        }

        @GuardedBy("lock")
        private int snapshotWhileHoldingLock() {
            synchronized (lock) {
                return value;
            }
        }

        int guardedSnapshot() {
            return snapshotWhileHoldingLock();
        }

        @GuardedBy("this")
        private synchronized String snapshotUsingThisMonitor() {
            return "count=" + value;
        }

        String describeState() {
            return snapshotUsingThisMonitor();
        }
    }

    private static final class StaticGuardedRegistry {
        @GuardedBy("StaticGuardedRegistry.class")
        private static final List<String> entries = new ArrayList<>();

        private StaticGuardedRegistry() {
        }

        private static synchronized int register(String entry) {
            entries.add(entry);
            return entries.size();
        }

        @GuardedBy("StaticGuardedRegistry.class")
        private static synchronized List<String> snapshotWhileHoldingClassMonitor() {
            return List.copyOf(entries);
        }

        private static List<String> snapshot() {
            return snapshotWhileHoldingClassMonitor();
        }

        @GuardedBy("StaticGuardedRegistry.class")
        private static synchronized String describeWhileHoldingClassMonitor() {
            return "registered=" + entries.size();
        }

        private static String describe() {
            return describeWhileHoldingClassMonitor();
        }

        private static synchronized void clear() {
            entries.clear();
        }
    }

    private static final class SelfGuardedTaskQueue {
        @GuardedBy("itself")
        private final List<String> tasks = new ArrayList<>();

        private boolean enqueueIfMissing(String task) {
            synchronized (tasks) {
                if (tasks.contains(task)) {
                    return false;
                }
                return tasks.add(task);
            }
        }

        private List<String> drain() {
            synchronized (tasks) {
                List<String> drainedTasks = List.copyOf(tasks);
                tasks.clear();
                return drainedTasks;
            }
        }
    }

    @NotThreadSafe
    private static final class MutableAuditTrail {
        private final List<String> entries = new ArrayList<>();

        void append(String event) {
            entries.add(event);
        }

        List<String> snapshot() {
            return List.copyOf(entries);
        }

        String joined() {
            return String.join(" -> ", entries);
        }

        int size() {
            return entries.size();
        }
    }

    @Immutable
    private static final class ImmutablePoint {
        private final int x;
        private final int y;

        private ImmutablePoint(int x, int y) {
            this.x = x;
            this.y = y;
        }

        ImmutablePoint translate(int dx, int dy) {
            return new ImmutablePoint(x + dx, y + dy);
        }

        int manhattanDistance() {
            return Math.abs(x) + Math.abs(y);
        }

        String asText() {
            return "(" + x + "," + y + ")";
        }
    }

    @Immutable
    private record ImmutableRange(int startInclusive, int endExclusive) {
        private boolean contains(int candidate) {
            return candidate >= startInclusive && candidate < endExclusive;
        }

        private int length() {
            return endExclusive - startInclusive;
        }
    }

    private static final class ThreadSafeLiteral implements ThreadSafe {
        @Override
        public Class<? extends Annotation> annotationType() {
            return ThreadSafe.class;
        }
    }

    private static final class NotThreadSafeLiteral implements NotThreadSafe {
        @Override
        public Class<? extends Annotation> annotationType() {
            return NotThreadSafe.class;
        }
    }

    private static final class ImmutableLiteral implements Immutable {
        @Override
        public Class<? extends Annotation> annotationType() {
            return Immutable.class;
        }
    }

    private static final class GuardedByLiteral implements GuardedBy {
        private final String value;

        private GuardedByLiteral(String value) {
            this.value = value;
        }

        @Override
        public String value() {
            return value;
        }

        @Override
        public Class<? extends Annotation> annotationType() {
            return GuardedBy.class;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof GuardedBy otherGuard)) {
                return false;
            }
            return value.equals(otherGuard.value());
        }

        @Override
        public int hashCode() {
            return (127 * "value".hashCode()) ^ value.hashCode();
        }

        @Override
        public String toString() {
            return "@" + GuardedBy.class.getName() + "(value=" + value + ")";
        }
    }
}
