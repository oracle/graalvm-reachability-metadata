/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_shared.maven_shared_utils;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.maven.shared.utils.introspection.ReflectionValueExtractor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ReflectionValueExtractorTest {
    @Test
    void evaluatesIndexedPropertyFromGetterResult() throws Exception {
        Project project = new Project(new Build("target/classes"));

        Object value = ReflectionValueExtractor.evaluate("project.build.sourceRoots[1].directory", project);

        assertThat(value).isEqualTo("src/integration-test/java");
    }

    @Test
    void evaluatesMappedPropertyFromGetterResult() throws Exception {
        Project project = new Project(new Build("target/classes"));

        Object value = ReflectionValueExtractor.evaluate("project.build.sourceRootsById(main).directory", project);

        assertThat(value).isEqualTo("src/main/java");
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
        private final List<SourceRoot> sourceRoots;
        private final Map<String, SourceRoot> sourceRootsById;

        public Build(String outputDirectory) {
            this.outputDirectory = outputDirectory;
            this.sourceRoots = Arrays.asList(
                    new SourceRoot("src/main/java"),
                    new SourceRoot("src/integration-test/java")
            );
            this.sourceRootsById = new LinkedHashMap<>();
            this.sourceRootsById.put("main", sourceRoots.get(0));
            this.sourceRootsById.put("integration-test", sourceRoots.get(1));
        }

        public String getOutputDirectory() {
            return outputDirectory;
        }

        public List<SourceRoot> getSourceRoots() {
            return sourceRoots;
        }

        public Map<String, SourceRoot> getSourceRootsById() {
            return sourceRootsById;
        }
    }

    public static class SourceRoot {
        private final String directory;

        public SourceRoot(String directory) {
            this.directory = directory;
        }

        public String getDirectory() {
            return directory;
        }
    }
}
