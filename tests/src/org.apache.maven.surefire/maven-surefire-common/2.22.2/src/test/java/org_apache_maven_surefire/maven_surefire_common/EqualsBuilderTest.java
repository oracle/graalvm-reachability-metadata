/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_surefire.maven_surefire_common;

import org.apache.maven.surefire.shade.org.apache.commons.lang3.builder.EqualsBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class EqualsBuilderTest {
    @Test
    void reflectionEqualsReadsPrivateFieldsDeclaredOnTheComparedClass() {
        ReleaseCandidate first = new ReleaseCandidate("surefire", 1, "cached-alpha");
        ReleaseCandidate sameValues = new ReleaseCandidate("surefire", 1, "cached-beta");
        ReleaseCandidate differentBuild = new ReleaseCandidate("surefire", 2, "cached-alpha");

        assertThat(EqualsBuilder.reflectionEquals(first, sameValues)).isTrue();
        assertThat(EqualsBuilder.reflectionEquals(first, differentBuild)).isFalse();
    }

    @Test
    void reflectionEqualsCanIncludeTransientFieldsWhenRequested() {
        ReleaseCandidate first = new ReleaseCandidate("surefire", 1, "cached-alpha");
        ReleaseCandidate second = new ReleaseCandidate("surefire", 1, "cached-beta");

        boolean defaultEquality = EqualsBuilder.reflectionEquals(first, second);
        boolean transientAwareEquality = EqualsBuilder.reflectionEquals(first, second, true);

        assertThat(defaultEquality).isTrue();
        assertThat(transientAwareEquality).isFalse();
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
