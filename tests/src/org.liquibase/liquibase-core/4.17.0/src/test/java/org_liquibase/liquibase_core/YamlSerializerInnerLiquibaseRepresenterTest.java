/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_liquibase.liquibase_core;

import liquibase.database.OfflineConnection;
import liquibase.database.core.H2Database;
import liquibase.parser.core.ParsedNode;
import liquibase.resource.ClassLoaderResourceAccessor;
import liquibase.serializer.core.yaml.YamlSnapshotSerializer;
import liquibase.snapshot.DatabaseSnapshot;
import liquibase.snapshot.RestoredDatabaseSnapshot;
import liquibase.structure.core.Table;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class YamlSerializerInnerLiquibaseRepresenterTest {

    @Test
    void serializesSnapshotDatabaseObjectsThroughLiquibaseRepresenter() throws Exception {
        try (ClassLoaderResourceAccessor resourceAccessor = new ClassLoaderResourceAccessor()) {
            H2Database database = new H2Database();
            database.setConnection(new OfflineConnection("offline:h2", resourceAccessor));
            DatabaseSnapshot snapshot = new RestoredDatabaseSnapshot(database);
            ParsedNode parsedSnapshot = new ParsedNode(null, "snapshot");
            ParsedNode objects = new ParsedNode(null, "objects");
            ParsedNode tables = new ParsedNode(null, Table.class.getName());
            ParsedNode table = new ParsedNode(null, "table");
            table.addChild(null, "snapshotId", "table-1");
            table.addChild(null, "name", "person");
            tables.addChild(table);
            objects.addChild(tables);
            parsedSnapshot.addChild(objects);

            snapshot.load(parsedSnapshot, resourceAccessor);

            String serializedSnapshot = new YamlSnapshotSerializer().serialize(snapshot, true);

            assertThat(serializedSnapshot)
                    .contains(Table.class.getName())
                    .contains("snapshotId: table-1");
        }
    }
}
