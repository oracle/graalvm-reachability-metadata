/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hamcrest.hamcrest;

import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ReflectiveTypeFinderTest {

    @Test
    void defaultTypeSafeMatcherInfersItsExpectedTypeFromMatchesSafely() {
        TypeSafeMatcher<SampleBean> matcher = new TypeSafeMatcher<>() {
            @Override
            protected boolean matchesSafely(SampleBean item) {
                return item.label.startsWith("ham");
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("a bean whose label starts with ham");
            }
        };

        assertThat(matcher.matches(new SampleBean("hamcrest"))).isTrue();
        assertThat(matcher.matches("hamcrest")).isFalse();
    }

    public static final class SampleBean {
        private final String label;

        public SampleBean(String label) {
            this.label = label;
        }
    }
}
