/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GenerateMetadataTaskTests {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void runWithFromJarCreatesFilterFromJarRootsAndAddsAgentBlock() throws IOException {
        Coordinates coordinates = Coordinates.parse("org.lz4:lz4-java:1.8.0");
        installLibraryArtifact(coordinates, List.of(
                "net/jpountz/lz4/LZ4Factory.class",
                "net/jpountz/util/SafeUtils.class",
                "META-INF/versions/11/net/jpountz/lz4/LZ4FrameInputStream.class",
                "module-info.class"
        ));
        Project project = createProject();
        prepareTestProject(coordinates, "plugins { id 'java' }\n");
        TestGenerateMetadataTask task = registerGenerateMetadataTask(project, "generateMetadata", coordinates);
        task.setAgentAllowedPackages("fromJar");

        task.run();

        assertGeneratedUserCodeFilter(
                "tests/src/org.lz4/lz4-java/1.8.0/user-code-filter.json",
                List.of("net.jpountz")
        );
        assertThat(readTestBuildGradle(coordinates))
                .contains("graalvmNative")
                .contains("agent")
                .contains("userCodeFilterPath = \"user-code-filter.json\"")
                .contains("metadataCopy")
                .contains("mergeWithExisting = true");
        assertThat(readGradlewInvocations())
                .contains("tests/src/org.lz4/lz4-java/1.8.0|-Pagent test")
                .contains("tests/src/org.lz4/lz4-java/1.8.0|metadataCopy --task test --dir " + tempDir.resolve("metadata/org.lz4/lz4-java/1.8.0"));
    }

    @Test
    void runWithFromJarFallsBackToGroupIdWhenJarHasNoPackages() throws IOException {
        Coordinates coordinates = Coordinates.parse("com.example:demo:1.0.0");
        installLibraryArtifact(coordinates, List.of(
                "PlainClass.class",
                "module-info.class"
        ));
        Project project = createProject();
        prepareTestProject(coordinates, "plugins { id 'java' }\n");
        TestGenerateMetadataTask task = registerGenerateMetadataTask(project, "generateMetadata", coordinates);
        task.setAgentAllowedPackages("fromJar");

        task.run();

        assertGeneratedUserCodeFilter(
                "tests/src/com.example/demo/1.0.0/user-code-filter.json",
                List.of("com.example")
        );
    }

    @Test
    void runRejectsMixedFromJarAndExplicitPackages() throws IOException {
        Coordinates coordinates = Coordinates.parse("org.postgresql:postgresql:42.7.3");
        Project project = createProject();
        prepareTestProject(coordinates, "plugins { id 'java' }\n");
        TestGenerateMetadataTask task = registerGenerateMetadataTask(project, "generateMetadata", coordinates);
        task.setAgentAllowedPackages("fromJar,org.example.app");

        assertThatThrownBy(task::run)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("--agentAllowedPackages=fromJar must be used on its own");
        assertThat(tempDir.resolve("tests/src/org.postgresql/postgresql/42.7.3/user-code-filter.json"))
                .doesNotExist();
        assertThat(readGradlewInvocations()).isEmpty();
    }

    private void prepareTestProject(Coordinates coordinates, String buildGradleContent) throws IOException {
        Path testsDirectory = tempDir.resolve("tests/src")
                .resolve(coordinates.group())
                .resolve(coordinates.artifact())
                .resolve(coordinates.version());
        Files.createDirectories(testsDirectory);
        Files.writeString(testsDirectory.resolve("build.gradle"), buildGradleContent, StandardCharsets.UTF_8);
        createGradlewScript();
    }

    private String readTestBuildGradle(Coordinates coordinates) throws IOException {
        Path buildGradle = tempDir.resolve("tests/src")
                .resolve(coordinates.group())
                .resolve(coordinates.artifact())
                .resolve(coordinates.version())
                .resolve("build.gradle");
        return Files.readString(buildGradle, StandardCharsets.UTF_8);
    }

    private String readGradlewInvocations() throws IOException {
        Path logFile = tempDir.resolve("gradlew-invocations.log");
        if (!Files.exists(logFile)) {
            return "";
        }
        return Files.readString(logFile, StandardCharsets.UTF_8);
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

    private Project createProject() throws IOException {
        Project project = ProjectBuilder.builder()
                .withProjectDir(tempDir.toFile())
                .build();
        Path repositoryRoot = ensureRepositoryRoot();
        project.getRepositories().maven(repository -> repository.setUrl(repositoryRoot.toUri()));
        return project;
    }

    private TestGenerateMetadataTask registerGenerateMetadataTask(Project project, String taskName, Coordinates coordinates) {
        TestGenerateMetadataTask task = project.getTasks().register(taskName, TestGenerateMetadataTask.class).get();
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

    private void createGradlewScript() throws IOException {
        Path logFile = tempDir.resolve("gradlew-invocations.log");
        Path gradlew = tempDir.resolve("gradlew");
        Files.writeString(
                gradlew,
                """
                #!/bin/sh
                printf '%%s|%%s\n' "$PWD" "$*" >> '%s'
                if [ "$1" = "metadataCopy" ]; then
                  while [ "$#" -gt 0 ]; do
                    if [ "$1" = "--dir" ]; then
                      mkdir -p "$2"
                      printf '{}\n' > "$2/reachability-metadata.json"
                      break
                    fi
                    shift
                  done
                fi
                exit 0
                """.formatted(logFile),
                StandardCharsets.UTF_8
        );
        boolean executable = gradlew.toFile().setExecutable(true);
        assertThat(executable).isTrue();
    }

    private Path ensureRepositoryRoot() throws IOException {
        return Files.createDirectories(tempDir.resolve("test-maven-repo"));
    }

    abstract static class TestGenerateMetadataTask extends GenerateMetadataTask {
        @Inject
        public TestGenerateMetadataTask() {
        }
    }
}
