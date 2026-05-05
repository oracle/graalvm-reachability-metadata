/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_osgi.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.osgi.framework.Filter;
import org.osgi.framework.FrameworkUtil;

public class FrameworkUtilInnerFilterImplTest {
    @Test
    void comparablePropertyIsReconstructedFromFilterValue() throws Exception {
        Filter filter = FrameworkUtil.createFilter("(ranking>=5)");
        Map<String, Object> properties = new HashMap<>();
        properties.put("ranking", new ComparableRanking("7"));

        assertThat(filter.matches(properties)).isTrue();
    }

    @Test
    void unknownPropertyTypeIsReconstructedFromFilterValue() throws Exception {
        Filter filter = FrameworkUtil.createFilter("(token=ready)");
        Map<String, Object> properties = new HashMap<>();
        properties.put("token", new EqualityToken("ready"));

        assertThat(filter.matches(properties)).isTrue();
    }

    public static final class ComparableRanking implements Comparable<ComparableRanking> {
        private final int value;

        public ComparableRanking(String value) {
            this.value = Integer.parseInt(value.trim());
        }

        @Override
        public int compareTo(ComparableRanking other) {
            return Integer.compare(value, other.value);
        }
    }

    public static final class EqualityToken {
        private final String value;

        public EqualityToken(String value) {
            this.value = value.trim();
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof EqualityToken)) {
                return false;
            }
            EqualityToken that = (EqualityToken) other;
            return value.equals(that.value);
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }
    }
}
