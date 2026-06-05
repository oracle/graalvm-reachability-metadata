/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_liquibase.liquibase_core;

import liquibase.database.core.H2Database;
import liquibase.parser.core.ParsedNode;
import liquibase.resource.ClassLoaderResourceAccessor;
import liquibase.snapshot.DatabaseSnapshot;
import liquibase.snapshot.RestoredDatabaseSnapshot;
import liquibase.structure.core.Table;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class DatabaseSnapshotTest {

    @Test
    void loadsSerializedDatabaseObjectsByClassName() throws Exception {
        DatabaseSnapshot snapshot = new RestoredDatabaseSnapshot(new H2Database());
        ParsedNode parsedSnapshot = new ParsedNode(null, "snapshot");
        ParsedNode objects = new ParsedNode(null, "objects");
        ParsedNode tables = new ParsedNode(null, Table.class.getName());
        ParsedNode table = new ParsedNode(null, "table");
        table.addChild(null, "snapshotId", "table-1");
        table.addChild(null, "name", "person");
        tables.addChild(table);
        objects.addChild(tables);
        parsedSnapshot.addChild(objects);

        try (ClassLoaderResourceAccessor resourceAccessor = new ClassLoaderResourceAccessor()) {
            snapshot.load(parsedSnapshot, resourceAccessor);
        }

        Set<Table> loadedTables = snapshot.get(Table.class);
        assertThat(loadedTables)
                .singleElement()
                .extracting(Table::getName, Table::getSnapshotId)
                .containsExactly("person", "table-1");
    }
}
