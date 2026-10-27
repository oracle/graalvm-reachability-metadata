/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_surefire.surefire_booter;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.maven.surefire.shade.org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.maven.surefire.shade.org.apache.commons.lang3.builder.ToStringStyle;
import org.junit.jupiter.api.Test;

public class ReflectionToStringBuilderTest {

    @Test
    public void reflectionToStringIncludesDeclaredFieldValues() {
        PrintableConfiguration configuration = new PrintableConfiguration("booter", 3, "internal");

        String description = ReflectionToStringBuilder.toString(configuration, ToStringStyle.SHORT_PREFIX_STYLE);

        assertThat(description)
                .startsWith("ReflectionToStringBuilderTest.PrintableConfiguration[")
                .contains("name=booter", "forkCount=3")
                .doesNotContain("transientNote")
                .endsWith("]");
    }

    private static final class PrintableConfiguration {
        private static final String DEFAULT_PROVIDER = "surefire";

        private final String name;
        private final int forkCount;
        private final transient String transientNote;

        private PrintableConfiguration(String name, int forkCount, String transientNote) {
            this.name = name;
            this.forkCount = forkCount;
            this.transientNote = transientNote;
        }
    }
}
