/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_core;

import org.apache.maven.project.InvalidProjectModelException;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.validation.ModelValidationResult;
import org.apache.maven.usability.ProjectBuildDiagnoser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ProjectBuildDiagnoserTest {
    @Test
    public void canDiagnoseFindsProjectBuildingExceptionInCausality() {
        ProjectBuildDiagnoser diagnoser = new ProjectBuildDiagnoser();
        ProjectBuildingException cause = new ProjectBuildingException(
                "com.example:demo-project:jar:1.0.0",
                "Project could not be built");

        boolean canDiagnose = diagnoser.canDiagnose(new IllegalStateException("Build failed", cause));

        assertTrue(canDiagnose);
    }

    @Test
    public void canDiagnoseRejectsUnrelatedErrors() {
        ProjectBuildDiagnoser diagnoser = new ProjectBuildDiagnoser();

        boolean canDiagnose = diagnoser.canDiagnose(new IllegalArgumentException("Unrelated failure"));

        assertFalse(canDiagnose);
    }

    @Test
    public void diagnoseIncludesProjectIdAndReason() {
        ProjectBuildDiagnoser diagnoser = new ProjectBuildDiagnoser();
        ProjectBuildingException cause = new ProjectBuildingException(
                "com.example:demo-project:jar:1.0.0",
                "Project could not be built");

        String diagnosis = diagnoser.diagnose(new IllegalStateException("Build failed", cause));

        assertTrue(diagnosis.contains("Error building POM"));
        assertTrue(diagnosis.contains("Project ID: com.example:demo-project:jar:1.0.0"));
        assertTrue(diagnosis.contains("Reason: Project could not be built"));
    }

    @Test
    public void diagnoseIncludesInvalidModelLocationAndValidationMessages() {
        ProjectBuildDiagnoser diagnoser = new ProjectBuildDiagnoser();
        ModelValidationResult validationResult = new ModelValidationResult();
        validationResult.addMessage("Missing required modelVersion");
        InvalidProjectModelException cause = new InvalidProjectModelException(
                "com.example:invalid-project:jar:1.0.0",
                "/workspace/project/pom.xml",
                "The project model is invalid",
                validationResult);

        String diagnosis = diagnoser.diagnose(new IllegalStateException("Build failed", cause));

        assertTrue(diagnosis.contains("Project ID: com.example:invalid-project:jar:1.0.0"));
        assertTrue(diagnosis.contains("POM Location: /workspace/project/pom.xml"));
        assertTrue(diagnosis.contains("Validation Messages:"));
        assertTrue(diagnosis.contains("Missing required modelVersion"));
        assertTrue(diagnosis.contains("Reason: The project model is invalid"));
    }
}
