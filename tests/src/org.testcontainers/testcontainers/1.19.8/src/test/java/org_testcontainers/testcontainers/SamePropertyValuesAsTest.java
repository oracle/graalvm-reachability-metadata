/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.org.hamcrest.Matcher;
import org.testcontainers.shaded.org.hamcrest.beans.SamePropertyValuesAs;

import static org.assertj.core.api.Assertions.assertThat;

public class SamePropertyValuesAsTest {
    @Test
    void comparesValuesReadFromBeanProperties() {
        Matcher<Bean> matcher = SamePropertyValuesAs.samePropertyValuesAs(new Bean("same", 4));

        assertThat(matcher.matches(new Bean("same", 4))).isTrue();
        assertThat(matcher.matches(new Bean("different", 4))).isFalse();
    }

    public static class Bean {
        private final String text;
        private final int count;

        public Bean(String text, int count) {
            this.text = text;
            this.count = count;
        }

        public String getText() {
            return text;
        }

        public int getCount() {
            return count;
        }
    }
}
