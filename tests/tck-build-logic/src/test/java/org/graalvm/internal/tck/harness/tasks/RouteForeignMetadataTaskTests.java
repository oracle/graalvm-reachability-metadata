/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.harness.tasks;

import com.fasterxml.jackson.databind.JsonNode;
import org.graalvm.internal.tck.Coordinates;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RouteForeignMetadataTaskTests extends MetadataTaskTestSupport {
    @Test
    void relocatesForeignConditionToExactSupportedOwner() throws IOException {
        String coordinate = "com.example:root:2.0.0";
        String ownerCoordinate = "org.owner:engine:1.0.0";
        copyReachabilitySchemaFile();
        writeMinimalTestProject("com.example", "root", "2.0.0");
        writeIndex("com.example", "root", "2.0.0", List.of("2.0.0"), List.of("com.example"));
        writeIndex("org.owner", "engine", "1.0.0", List.of("1.0.0"), List.of("org.owner"));
        writeJson(
                "metadata/com.example/root/2.0.0/reachability-metadata.json",
                foreignSerializationMetadata()
        );
        writeJson(
                "metadata/org.owner/engine/1.0.0/reachability-metadata.json",
                """
                {
                  "reflection": [
                    {
                      "condition": {
                        "typeReached": "org.owner.Engine"
                      },
                      "type": "org.owner.Engine"
                    }
                  ]
                }
                """
        );

        runRouteTask(coordinate, ownerCoordinate);

        JsonNode sourceMetadata = readJson("metadata/com.example/root/2.0.0/reachability-metadata.json");
        JsonNode ownerMetadata = readJson("metadata/org.owner/engine/1.0.0/reachability-metadata.json");
        assertThat(sourceMetadata.isEmpty()).isTrue();
        assertThat(ownerMetadata.get("serialization"))
                .extracting(entry -> entry.get("type").asText())
                .containsExactly("org.owner.Engine$SerializedForm");
    }

    @Test
    void forksSharedOwnerBucketBeforeRelocatingEntry() throws IOException {
        String coordinate = "com.example:root:2.0.0";
        String ownerCoordinate = "org.owner:engine:1.0.0";
        copyReachabilitySchemaFile();
        writeMinimalTestProject("com.example", "root", "2.0.0");
        writeIndex("com.example", "root", "2.0.0", List.of("2.0.0"), List.of("com.example"));
        writeIndex(
                "org.owner",
                "engine",
                "0.9.0",
                List.of("0.9.0", "1.0.0", "1.1.0", "2.0.0"),
                List.of("org.owner")
        );
        writeJson(
                "metadata/com.example/root/2.0.0/reachability-metadata.json",
                foreignSerializationMetadata()
        );
        writeJson(
                "metadata/org.owner/engine/0.9.0/reachability-metadata.json",
                """
                {
                  "reflection": [
                    {
                      "condition": {
                        "typeReached": "org.owner.Engine"
                      },
                      "type": "org.owner.Engine"
                    }
                  ]
                }
                """
        );

        RecordingRouteForeignMetadataTask task = runRouteTask(coordinate, ownerCoordinate);

        JsonNode inheritedMetadata = readJson("metadata/org.owner/engine/0.9.0/reachability-metadata.json");
        JsonNode exactMetadata = readJson("metadata/org.owner/engine/1.0.0/reachability-metadata.json");
        JsonNode ownerIndex = readJson("metadata/org.owner/engine/index.json");
        assertThat(inheritedMetadata.has("serialization")).isFalse();
        assertThat(exactMetadata.get("reflection")).isEqualTo(inheritedMetadata.get("reflection"));
        assertThat(exactMetadata.get("serialization"))
                .extracting(entry -> entry.get("type").asText())
                .containsExactly("org.owner.Engine$SerializedForm");
        assertThat(ownerIndex.get(0).get("tested-versions"))
                .extracting(JsonNode::asText)
                .containsExactly("0.9.0");
        assertThat(ownerIndex.get(0).has("latest")).isFalse();
        assertThat(ownerIndex.get(0).has("auto-update")).isFalse();
        assertThat(ownerIndex.get(0).has("high-priority")).isFalse();
        assertThat(ownerIndex.get(1).get("metadata-version").asText()).isEqualTo("1.0.0");
        assertThat(ownerIndex.get(1).get("test-version").asText()).isEqualTo("0.9.0");
        assertThat(ownerIndex.get(1).get("tested-versions"))
                .extracting(JsonNode::asText)
                .containsExactly("1.0.0", "1.1.0", "2.0.0");
        assertThat(ownerIndex.get(1).get("latest").asBoolean()).isTrue();
        assertThat(ownerIndex.get(1).get("auto-update").asBoolean()).isTrue();
        assertThat(ownerIndex.get(1).get("high-priority").asBoolean()).isTrue();
        assertThat(task.generatedStatsCoordinates())
                .containsExactly("org.owner:engine:1.0.0");
    }

    @Test
    void reportsResolvedUnsupportedOwnerWithoutChangingMetadata() throws IOException {
        String coordinate = "com.example:root:2.0.0";
        String ownerCoordinate = "org.owner:engine:1.0.0";
        writeMinimalTestProject("com.example", "root", "2.0.0");
        writeIndex("com.example", "root", "2.0.0", List.of("2.0.0"), List.of("com.example"));
        writeJson(
                "metadata/com.example/root/2.0.0/reachability-metadata.json",
                foreignSerializationMetadata()
        );

        assertThatThrownBy(() -> runRouteTask(coordinate, ownerCoordinate))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("owner-library-unsupported: " + ownerCoordinate);

        JsonNode sourceMetadata = readJson("metadata/com.example/root/2.0.0/reachability-metadata.json");
        assertThat(sourceMetadata.get("serialization"))
                .extracting(entry -> entry.get("type").asText())
                .containsExactly("org.owner.Engine$SerializedForm");
        JsonNode report = readJson("build/reports/route-foreign-metadata/com.example_root_2.0.0.json");
        assertThat(report.fieldNames()).toIterable().containsExactly("reason", "coordinate");
        assertThat(report.get("reason").asText()).isEqualTo("owner-library-unsupported");
        assertThat(report.get("coordinate").asText()).isEqualTo(ownerCoordinate);
    }

    @Test
    void reportsResolvedUnsupportedOwnerVersion() throws IOException {
        String coordinate = "com.example:root:2.0.0";
        String ownerCoordinate = "org.owner:engine:1.0.0";
        writeMinimalTestProject("com.example", "root", "2.0.0");
        writeIndex("com.example", "root", "2.0.0", List.of("2.0.0"), List.of("com.example"));
        writeIndex("org.owner", "engine", "0.9.0", List.of("0.9.0"), List.of("org.owner"));
        writeJson(
                "metadata/com.example/root/2.0.0/reachability-metadata.json",
                foreignSerializationMetadata()
        );

        assertThatThrownBy(() -> runRouteTask(coordinate, ownerCoordinate))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("owner-version-unsupported: " + ownerCoordinate);

        JsonNode report = readJson("build/reports/route-foreign-metadata/com.example_root_2.0.0.json");
        assertThat(report.fieldNames()).toIterable().containsExactly("reason", "coordinate");
        assertThat(report.get("reason").asText()).isEqualTo("owner-version-unsupported");
        assertThat(report.get("coordinate").asText()).isEqualTo(ownerCoordinate);
    }

    private RecordingRouteForeignMetadataTask runRouteTask(String coordinate, String ownerCoordinate) throws IOException {
        Project project = ProjectBuilder.builder()
                .withProjectDir(tempDir.toFile())
                .build();
        RecordingRouteForeignMetadataTask task = project.getTasks()
                .register("routeForeignMetadata", RecordingRouteForeignMetadataTask.class)
                .get();
        task.setCoordinatesOverride(List.of(coordinate));
        task.setDependencyArtifacts(List.of(new ResolvedDependencyArtifact(
                ownerCoordinate,
                writeJar("resolved/" + ownerCoordinate.replace(':', '_') + ".jar", "org/owner/Engine.class")
        )));
        task.run();
        return task;
    }

    public abstract static class RecordingRouteForeignMetadataTask extends RouteForeignMetadataTask {
        private final Set<String> generatedStatsCoordinates = new LinkedHashSet<>();
        private List<ResolvedDependencyArtifact> dependencyArtifacts = List.of();

        void setDependencyArtifacts(List<ResolvedDependencyArtifact> dependencyArtifacts) {
            this.dependencyArtifacts = dependencyArtifacts;
        }

        @Override
        protected List<ResolvedDependencyArtifact> resolveDependencyArtifacts(Coordinates coordinates) {
            return dependencyArtifacts;
        }

        @Override
        protected void generateRelocatedOwnerStats(Set<String> relocatedOwners) {
            generatedStatsCoordinates.addAll(relocatedOwners);
        }

        Set<String> generatedStatsCoordinates() {
            return generatedStatsCoordinates;
        }
    }

    private void copyReachabilitySchemaFile() throws IOException {
        Path source = findRepoFile("metadata/schemas/reachability-metadata-schema-v1.2.0.json");
        Path target = tempDir.resolve("metadata/schemas/reachability-metadata-schema-v1.2.0.json");
        Files.createDirectories(target.getParent());
        Files.copy(source, target);
    }

    private static Path findRepoFile(String relativePath) {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(relativePath);
            if (Files.exists(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate " + relativePath + " from " + Path.of("").toAbsolutePath());
    }

    private Path writeJar(String relativePath, String classEntry) throws IOException {
        Path file = tempDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(file))) {
            output.putNextEntry(new ZipEntry(classEntry));
            output.write(new byte[] {0});
            output.closeEntry();
        }
        return file;
    }
}
