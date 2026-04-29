/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.harness.tasks;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FetchExistingLibrariesWithNewerVersionsTaskTests {

    @TempDir
    private Path tempDir;

    @Test
    void readMavenMetadataReturnsEmptyWhenMetadataIsMissing() {
        String missingMetadataUrl = tempDir.resolve("missing-maven-metadata.xml").toUri().toString();

        assertThat(FetchExistingLibrariesWithNewerVersionsTask.readMavenMetadata(missingMetadataUrl)).isEmpty();
    }

    @Test
    void readMavenMetadataReadsExistingMetadata() throws IOException {
        Path metadataFile = tempDir.resolve("maven-metadata.xml");
        Files.writeString(metadataFile, """
                <metadata>
                  <versioning>
                    <versions>
                      <version>1.0.0</version>
                    </versions>
                  </versioning>
                </metadata>
                """);

        assertThat(FetchExistingLibrariesWithNewerVersionsTask.readMavenMetadata(metadataFile.toUri().toString()))
                .hasValueSatisfying(metadata -> assertThat(metadata).contains("<version>1.0.0</version>"));
    }

    @Test
    void getNewerVersionsFromLibraryIndexKeepsVersionsAfterStartingVersion() {
        String metadata = """
                <metadata>
                  <versioning>
                    <versions>
                      <version>1.0.0</version>
                      <version>1.1.0</version>
                      <version>1.2.0</version>
                    </versions>
                  </versioning>
                </metadata>
                """;

        List<String> newerVersions = FetchExistingLibrariesWithNewerVersionsTask.getNewerVersionsFromLibraryIndex(
                metadata, "1.0.0", "com.example:demo");

        assertThat(newerVersions).containsExactly("1.1.0", "1.2.0");
    }
}
