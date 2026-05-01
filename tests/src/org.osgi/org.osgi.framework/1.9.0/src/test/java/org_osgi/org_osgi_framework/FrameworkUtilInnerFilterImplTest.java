/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_osgi.org_osgi_framework;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.osgi.framework.Filter;
import org.osgi.framework.FrameworkUtil;

public class FrameworkUtilInnerFilterImplTest {
    @Test
    void matchesComparableAttributeUsingStaticValueOf() throws Exception {
        ValueOfComparable.reset();
        Filter filter = FrameworkUtil.createFilter("(priority>=100)");
        Map<String, Object> attributes = Map.of("priority", new ValueOfComparable(150));

        assertThat(filter.matches(attributes)).isTrue();
        assertThat(ValueOfComparable.valueOfCalls()).isEqualTo(1);
    }

    @Test
    void matchesComparableAttributeUsingStringConstructor() throws Exception {
        ConstructorComparable.reset();
        Filter filter = FrameworkUtil.createFilter("(threshold<=300)");
        Map<String, Object> attributes = Map.of("threshold", new ConstructorComparable(200));

        assertThat(filter.matches(attributes)).isTrue();
        assertThat(ConstructorComparable.constructorCalls()).isEqualTo(1);
    }

    public static final class ValueOfComparable implements Comparable<Object> {
        private static final AtomicInteger VALUE_OF_CALLS = new AtomicInteger();

        private final int value;

        public ValueOfComparable(int value) {
            this.value = value;
        }

        public static ValueOfComparable valueOf(String value) {
            VALUE_OF_CALLS.incrementAndGet();
            return new ValueOfComparable(Integer.parseInt(value));
        }

        static void reset() {
            VALUE_OF_CALLS.set(0);
        }

        static int valueOfCalls() {
            return VALUE_OF_CALLS.get();
        }

        @Override
        public int compareTo(Object other) {
            ValueOfComparable otherValue = (ValueOfComparable) other;
            return Integer.compare(value, otherValue.value);
        }
    }

    public static final class ConstructorComparable implements Comparable<Object> {
        private static final AtomicInteger CONSTRUCTOR_CALLS = new AtomicInteger();

        private final int value;

        public ConstructorComparable(int value) {
            this.value = value;
        }

        public ConstructorComparable(String value) {
            CONSTRUCTOR_CALLS.incrementAndGet();
            this.value = Integer.parseInt(value);
        }

        static void reset() {
            CONSTRUCTOR_CALLS.set(0);
        }

        static int constructorCalls() {
            return CONSTRUCTOR_CALLS.get();
        }

        @Override
        public int compareTo(Object other) {
            ConstructorComparable otherValue = (ConstructorComparable) other;
            return Integer.compare(value, otherValue.value);
        }
    }
}
