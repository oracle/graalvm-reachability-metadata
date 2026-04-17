/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_osgi.org_osgi_core;

import java.util.Map;

import org.junit.jupiter.api.Test;

import org.osgi.framework.Filter;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;

import static org.assertj.core.api.Assertions.assertThat;

public class FrameworkUtil_FilterImplTest {
    @Test
    void matchesComparablePropertyUsingStringConstructor() throws InvalidSyntaxException {
        Filter filter = FrameworkUtil.createFilter("(comparable=alpha)");
        Map<String, Object> properties = Map.of("comparable", new ComparableValue("alpha"));

        assertThat(filter.matches(properties)).isTrue();
    }

    @Test
    void matchesUnknownPropertyUsingStringConstructor() throws InvalidSyntaxException {
        Filter filter = FrameworkUtil.createFilter("(unknown=value)");
        Map<String, Object> properties = Map.of("unknown", new UnknownValue("value"));

        assertThat(filter.matches(properties)).isTrue();
    }

    public static final class ComparableValue implements Comparable<ComparableValue> {
        private final String value;

        public ComparableValue(String value) {
            this.value = value;
        }

        @Override
        public int compareTo(ComparableValue other) {
            return value.compareTo(other.value);
        }
    }

    public static final class UnknownValue {
        private final String value;

        public UnknownValue(String value) {
            this.value = value;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof UnknownValue)) {
                return false;
            }
            UnknownValue unknownValue = (UnknownValue) other;
            return value.equals(unknownValue.value);
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }
    }
}
