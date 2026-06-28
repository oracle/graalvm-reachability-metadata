/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package cn_hutool.hutool_all;

import cn.hutool.core.builder.EqualsBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class EqualsBuilderTest {
    @Test
    void reflectionEqualsReadsDeclaredPrivateFields() {
        ValueObject left = new ValueObject("alpha", 7);
        ValueObject matching = new ValueObject("alpha", 7);
        ValueObject different = new ValueObject("alpha", 8);

        assertThat(EqualsBuilder.reflectionEquals(left, matching)).isTrue();
        assertThat(EqualsBuilder.reflectionEquals(left, different)).isFalse();
    }

    @Test
    void reflectionEqualsHonorsExcludedFields() {
        ValueObject left = new ValueObject("alpha", 7);
        ValueObject sameName = new ValueObject("alpha", 8);
        ValueObject differentName = new ValueObject("beta", 7);

        assertThat(EqualsBuilder.reflectionEquals(left, sameName, "priority")).isTrue();
        assertThat(EqualsBuilder.reflectionEquals(left, differentName, "priority")).isFalse();
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
