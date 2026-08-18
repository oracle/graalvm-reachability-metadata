/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_core;

import java.util.Collections;

import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.usability.ArtifactNotFoundDiagnoser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ArtifactNotFoundDiagnoserTest {
    @Test
    void recognizesArtifactNotFoundFailuresInCausality() {
        ArtifactNotFoundDiagnoser diagnoser = new ArtifactNotFoundDiagnoser();
        ArtifactNotFoundException notFound = new ArtifactNotFoundException(
                "The artifact is unavailable",
                "example.group",
                "missing-artifact",
                "1.0",
                "jar",
                Collections.emptyList(),
                null,
                Collections.emptyList(),
                null);

        boolean canDiagnose = diagnoser.canDiagnose(new IllegalStateException("Resolution failed", notFound));

        assertThat(canDiagnose).isTrue();
    }

    @Test
    void ignoresUnrelatedFailures() {
        ArtifactNotFoundDiagnoser diagnoser = new ArtifactNotFoundDiagnoser();

        boolean canDiagnose = diagnoser.canDiagnose(new IllegalArgumentException("Not a resolution failure"));

        assertThat(canDiagnose).isFalse();
    }
}
