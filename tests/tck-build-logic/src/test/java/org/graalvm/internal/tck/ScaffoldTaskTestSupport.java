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
import org.graalvm.internal.tck.utils.ArtifactMetadataDiscoveryUtils;
import org.graalvm.internal.tck.utils.CoordinateUtils;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
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

/**
 * Shared scaffold fixtures: template assertions, project skeletons, and
 * installable library artifacts for `ScaffoldTask` tests.
 */
abstract class ScaffoldTaskTestSupport {
    static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @TempDir
    Path tempDir;

    void assertGeneratedFileMatchesTemplate(Coordinates coordinates, String relativePath, String templateResourcePath) throws IOException {
        String expectedContent = CoordinateUtils.replace(loadResource(templateResourcePath), coordinates);
        String actualContent = Files.readString(tempDir.resolve(relativePath), StandardCharsets.UTF_8);
        assertThat(actualContent).isEqualTo(expectedContent);
    }

    void assertGeneratedMetadataIndex(String relativePath, List<String> allowedPackages, List<String> testedVersions) throws IOException {
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

    void assertGeneratedUserCodeFilter(String relativePath, List<String> packageRoots) throws IOException {
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

    void assertGeneratedReachabilityMetadataIsEmptyJsonObject(String relativePath) throws IOException {
        Map<String, Object> reachabilityMetadata = OBJECT_MAPPER.readValue(
                tempDir.resolve(relativePath).toFile(),
                new TypeReference<>() {}
        );
        assertThat(reachabilityMetadata).isEmpty();
    }

    List<String> listGeneratedFiles() throws IOException {
        List<String> generatedFiles = listRelativeFiles(tempDir.resolve("metadata"));
        generatedFiles.addAll(listRelativeFiles(tempDir.resolve("tests")));
        generatedFiles.sort(String::compareTo);
        return generatedFiles;
    }

    List<String> listRelativeFiles(Path root) throws IOException {
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

    String loadResource(String path) throws IOException {
        try (InputStream stream = ScaffoldTask.class.getResourceAsStream(path)) {
            assertThat(stream).isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    Project createProject() throws IOException {
        Project project = ProjectBuilder.builder()
                .withProjectDir(tempDir.toFile())
                .build();
        Path repositoryRoot = ensureRepositoryRoot();
        project.getRepositories().maven(repository -> repository.setUrl(repositoryRoot.toUri()));
        return project;
    }

    ScaffoldTask registerScaffoldTask(Project project, String taskName, Coordinates coordinates) {
        ScaffoldTask task = project.getTasks().register(taskName, ScaffoldTask.class).get();
        task.setCoordinates(coordinates.toString());
        return task;
    }

    void installLibraryArtifact(Coordinates coordinates, List<String> jarEntries) throws IOException {
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

    void installJvmAndAndroidVariantArtifacts(
            Coordinates coordinates,
            String androidVersion,
            List<String> jvmJarEntries,
            List<String> androidJarEntries
    ) throws IOException {
        Path artifactRoot = ensureRepositoryRoot()
                .resolve(coordinates.group().replace('.', '/'))
                .resolve(coordinates.artifact());
        Path jvmArtifactDirectory = artifactRoot.resolve(coordinates.version());
        Path androidArtifactDirectory = artifactRoot.resolve(androidVersion);
        Files.createDirectories(jvmArtifactDirectory);
        Files.createDirectories(androidArtifactDirectory);

        createLibraryJar(
                jvmArtifactDirectory.resolve(coordinates.artifact() + "-" + coordinates.version() + ".jar"),
                jvmJarEntries
        );
        createLibraryJar(
                androidArtifactDirectory.resolve(coordinates.artifact() + "-" + androidVersion + ".jar"),
                androidJarEntries
        );
        writePomWithGradleMetadataMarker(jvmArtifactDirectory, coordinates);
        writeGradleModuleMetadata(jvmArtifactDirectory, coordinates, androidVersion);
    }

    Path createLibraryJar(Path jarPath, List<String> entries) throws IOException {
        try (JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(jarPath))) {
            for (String entry : entries) {
                jarOutputStream.putNextEntry(new JarEntry(entry));
                jarOutputStream.write(new byte[]{0});
                jarOutputStream.closeEntry();
            }
        }
        return jarPath;
    }

    void writePomWithGradleMetadataMarker(Path artifactDirectory, Coordinates coordinates) throws IOException {
        Files.writeString(
                artifactDirectory.resolve(coordinates.artifact() + "-" + coordinates.version() + ".pom"),
                """
                <project xmlns="http://maven.apache.org/POM/4.0.0"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                  <!-- do_not_remove: published-with-gradle-metadata -->
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>%s</groupId>
                  <artifactId>%s</artifactId>
                  <version>%s</version>
                </project>
                """.formatted(coordinates.group(), coordinates.artifact(), coordinates.version()),
                StandardCharsets.UTF_8
        );
    }

    void writeGradleModuleMetadata(Path artifactDirectory, Coordinates coordinates, String androidVersion) throws IOException {
        Files.writeString(
                artifactDirectory.resolve(coordinates.artifact() + "-" + coordinates.version() + ".module"),
                """
                {
                  "formatVersion": "1.1",
                  "component": {
                    "group": "%s",
                    "module": "%s",
                    "version": "%s",
                    "attributes": {
                      "org.gradle.status": "release"
                    }
                  },
                  "variants": [
                    {
                      "name": "jreRuntimeElements",
                      "attributes": {
                        "org.gradle.category": "library",
                        "org.gradle.dependency.bundling": "external",
                        "org.gradle.jvm.environment": "standard-jvm",
                        "org.gradle.libraryelements": "jar",
                        "org.gradle.usage": "java-runtime"
                      },
                      "files": [
                        {
                          "name": "%s-%s.jar",
                          "url": "%s-%s.jar"
                        }
                      ]
                    },
                    {
                      "name": "androidRuntimeElements",
                      "attributes": {
                        "org.gradle.category": "library",
                        "org.gradle.dependency.bundling": "external",
                        "org.gradle.jvm.environment": "android",
                        "org.gradle.libraryelements": "jar",
                        "org.gradle.usage": "java-runtime"
                      },
                      "files": [
                        {
                          "name": "%s-%s.jar",
                          "url": "../%s/%s-%s.jar"
                        }
                      ]
                    }
                  ]
                }
                """.formatted(
                        coordinates.group(),
                        coordinates.artifact(),
                        coordinates.version(),
                        coordinates.artifact(),
                        coordinates.version(),
                        coordinates.artifact(),
                        coordinates.version(),
                        coordinates.artifact(),
                        androidVersion,
                        androidVersion,
                        coordinates.artifact(),
                        androidVersion
                ),
                StandardCharsets.UTF_8
        );
    }

    Path ensureRepositoryRoot() throws IOException {
        return Files.createDirectories(tempDir.resolve("test-maven-repo"));
    }

    void writeDiscoveredArtifactMetadata(Project project, DiscoveredArtifactMetadata metadata) throws IOException {
        ArtifactMetadataDiscoveryUtils.writeDiscoveryFile(
                ArtifactMetadataDiscoveryUtils.discoveryFile(project.getLayout(), metadata.coordinates()),
                metadata
        );
    }
}
