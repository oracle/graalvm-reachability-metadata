/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_model_builder;

import org.apache.maven.model.Build;
import org.apache.maven.model.InputLocation;
import org.apache.maven.model.Model;
import org.apache.maven.model.building.DefaultModelBuildingRequest;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.model.building.ModelProblem;
import org.apache.maven.model.building.ModelProblemCollector;
import org.apache.maven.model.interpolation.StringSearchModelInterpolator;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

public class StringSearchModelInterpolatorInnerInterpolateObjectActionTest {
    @Test
    void interpolatesStringsCollectionsMapsAndNestedObjects() {
        Model model = new Model();
        model.setModelVersion("4.0.0");
        model.setGroupId("org.example");
        model.setArtifactId("demo-artifact");
        model.setVersion("1.0");
        model.setName("${project.artifactId}-name");
        model.addModule("${project.artifactId}-module");
        model.addProperty("resolved.property", "${project.artifactId}-property");

        Build build = new Build();
        build.setDirectory("${project.artifactId}-target");
        model.setBuild(build);

        StringSearchModelInterpolator interpolator = new StringSearchModelInterpolator();
        ModelProblemRecorder problems = new ModelProblemRecorder();

        Model interpolated = interpolator.interpolateModel(model, null, newModelBuildingRequest(), problems);

        assertThat(problems.messages).isEmpty();
        assertThat(interpolated).isSameAs(model);
        assertThat(model.getName()).isEqualTo("demo-artifact-name");
        assertThat(model.getModules()).containsExactly("demo-artifact-module");
        assertThat(model.getProperties()).containsEntry("resolved.property", "demo-artifact-property");
        assertThat(model.getBuild().getDirectory()).isEqualTo("demo-artifact-target");
    }

    private static ModelBuildingRequest newModelBuildingRequest() {
        Properties systemProperties = new Properties();
        Properties userProperties = new Properties();

        return new DefaultModelBuildingRequest()
                .setValidationLevel(ModelBuildingRequest.VALIDATION_LEVEL_MINIMAL)
                .setSystemProperties(systemProperties)
                .setUserProperties(userProperties)
                .setBuildStartTime(new Date(0L));
    }

    private static final class ModelProblemRecorder implements ModelProblemCollector {
        private final StringBuilder messages = new StringBuilder();

        @Override
        public void add(ModelProblem.Severity severity, String message, InputLocation location, Exception cause) {
            if (messages.length() > 0) {
                messages.append('\n');
            }
            messages.append(severity).append(": ").append(message);
            if (cause != null) {
                messages.append(" (").append(cause.getClass().getName()).append(')');
            }
        }
    }
}
