/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_core;

import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.usability.ArtifactNotFoundDiagnoser;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ArtifactNotFoundDiagnoserTest {
    @Test
    public void canDiagnoseFindsArtifactNotFoundExceptionInCausality() {
        ArtifactNotFoundDiagnoser diagnoser = new ArtifactNotFoundDiagnoser();
        ArtifactNotFoundException cause = new ArtifactNotFoundException(
                "Missing dependency",
                "com.example",
                "demo-artifact",
                "1.0.0",
                "jar",
                Collections.emptyList(),
                null,
                Collections.emptyList(),
                null);

        boolean canDiagnose = diagnoser.canDiagnose(new IllegalStateException("Build failed", cause));

        assertTrue(canDiagnose);
    }

    @Test
    public void canDiagnoseRejectsUnrelatedErrors() {
        ArtifactNotFoundDiagnoser diagnoser = new ArtifactNotFoundDiagnoser();

        boolean canDiagnose = diagnoser.canDiagnose(new IllegalArgumentException("Unrelated failure"));

        assertFalse(canDiagnose);
    }
}
