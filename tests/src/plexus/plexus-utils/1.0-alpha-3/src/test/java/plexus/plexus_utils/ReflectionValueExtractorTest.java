/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package plexus.plexus_utils;

import org.codehaus.plexus.util.introspection.ReflectionValueExtractor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ReflectionValueExtractorTest {
    @Test
    void evaluatesNestedGetterExpression() throws Exception {
        Project project = new Project(new Build("target/classes"));

        Object value = ReflectionValueExtractor.evaluate("project.build.outputDirectory", project);

        assertThat(value).isEqualTo("target/classes");
    }

    @Test
    void returnsNullWhenGetterIsMissing() throws Exception {
        Project project = new Project(new Build("target/classes"));

        Object value = ReflectionValueExtractor.evaluate("project.build.missing", project);

        assertThat(value).isNull();
    }

    public static class Project {
        private final Build build;

        public Project(Build build) {
            this.build = build;
        }

        public Build getBuild() {
            return build;
        }
    }

    public static class Build {
        private final String outputDirectory;

        public Build(String outputDirectory) {
            this.outputDirectory = outputDirectory;
        }

        public String getOutputDirectory() {
            return outputDirectory;
        }
    }
}
