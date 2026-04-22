/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_osgi.org_osgi_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.osgi.framework.Filter;
import org.osgi.framework.FrameworkUtil;

public class FrameworkUtil_FilterImplTest {
    @Test
    void filtersConstructComparableAndUnknownTypesFromStringOperands() throws Exception {
        Filter comparableFilter = FrameworkUtil.createFilter("(comparable>=beta)");
        Filter unknownFilter = FrameworkUtil.createFilter("(unknown=gamma)");

        Map<String, Object> matchingProperties = Map.of(
                "comparable", new ComparableStringValue("delta"),
                "unknown", new UnknownValue("gamma"));
        Map<String, Object> failingProperties = Map.of(
                "comparable", new ComparableStringValue("alpha"),
                "unknown", new UnknownValue("other"));

        assertThat(comparableFilter.matches(matchingProperties)).isTrue();
        assertThat(comparableFilter.matches(failingProperties)).isFalse();
        assertThat(unknownFilter.matches(matchingProperties)).isTrue();
        assertThat(unknownFilter.matches(failingProperties)).isFalse();
    }

    public static final class ComparableStringValue implements Comparable<Object> {
        private final String value;

        public ComparableStringValue(String value) {
            this.value = value;
        }

        @Override
        public int compareTo(Object other) {
            return value.compareTo(((ComparableStringValue) other).value);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof ComparableStringValue that)) {
                return false;
            }
            return value.equals(that.value);
        }

        @Override
        public int hashCode() {
            return value.hashCode();
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
            if (!(other instanceof UnknownValue that)) {
                return false;
            }
            return value.equals(that.value);
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }
    }
}
