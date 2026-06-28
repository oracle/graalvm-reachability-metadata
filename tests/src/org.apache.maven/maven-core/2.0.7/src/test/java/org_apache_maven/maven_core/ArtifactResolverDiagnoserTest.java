/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_core;

import java.io.IOException;
import java.util.Collections;

import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.usability.ArtifactResolverDiagnoser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ArtifactResolverDiagnoserTest {
    @Test
    void recognizesArtifactResolutionFailuresInCausality() {
        ArtifactResolverDiagnoser diagnoser = new ArtifactResolverDiagnoser();
        ArtifactResolutionException resolutionFailure = new ArtifactResolutionException(
                "Could not transfer artifact from repository",
                "example.group",
                "demo-artifact",
                "1.0",
                "jar",
                Collections.emptyList(),
                Collections.emptyList(),
                new IOException("Repository is unreachable"));

        boolean canDiagnose = diagnoser.canDiagnose(new IllegalStateException("Build failed", resolutionFailure));

        assertThat(canDiagnose).isTrue();
    }

    @Test
    void ignoresUnrelatedFailures() {
        ArtifactResolverDiagnoser diagnoser = new ArtifactResolverDiagnoser();

        boolean canDiagnose = diagnoser.canDiagnose(new IllegalArgumentException("Not a resolution failure"));

        assertThat(canDiagnose).isFalse();
    }
}
