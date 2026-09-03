/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_core;

import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.usability.MojoFailureExceptionDiagnoser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MojoFailureExceptionDiagnoserTest {
    @Test
    void recognizesMojoFailuresInCausality() {
        MojoFailureExceptionDiagnoser diagnoser = new MojoFailureExceptionDiagnoser();
        MojoFailureException mojoFailure = new MojoFailureException("Plugin validation failed");

        boolean canDiagnose = diagnoser.canDiagnose(new IllegalStateException("Build failed", mojoFailure));

        assertThat(canDiagnose).isTrue();
    }

    @Test
    void formatsSourceMessageAndLongMessage() {
        MojoFailureExceptionDiagnoser diagnoser = new MojoFailureExceptionDiagnoser();
        MojoFailureException mojoFailure = new MojoFailureException(
                "compile goal",
                "Plugin validation failed",
                "Fix the plugin configuration");

        String diagnosis = diagnoser.diagnose(new IllegalStateException("Build failed", mojoFailure));

        assertThat(diagnosis).isEqualTo("""
                : compile goal
                Plugin validation failed

                Fix the plugin configuration""");
    }

    @Test
    void ignoresUnrelatedFailures() {
        MojoFailureExceptionDiagnoser diagnoser = new MojoFailureExceptionDiagnoser();

        boolean canDiagnose = diagnoser.canDiagnose(new IllegalArgumentException("Not a mojo failure"));

        assertThat(canDiagnose).isFalse();
    }
}
