/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_jcip.jcip_annotations;

import java.lang.annotation.Annotation;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;

import net.jcip.annotations.GuardedBy;
import net.jcip.annotations.Immutable;
import net.jcip.annotations.NotThreadSafe;
import net.jcip.annotations.ThreadSafe;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class Jcip_annotationsTest {
    @Test
    @SuppressWarnings("annotationAccess")
    void markerAnnotationsDeclareRuntimeTypeLevelContracts() {
        assertMarkerAnnotation(ThreadSafe.class, "net.jcip.annotations.ThreadSafe");
        assertMarkerAnnotation(NotThreadSafe.class, "net.jcip.annotations.NotThreadSafe");
        assertMarkerAnnotation(Immutable.class, "net.jcip.annotations.Immutable");
    }

    @Test
    @SuppressWarnings("annotationAccess")
    void guardedByDeclaresRuntimeFieldAndMethodContract() throws NoSuchMethodException {
        Class<GuardedBy> annotationType = GuardedBy.class;
        Retention retention = annotationType.getAnnotation(Retention.class);
        Target target = annotationType.getAnnotation(Target.class);
        Method valueMember = annotationType.getDeclaredMethod("value");

        assertThat(annotationType.isAnnotation()).isTrue();
        assertThat(Annotation.class.isAssignableFrom(annotationType)).isTrue();
        assertThat(annotationType.getName()).isEqualTo("net.jcip.annotations.GuardedBy");
        assertThat(annotationType.getAnnotation(Documented.class)).isNull();
        assertThat(annotationType.getAnnotation(Inherited.class)).isNull();
        assertThat(retention).isNotNull();
        assertThat(retention.value()).isEqualTo(RetentionPolicy.RUNTIME);
        assertThat(target).isNotNull();
        assertThat(target.value()).containsExactly(ElementType.FIELD, ElementType.METHOD);
        assertThat(annotationType.getDeclaredFields()).isEmpty();
        assertThat(annotationType.getDeclaredMethods()).containsExactly(valueMember);
        assertThat(valueMember.getReturnType()).isEqualTo(String.class);
        assertThat(valueMember.getDefaultValue()).isNull();
    }

    @Test
    @SuppressWarnings("annotationAccess")
    void runtimeVisibleAnnotationInstancesRetainValuesAndStandardSemantics() throws NoSuchFieldException, NoSuchMethodException {
        ThreadSafe threadSafeOnBase = ThreadSafeBase.class.getAnnotation(ThreadSafe.class);
        ThreadSafe threadSafeOnOtherType = AnotherThreadSafeType.class.getAnnotation(ThreadSafe.class);
        NotThreadSafe notThreadSafe = MutableLedger.class.getAnnotation(NotThreadSafe.class);
        Immutable immutable = ImmutablePoint.class.getAnnotation(Immutable.class);
        GuardedBy fieldGuard = ThreadSafeCounter.class.getDeclaredField("value").getAnnotation(GuardedBy.class);
        GuardedBy methodGuard = ThreadSafeCounter.class.getDeclaredMethod("snapshotWhileHoldingLock")
                .getAnnotation(GuardedBy.class);
        GuardedBy thisGuard = ThreadSafeCounter.class.getDeclaredMethod("snapshotUsingThisMonitor")
                .getAnnotation(GuardedBy.class);

        assertThat(threadSafeOnBase).isNotNull();
        assertThat(threadSafeOnOtherType).isEqualTo(threadSafeOnBase);
        assertThat(threadSafeOnOtherType.hashCode()).isEqualTo(threadSafeOnBase.hashCode());
        assertThat(threadSafeOnBase.annotationType()).isSameAs(ThreadSafe.class);
        assertThat(threadSafeOnBase.toString()).contains("net.jcip.annotations.ThreadSafe");

        assertThat(notThreadSafe).isNotNull();
        assertThat(notThreadSafe.annotationType()).isSameAs(NotThreadSafe.class);
        assertThat(immutable).isNotNull();
        assertThat(immutable.annotationType()).isSameAs(Immutable.class);

        assertThat(fieldGuard).isNotNull();
        assertThat(fieldGuard.value()).isEqualTo("lock");
        assertThat(fieldGuard.annotationType()).isSameAs(GuardedBy.class);
        assertThat(methodGuard).isEqualTo(fieldGuard);
        assertThat(methodGuard.hashCode()).isEqualTo(fieldGuard.hashCode());
        assertThat(thisGuard).isNotEqualTo(fieldGuard);
        assertThat(thisGuard.value()).isEqualTo("this");
        assertThat(thisGuard.toString()).contains("GuardedBy").contains("this");

        assertThat(DerivedFromThreadSafeBase.class.getAnnotation(ThreadSafe.class)).isNull();
    }

    @Test
    void annotatedTypesExecuteNormally() {
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
    @SuppressWarnings("annotationAccess")
    void markerAnnotationsApplyToInterfacesEnumsAndAnnotationTypes() {
        ThreadSafe threadSafeInterface = ConcurrencyContract.class.getAnnotation(ThreadSafe.class);
        NotThreadSafe notThreadSafeEnum = LifecycleMode.class.getAnnotation(NotThreadSafe.class);
        Immutable immutableAnnotationType = SnapshotState.class.getAnnotation(Immutable.class);

        assertThat(ConcurrencyContract.class.isInterface()).isTrue();
        assertThat(LifecycleMode.class.isEnum()).isTrue();
        assertThat(SnapshotState.class.isAnnotation()).isTrue();

        assertThat(threadSafeInterface).isNotNull();
        assertThat(threadSafeInterface.annotationType()).isSameAs(ThreadSafe.class);
        assertThat(notThreadSafeEnum).isNotNull();
        assertThat(notThreadSafeEnum.annotationType()).isSameAs(NotThreadSafe.class);
        assertThat(immutableAnnotationType).isNotNull();
        assertThat(immutableAnnotationType.annotationType()).isSameAs(Immutable.class);
    }

    @Test
    @SuppressWarnings("annotationAccess")
    void immutableAnnotationAppliesToRecordTypes() {
        ImmutableCoordinates coordinates = new ImmutableCoordinates(4, -1);
        ImmutableCoordinates translated = coordinates.translate(3, 2);
        Immutable immutableRecord = ImmutableCoordinates.class.getAnnotation(Immutable.class);

        assertThat(ImmutableCoordinates.class.isRecord()).isTrue();
        assertThat(immutableRecord).isNotNull();
        assertThat(immutableRecord.annotationType()).isSameAs(Immutable.class);

        assertThat(coordinates.x()).isEqualTo(4);
        assertThat(coordinates.y()).isEqualTo(-1);
        assertThat(coordinates.asText()).isEqualTo("(4,-1)");
        assertThat(translated).isEqualTo(new ImmutableCoordinates(7, 1));
        assertThat(translated.asText()).isEqualTo("(7,1)");
        assertThat(coordinates.manhattanDistance()).isEqualTo(5);
        assertThat(translated.manhattanDistance()).isEqualTo(8);
    }

    @SuppressWarnings("annotationAccess")
    private static void assertMarkerAnnotation(Class<? extends Annotation> annotationType, String expectedName) {
        Retention retention = annotationType.getAnnotation(Retention.class);
        Target target = annotationType.getAnnotation(Target.class);

        assertThat(annotationType.isAnnotation()).isTrue();
        assertThat(Annotation.class.isAssignableFrom(annotationType)).isTrue();
        assertThat(annotationType.getName()).isEqualTo(expectedName);
        assertThat(annotationType.getAnnotation(Documented.class)).isNotNull();
        assertThat(annotationType.getAnnotation(Inherited.class)).isNull();
        assertThat(retention).isNotNull();
        assertThat(retention.value()).isEqualTo(RetentionPolicy.RUNTIME);
        assertThat(target).isNotNull();
        assertThat(target.value()).containsExactly(ElementType.TYPE);
        assertThat(annotationType.getDeclaredMethods()).isEmpty();
        assertThat(annotationType.getDeclaredFields()).isEmpty();
    }

    @ThreadSafe
    private static class ThreadSafeBase {
    }

    private static final class DerivedFromThreadSafeBase extends ThreadSafeBase {
    }

    @ThreadSafe
    private static final class AnotherThreadSafeType {
    }

    @ThreadSafe
    private interface ConcurrencyContract {
    }

    @NotThreadSafe
    private enum LifecycleMode {
        SINGLE_WRITER,
        BEST_EFFORT
    }

    @Immutable
    private @interface SnapshotState {
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
