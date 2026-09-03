/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_surefire.surefire_booter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.apache.maven.surefire.booter.Classpath;
import org.apache.maven.surefire.shade.org.apache.commons.lang3.builder.EqualsBuilder;
import org.junit.jupiter.api.Test;

public class EqualsBuilderTest {

    @Test
    public void reflectionEqualsComparesDeclaredFieldValues() {
        Classpath first = new Classpath(Arrays.asList("target/classes", "target/test-classes"));
        Classpath matching = new Classpath(Arrays.asList("target/classes", "target/test-classes"));
        Classpath different = new Classpath(Arrays.asList("target/classes", "target/it-classes"));

        assertThat(EqualsBuilder.reflectionEquals(first, matching)).isTrue();
        assertThat(EqualsBuilder.reflectionEquals(first, different)).isFalse();
    }
}
