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

import static org.assertj.core.api.Assertions.assertThat;

public class ProjectBuildDiagnoserTest {
    @Test
    void recognizesProjectBuildingFailuresInCausality() {
        ProjectBuildDiagnoser diagnoser = new ProjectBuildDiagnoser();
        ProjectBuildingException projectFailure = new ProjectBuildingException(
                "com.example:demo",
                "Unable to read the project model");

        boolean canDiagnose = diagnoser.canDiagnose(new IllegalStateException("Build failed", projectFailure));

        assertThat(canDiagnose).isTrue();
    }

    @Test
    void formatsProjectBuildingFailureDetails() {
        ProjectBuildDiagnoser diagnoser = new ProjectBuildDiagnoser();
        ProjectBuildingException projectFailure = new ProjectBuildingException(
                "com.example:demo",
                "Unable to read the project model");

        String diagnosis = diagnoser.diagnose(new IllegalStateException("Build failed", projectFailure));

        assertThat(diagnosis).contains("Error building POM");
        assertThat(diagnosis).contains("Project ID: com.example:demo");
        assertThat(diagnosis).contains("Reason: Unable to read the project model");
    }

    @Test
    void includesInvalidProjectModelLocationAndValidationMessages() {
        ProjectBuildDiagnoser diagnoser = new ProjectBuildDiagnoser();
        ModelValidationResult validationResult = new ModelValidationResult();
        validationResult.addMessage("'dependencies.dependency.version' is missing");
        InvalidProjectModelException projectFailure = new InvalidProjectModelException(
                "com.example:demo",
                "/workspace/demo/pom.xml",
                "Invalid model",
                validationResult);

        String diagnosis = diagnoser.diagnose(new IllegalStateException("Build failed", projectFailure));

        assertThat(diagnosis).contains("Project ID: com.example:demo");
        assertThat(diagnosis).contains("POM Location: /workspace/demo/pom.xml");
        assertThat(diagnosis).contains("Validation Messages:");
        assertThat(diagnosis).contains("'dependencies.dependency.version' is missing");
        assertThat(diagnosis).contains("Reason: Invalid model");
    }

    @Test
    void ignoresUnrelatedFailures() {
        ProjectBuildDiagnoser diagnoser = new ProjectBuildDiagnoser();

        boolean canDiagnose = diagnoser.canDiagnose(new IllegalArgumentException("Not a project building failure"));

        assertThat(canDiagnose).isFalse();
    }
}
