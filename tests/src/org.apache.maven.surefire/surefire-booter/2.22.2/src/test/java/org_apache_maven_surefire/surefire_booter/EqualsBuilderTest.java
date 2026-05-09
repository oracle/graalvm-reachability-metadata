/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_surefire.surefire_booter;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.maven.surefire.shade.org.apache.commons.lang3.builder.EqualsBuilder;
import org.junit.jupiter.api.Test;

public class EqualsBuilderTest {

    @Test
    public void reflectionEqualsComparesPrivateDeclaredFields() {
        SampleValue left = new SampleValue("alpha", 7);
        SampleValue right = new SampleValue("alpha", 7);

        boolean result = EqualsBuilder.reflectionEquals(left, right);

        assertThat(result).isTrue();
    }

    @Test
    public void reflectionEqualsDetectsDifferentPrivateDeclaredFieldValues() {
        SampleValue left = new SampleValue("alpha", 7);
        SampleValue right = new SampleValue("alpha", 8);

        boolean result = EqualsBuilder.reflectionEquals(left, right);

        assertThat(result).isFalse();
    }

    private static final class SampleValue {
        private final String name;
        private final int priority;

        private SampleValue(String name, int priority) {
            this.name = name;
            this.priority = priority;
        }
    }
}
