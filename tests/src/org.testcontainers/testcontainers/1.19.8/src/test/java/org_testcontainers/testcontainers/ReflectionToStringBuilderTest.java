/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.testcontainers.shaded.org.apache.commons.lang3.builder.ToStringStyle;

import static org.assertj.core.api.Assertions.assertThat;

public class ReflectionToStringBuilderTest {
    @Test
    void rendersObjectFieldsReflectively() {
        String rendered = ReflectionToStringBuilder.toString(
            new Value(7, "metadata"),
            ToStringStyle.SHORT_PREFIX_STYLE
        );

        assertThat(rendered).contains("number=7", "text=metadata");
    }

    public static class Value {
        private final int number;
        private final String text;

        public Value(int number, String text) {
            this.number = number;
            this.text = text;
        }
    }
}
