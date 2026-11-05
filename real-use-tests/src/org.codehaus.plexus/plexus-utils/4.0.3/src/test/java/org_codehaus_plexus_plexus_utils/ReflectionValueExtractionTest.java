/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_plexus_plexus_utils;

import java.util.List;
import java.util.Map;

import org.codehaus.plexus.util.introspection.ReflectionValueExtractor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Resolves nested build-model properties with Plexus reflection utilities.
 *
 * A build extension receives a project model and reads the output directory,
 * selected module, and release channel before creating an artifact.
 */
public class ReflectionValueExtractionTest {

    @Test
    void readsNestedProjectValuesFromBuildModel() throws Exception {
        BuildModel buildModel = new BuildModel(
                new Project("catalog", new OutputDirectory("target/catalog")),
                List.of(new Module("api"), new Module("worker")),
                Map.of("releaseChannel", "stable"));

        assertThat(ReflectionValueExtractor.evaluate(
                "project.outputDirectory.path", buildModel, false))
                .isEqualTo("target/catalog");
        assertThat(ReflectionValueExtractor.evaluate("modules[1].artifactId", buildModel, false))
                .isEqualTo("worker");
        assertThat(ReflectionValueExtractor.evaluate(
                "properties(releaseChannel)", buildModel, false))
                .isEqualTo("stable");
    }

    public static final class BuildModel {
        private final Project project;
        private final List<Module> modules;
        private final Map<String, String> properties;

        public BuildModel(Project project, List<Module> modules, Map<String, String> properties) {
            this.project = project;
            this.modules = modules;
            this.properties = properties;
        }

        public Project getProject() {
            return project;
        }

        public List<Module> getModules() {
            return modules;
        }

        public Map<String, String> getProperties() {
            return properties;
        }
    }

    public static final class Project {
        private final String name;
        private final OutputDirectory outputDirectory;

        public Project(String name, OutputDirectory outputDirectory) {
            this.name = name;
            this.outputDirectory = outputDirectory;
        }

        public String getName() {
            return name;
        }

        public OutputDirectory getOutputDirectory() {
            return outputDirectory;
        }
    }

    public static final class OutputDirectory {
        private final String path;

        public OutputDirectory(String path) {
            this.path = path;
        }

        public String getPath() {
            return path;
        }
    }

    public static final class Module {
        private final String artifactId;

        public Module(String artifactId) {
            this.artifactId = artifactId;
        }

        public String getArtifactId() {
            return artifactId;
        }
    }
}
