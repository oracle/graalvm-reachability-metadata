/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.harness;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/// Exercises the actual workflow's aggregation and recording across maintenance lines.
/// §FS-library-version-update-automation.3 §AR-verify-new-library-version-compatibility
@EnabledOnOs(OS.LINUX)
class VersionUpdateAggregationTests {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void intersectsEnvironmentsWithinEachLineAndKeepsOtherLinesIndependent() throws Exception {
        Path first = tempDir.resolve("one.json");
        Path second = tempDir.resolve("two.json");
        Path otherLine = tempDir.resolve("five.json");
        Files.writeString(first, result("4.2", "4.2.19.Final", "[\"4.2.20.Final\",\"4.2.21.Final\"]"));
        Files.writeString(second, result("4.2", "4.2.19.Final", "[\"4.2.20.Final\"]"));
        Files.writeString(otherLine, result("5", "5.0.0.Alpha2", "[\"5.0.0.Alpha3\"]"));

        String source = workflow();
        int start = source.indexOf("jq -s '") + "jq -s '".length();
        String program = source.substring(start, source.indexOf("' \"${RESULT_FILES[@]}\"", start));
        JsonNode aggregate = MAPPER.readTree(command("jq", "-s", program,
                first.toString(), second.toString(), otherLine.toString()));
        JsonNode lines = aggregate.path("successes");
        assertThat(lines.size()).isEqualTo(2);
        assertThat(lines.get(0).path("latestVersion").asText()).isEqualTo("4.2.19.Final");
        assertThat(lines.get(0).path("successfulVersions").toString()).isEqualTo("[\"4.2.20.Final\"]");
        assertThat(lines.get(1).path("successfulVersions").toString()).isEqualTo("[\"5.0.0.Alpha3\"]");
    }

    @Test
    void recordingFailureRollsBackOnlyTheFailedLineAndContinuesAnotherLine() throws Exception {
        command("git", "init", "-q");
        command("git", "config", "user.name", "Test");
        command("git", "config", "user.email", "test@example.com");
        Files.createDirectories(tempDir.resolve("metadata"));
        Files.createDirectories(tempDir.resolve("stats"));
        Files.createDirectories(tempDir.resolve("tests/src"));
        Files.writeString(tempDir.resolve("metadata/index.json"), "base\n");
        Files.writeString(tempDir.resolve("stats/base"), "base");
        Files.writeString(tempDir.resolve("tests/src/base"), "base");
        command("git", "add", "metadata", "stats", "tests/src");
        command("git", "commit", "-qm", "base");
        Files.writeString(tempDir.resolve("gradlew"), """
                #!/bin/bash
                echo "$*" >> attempts
                echo partial > metadata/partial
                if [[ "$*" == *"4.1.116"* ]]; then exit 1; fi
                echo supported >> metadata/index.json
                rm metadata/partial
                """);
        assertThat(tempDir.resolve("gradlew").toFile().setExecutable(true)).isTrue();
        Files.writeString(tempDir.resolve("successful_updates.txt"), """
                io.netty:netty-common:4.1.116.Final|4.1.115.Final|4.1
                io.netty:netty-common:4.1.117.Final|4.1.116.Final|4.1
                io.netty:netty-common:4.2.20.Final|4.2.19.Final|4.2
                """);
        String source = workflow();
        int start = source.indexOf("          update_new_versions() {");
        int end = source.indexOf("          # Fails the step", start);
        command("bash", "-c", "set -euo pipefail\n" + source.substring(start, end) + "\nupdate_new_versions");
        assertThat(Files.readString(tempDir.resolve("recorded_updates.txt")))
                .isEqualTo("io.netty:netty-common:4.2.20.Final|4.2.19.Final\n");
        assertThat(Files.readString(tempDir.resolve("attempts"))).doesNotContain("4.1.117.Final");
        assertThat(tempDir.resolve("metadata/partial")).doesNotExist();
        assertThat(Files.readString(tempDir.resolve("metadata/index.json"))).isEqualTo("base\nsupported\n");
    }

    private String result(String line, String baseline, String successes) {
        return """
                {"name":"io.netty:netty-common","releaseLine":"%s","latestVersion":"%s",
                 "successfulVersions":%s,"failure":null}
                """.formatted(line, baseline, successes);
    }

    private String workflow() throws IOException {
        Path root = Path.of("").toAbsolutePath();
        while (root != null) {
            Path workflow = root.resolve(".github/workflows/verify-new-library-version-compatibility.yml");
            if (Files.exists(workflow)) {
                return Files.readString(workflow);
            }
            root = root.getParent();
        }
        throw new IOException("Cannot locate the compatibility workflow");
    }

    private String command(String... arguments) throws Exception {
        Process process = new ProcessBuilder(arguments).directory(tempDir.toFile())
                .redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.waitFor()).as(output).isZero();
        return output;
    }
}
