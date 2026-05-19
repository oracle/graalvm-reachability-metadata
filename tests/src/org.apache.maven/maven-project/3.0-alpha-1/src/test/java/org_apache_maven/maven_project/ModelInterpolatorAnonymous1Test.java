/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_project;

import org.apache.maven.model.Model;
import org.apache.maven.project.builder.Interpolator;
import org.apache.maven.project.builder.PomInterpolatorTag;
import org.apache.maven.shared.model.InterpolatorProperty;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

public class ModelInterpolatorAnonymous1Test {
    @Test
    void interpolateModelAppliesProvidedProperties() throws IOException {
        Model model = new Model();
        model.setModelVersion("4.0.0");
        model.setGroupId("${project.groupId}");
        model.setArtifactId("demo");
        model.setVersion("1");

        Model interpolated = Interpolator.interpolateModel(
                model,
                Collections.singletonList(new InterpolatorProperty(
                        "${project.groupId}", "org.example", PomInterpolatorTag.USER_PROPERTIES.name())),
                new File("."));

        assertThat(interpolated.getGroupId()).isEqualTo("org.example");
    }
}
