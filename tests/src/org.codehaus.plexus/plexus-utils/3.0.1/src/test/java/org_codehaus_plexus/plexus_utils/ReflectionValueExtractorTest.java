/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_plexus.plexus_utils;

import java.util.ArrayList;
import java.util.HashMap;

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
    void evaluatesBooleanIsAccessorExpression() throws Exception {
        Project project = new Project(new Build("target/classes"));

        Object value = ReflectionValueExtractor.evaluate("project.build.active", project);

        assertThat(value).isEqualTo(Boolean.TRUE);
    }

    @Test
    void evaluatesIndexedListPropertyExpression() throws Exception {
        Project project = new Project(new Build("target/classes"));

        Object value = ReflectionValueExtractor.evaluate("project.build.sourceRoots[1].directory", project);

        assertThat(value).isEqualTo("src/integration-test/java");
    }

    @Test
    void evaluatesMappedPropertyExpression() throws Exception {
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
        private final SourceRootList sourceRoots;
        private final SourceRootMap sourceRootsById;

        public Build(String outputDirectory) {
            this.outputDirectory = outputDirectory;
            this.sourceRoots = new SourceRootList();
            this.sourceRoots.add(new SourceRoot("src/main/java"));
            this.sourceRoots.add(new SourceRoot("src/integration-test/java"));
            this.sourceRootsById = new SourceRootMap();
            this.sourceRootsById.put("main", sourceRoots.get(0));
            this.sourceRootsById.put("integration-test", sourceRoots.get(1));
        }

        public String getOutputDirectory() {
            return outputDirectory;
        }

        public boolean isActive() {
            return true;
        }

        public SourceRootList getSourceRoots() {
            return sourceRoots;
        }

        public SourceRootMap getSourceRootsById() {
            return sourceRootsById;
        }
    }

    public static class SourceRootList extends ArrayList<SourceRoot> {
        private static final long serialVersionUID = 1L;

        @Override
        public SourceRoot get(int index) {
            return super.get(index);
        }
    }

    public static class SourceRootMap extends HashMap<String, SourceRoot> {
        private static final long serialVersionUID = 1L;

        public SourceRoot get(String key) {
            return super.get(key);
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
