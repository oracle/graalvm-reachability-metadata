/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_core;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.usability.MojoFailureExceptionDiagnoser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MojoFailureExceptionDiagnoserTest {
    @Test
    public void canDiagnoseFindsMojoFailureExceptionInCausality() {
        MojoFailureExceptionDiagnoser diagnoser = new MojoFailureExceptionDiagnoser();
        MojoFailureException cause = new MojoFailureException("Validation failed");

        boolean canDiagnose = diagnoser.canDiagnose(new IllegalStateException("Build failed", cause));

        assertTrue(canDiagnose);
    }

    @Test
    public void canDiagnoseRejectsOtherMojoExceptions() {
        MojoFailureExceptionDiagnoser diagnoser = new MojoFailureExceptionDiagnoser();
        MojoExecutionException cause = new MojoExecutionException("Plugin execution failed");

        boolean canDiagnose = diagnoser.canDiagnose(new IllegalStateException("Build failed", cause));

        assertFalse(canDiagnose);
    }

    @Test
    public void diagnoseIncludesSourceShortMessageAndLongMessage() {
        MojoFailureExceptionDiagnoser diagnoser = new MojoFailureExceptionDiagnoser();
        MojoFailureException cause = new MojoFailureException(
                "example-mojo",
                "Validation failed",
                "The plugin reported an invalid project configuration.");

        String diagnosis = diagnoser.diagnose(new IllegalStateException("Build failed", cause));

        assertEquals(
                ": example-mojo\nValidation failed\n\nThe plugin reported an invalid project configuration.",
                diagnosis);
    }

    @Test
    public void diagnoseOmitsSourceAndLongMessageWhenTheyAreAbsent() {
        MojoFailureExceptionDiagnoser diagnoser = new MojoFailureExceptionDiagnoser();
        MojoFailureException cause = new MojoFailureException("Validation failed");

        String diagnosis = diagnoser.diagnose(new IllegalArgumentException("Build failed", cause));

        assertEquals("Validation failed", diagnosis);
    }
}
