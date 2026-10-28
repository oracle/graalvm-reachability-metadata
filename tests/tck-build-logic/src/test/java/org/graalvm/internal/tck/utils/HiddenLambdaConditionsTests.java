/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class HiddenLambdaConditionsTests {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void hostClassStripsHiddenLambdaSuffixes() {
        assertThat(HiddenLambdaConditions.hostClass("org.example.Binder$$Lambda/0x00000000463c8c00"))
                .isEqualTo("org.example.Binder");
        assertThat(HiddenLambdaConditions.hostClass("org.example.Binder$$Lambda$12/0x0000000800c0b448"))
                .isEqualTo("org.example.Binder");
        assertThat(HiddenLambdaConditions.hostClass("org.example.Binder$$Lambda$12/1234567"))
                .isEqualTo("org.example.Binder");
        assertThat(HiddenLambdaConditions.hostClass("org.example.Outer$Inner$$Lambda"))
                .isEqualTo("org.example.Outer$Inner");
    }

    @Test
    void hostClassLeavesOrdinaryNestedClassesUntouched() {
        assertThat(HiddenLambdaConditions.hostClass("org.example.Outer$Inner")).isEqualTo("org.example.Outer$Inner");
        assertThat(HiddenLambdaConditions.hostClass("org.example.Outer$1")).isEqualTo("org.example.Outer$1");
        assertThat(HiddenLambdaConditions.hostClass("org.example.Outer$$Builder")).isEqualTo("org.example.Outer$$Builder");
        assertThat(HiddenLambdaConditions.hostClass("org.example.LambdaHolder")).isEqualTo("org.example.LambdaHolder");
    }

    @Test
    void rewriteMovesReflectionConditionToHostClass() throws IOException {
        JsonNode metadata = OBJECT_MAPPER.readTree("""
                {
                  "reflection": [
                    {
                      "condition": {"typeReached": "org.example.Binder$$Lambda/0x00000000463c8c00"},
                      "type": "org.example.Target",
                      "methods": [{"name": "<init>", "parameterTypes": ["int", "int"]}]
                    }
                  ]
                }
                """);

        assertThat(HiddenLambdaConditions.rewrite(metadata)).isTrue();

        assertThat(metadata).isEqualTo(OBJECT_MAPPER.readTree("""
                {
                  "reflection": [
                    {
                      "condition": {"typeReached": "org.example.Binder"},
                      "type": "org.example.Target",
                      "methods": [{"name": "<init>", "parameterTypes": ["int", "int"]}]
                    }
                  ]
                }
                """));
    }

    @Test
    void rewriteHandlesConditionsNestedInResources() throws IOException {
        JsonNode metadata = OBJECT_MAPPER.readTree("""
                {
                  "resources": {
                    "includes": [
                      {"condition": {"typeReached": "org.example.Loader$$Lambda$3/0x0000000800c0b448"}, "pattern": "\\\\Qa.txt\\\\E"}
                    ],
                    "bundles": [
                      {"condition": {"typeReachable": "org.example.Loader$$Lambda/0x0000000800c0b000"}, "name": "messages"}
                    ]
                  }
                }
                """);

        assertThat(HiddenLambdaConditions.rewrite(metadata)).isTrue();

        assertThat(metadata.at("/resources/includes/0/condition/typeReached").asText()).isEqualTo("org.example.Loader");
        assertThat(metadata.at("/resources/bundles/0/condition/typeReachable").asText()).isEqualTo("org.example.Loader");
    }

    @Test
    void rewriteDropsEntriesThatBecomeDuplicates() throws IOException {
        JsonNode metadata = OBJECT_MAPPER.readTree("""
                {
                  "reflection": [
                    {"condition": {"typeReached": "org.example.Runner"}, "type": "org.example.Target"},
                    {"condition": {"typeReached": "org.example.Runner$$Lambda/0x00000000831ea0c0"}, "type": "org.example.Target"},
                    {"condition": {"typeReached": "org.example.Runner$$Lambda/0x0000000083498910"}, "type": "org.example.Target"},
                    {"condition": {"typeReached": "org.example.Runner$$Lambda/0x0000000083498910"}, "type": "org.example.Other"}
                  ]
                }
                """);

        assertThat(HiddenLambdaConditions.rewrite(metadata)).isTrue();

        assertThat(metadata).isEqualTo(OBJECT_MAPPER.readTree("""
                {
                  "reflection": [
                    {"condition": {"typeReached": "org.example.Runner"}, "type": "org.example.Target"},
                    {"condition": {"typeReached": "org.example.Runner"}, "type": "org.example.Other"}
                  ]
                }
                """));
    }

    @Test
    void rewriteFileLeavesMetadataWithoutHiddenLambdasUnchanged() throws IOException {
        String content = """
                {"reflection": [{"condition": {"typeReached": "org.example.Outer$Inner"}, "type": "org.example.Outer$1"}]}
                """;
        Path metadataFile = tempDir.resolve("reachability-metadata.json");
        Files.writeString(metadataFile, content, StandardCharsets.UTF_8);

        assertThat(HiddenLambdaConditions.rewriteFile(metadataFile)).isFalse();

        assertThat(Files.readString(metadataFile, StandardCharsets.UTF_8)).isEqualTo(content);
    }

    @Test
    void rewriteFileWritesRewrittenMetadata() throws IOException {
        Path metadataFile = tempDir.resolve("reachability-metadata.json");
        Files.writeString(metadataFile, """
                {"reflection": [{"condition": {"typeReached": "org.example.Host$$Lambda/0x01"}, "type": "org.example.Target"}]}
                """, StandardCharsets.UTF_8);

        assertThat(HiddenLambdaConditions.rewriteFile(metadataFile)).isTrue();

        assertThat(OBJECT_MAPPER.readTree(metadataFile.toFile()).at("/reflection/0/condition/typeReached").asText())
                .isEqualTo("org.example.Host");
        assertThat(HiddenLambdaConditions.rewriteFile(tempDir.resolve("missing.json"))).isFalse();
    }
}
