/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_android_tools.annotations;

import com.android.annotations.NonNull;
import com.android.annotations.NonNullByDefault;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.annotations.VisibleForTesting.Visibility;
import com.android.annotations.concurrency.GuardedBy;
import com.android.annotations.concurrency.Immutable;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AnnotationsTest {
    @Test
    void nonNullAndNullableCanDocumentFieldsMethodsParametersAndLocalVariables() {
        NullabilityFixture fixture = new NullabilityFixture("android-tools");

        assertEquals("ANDROID-TOOLS", fixture.uppercaseName());
        assertEquals("android-tools!", fixture.appendSuffix("!"));
        assertNull(fixture.optionalName(false));
        assertEquals("android-tools", fixture.optionalName(true));
    }

    @Test
    void nonNullByDefaultCanDocumentTypeLevelNullnessContracts() {
        DefaultNonNullFixture fixture = new DefaultNonNullFixture("annotations");

        assertEquals("annotations", fixture.value());
        assertEquals("annotations-tested", fixture.join("tested"));
    }

    @Test
    void visibleForTestingVisibilityEnumHasStableOrderAndLookup() {
        assertArrayEquals(new Visibility[] {Visibility.PROTECTED, Visibility.PACKAGE, Visibility.PRIVATE}, Visibility.values());
        assertSame(Visibility.PROTECTED, Visibility.valueOf("PROTECTED"));
        assertSame(Visibility.PACKAGE, Visibility.valueOf("PACKAGE"));
        assertSame(Visibility.PRIVATE, Visibility.valueOf("PRIVATE"));
        assertEquals(0, Visibility.PROTECTED.ordinal());
        assertEquals(1, Visibility.PACKAGE.ordinal());
        assertEquals(2, Visibility.PRIVATE.ordinal());
    }

    @Test
    void visibleForTestingAnnotationsCanUseEachVisibilityConstant() {
        VisibilityFixture fixture = new VisibilityFixture();

        assertEquals("protected helper", fixture.protectedHelper());
        assertEquals("package helper", fixture.packageHelper());
        assertEquals("private helper", fixture.privateHelper());
        assertEquals("default helper", fixture.defaultHelper());
    }

    @Test
    void visibleForTestingCanDocumentTypesConstructorsAndFields() {
        TestOnlyNameTokenizer tokenizer = new TestOnlyNameTokenizer("Ada Lovelace");

        assertArrayEquals(new String[] {"Ada", "Lovelace"}, tokenizer.tokens());
        assertEquals(' ', tokenizer.separator());
    }

    @Test
    void guardedByCanDocumentFieldAndMethodSynchronization() {
        SynchronizedCounter counter = new SynchronizedCounter();

        assertEquals(0, counter.value());
        assertEquals(1, counter.incrementAndGet());
        assertEquals(2, counter.incrementAndGet());
        assertEquals(2, counter.value());
    }

    @Test
    void guardedByCanDocumentIntrinsicInstanceAndClassLocks() {
        IntrinsicLockLedger.resetTotalDeposits();
        IntrinsicLockLedger ledger = new IntrinsicLockLedger();

        assertEquals(0, ledger.balance());
        assertEquals(5, ledger.deposit(5));
        assertEquals(3, ledger.withdraw(2));
        assertEquals(4, IntrinsicLockLedger.recordGlobalDeposit(4));
        assertEquals(10, IntrinsicLockLedger.recordGlobalDeposit(6));
        assertEquals(10, IntrinsicLockLedger.totalDeposits());
    }

    @Test
    void immutableCanDocumentStableValueObjects() {
        ImmutablePoint origin = new ImmutablePoint(0, 0);
        ImmutablePoint shifted = origin.translate(3, 4);

        assertEquals(0, origin.x());
        assertEquals(0, origin.y());
        assertEquals(3, shifted.x());
        assertEquals(4, shifted.y());
        assertFalse(origin.sameCoordinates(shifted));
        assertTrue(shifted.sameCoordinates(new ImmutablePoint(3, 4)));
    }

    private static final class NullabilityFixture {
        @NonNull
        private final String name;

        @Nullable
        private String cachedName;

        NullabilityFixture(@NonNull String name) {
            this.name = name;
        }

        @NonNull
        String uppercaseName() {
            @NonNull String uppercase = name.toUpperCase();
            return uppercase;
        }

        @NonNull
        String appendSuffix(@NonNull String suffix) {
            return name + suffix;
        }

        @Nullable
        String optionalName(boolean present) {
            if (present) {
                cachedName = name;
            } else {
                cachedName = null;
            }
            @Nullable String result = cachedName;
            return result;
        }
    }

    @NonNullByDefault
    private static final class DefaultNonNullFixture {
        private final String value;

        DefaultNonNullFixture(String value) {
            this.value = value;
        }

        String value() {
            return value;
        }

        String join(String suffix) {
            return value + "-" + suffix;
        }
    }

    private static final class VisibilityFixture {
        @VisibleForTesting(visibility = Visibility.PROTECTED)
        String protectedHelper() {
            return "protected helper";
        }

        @VisibleForTesting(visibility = Visibility.PACKAGE)
        String packageHelper() {
            return "package helper";
        }

        @VisibleForTesting(visibility = Visibility.PRIVATE)
        String privateHelper() {
            return "private helper";
        }

        @VisibleForTesting
        String defaultHelper() {
            return "default helper";
        }
    }

    @VisibleForTesting(visibility = Visibility.PACKAGE)
    private static final class TestOnlyNameTokenizer {
        private final String name;

        @VisibleForTesting(visibility = Visibility.PRIVATE)
        private final char separator;

        @VisibleForTesting(visibility = Visibility.PACKAGE)
        TestOnlyNameTokenizer(String name) {
            this.name = name;
            separator = ' ';
        }

        String[] tokens() {
            return name.split(String.valueOf(separator));
        }

        char separator() {
            return separator;
        }
    }

    private static final class SynchronizedCounter {
        private final Object lock = new Object();

        @GuardedBy("lock")
        private int count;

        @GuardedBy("lock")
        int incrementAndGet() {
            synchronized (lock) {
                count++;
                return count;
            }
        }

        int value() {
            synchronized (lock) {
                return count;
            }
        }
    }

    private static final class IntrinsicLockLedger {
        @GuardedBy("IntrinsicLockLedger.class")
        private static int totalDeposits;

        @GuardedBy("this")
        private int balance;

        synchronized int deposit(int amount) {
            balance += amount;
            return balance;
        }

        synchronized int withdraw(int amount) {
            balance -= amount;
            return balance;
        }

        synchronized int balance() {
            return balance;
        }

        static synchronized int recordGlobalDeposit(int amount) {
            totalDeposits += amount;
            return totalDeposits;
        }

        static synchronized int totalDeposits() {
            return totalDeposits;
        }

        static synchronized void resetTotalDeposits() {
            totalDeposits = 0;
        }
    }

    @Immutable
    private static final class ImmutablePoint {
        private final int x;
        private final int y;

        ImmutablePoint(int x, int y) {
            this.x = x;
            this.y = y;
        }

        int x() {
            return x;
        }

        int y() {
            return y;
        }

        ImmutablePoint translate(int deltaX, int deltaY) {
            return new ImmutablePoint(x + deltaX, y + deltaY);
        }

        boolean sameCoordinates(ImmutablePoint other) {
            return x == other.x && y == other.y;
        }
    }
}
