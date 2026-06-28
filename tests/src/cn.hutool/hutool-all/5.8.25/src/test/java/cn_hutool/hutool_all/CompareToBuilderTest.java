/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package cn_hutool.hutool_all;

import cn.hutool.core.builder.CompareToBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CompareToBuilderTest {
    @Test
    void reflectionCompareReadsDeclaredPrivateFields() {
        ComparableSample low = new ComparableSample("alpha", 1);
        ComparableSample high = new ComparableSample("alpha", 2);
        ComparableSample matching = new ComparableSample("alpha", 1);

        assertThat(CompareToBuilder.reflectionCompare(low, high)).isNegative();
        assertThat(CompareToBuilder.reflectionCompare(high, low)).isPositive();
        assertThat(CompareToBuilder.reflectionCompare(low, matching)).isZero();
    }

    @Test
    void reflectionCompareHonorsExcludedFields() {
        ComparableSample left = new ComparableSample("left", 1);
        ComparableSample right = new ComparableSample("right", 1);

        assertThat(CompareToBuilder.reflectionCompare(left, right)).isNegative();
        assertThat(CompareToBuilder.reflectionCompare(left, right, "name")).isZero();
    }

    public static class ComparableSample {
        private final String name;
        private final int priority;

        public ComparableSample(String name, int priority) {
            this.name = name;
            this.priority = priority;
        }
    }
}
