/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.graalvm.internal.tck.utils.CoordinateUtils;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScaffoldTaskTests {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void runCreatesCompleteScaffoldFromTemplates() throws IOException {
        Coordinates coordinates = Coordinates.parse("com.example.lib:some-artifact:1.0.0.FINAL");
        Project project = ProjectBuilder.builder()
                .withProjectDir(tempDir.toFile())
                .build();
        ScaffoldTask task = project.getTasks().register("scaffold", ScaffoldTask.class).get();
        task.setCoordinates(coordinates.toString());

        task.run();

        assertThat(listGeneratedFiles()).containsExactly(
                "metadata/com.example.lib/some-artifact/1.0.0.FINAL/reachability-metadata.json",
                "metadata/com.example.lib/some-artifact/index.json",
                "tests/src/com.example.lib/some-artifact/1.0.0.FINAL/.gitignore",
                "tests/src/com.example.lib/some-artifact/1.0.0.FINAL/access-filter.json",
                "tests/src/com.example.lib/some-artifact/1.0.0.FINAL/build.gradle",
                "tests/src/com.example.lib/some-artifact/1.0.0.FINAL/gradle.properties",
                "tests/src/com.example.lib/some-artifact/1.0.0.FINAL/settings.gradle",
                "tests/src/com.example.lib/some-artifact/1.0.0.FINAL/src/test/java/com_example_lib/some_artifact/Some_artifactTest.java",
                "tests/src/com.example.lib/some-artifact/1.0.0.FINAL/user-code-filter.json"
        );

        assertGeneratedFileMatchesTemplate(
                coordinates,
                "metadata/com.example.lib/some-artifact/index.json",
                "/scaffold/metadataIndex.json.template"
        );
        assertGeneratedFileMatchesTemplate(
                coordinates,
                "metadata/com.example.lib/some-artifact/1.0.0.FINAL/reachability-metadata.json",
                "/scaffold/reachability-metadata.json.template"
        );
        assertGeneratedReachabilityMetadataIsEmptyJsonObject(
                "metadata/com.example.lib/some-artifact/1.0.0.FINAL/reachability-metadata.json"
        );
        assertGeneratedFileMatchesTemplate(
                coordinates,
                "tests/src/com.example.lib/some-artifact/1.0.0.FINAL/.gitignore",
                "/scaffold/.gitignore.template"
        );
        assertGeneratedFileMatchesTemplate(
                coordinates,
                "tests/src/com.example.lib/some-artifact/1.0.0.FINAL/access-filter.json",
                "/scaffold/access-filter.json.template"
        );
        assertGeneratedFileMatchesTemplate(
                coordinates,
                "tests/src/com.example.lib/some-artifact/1.0.0.FINAL/build.gradle",
                "/scaffold/build.gradle.template"
        );
        assertGeneratedFileMatchesTemplate(
                coordinates,
                "tests/src/com.example.lib/some-artifact/1.0.0.FINAL/gradle.properties",
                "/scaffold/gradle.properties.template"
        );
        assertGeneratedFileMatchesTemplate(
                coordinates,
                "tests/src/com.example.lib/some-artifact/1.0.0.FINAL/settings.gradle",
                "/scaffold/settings.gradle.template"
        );
        assertGeneratedFileMatchesTemplate(
                coordinates,
                "tests/src/com.example.lib/some-artifact/1.0.0.FINAL/user-code-filter.json",
                "/scaffold/user-code-filter.json.template"
        );
        assertGeneratedFileMatchesTemplate(
                coordinates,
                "tests/src/com.example.lib/some-artifact/1.0.0.FINAL/src/test/java/com_example_lib/some_artifact/Some_artifactTest.java",
                "/scaffold/Test.java.template"
        );
    }

    @Test
    void runWithSkipTestsOmitsOnlyJavaTestStub() throws IOException {
        Coordinates coordinates = Coordinates.parse("com.example:demo:1.0.0");
        Project project = ProjectBuilder.builder()
                .withProjectDir(tempDir.toFile())
                .build();
        ScaffoldTask task = project.getTasks().register("scaffold", ScaffoldTask.class).get();
        task.setCoordinates(coordinates.toString());
        task.setSkipTests(true);

        task.run();

        assertThat(listGeneratedFiles()).containsExactly(
                "metadata/com.example/demo/1.0.0/reachability-metadata.json",
                "metadata/com.example/demo/index.json",
                "tests/src/com.example/demo/1.0.0/.gitignore",
                "tests/src/com.example/demo/1.0.0/access-filter.json",
                "tests/src/com.example/demo/1.0.0/build.gradle",
                "tests/src/com.example/demo/1.0.0/gradle.properties",
                "tests/src/com.example/demo/1.0.0/settings.gradle",
                "tests/src/com.example/demo/1.0.0/user-code-filter.json"
        );

        assertThat(tempDir.resolve("tests/src/com.example/demo/1.0.0/src/test/java/com_example/demo/DemoTest.java"))
                .doesNotExist();
    }

    @Test
    void runWithUpdateAddsNewVersionMetadataAndTestScaffold() throws IOException {
        Project project = ProjectBuilder.builder()
                .withProjectDir(tempDir.toFile())
                .build();

        ScaffoldTask initialTask = project.getTasks().register("scaffoldInitial", ScaffoldTask.class).get();
        initialTask.setCoordinates("com.example:demo:1.0.0");
        initialTask.run();

        Coordinates updatedCoordinates = Coordinates.parse("com.example:demo:2.0.0");
        ScaffoldTask updateTask = project.getTasks().register("scaffoldUpdate", ScaffoldTask.class).get();
        updateTask.setCoordinates(updatedCoordinates.toString());
        updateTask.setUpdate(true);

        updateTask.run();

        Path updatedMetadataRoot = tempDir.resolve("metadata/com.example/demo/2.0.0");
        assertGeneratedFileMatchesTemplate(
                updatedCoordinates,
                "metadata/com.example/demo/2.0.0/reachability-metadata.json",
                "/scaffold/reachability-metadata.json.template"
        );
        assertGeneratedFileMatchesTemplate(
                updatedCoordinates,
                "tests/src/com.example/demo/2.0.0/build.gradle",
                "/scaffold/build.gradle.template"
        );
        assertGeneratedFileMatchesTemplate(
                updatedCoordinates,
                "tests/src/com.example/demo/2.0.0/settings.gradle",
                "/scaffold/settings.gradle.template"
        );
        assertGeneratedFileMatchesTemplate(
                updatedCoordinates,
                "tests/src/com.example/demo/2.0.0/gradle.properties",
                "/scaffold/gradle.properties.template"
        );
        assertGeneratedFileMatchesTemplate(
                updatedCoordinates,
                "tests/src/com.example/demo/2.0.0/.gitignore",
                "/scaffold/.gitignore.template"
        );
        assertGeneratedFileMatchesTemplate(
                updatedCoordinates,
                "tests/src/com.example/demo/2.0.0/src/test/java/com_example/demo/DemoTest.java",
                "/scaffold/Test.java.template"
        );

        List<Map<String, Object>> indexEntries = OBJECT_MAPPER.readValue(
                tempDir.resolve("metadata/com.example/demo/index.json").toFile(),
                new TypeReference<>() {}
        );
        assertThat(indexEntries).hasSize(2);
        assertThat(indexEntries.get(0)).containsEntry("metadata-version", "1.0.0")
                .containsEntry("tested-versions", List.of("1.0.0"))
                .containsEntry("allowed-packages", List.of("com.example"))
                .doesNotContainKey("latest");
        assertThat(indexEntries.get(1)).containsEntry("metadata-version", "2.0.0")
                .containsEntry("tested-versions", List.of("2.0.0"))
                .containsEntry("allowed-packages", List.of("com.example"))
                .containsEntry("latest", true);
    }

    @Test
    void runWithoutUpdateFailsWhenArtifactMetadataAlreadyExists() throws IOException {
        Project project = ProjectBuilder.builder()
                .withProjectDir(tempDir.toFile())
                .build();

        ScaffoldTask initialTask = project.getTasks().register("scaffoldInitial", ScaffoldTask.class).get();
        initialTask.setCoordinates("com.example:demo:1.0.0");
        initialTask.run();

        ScaffoldTask secondTask = project.getTasks().register("scaffoldSecond", ScaffoldTask.class).get();
        secondTask.setCoordinates("com.example:demo:2.0.0");

        assertThatThrownBy(secondTask::run)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Metadata for 'com.example:demo' already exists");
    }

    private void assertGeneratedFileMatchesTemplate(Coordinates coordinates, String relativePath, String templateResourcePath) throws IOException {
        String expectedContent = CoordinateUtils.replace(loadResource(templateResourcePath), coordinates);
        String actualContent = Files.readString(tempDir.resolve(relativePath), StandardCharsets.UTF_8);
        assertThat(actualContent).isEqualTo(expectedContent);
    }

    private void assertGeneratedReachabilityMetadataIsEmptyJsonObject(String relativePath) throws IOException {
        Map<String, Object> reachabilityMetadata = OBJECT_MAPPER.readValue(
                tempDir.resolve(relativePath).toFile(),
                new TypeReference<>() {}
        );
        assertThat(reachabilityMetadata).isEmpty();
    }

    private List<String> listGeneratedFiles() throws IOException {
        List<String> generatedFiles = listRelativeFiles(tempDir.resolve("metadata"));
        generatedFiles.addAll(listRelativeFiles(tempDir.resolve("tests")));
        generatedFiles.sort(String::compareTo);
        return generatedFiles;
    }

    private List<String> listRelativeFiles(Path root) throws IOException {
        if (!Files.exists(root)) {
            return new java.util.ArrayList<>();
        }

        try (Stream<Path> pathStream = Files.walk(root)) {
            return pathStream
                    .filter(Files::isRegularFile)
                    .map(path -> tempDir.relativize(path).toString().replace('\\', '/'))
                    .sorted()
                    .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
        }
    }

    private String loadResource(String path) throws IOException {
        try (InputStream stream = ScaffoldTask.class.getResourceAsStream(path)) {
            assertThat(stream).isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
