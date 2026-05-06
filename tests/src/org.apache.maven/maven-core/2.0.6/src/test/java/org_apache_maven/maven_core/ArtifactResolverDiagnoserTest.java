/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_core;

import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.usability.ArtifactResolverDiagnoser;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ArtifactResolverDiagnoserTest {
    @Test
    public void canDiagnoseFindsArtifactResolutionExceptionInCausality() {
        ArtifactResolverDiagnoser diagnoser = new ArtifactResolverDiagnoser();
        ArtifactResolutionException cause = new ArtifactResolutionException(
                "Could not resolve artifact",
                "com.example",
                "demo-artifact",
                "1.0.0",
                "jar",
                new IOException("Repository is unavailable"));

        boolean canDiagnose = diagnoser.canDiagnose(new IllegalStateException("Build failed", cause));

        assertTrue(canDiagnose);
    }

    @Test
    public void canDiagnoseRejectsUnrelatedErrors() {
        ArtifactResolverDiagnoser diagnoser = new ArtifactResolverDiagnoser();

        boolean canDiagnose = diagnoser.canDiagnose(new IllegalArgumentException("Unrelated failure"));

        assertFalse(canDiagnose);
    }
}
