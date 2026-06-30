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

import static org.assertj.core.api.Assertions.assertThat;

public class MojoExecutionExceptionDiagnoserTest {
    @Test
    void recognizesMojoExecutionFailuresInCausality() {
        MojoExecutionExceptionDiagnoser diagnoser = new MojoExecutionExceptionDiagnoser();
        MojoExecutionException mojoFailure = new MojoExecutionException("Plugin execution failed");

        boolean canDiagnose = diagnoser.canDiagnose(new IllegalStateException("Build failed", mojoFailure));

        assertThat(canDiagnose).isTrue();
    }

    @Test
    void formatsSourceMessageAndLongMessage() {
        MojoExecutionExceptionDiagnoser diagnoser = new MojoExecutionExceptionDiagnoser();
        MojoExecutionException mojoFailure = new MojoExecutionException(
                "compile goal",
                "Plugin execution failed",
                "Check the plugin configuration");

        String diagnosis = diagnoser.diagnose(new IllegalStateException("Build failed", mojoFailure));

        assertThat(diagnosis).isEqualTo("""
                : compile goal
                Plugin execution failed

                Check the plugin configuration""");
    }

    @Test
    void includesEmbeddedCauseMessageWhenItAddsContext() {
        MojoExecutionExceptionDiagnoser diagnoser = new MojoExecutionExceptionDiagnoser();
        MojoExecutionException mojoFailure = new MojoExecutionException(
                "Plugin execution failed",
                new IllegalArgumentException("Unsupported configuration value"));

        String diagnosis = diagnoser.diagnose(new IllegalStateException("Build failed", mojoFailure));

        assertThat(diagnosis).contains("Plugin execution failed");
        assertThat(diagnosis).contains("Embedded error: Unsupported configuration value");
    }

    @Test
    void ignoresUnrelatedFailures() {
        MojoExecutionExceptionDiagnoser diagnoser = new MojoExecutionExceptionDiagnoser();

        boolean canDiagnose = diagnoser.canDiagnose(new IllegalArgumentException("Not a mojo execution failure"));

        assertThat(canDiagnose).isFalse();
    }
}
