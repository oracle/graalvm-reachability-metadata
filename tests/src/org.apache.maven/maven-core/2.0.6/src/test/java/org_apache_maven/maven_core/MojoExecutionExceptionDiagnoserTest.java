/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_core;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.usability.MojoExecutionExceptionDiagnoser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MojoExecutionExceptionDiagnoserTest {
    @Test
    public void canDiagnoseFindsMojoExecutionExceptionInCausality() {
        MojoExecutionExceptionDiagnoser diagnoser = new MojoExecutionExceptionDiagnoser();
        MojoExecutionException cause = new MojoExecutionException("Plugin execution failed");

        boolean canDiagnose = diagnoser.canDiagnose(new IllegalStateException("Build failed", cause));

        assertTrue(canDiagnose);
    }

    @Test
    public void canDiagnoseRejectsUnrelatedErrors() {
        MojoExecutionExceptionDiagnoser diagnoser = new MojoExecutionExceptionDiagnoser();

        boolean canDiagnose = diagnoser.canDiagnose(new IllegalArgumentException("Unrelated failure"));

        assertFalse(canDiagnose);
    }

    @Test
    public void diagnoseIncludesSourceShortMessageAndLongMessage() {
        MojoExecutionExceptionDiagnoser diagnoser = new MojoExecutionExceptionDiagnoser();
        MojoExecutionException cause = new MojoExecutionException(
                "example-mojo",
                "Plugin execution failed",
                "The configured goal could not be completed.");

        String diagnosis = diagnoser.diagnose(new IllegalStateException("Build failed", cause));

        assertEquals(": example-mojo\nPlugin execution failed\n\nThe configured goal could not be completed.", diagnosis);
    }
}
