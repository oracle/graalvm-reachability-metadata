/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_plexus.plexus_utils;

import org.codehaus.plexus.util.introspection.ReflectionValueExtractor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ReflectionValueExtractorTest {
    @Test
    void evaluatesNestedGetterExpression() throws Exception {
        ProjectModel project = new ProjectModel(new BuildModel("target/classes"), true);

        Object value = ReflectionValueExtractor.evaluate("project.build.outputDirectory", project);

        assertThat(value).isEqualTo("target/classes");
    }

    @Test
    void evaluatesBooleanAccessorExpression() throws Exception {
        ProjectModel project = new ProjectModel(new BuildModel("target/classes"), true);

        Object value = ReflectionValueExtractor.evaluate("project.active", project);

        assertThat(value).isEqualTo(Boolean.TRUE);
    }

    @Test
    void returnsNullWhenIntermediateValueIsNull() throws Exception {
        ProjectModel project = new ProjectModel(null, false);

        Object value = ReflectionValueExtractor.evaluate("project.build.outputDirectory", project);

        assertThat(value).isNull();
    }

    @Test
    void returnsNullWhenAccessorDoesNotExist() throws Exception {
        ProjectModel project = new ProjectModel(new BuildModel("target/classes"), false);

        Object value = ReflectionValueExtractor.evaluate("project.missing", project);

        assertThat(value).isNull();
    }

    public static class ProjectModel {
        private final BuildModel build;
        private final boolean active;

        public ProjectModel(BuildModel build, boolean active) {
            this.build = build;
            this.active = active;
        }

        public BuildModel getBuild() {
            return build;
        }

        public boolean isActive() {
            return active;
        }
    }

    public static class BuildModel {
        private final String outputDirectory;

        public BuildModel(String outputDirectory) {
            this.outputDirectory = outputDirectory;
        }

        public String getOutputDirectory() {
            return outputDirectory;
        }
    }
}
