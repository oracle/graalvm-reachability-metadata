/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_liquibase.liquibase_core;

import liquibase.database.OfflineConnection;
import liquibase.database.core.H2Database;
import liquibase.parser.core.yaml.YamlSnapshotParser;
import liquibase.resource.DirectoryResourceAccessor;
import liquibase.snapshot.DatabaseSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class YamlSnapshotParserTest {

    @TempDir
    Path tempDirectory;

    @Test
    void parsesSnapshotYamlAndRestoresConfiguredDatabase() throws Exception {
        Path snapshotFile = tempDirectory.resolve("snapshot.yaml");
        Files.writeString(snapshotFile, """
                snapshot:
                  database:
                    shortName: h2
                    majorVersion: 2
                    minorVersion: 1
                    productVersion: "2.1"
                    user: SA
                  metadata:
                    source: yaml-snapshot-parser-test
                """);

        YamlSnapshotParser parser = new YamlSnapshotParser();
        try (DirectoryResourceAccessor resourceAccessor = new DirectoryResourceAccessor(tempDirectory)) {
            DatabaseSnapshot snapshot = parser.parse("snapshot.yaml", resourceAccessor);

            assertThat(snapshot.getDatabase()).isInstanceOf(H2Database.class);
            assertThat(snapshot.getDatabase().getConnection()).isInstanceOf(OfflineConnection.class);
            assertThat(snapshot.getDatabase().getConnection().getURL()).isEqualTo("offline:h2");
            assertThat(snapshot.getMetadata()).containsEntry("source", "yaml-snapshot-parser-test");
        }
    }
}
