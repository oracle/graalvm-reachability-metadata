/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_core;

import org.apache.maven.profiles.activation.ProfileActivationException;
import org.apache.maven.usability.ProfileActivationDiagnoser;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ProfileActivationDiagnoserTest {
    @Test
    void recognizesProfileActivationFailuresInCausality() {
        ProfileActivationDiagnoser diagnoser = new ProfileActivationDiagnoser();
        ProfileActivationException activationFailure = new ProfileActivationException(
                "Activation property is invalid");

        boolean canDiagnose = diagnoser.canDiagnose(new IllegalStateException("Build failed", activationFailure));

        assertThat(canDiagnose).isTrue();
    }

    @Test
    void formatsProfileActivationFailureMessage() {
        ProfileActivationDiagnoser diagnoser = new ProfileActivationDiagnoser();
        ProfileActivationException activationFailure = new ProfileActivationException(
                "Activation property is invalid");

        String diagnosis = diagnoser.diagnose(new IllegalStateException("Build failed", activationFailure));

        assertThat(diagnosis).isEqualTo("""
                Error activating profiles.

                Reason: Activation property is invalid
                """);
    }

    @Test
    void includesProfileActivatorLookupFailureDetails() {
        ProfileActivationDiagnoser diagnoser = new ProfileActivationDiagnoser();
        ComponentLookupException lookupFailure = new ComponentLookupException(
                "No such profile activator: custom");
        ProfileActivationException activationFailure = new ProfileActivationException(
                "Could not load activator", lookupFailure);

        String diagnosis = diagnoser.diagnose(new IllegalStateException("Build failed", activationFailure));

        assertThat(diagnosis).contains("Error activating profiles.");
        assertThat(diagnosis).contains("Reason: Could not load activator");
        assertThat(diagnosis).contains("There was a problem retrieving one or more profile activators.");
        assertThat(diagnosis).contains("No such profile activator: custom");
    }

    @Test
    void ignoresUnrelatedFailures() {
        ProfileActivationDiagnoser diagnoser = new ProfileActivationDiagnoser();

        boolean canDiagnose = diagnoser.canDiagnose(new IllegalArgumentException("Not a profile activation failure"));

        assertThat(canDiagnose).isFalse();
    }
}
