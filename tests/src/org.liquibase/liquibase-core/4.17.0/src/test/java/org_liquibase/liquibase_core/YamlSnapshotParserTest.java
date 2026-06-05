/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_liquibase.liquibase_core;

import liquibase.database.core.H2Database;
import liquibase.parser.SnapshotParser;
import liquibase.parser.SnapshotParserFactory;
import liquibase.parser.core.yaml.YamlSnapshotParser;
import liquibase.resource.DirectoryResourceAccessor;
import liquibase.snapshot.DatabaseSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class YamlSnapshotParserTest {

    @Test
    void parsesSnapshotYamlAndRestoresDatabase(@TempDir Path snapshotDirectory) throws Exception {
        Path snapshotFile = snapshotDirectory.resolve("snapshot.yaml");
        Files.writeString(snapshotFile, h2SnapshotYaml(), StandardCharsets.UTF_8);

        try (DirectoryResourceAccessor resourceAccessor =
                new DirectoryResourceAccessor(snapshotDirectory)) {
            SnapshotParser parser = SnapshotParserFactory.getInstance()
                    .getParser("snapshot.yaml", resourceAccessor);

            DatabaseSnapshot snapshot = parser.parse("snapshot.yaml", resourceAccessor);

            assertThat(parser).isInstanceOf(YamlSnapshotParser.class);
            assertThat(snapshot.getDatabase()).isInstanceOf(H2Database.class);
            assertThat(snapshot.getDatabase().getConnection().getURL()).isEqualTo("offline:h2");
            assertThat(snapshot.getMetadata()).containsEntry("source", "yaml-snapshot-parser-test");
        }
    }

    private static String h2SnapshotYaml() {
        return """
                snapshot:
                  database:
                    shortName: h2
                    majorVersion: 2
                    minorVersion: 1
                    productVersion: "2.1"
                    user: reachability-test
                  metadata:
                    source: yaml-snapshot-parser-test
                """;
    }
}
