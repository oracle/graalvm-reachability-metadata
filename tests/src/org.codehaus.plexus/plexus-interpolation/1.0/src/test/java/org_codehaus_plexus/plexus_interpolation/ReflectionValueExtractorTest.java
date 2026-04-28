/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_plexus.plexus_interpolation;

import org.codehaus.plexus.interpolation.reflection.ReflectionValueExtractor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ReflectionValueExtractorTest {
    @Test
    void evaluatesNestedGetterExpression() throws Exception {
        ProjectModel project = new ProjectModel(new BuildModel("target/generated-app"), true);

        Object value = ReflectionValueExtractor.evaluate("project.build.directory", project);

        assertThat(value).isEqualTo("target/generated-app");
    }

    @Test
    void evaluatesBooleanGetterWithoutTrimmingRootToken() throws Exception {
        ProjectModel project = new ProjectModel(new BuildModel("target/classes"), true);

        Object value = ReflectionValueExtractor.evaluate("active", project, false);

        assertThat(value).isEqualTo(Boolean.TRUE);
    }

    public static final class ProjectModel {
        private final BuildModel build;
        private final boolean active;

        ProjectModel(BuildModel build, boolean active) {
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

    public static final class BuildModel {
        private final String directory;

        BuildModel(String directory) {
            this.directory = directory;
        }

        public String getDirectory() {
            return directory;
        }
    }
}
