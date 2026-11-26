/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.harness.tasks;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.graalvm.internal.tck.model.MetadataVersionsIndexEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Covers the Netty 4.x lines hidden below a tested 5.x alpha.
/// §FS-library-version-update-automation.1 §AR-test-harness.7
class ReleaseLineUpdatesTests {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void selectsHighestTestedVersionIndependentlyOfMetadataDirectoryAndOtherLines() throws IOException {
        List<MetadataVersionsIndexEntry> entries = entries("""
                ["4.1", "4.2", "5"]
                """);
        assertThat(ReleaseLineUpdates.baselines(entries)).containsExactly(
                Map.entry("4.1", "4.1.115.Final"),
                Map.entry("4.2", "4.2.19.Final"),
                Map.entry("5", "5.0.0.Alpha2"));
    }

    @Test
    void findsMaintenanceReleasesEvenWhenMavenAlsoListsANewerMajor() {
        String mavenIndex = """
                <version>4.1.114.Final</version>
                <version>5.0.0.Alpha2</version>
                <version>4.2.19.Final</version>
                <version>4.1.115.Final</version>
                <version>4.2.20.Final</version>
                <version>4.1.116.Final</version>
                <version>4.2.21.Final</version>
                <version>4.10.0.Final</version>
                <version>5.0.0.Alpha3</version>
                """;
        assertThat(ReleaseLineUpdates.candidates(mavenIndex, "io.netty:netty-common",
                "4.2.19.Final", "4.2", List.of("4.2.19.Final"), List.of("4.2.21.Final")))
                .containsExactly("4.2.20.Final");
        assertThat(ReleaseLineUpdates.candidates(mavenIndex, "io.netty:netty-common",
                "4.1.115.Final", "4.1", List.of(), List.of())).containsExactly("4.1.116.Final");
    }

    @ParameterizedTest
    @ValueSource(strings = {"[]", "[\"4\", \"4.2\"]", "[\"4.2\", \"4.2\"]",
            "[\"6\"]", "[\"4.x\"]", "[null]"})
    void rejectsInvalidOverlappingOrUnseededLines(String lines) throws IOException {
        List<MetadataVersionsIndexEntry> entries = entries(lines);
        assertThatThrownBy(() -> ReleaseLineUpdates.baselines(entries))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void requiresAutomationEnabledOnLatestEntry() throws IOException {
        List<MetadataVersionsIndexEntry> entries = MAPPER.readValue("""
                [{"metadata-version":"4.2.19.Final","tested-versions":["4.2.19.Final"],
                  "auto-update-lines":["4.2"]}]
                """, new TypeReference<>() {});
        assertThatThrownBy(() -> ReleaseLineUpdates.baselines(entries))
                .hasMessageContaining("requires latest: true and auto-update: true");
    }

    @Test
    void leavesUnconfiguredLibrariesOnTheirExistingDiscoveryPath() throws IOException {
        List<MetadataVersionsIndexEntry> entries = MAPPER.readValue("""
                [{"latest":true,"auto-update":true,"metadata-version":"1.0","tested-versions":["1.0"]}]
                """, new TypeReference<>() {});
        assertThat(ReleaseLineUpdates.baselines(entries)).isEmpty();
    }

    private List<MetadataVersionsIndexEntry> entries(String lines) throws IOException {
        return MAPPER.readValue("""
                [{"latest":true,"auto-update":true,"auto-update-lines":%s,
                  "metadata-version":"5.0.0.Alpha2","tested-versions":["5.0.0.Alpha2"]},
                 {"metadata-version":"4.1.80.Final","tested-versions":["4.1.80.Final","4.1.115.Final"]},
                 {"metadata-version":"4.2.19.Final","tested-versions":["4.2.19.Final"]}]
                """.formatted(lines), new TypeReference<>() {});
    }
}
