/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;

import static org.assertj.core.api.Assertions.assertThat;

import io.sundr.deps.org.apache.commons.lang.builder.CompareToBuilder;
import org.junit.jupiter.api.Test;

public class CompareToBuilderTest {

    @Test
    public void reflectionCompareOrdersObjectsByPrivateFieldValues() {
        ComparableRecord first = new ComparableRecord("alpha", 3);
        ComparableRecord second = new ComparableRecord("bravo", 3);
        ComparableRecord sameAsFirst = new ComparableRecord("alpha", 3);

        int lowerComparison = CompareToBuilder.reflectionCompare(first, second);
        int higherComparison = CompareToBuilder.reflectionCompare(second, first);
        int equalComparison = CompareToBuilder.reflectionCompare(first, sameAsFirst);

        assertThat(lowerComparison).isNegative();
        assertThat(higherComparison).isPositive();
        assertThat(equalComparison).isZero();
    }

    private static final class ComparableRecord {
        private final String name;
        private final int priority;

        private ComparableRecord(String name, int priority) {
            this.name = name;
            this.priority = priority;
        }
    }
}
