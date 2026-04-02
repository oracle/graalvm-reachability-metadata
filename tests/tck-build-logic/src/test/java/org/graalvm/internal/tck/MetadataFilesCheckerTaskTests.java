/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck;

import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import javax.inject.Inject;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MetadataFilesCheckerTaskTests {

    @TempDir
    Path tempDir;

    @Test
    void runUsesSharedMetadataVersionForSupportedVersion() throws IOException {
        createMetadataIndex();
        copyReachabilitySchemaFile();
        Files.createDirectories(tempDir.resolve("metadata/com.example/demo/1.0.0"));
        Files.writeString(
                tempDir.resolve("metadata/com.example/demo/1.0.0/reachability-metadata.json"),
                """
                {
                  "reflection": [
                    {
                      "type": "com.example.Demo",
                      "allDeclaredMethods": true
                    }
                  ]
                }
                """
        );

        Project project = ProjectBuilder.builder()
                .withProjectDir(tempDir.toFile())
                .build();
        TestMetadataFilesCheckerTask task = project.getTasks().register("checkMetadataFiles", TestMetadataFilesCheckerTask.class).get();
        task.setCoordinates("com.example:demo:1.0.1");

        assertThatCode(task::run).doesNotThrowAnyException();
    }

    @Test
    void runFailsWhenReachabilityMetadataJsonIsMalformed() throws IOException {
        createMetadataIndex();
        copyReachabilitySchemaFile();
        Files.createDirectories(tempDir.resolve("metadata/com.example/demo/1.0.0"));
        Files.writeString(
                tempDir.resolve("metadata/com.example/demo/1.0.0/reachability-metadata.json"),
                "{ not valid json"
        );

        TestMetadataFilesCheckerTask task = createTask();
        task.setCoordinates("com.example:demo:1.0.1");

        assertThatThrownBy(task::run)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Errors above found");
    }

    @Test
    void runFailsWhenReachabilityMetadataViolatesSchema() throws IOException {
        createMetadataIndex();
        copyReachabilitySchemaFile();
        Files.createDirectories(tempDir.resolve("metadata/com.example/demo/1.0.0"));
        Files.writeString(
                tempDir.resolve("metadata/com.example/demo/1.0.0/reachability-metadata.json"),
                """
                {
                  "reflection": [
                    {
                      "allDeclaredMethods": true
                    }
                  ]
                }
                """
        );

        TestMetadataFilesCheckerTask task = createTask();
        task.setCoordinates("com.example:demo:1.0.1");

        assertThatThrownBy(task::run)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Errors above found");
    }

    @Test
    void runFailsWhenReachabilityMetadataContainsDuplicatedReflectionEntries() throws IOException {
        createMetadataIndex();
        copyReachabilitySchemaFile();
        Files.createDirectories(tempDir.resolve("metadata/com.example/demo/1.0.0"));
        Files.writeString(
                tempDir.resolve("metadata/com.example/demo/1.0.0/reachability-metadata.json"),
                """
                {
                  "reflection": [
                    {
                      "type": "com.example.Demo",
                      "allDeclaredMethods": true
                    },
                    {
                      "type": "com.example.Demo",
                      "allDeclaredMethods": true
                    }
                  ]
                }
                """
        );

        TestMetadataFilesCheckerTask task = createTask();
        task.setCoordinates("com.example:demo:1.0.1");

        assertThatThrownBy(task::run)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Errors above found");
    }

    @Test
    void runFailsWhenConditionTypeReachedIsOutsideAllowedPackages() throws IOException {
        createMetadataIndex();
        copyReachabilitySchemaFile();
        Files.createDirectories(tempDir.resolve("metadata/com.example/demo/1.0.0"));
        Files.writeString(
                tempDir.resolve("metadata/com.example/demo/1.0.0/reachability-metadata.json"),
                """
                {
                  "reflection": [
                    {
                      "condition": {
                        "typeReached": "org.other.Library"
                      },
                      "type": "com.example.Demo",
                      "allDeclaredMethods": true
                    }
                  ]
                }
                """
        );

        TestMetadataFilesCheckerTask task = createTask();
        task.setCoordinates("com.example:demo:1.0.1");

        assertThatThrownBy(task::run)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Errors above found");
    }

    @Test
    void runAllowsJavaLangTypeReachedWhenEntryAlsoTargetsJavaLang() throws IOException {
        createMetadataIndex();
        copyReachabilitySchemaFile();
        Files.createDirectories(tempDir.resolve("metadata/com.example/demo/1.0.0"));
        Files.writeString(
                tempDir.resolve("metadata/com.example/demo/1.0.0/reachability-metadata.json"),
                """
                {
                  "reflection": [
                    {
                      "condition": {
                        "typeReached": "java.lang.ClassLoader"
                      },
                      "type": "java.lang.ClassLoader",
                      "jniAccessible": true
                    }
                  ]
                }
                """
        );

        TestMetadataFilesCheckerTask task = createTask();
        task.setCoordinates("com.example:demo:1.0.1");

        assertThatCode(task::run).doesNotThrowAnyException();
    }

    @Test
    void runAllowsRenamedPackageFromAllowedPackagesInResources() throws IOException {
        createMetadataIndex(
                "org.freemarker",
                "freemarker",
                "2.3.31",
                List.of("org.freemarker", "freemarker"),
                List.of("2.3.31", "2.3.34")
        );
        copyReachabilitySchemaFile();
        Files.createDirectories(tempDir.resolve("metadata/org.freemarker/freemarker/2.3.31"));
        Files.writeString(
                tempDir.resolve("metadata/org.freemarker/freemarker/2.3.31/reachability-metadata.json"),
                """
                {
                  "resources": [
                    {
                      "condition": {
                        "typeReached": "freemarker.template.utility.ClassUtil"
                      },
                      "glob": "freemarker/version.properties"
                    }
                  ]
                }
                """
        );

        TestMetadataFilesCheckerTask task = createTask();
        task.setCoordinates("org.freemarker:freemarker:2.3.34");

        assertThatCode(task::run).doesNotThrowAnyException();
    }

    @Test
    void runFailsWhenResourcesDependOnExternalTriggerPackage() throws IOException {
        createMetadataIndex(
                "ch.qos.logback",
                "logback-classic",
                "1.5.7",
                List.of("ch.qos.logback"),
                List.of("1.5.7", "1.5.8")
        );
        copyReachabilitySchemaFile();
        Files.createDirectories(tempDir.resolve("metadata/ch.qos.logback/logback-classic/1.5.7"));
        Files.writeString(
                tempDir.resolve("metadata/ch.qos.logback/logback-classic/1.5.7/reachability-metadata.json"),
                """
                {
                  "resources": [
                    {
                      "condition": {
                        "typeReached": "org.slf4j.LoggerFactory"
                      },
                      "glob": "META-INF/services/org.slf4j.spi.SLF4JServiceProvider"
                    }
                  ]
                }
                """
        );

        TestMetadataFilesCheckerTask task = createTask();
        task.setCoordinates("ch.qos.logback:logback-classic:1.5.8");

        assertThatThrownBy(task::run)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Errors above found");
    }

    @Test
    void runDoesNotApplyJavaUtilExceptionToResourceBundles() throws IOException {
        createMetadataIndex();
        copyReachabilitySchemaFile();
        Files.createDirectories(tempDir.resolve("metadata/com.example/demo/1.0.0"));
        Files.writeString(
                tempDir.resolve("metadata/com.example/demo/1.0.0/reachability-metadata.json"),
                """
                {
                  "resources": [
                    {
                      "condition": {
                        "typeReached": "java.util.ServiceLoader"
                      },
                      "bundle": "java.util.logging"
                    }
                  ]
                }
                """
        );

        TestMetadataFilesCheckerTask task = createTask();
        task.setCoordinates("com.example:demo:1.0.1");

        assertThatThrownBy(task::run)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Errors above found");
    }

    @Test
    void runAllowsLegacySerializationEntriesWithRealSchema() throws IOException {
        createMetadataIndex();
        copyReachabilitySchemaFile();
        Files.createDirectories(tempDir.resolve("metadata/com.example/demo/1.0.0"));
        Files.writeString(
                tempDir.resolve("metadata/com.example/demo/1.0.0/reachability-metadata.json"),
                """
                {
                  "serialization": [
                    {
                      "type": "java.lang.String"
                    },
                    {
                      "condition": {
                        "typeReached": "com.example.Demo"
                      },
                      "type": "com.example.Demo$State"
                    }
                  ]
                }
                """
        );

        TestMetadataFilesCheckerTask task = createTask();
        task.setCoordinates("com.example:demo:1.0.1");

        assertThatCode(task::run).doesNotThrowAnyException();
    }

    @Test
    void runFailsWhenLegacySerializationEntryViolatesSchema() throws IOException {
        createMetadataIndex();
        copyReachabilitySchemaFile();
        Files.createDirectories(tempDir.resolve("metadata/com.example/demo/1.0.0"));
        Files.writeString(
                tempDir.resolve("metadata/com.example/demo/1.0.0/reachability-metadata.json"),
                """
                {
                  "serialization": [
                    {
                      "condition": {
                        "typeReached": "com.example.Demo"
                      }
                    }
                  ]
                }
                """
        );

        TestMetadataFilesCheckerTask task = createTask();
        task.setCoordinates("com.example:demo:1.0.1");

        assertThatThrownBy(task::run)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Errors above found");
    }

    private TestMetadataFilesCheckerTask createTask() {
        Project project = ProjectBuilder.builder()
                .withProjectDir(tempDir.toFile())
                .build();
        return project.getTasks().create("checkMetadataFiles", TestMetadataFilesCheckerTask.class);
    }

    private void createMetadataIndex() throws IOException {
        createMetadataIndex(
                "com.example",
                "demo",
                "1.0.0",
                List.of("com.example"),
                List.of("1.0.0", "1.0.1")
        );
    }

    private void createMetadataIndex(
            String group,
            String artifact,
            String metadataVersion,
            List<String> allowedPackages,
            List<String> testedVersions
    ) throws IOException {
        Files.createDirectories(tempDir.resolve("metadata/" + group + "/" + artifact));
        Files.writeString(
                tempDir.resolve("metadata/" + group + "/" + artifact + "/index.json"),
                """
                [
                  {
                    "latest": true,
                    "allowed-packages": %s,
                    "metadata-version": "%s",
                    "tested-versions": %s
                  }
                ]
                """.formatted(toJsonArray(allowedPackages), metadataVersion, toJsonArray(testedVersions))
        );
    }

    private String toJsonArray(List<String> values) {
        return values.stream()
                .map(value -> "\"" + value + "\"")
                .collect(Collectors.joining(", ", "[", "]"));
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

    abstract static class TestMetadataFilesCheckerTask extends MetadataFilesCheckerTask {
        @Inject
        public TestMetadataFilesCheckerTask() {
        }
    }
}
