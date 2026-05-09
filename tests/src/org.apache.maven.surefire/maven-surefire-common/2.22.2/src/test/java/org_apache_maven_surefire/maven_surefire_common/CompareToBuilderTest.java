/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_surefire.maven_surefire_common;

import org.apache.maven.surefire.shade.org.apache.commons.lang3.builder.CompareToBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CompareToBuilderTest {
    @Test
    void reflectionCompareReadsPrivateFieldsDeclaredOnTheComparedClass() {
        ReleaseCandidate earlier = new ReleaseCandidate("surefire", 1, "cached-alpha");
        ReleaseCandidate later = new ReleaseCandidate("surefire", 2, "cached-alpha");

        int comparison = CompareToBuilder.reflectionCompare(earlier, later);

        assertThat(comparison).isLessThan(0);
    }

    @Test
    void reflectionCompareCanIncludeTransientFieldsWhenRequested() {
        ReleaseCandidate first = new ReleaseCandidate("surefire", 1, "cached-alpha");
        ReleaseCandidate second = new ReleaseCandidate("surefire", 1, "cached-beta");

        int defaultComparison = CompareToBuilder.reflectionCompare(first, second);
        int transientComparison = CompareToBuilder.reflectionCompare(first, second, true);

        assertThat(defaultComparison).isZero();
        assertThat(transientComparison).isLessThan(0);
    }

    private static final class ReleaseCandidate {
        private final String artifactId;
        private final int buildNumber;
        private transient String cachedLabel;

        private ReleaseCandidate(String artifactId, int buildNumber, String cachedLabel) {
            this.artifactId = artifactId;
            this.buildNumber = buildNumber;
            this.cachedLabel = cachedLabel;
        }
    }
}
