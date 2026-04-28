/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_stephenc_jcip.jcip_annotations;

import java.lang.annotation.Annotation;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import net.jcip.annotations.GuardedBy;
import net.jcip.annotations.Immutable;
import net.jcip.annotations.NotThreadSafe;
import net.jcip.annotations.ThreadSafe;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class Jcip_annotationsTest {
    @Test
    void annotationInterfacesExposeTheirPublicContracts() {
        ThreadSafe threadSafe = new ThreadSafeLiteral();
        NotThreadSafe notThreadSafe = new NotThreadSafeLiteral();
        Immutable immutable = new ImmutableLiteral();
        GuardedBy guardedByLock = new GuardedByLiteral("lock");
        GuardedBy guardedByThis = new GuardedByLiteral("this");

        assertThat(threadSafe.annotationType()).isSameAs(ThreadSafe.class);
        assertThat(notThreadSafe.annotationType()).isSameAs(NotThreadSafe.class);
        assertThat(immutable.annotationType()).isSameAs(Immutable.class);
        assertThat(guardedByLock.annotationType()).isSameAs(GuardedBy.class);
        assertThat(guardedByLock.value()).isEqualTo("lock");
        assertThat(guardedByThis.value()).isEqualTo("this");
    }

    @Test
    void annotationLiteralsFollowAnnotationEqualityAndHashCodeRules() {
        ThreadSafe firstThreadSafe = new ThreadSafeLiteral();
        ThreadSafe secondThreadSafe = new ThreadSafeLiteral();
        NotThreadSafe notThreadSafe = new NotThreadSafeLiteral();
        GuardedBy firstLockGuard = new GuardedByLiteral("lock");
        GuardedBy secondLockGuard = new GuardedByLiteral("lock");
        GuardedBy thisGuard = new GuardedByLiteral("this");

        assertThat(firstThreadSafe).isEqualTo(secondThreadSafe);
        assertThat(firstThreadSafe).hasSameHashCodeAs(secondThreadSafe);
        assertThat(firstThreadSafe).isNotEqualTo(notThreadSafe);

        assertThat(firstLockGuard).isEqualTo(secondLockGuard);
        assertThat(firstLockGuard).hasSameHashCodeAs(secondLockGuard);
        assertThat(firstLockGuard).isNotEqualTo(thisGuard);
        assertThat(firstLockGuard).isNotEqualTo(firstThreadSafe);
    }

    @Test
    void guardedByValuesCanRepresentCommonLockExpressions() {
        Set<GuardedBy> guards = new HashSet<>();

        guards.add(new GuardedByLiteral("lock"));
        guards.add(new GuardedByLiteral("this"));
        guards.add(new GuardedByLiteral("ThreadSafeCounter.class"));
        guards.add(new GuardedByLiteral("owner.monitor"));
        guards.add(new GuardedByLiteral("lock"));

        assertThat(guards).hasSize(4);
        assertThat(guards).extracting(GuardedBy::value)
                .containsExactlyInAnyOrder("lock", "this", "ThreadSafeCounter.class", "owner.monitor");
    }

    @Test
    void annotationsCanDocumentExecutableConcurrencyExamples() {
        ThreadSafeCounter counter = new ThreadSafeCounter();
        MutableLedger ledger = new MutableLedger();
        ImmutablePoint origin = new ImmutablePoint(2, 3);
        ImmutablePoint translated = origin.translate(-1, 4);

        assertThat(counter.incrementAndGet()).isEqualTo(1);
        assertThat(counter.incrementAndGet()).isEqualTo(2);
        assertThat(counter.current()).isEqualTo(2);
        assertThat(counter.describeState()).isEqualTo("increment:2");
        assertThat(counter.guardedSnapshot()).isEqualTo(2);

        ledger.add("alpha");
        ledger.add("beta");
        assertThat(ledger.snapshot()).isEqualTo("alpha|beta");
        assertThat(ledger.entryCount()).isEqualTo(2);

        assertThat(origin.asText()).isEqualTo("(2,3)");
        assertThat(translated.asText()).isEqualTo("(1,7)");
        assertThat(origin.manhattanDistance()).isEqualTo(5);
        assertThat(translated.manhattanDistance()).isEqualTo(8);
    }

    @Test
    void markerAnnotationsCanBeAppliedToDifferentTypeKinds() {
        ImmutableCoordinates coordinates = new ImmutableCoordinates(4, -1);
        ImmutableCoordinates translated = coordinates.translate(3, 2);
        LifecycleMode mode = LifecycleMode.SINGLE_WRITER;
        ConcurrencyContract contract = value -> "state=" + value;
        SnapshotState snapshotState = new SnapshotStateLiteral("stable");

        assertThat(contract.describe(7)).isEqualTo("state=7");
        assertThat(mode.allowsConcurrentWriters()).isFalse();
        assertThat(snapshotState.value()).isEqualTo("stable");

        assertThat(coordinates.x()).isEqualTo(4);
        assertThat(coordinates.y()).isEqualTo(-1);
        assertThat(coordinates.asText()).isEqualTo("(4,-1)");
        assertThat(translated).isEqualTo(new ImmutableCoordinates(7, 1));
        assertThat(translated.asText()).isEqualTo("(7,1)");
        assertThat(coordinates.manhattanDistance()).isEqualTo(5);
        assertThat(translated.manhattanDistance()).isEqualTo(8);
    }

    private abstract static class AnnotationLiteral implements Annotation {
        @Override
        public final String toString() {
            return "@" + annotationType().getName() + membersAsText();
        }

        String membersAsText() {
            return "";
        }
    }

    private static final class ThreadSafeLiteral extends AnnotationLiteral implements ThreadSafe {
        @Override
        public Class<? extends Annotation> annotationType() {
            return ThreadSafe.class;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof ThreadSafe;
        }

        @Override
        public int hashCode() {
            return 0;
        }
    }

    private static final class NotThreadSafeLiteral extends AnnotationLiteral implements NotThreadSafe {
        @Override
        public Class<? extends Annotation> annotationType() {
            return NotThreadSafe.class;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof NotThreadSafe;
        }

        @Override
        public int hashCode() {
            return 0;
        }
    }

    private static final class ImmutableLiteral extends AnnotationLiteral implements Immutable {
        @Override
        public Class<? extends Annotation> annotationType() {
            return Immutable.class;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Immutable;
        }

        @Override
        public int hashCode() {
            return 0;
        }
    }

    private static final class GuardedByLiteral extends AnnotationLiteral implements GuardedBy {
        private final String value;

        private GuardedByLiteral(String value) {
            this.value = Objects.requireNonNull(value);
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
            return other instanceof GuardedBy guardedBy && value.equals(guardedBy.value());
        }

        @Override
        public int hashCode() {
            return (127 * "value".hashCode()) ^ value.hashCode();
        }

        @Override
        String membersAsText() {
            return "(value=" + value + ")";
        }
    }

    private static final class SnapshotStateLiteral extends AnnotationLiteral implements SnapshotState {
        private final String value;

        private SnapshotStateLiteral(String value) {
            this.value = Objects.requireNonNull(value);
        }

        @Override
        public String value() {
            return value;
        }

        @Override
        public Class<? extends Annotation> annotationType() {
            return SnapshotState.class;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof SnapshotState snapshotState && value.equals(snapshotState.value());
        }

        @Override
        public int hashCode() {
            return (127 * "value".hashCode()) ^ value.hashCode();
        }

        @Override
        String membersAsText() {
            return "(value=" + value + ")";
        }
    }

    @ThreadSafe
    private interface ConcurrencyContract {
        String describe(int value);
    }

    @NotThreadSafe
    private enum LifecycleMode {
        SINGLE_WRITER,
        BEST_EFFORT;

        boolean allowsConcurrentWriters() {
            return false;
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

        private int manhattanDistance() {
            return Math.abs(x) + Math.abs(y);
        }

        private String asText() {
            return "(" + x + "," + y + ")";
        }
    }

    @ThreadSafe
    private static final class ThreadSafeCounter {
        private final Object lock = new Object();

        @GuardedBy("lock")
        private int value;

        @GuardedBy("lock")
        private int snapshotWhileHoldingLock() {
            synchronized (lock) {
                return value;
            }
        }

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

        int guardedSnapshot() {
            return snapshotWhileHoldingLock();
        }

        @GuardedBy("this")
        private synchronized String snapshotUsingThisMonitor() {
            return "increment:" + value;
        }

        String describeState() {
            return snapshotUsingThisMonitor();
        }
    }

    @NotThreadSafe
    private static final class MutableLedger {
        private final StringBuilder entries = new StringBuilder();
        private int entryCount;

        void add(String entry) {
            if (entries.length() > 0) {
                entries.append('|');
            }
            entries.append(entry);
            entryCount++;
        }

        String snapshot() {
            return entries.toString();
        }

        int entryCount() {
            return entryCount;
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
}
