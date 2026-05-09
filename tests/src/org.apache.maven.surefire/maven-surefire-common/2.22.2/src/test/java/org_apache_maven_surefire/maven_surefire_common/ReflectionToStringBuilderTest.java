/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_surefire.maven_surefire_common;

import org.apache.maven.surefire.shade.org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.maven.surefire.shade.org.apache.commons.lang3.builder.ToStringStyle;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ReflectionToStringBuilderTest {
    @Test
    void reflectionToStringReadsPrivateFieldsDeclaredOnTheRenderedObject() {
        ArtifactExecution execution = new ArtifactExecution("maven-surefire-common", 3, "warm-cache");

        String rendered = ReflectionToStringBuilder.toString(execution, ToStringStyle.SHORT_PREFIX_STYLE);

        assertThat(rendered)
                .contains("artifactId=maven-surefire-common")
                .contains("forkCount=3")
                .doesNotContain("warm-cache");
    }

    @Test
    void reflectionToStringCanIncludeTransientFieldsWhenRequested() {
        ArtifactExecution execution = new ArtifactExecution("maven-surefire-common", 3, "warm-cache");
        ReflectionToStringBuilder builder = new ReflectionToStringBuilder(execution, ToStringStyle.SHORT_PREFIX_STYLE);

        builder.setAppendTransients(true);

        assertThat(builder.toString()).contains("cachedStatus=warm-cache");
    }

    private static final class ArtifactExecution {
        private final String artifactId;
        private final int forkCount;
        private transient String cachedStatus;

        private ArtifactExecution(String artifactId, int forkCount, String cachedStatus) {
            this.artifactId = artifactId;
            this.forkCount = forkCount;
            this.cachedStatus = cachedStatus;
        }
    }
}
