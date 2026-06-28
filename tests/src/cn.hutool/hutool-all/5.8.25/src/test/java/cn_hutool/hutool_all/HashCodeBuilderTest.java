/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package cn_hutool.hutool_all;

import cn.hutool.core.builder.HashCodeBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class HashCodeBuilderTest {
    @Test
    void reflectionHashCodeReadsDeclaredPrivateFields() {
        ValueObject left = new ValueObject("alpha", 7);
        ValueObject matching = new ValueObject("alpha", 7);
        ValueObject different = new ValueObject("alpha", 8);

        assertThat(HashCodeBuilder.reflectionHashCode(left))
                .isEqualTo(HashCodeBuilder.reflectionHashCode(matching));
        assertThat(HashCodeBuilder.reflectionHashCode(left))
                .isNotEqualTo(HashCodeBuilder.reflectionHashCode(different));
    }

    @Test
    void reflectionHashCodeHonorsExcludedFields() {
        ValueObject left = new ValueObject("alpha", 7);
        ValueObject sameName = new ValueObject("alpha", 8);
        ValueObject differentName = new ValueObject("beta", 7);

        assertThat(HashCodeBuilder.reflectionHashCode(left, "priority"))
                .isEqualTo(HashCodeBuilder.reflectionHashCode(sameName, "priority"));
        assertThat(HashCodeBuilder.reflectionHashCode(left, "priority"))
                .isNotEqualTo(HashCodeBuilder.reflectionHashCode(differentName, "priority"));
    }

    public static class ValueObject {
        private final String name;
        private final int priority;

        public ValueObject(String name, int priority) {
            this.name = name;
            this.priority = priority;
        }
    }
}
