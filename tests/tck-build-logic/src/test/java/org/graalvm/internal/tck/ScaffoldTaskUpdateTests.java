/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck;

import com.fasterxml.jackson.core.type.TypeReference;
import org.gradle.api.Project;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScaffoldTaskUpdateTests extends ScaffoldTaskTestSupport {

    @Test
    void runWithUpdateAddsNewVersionMetadataAndTestScaffold() throws IOException {
        Coordinates initialCoordinates = Coordinates.parse("org.postgresql:postgresql:42.7.2");
        Coordinates updatedCoordinates = Coordinates.parse("org.postgresql:postgresql:42.7.3");
        installLibraryArtifact(initialCoordinates, List.of(
                "org/postgresql/Driver.class",
                "org/postgresql/ds/PGSimpleDataSource.class"
        ));
        installLibraryArtifact(updatedCoordinates, List.of(
                "org/postgresql/jdbc/PgConnection.class",
                "org/postgresql/util/PGobject.class"
        ));
        Project project = createProject();

        ScaffoldTask initialTask = registerScaffoldTask(project, "scaffoldInitial", initialCoordinates);
        initialTask.run();

        ScaffoldTask updateTask = registerScaffoldTask(project, "scaffoldUpdate", updatedCoordinates);
        updateTask.setUpdate(true);

        updateTask.run();

        assertGeneratedFileMatchesTemplate(
                updatedCoordinates,
                "metadata/org.postgresql/postgresql/42.7.3/reachability-metadata.json",
                "/scaffold/reachability-metadata.json.template"
        );
        assertGeneratedFileMatchesTemplate(
                updatedCoordinates,
                "tests/src/org.postgresql/postgresql/42.7.3/build.gradle",
                "/scaffold/build.gradle.template"
        );
        assertGeneratedFileMatchesTemplate(
                updatedCoordinates,
                "tests/src/org.postgresql/postgresql/42.7.3/settings.gradle",
                "/scaffold/settings.gradle.template"
        );
        assertGeneratedFileMatchesTemplate(
                updatedCoordinates,
                "tests/src/org.postgresql/postgresql/42.7.3/gradle.properties",
                "/scaffold/gradle.properties.template"
        );
        assertGeneratedFileMatchesTemplate(
                updatedCoordinates,
                "tests/src/org.postgresql/postgresql/42.7.3/.gitignore",
                "/scaffold/.gitignore.template"
        );
        assertGeneratedFileMatchesTemplate(
                updatedCoordinates,
                "tests/src/org.postgresql/postgresql/42.7.3/src/test/java/org_postgresql/postgresql/PostgresqlTest.java",
                "/scaffold/Test.java.template"
        );
        assertGeneratedUserCodeFilter(
                "tests/src/org.postgresql/postgresql/42.7.3/user-code-filter.json",
                List.of("org.postgresql")
        );

        List<Map<String, Object>> indexEntries = OBJECT_MAPPER.readValue(
                tempDir.resolve("metadata/org.postgresql/postgresql/index.json").toFile(),
                new TypeReference<>() {}
        );
        assertThat(indexEntries).hasSize(2);
        assertThat(indexEntries.get(0)).containsEntry("metadata-version", "42.7.2")
                .containsEntry("tested-versions", List.of("42.7.2"))
                .containsEntry("allowed-packages", List.of("org.postgresql"))
                .doesNotContainKey("latest");
        assertThat(indexEntries.get(1)).containsEntry("metadata-version", "42.7.3")
                .containsEntry("tested-versions", List.of("42.7.3"))
                .containsEntry("allowed-packages", List.of("org.postgresql"))
                .containsEntry("latest", true);
    }

    @Test
    void runWithoutUpdateAddsNewVersionWhenArtifactMetadataAlreadyExists() throws IOException {
        Coordinates initialCoordinates = Coordinates.parse("org.postgresql:postgresql:42.7.2");
        Coordinates secondCoordinates = Coordinates.parse("org.postgresql:postgresql:42.7.3");
        installLibraryArtifact(initialCoordinates, List.of(
                "org/postgresql/Driver.class",
                "org/postgresql/util/PGobject.class"
        ));
        installLibraryArtifact(secondCoordinates, List.of(
                "org/postgresql/jdbc/PgConnection.class",
                "org/postgresql/util/PGobject.class"
        ));
        Project project = createProject();

        ScaffoldTask initialTask = registerScaffoldTask(project, "scaffoldInitial", initialCoordinates);
        initialTask.run();
        Path indexFile = tempDir.resolve("metadata/org.postgresql/postgresql/index.json");
        List<Map<String, Object>> initialEntries = OBJECT_MAPPER.readValue(
                indexFile.toFile(),
                new TypeReference<>() {}
        );
        initialEntries.stream()
                .filter(entry -> Boolean.TRUE.equals(entry.get("latest")))
                .forEach(entry -> {
                    entry.put("auto-update", true);
                    entry.put("high-priority", true);
                });
        OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(indexFile.toFile(), initialEntries);

        ScaffoldTask secondTask = registerScaffoldTask(project, "scaffoldSecond", secondCoordinates);
        secondTask.run();

        List<Map<String, Object>> indexEntries = OBJECT_MAPPER.readValue(
                tempDir.resolve("metadata/org.postgresql/postgresql/index.json").toFile(),
                new TypeReference<>() {}
        );
        assertThat(indexEntries).hasSize(2);
        assertThat(indexEntries.get(0)).containsEntry("metadata-version", "42.7.2")
                .containsEntry("tested-versions", List.of("42.7.2"))
                .containsEntry("allowed-packages", List.of("org.postgresql"))
                .doesNotContainKeys("latest", "auto-update", "high-priority");
        assertThat(indexEntries.get(1)).containsEntry("metadata-version", "42.7.3")
                .containsEntry("tested-versions", List.of("42.7.3"))
                .containsEntry("allowed-packages", List.of("org.postgresql"))
                .containsEntry("latest", true)
                .containsEntry("auto-update", true)
                .containsEntry("high-priority", true);
        assertThat(tempDir.resolve("tests/src/org.postgresql/postgresql/42.7.3/build.gradle")).exists();
        assertThat(tempDir.resolve("metadata/org.postgresql/postgresql/42.7.3/reachability-metadata.json")).exists();
        assertThat(Files.readString(tempDir.resolve("metadata/org.postgresql/postgresql/index.json"), StandardCharsets.UTF_8))
                .startsWith("[\n  {\n")
                .contains("\n  },\n  {\n")
                .doesNotContain("[ {");
    }

    @Test
    void runWithoutUpdateKeepsLatestOnHighestVersion() throws IOException {
        Coordinates highestCoordinates = Coordinates.parse("org.postgresql:postgresql:42.7.3");
        Coordinates lowerCoordinates = Coordinates.parse("org.postgresql:postgresql:42.7.2");
        installLibraryArtifact(highestCoordinates, List.of(
                "org/postgresql/jdbc/PgConnection.class",
                "org/postgresql/util/PGobject.class"
        ));
        installLibraryArtifact(lowerCoordinates, List.of(
                "org/postgresql/Driver.class",
                "org/postgresql/util/PGobject.class"
        ));
        Project project = createProject();

        ScaffoldTask initialTask = registerScaffoldTask(project, "scaffoldInitial", highestCoordinates);
        initialTask.run();

        ScaffoldTask secondTask = registerScaffoldTask(project, "scaffoldSecond", lowerCoordinates);
        secondTask.run();

        List<Map<String, Object>> indexEntries = OBJECT_MAPPER.readValue(
                tempDir.resolve("metadata/org.postgresql/postgresql/index.json").toFile(),
                new TypeReference<>() {}
        );
        assertThat(indexEntries).hasSize(2);
        assertThat(indexEntries.get(0)).containsEntry("metadata-version", "42.7.2")
                .containsEntry("allowed-packages", List.of("org.postgresql"))
                .doesNotContainKey("latest");
        assertThat(indexEntries.get(1)).containsEntry("metadata-version", "42.7.3")
                .containsEntry("allowed-packages", List.of("org.postgresql"))
                .containsEntry("latest", true);
    }

    @Test
    void runWithoutForceFailsWhenExactVersionMetadataAlreadyExists() throws IOException {
        Coordinates coordinates = Coordinates.parse("org.postgresql:postgresql:42.7.3");
        installLibraryArtifact(coordinates, List.of("org/postgresql/Driver.class"));
        Project project = createProject();

        ScaffoldTask initialTask = registerScaffoldTask(project, "scaffoldInitial", coordinates);
        initialTask.run();

        ScaffoldTask secondTask = registerScaffoldTask(project, "scaffoldSecond", coordinates);

        assertThatThrownBy(secondTask::run)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Metadata for 'org.postgresql:postgresql:42.7.3' already exists");
    }
}
