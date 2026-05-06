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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ProfileActivationDiagnoserTest {
    @Test
    public void canDiagnoseFindsProfileActivationExceptionInCausality() {
        ProfileActivationDiagnoser diagnoser = new ProfileActivationDiagnoser();
        ProfileActivationException cause = new ProfileActivationException("Missing profile activator");

        boolean canDiagnose = diagnoser.canDiagnose(new IllegalStateException("Build failed", cause));

        assertTrue(canDiagnose);
    }

    @Test
    public void canDiagnoseRejectsUnrelatedErrors() {
        ProfileActivationDiagnoser diagnoser = new ProfileActivationDiagnoser();

        boolean canDiagnose = diagnoser.canDiagnose(new IllegalArgumentException("Unrelated failure"));

        assertFalse(canDiagnose);
    }

    @Test
    public void diagnoseIncludesProfileActivationAndComponentLookupDetails() {
        ProfileActivationDiagnoser diagnoser = new ProfileActivationDiagnoser();
        ComponentLookupException lookupException = new ComponentLookupException("No profile activator was configured");
        ProfileActivationException activationException = new ProfileActivationException(
                "Unable to activate the requested profile",
                lookupException);

        String diagnosis = diagnoser.diagnose(new IllegalStateException("Build failed", activationException));

        assertTrue(diagnosis.contains("Error activating profiles."));
        assertTrue(diagnosis.contains("Reason: Unable to activate the requested profile"));
        assertTrue(diagnosis.contains("There was a problem retrieving one or more profile activators."));
        assertTrue(diagnosis.contains("No profile activator was configured"));
    }
}
