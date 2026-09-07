/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.org.hamcrest.Matcher;
import org.testcontainers.shaded.org.hamcrest.beans.HasPropertyWithValue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.testcontainers.shaded.org.hamcrest.Matchers.equalTo;

public class HasPropertyWithValueAnonymous1Test {
    @Test
    void readsAPropertyThroughItsBeanAccessor() {
        Matcher<Bean> matcher = HasPropertyWithValue.hasProperty("value", equalTo("matched"));

        assertThat(matcher.matches(new Bean("matched"))).isTrue();
    }

    public static class Bean {
        private final String value;

        public Bean(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }
}
