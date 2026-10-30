/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.harness.tasks;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

/// Temporary repository fixtures shared by the `splitTestOnlyMetadata` and
/// `routeForeignMetadata` task tests.
abstract class MetadataTaskTestSupport {
    static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @TempDir
    Path tempDir;

    void writeMinimalTestProject(String group, String artifact, String version) throws IOException {
        writeSource(
                "tests/src/" + group + "/" + artifact + "/" + version + "/src/test/java/example/RootTest.java",
                """
                package example;

                final class RootTest {
                }
                """
        );
    }

    void writeIndex(
            String group,
            String artifact,
            String metadataVersion,
            List<String> testedVersions,
            List<String> allowedPackages
    ) throws IOException {
        writeJson(
                "metadata/" + group + "/" + artifact + "/index.json",
                """
                [
                  {
                    "latest": true,
                    "auto-update": true,
                    "high-priority": true,
                    "metadata-version": "%s",
                    "tested-versions": %s,
                    "allowed-packages": %s
                  }
                ]
                """.formatted(metadataVersion, toJson(testedVersions), toJson(allowedPackages))
        );
    }

    String foreignSerializationMetadata() {
        return """
                {
                  "serialization": [
                    {
                      "condition": {
                        "typeReached": "org.owner.Engine"
                      },
                      "type": "org.owner.Engine$SerializedForm"
                    }
                  ]
                }
                """;
    }

    String toJson(List<String> values) {
        return values.stream()
                .map(value -> "\"" + value + "\"")
                .collect(Collectors.joining(", ", "[", "]"));
    }

    void writeSource(String relativePath, String content) throws IOException {
        Path file = tempDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content.stripIndent(), StandardCharsets.UTF_8);
    }

    void writeJson(String relativePath, String content) throws IOException {
        Path file = tempDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content.stripIndent(), StandardCharsets.UTF_8);
    }

    JsonNode readJson(String relativePath) throws IOException {
        return OBJECT_MAPPER.readTree(tempDir.resolve(relativePath).toFile());
    }
}
