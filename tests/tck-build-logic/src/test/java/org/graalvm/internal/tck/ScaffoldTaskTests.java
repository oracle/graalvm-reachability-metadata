/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.graalvm.internal.tck.model.DiscoveredArtifactMetadata;
import org.graalvm.internal.tck.model.LibraryLanguage;
import org.graalvm.internal.tck.utils.ArtifactMetadataDiscoveryUtils;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScaffoldTaskTests {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void runCreatesCompleteScaffoldFromTemplates() throws IOException {
        Coordinates coordinates = Coordinates.parse("org.lz4:lz4-java:1.8.0");
        installLibraryArtifact(coordinates, List.of(
                "net/jpountz/lz4/LZ4Factory.class",
                "net/jpountz/util/SafeUtils.class",
                "META-INF/versions/11/net/jpountz/lz4/LZ4FrameInputStream.class",
                "module-info.class"
        ));
        Project project = createProject();
        ScaffoldTask task = registerScaffoldTask(project, "scaffold", coordinates);

        task.run();

        assertThat(listGeneratedFiles()).containsExactly(
                "metadata/org.lz4/lz4-java/1.8.0/reachability-metadata.json",
                "metadata/org.lz4/lz4-java/index.json",
                "tests/src/org.lz4/lz4-java/1.8.0/.gitignore",
                "tests/src/org.lz4/lz4-java/1.8.0/build.gradle",
                "tests/src/org.lz4/lz4-java/1.8.0/gradle.properties",
                "tests/src/org.lz4/lz4-java/1.8.0/settings.gradle",
                "tests/src/org.lz4/lz4-java/1.8.0/src/test/java/org_lz4/lz4_java/Lz4_javaTest.java",
                "tests/src/org.lz4/lz4-java/1.8.0/user-code-filter.json"
        );

        assertGeneratedMetadataIndex(
                "metadata/org.lz4/lz4-java/index.json",
                List.of("net.jpountz"),
                List.of("1.8.0")
        );
        assertGeneratedFileMatchesTemplate(
                coordinates,
                "metadata/org.lz4/lz4-java/1.8.0/reachability-metadata.json",
                "/scaffold/reachability-metadata.json.template"
        );
        assertGeneratedReachabilityMetadataIsEmptyJsonObject(
                "metadata/org.lz4/lz4-java/1.8.0/reachability-metadata.json"
        );
        assertGeneratedFileMatchesTemplate(
                coordinates,
                "tests/src/org.lz4/lz4-java/1.8.0/.gitignore",
                "/scaffold/.gitignore.template"
        );
        assertGeneratedFileMatchesTemplate(
                coordinates,
                "tests/src/org.lz4/lz4-java/1.8.0/build.gradle",
                "/scaffold/build.gradle.template"
        );
        assertGeneratedFileMatchesTemplate(
                coordinates,
                "tests/src/org.lz4/lz4-java/1.8.0/gradle.properties",
                "/scaffold/gradle.properties.template"
        );
        assertGeneratedFileMatchesTemplate(
                coordinates,
                "tests/src/org.lz4/lz4-java/1.8.0/settings.gradle",
                "/scaffold/settings.gradle.template"
        );
        assertGeneratedUserCodeFilter(
                "tests/src/org.lz4/lz4-java/1.8.0/user-code-filter.json",
                List.of("net.jpountz")
        );
        assertGeneratedFileMatchesTemplate(
                coordinates,
                "tests/src/org.lz4/lz4-java/1.8.0/src/test/java/org_lz4/lz4_java/Lz4_javaTest.java",
                "/scaffold/Test.java.template"
        );
    }

    @Test
    void runWithSkipTestsOmitsOnlyJavaTestStub() throws IOException {
        Coordinates coordinates = Coordinates.parse("org.postgresql:postgresql:42.7.3");
        installLibraryArtifact(coordinates, List.of(
                "org/postgresql/Driver.class",
                "org/postgresql/util/PGobject.class"
        ));
        Project project = createProject();
        ScaffoldTask task = registerScaffoldTask(project, "scaffold", coordinates);
        task.setSkipTests(true);

        task.run();

        assertThat(listGeneratedFiles()).containsExactly(
                "metadata/org.postgresql/postgresql/42.7.3/reachability-metadata.json",
                "metadata/org.postgresql/postgresql/index.json",
                "tests/src/org.postgresql/postgresql/42.7.3/.gitignore",
                "tests/src/org.postgresql/postgresql/42.7.3/build.gradle",
                "tests/src/org.postgresql/postgresql/42.7.3/gradle.properties",
                "tests/src/org.postgresql/postgresql/42.7.3/settings.gradle",
                "tests/src/org.postgresql/postgresql/42.7.3/user-code-filter.json"
        );

        assertGeneratedUserCodeFilter(
                "tests/src/org.postgresql/postgresql/42.7.3/user-code-filter.json",
                List.of("org.postgresql")
        );
        assertThat(tempDir.resolve("tests/src/org.postgresql/postgresql/42.7.3/src/test/java/org_postgresql/postgresql/PostgresqlTest.java"))
                .doesNotExist();
    }

    @Test
    void runUsesDiscoveredKotlinScaffoldAndSeedsIndexEntry() throws IOException {
        Coordinates coordinates = Coordinates.parse("io.ktor:ktor-server-core-jvm:3.1.0");
        installLibraryArtifact(coordinates, List.of(
                "io/ktor/server/application/Application.class",
                "io/ktor/util/KtorExperimentalAPI.class"
        ));
        Project project = createProject();
        writeDiscoveredArtifactMetadata(project, new DiscoveredArtifactMetadata(
                coordinates.toString(),
                "https://example.com/source",
                "https://example.com/repository",
                "https://example.com/tests",
                "https://example.com/docs",
                "Ktor provides asynchronous servers. It is designed for Kotlin applications.",
                new LibraryLanguage("kotlin", "2.0")
        ));
        ScaffoldTask task = registerScaffoldTask(project, "scaffold", coordinates);

        task.run();

        assertThat(tempDir.resolve("tests/src/io.ktor/ktor-server-core-jvm/3.1.0/src/test/kotlin/io_ktor/ktor_server_core_jvm/Ktor_server_core_jvmTest.kt"))
                .exists();
        assertGeneratedFileMatchesTemplate(
                coordinates,
                "tests/src/io.ktor/ktor-server-core-jvm/3.1.0/build.gradle",
                "/scaffold/build.gradle.kotlin.template"
        );
        assertThat(Files.readString(tempDir.resolve("tests/src/io.ktor/ktor-server-core-jvm/3.1.0/build.gradle"), StandardCharsets.UTF_8))
                .contains("jvmToolchain(21)")
                .contains("kotlinOptions.jvmTarget = \"21\"")
                .doesNotContain("25");
        List<Map<String, Object>> indexEntries = OBJECT_MAPPER.readValue(
                tempDir.resolve("metadata/io.ktor/ktor-server-core-jvm/index.json").toFile(),
                new TypeReference<>() {}
        );
        assertThat(indexEntries.get(0))
                .containsEntry("source-code-url", "https://example.com/source")
                .containsEntry("repository-url", "https://example.com/repository")
                .containsEntry("test-code-url", "https://example.com/tests")
                .containsEntry("documentation-url", "https://example.com/docs")
                .containsEntry("description", "Ktor provides asynchronous servers. It is designed for Kotlin applications.")
                .containsEntry("language", Map.of("name", "kotlin", "version", "2.0"));
    }

    @Test
    void runUsesDiscoveredScala3Scaffold() throws IOException {
        Coordinates coordinates = Coordinates.parse("org.typelevel:cats-core_3:2.12.0");
        installLibraryArtifact(coordinates, List.of(
                "cats/Functor.class",
                "cats/data/Validated.class"
        ));
        Project project = createProject();
        writeDiscoveredArtifactMetadata(project, new DiscoveredArtifactMetadata(
                coordinates.toString(),
                null,
                null,
                null,
                null,
                null,
                new LibraryLanguage("scala", "3")
        ));
        ScaffoldTask task = registerScaffoldTask(project, "scaffold", coordinates);

        task.run();

        assertThat(tempDir.resolve("tests/src/org.typelevel/cats-core_3/2.12.0/src/test/scala/org_typelevel/cats_core_3/Cats_core_3Test.scala"))
                .exists();
        assertGeneratedFileMatchesTemplate(
                coordinates,
                "tests/src/org.typelevel/cats-core_3/2.12.0/build.gradle",
                "/scaffold/build.gradle.scala3.template"
        );
        assertThat(Files.readString(tempDir.resolve("tests/src/org.typelevel/cats-core_3/2.12.0/build.gradle"), StandardCharsets.UTF_8))
                .contains("JavaLanguageVersion.of(21)")
                .doesNotContain("25");
    }

    @Test
    void runUsesDiscoveredScala2Scaffold() throws IOException {
        Coordinates coordinates = Coordinates.parse("org.typelevel:cats-core_2.13:2.12.0");
        installLibraryArtifact(coordinates, List.of(
                "cats/Functor.class",
                "cats/data/Validated.class"
        ));
        Project project = createProject();
        writeDiscoveredArtifactMetadata(project, new DiscoveredArtifactMetadata(
                coordinates.toString(),
                null,
                null,
                null,
                null,
                null,
                new LibraryLanguage("scala", "2")
        ));
        ScaffoldTask task = registerScaffoldTask(project, "scaffold", coordinates);

        task.run();

        assertThat(tempDir.resolve("tests/src/org.typelevel/cats-core_2.13/2.12.0/src/test/scala/org_typelevel/cats_core_2_13/Cats_core_2_13Test.scala"))
                .exists();
        assertGeneratedFileMatchesTemplate(
                coordinates,
                "tests/src/org.typelevel/cats-core_2.13/2.12.0/build.gradle",
                "/scaffold/build.gradle.scala2.template"
        );
        assertThat(Files.readString(tempDir.resolve("tests/src/org.typelevel/cats-core_2.13/2.12.0/build.gradle"), StandardCharsets.UTF_8))
                .contains("JavaLanguageVersion.of(21)")
                .doesNotContain("25");
    }

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
                .doesNotContainKey("latest");
        assertThat(indexEntries.get(1)).containsEntry("metadata-version", "42.7.3")
                .containsEntry("tested-versions", List.of("42.7.3"))
                .containsEntry("allowed-packages", List.of("org.postgresql"))
                .containsEntry("latest", true);
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

    private void assertGeneratedFileMatchesTemplate(Coordinates coordinates, String relativePath, String templateResourcePath) throws IOException {
        String expectedContent = CoordinateUtils.replace(loadResource(templateResourcePath), coordinates);
        String actualContent = Files.readString(tempDir.resolve(relativePath), StandardCharsets.UTF_8);
        assertThat(actualContent).isEqualTo(expectedContent);
    }

    private void assertGeneratedMetadataIndex(String relativePath, List<String> allowedPackages, List<String> testedVersions) throws IOException {
        List<Map<String, Object>> indexEntries = OBJECT_MAPPER.readValue(
                tempDir.resolve(relativePath).toFile(),
                new TypeReference<>() {}
        );
        assertThat(indexEntries).hasSize(1);
        assertThat(indexEntries.get(0))
                .containsEntry("allowed-packages", allowedPackages)
                .containsEntry("tested-versions", testedVersions)
                .containsEntry("latest", true);
    }

    private void assertGeneratedUserCodeFilter(String relativePath, List<String> packageRoots) throws IOException {
        Map<String, List<Map<String, String>>> userCodeFilter = OBJECT_MAPPER.readValue(
                tempDir.resolve(relativePath).toFile(),
                new TypeReference<>() {}
        );
        List<Map<String, String>> expectedRules = new ArrayList<>();
        expectedRules.add(Map.of("excludeClasses", "**"));
        for (String packageRoot : packageRoots) {
            expectedRules.add(Map.of("includeClasses", packageRoot + ".**"));
        }
        assertThat(userCodeFilter).containsEntry("rules", expectedRules);
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

    private Project createProject() throws IOException {
        Project project = ProjectBuilder.builder()
                .withProjectDir(tempDir.toFile())
                .build();
        Path repositoryRoot = ensureRepositoryRoot();
        project.getRepositories().maven(repository -> repository.setUrl(repositoryRoot.toUri()));
        return project;
    }

    private ScaffoldTask registerScaffoldTask(Project project, String taskName, Coordinates coordinates) {
        ScaffoldTask task = project.getTasks().register(taskName, ScaffoldTask.class).get();
        task.setCoordinates(coordinates.toString());
        return task;
    }

    private void installLibraryArtifact(Coordinates coordinates, List<String> jarEntries) throws IOException {
        Path artifactDirectory = ensureRepositoryRoot()
                .resolve(coordinates.group().replace('.', '/'))
                .resolve(coordinates.artifact())
                .resolve(coordinates.version());
        Files.createDirectories(artifactDirectory);
        createLibraryJar(
                artifactDirectory.resolve(coordinates.artifact() + "-" + coordinates.version() + ".jar"),
                jarEntries
        );
        Files.writeString(
                artifactDirectory.resolve(coordinates.artifact() + "-" + coordinates.version() + ".pom"),
                """
                <project xmlns="http://maven.apache.org/POM/4.0.0"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>%s</groupId>
                  <artifactId>%s</artifactId>
                  <version>%s</version>
                </project>
                """.formatted(coordinates.group(), coordinates.artifact(), coordinates.version()),
                StandardCharsets.UTF_8
        );
    }

    private Path createLibraryJar(Path jarPath, List<String> entries) throws IOException {
        try (JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(jarPath))) {
            for (String entry : entries) {
                jarOutputStream.putNextEntry(new JarEntry(entry));
                jarOutputStream.write(new byte[]{0});
                jarOutputStream.closeEntry();
            }
        }
        return jarPath;
    }

    private Path ensureRepositoryRoot() throws IOException {
        return Files.createDirectories(tempDir.resolve("test-maven-repo"));
    }

    private void writeDiscoveredArtifactMetadata(Project project, DiscoveredArtifactMetadata metadata) throws IOException {
        ArtifactMetadataDiscoveryUtils.writeDiscoveryFile(
                ArtifactMetadataDiscoveryUtils.discoveryFile(project.getLayout(), metadata.coordinates()),
                metadata
        );
    }
}
