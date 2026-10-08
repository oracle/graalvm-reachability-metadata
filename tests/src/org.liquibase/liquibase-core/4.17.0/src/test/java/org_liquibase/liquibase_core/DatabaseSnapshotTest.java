/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_liquibase.liquibase_core;

import liquibase.database.core.H2Database;
import liquibase.parser.core.ParsedNode;
import liquibase.snapshot.RestoredDatabaseSnapshot;
import liquibase.structure.core.Table;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class DatabaseSnapshotTest {

    @Test
    void loadsSnapshotObjectsFromSerializedTypeNames() throws Exception {
        RestoredDatabaseSnapshot snapshot = new RestoredDatabaseSnapshot(new H2Database());
        ParsedNode root = new ParsedNode(null, "snapshot")
                .addChild(snapshotObjectsNode());

        snapshot.load(root, null);

        Set<Table> tables = snapshot.get(Table.class);
        assertThat(tables).hasSize(1);
        Table restoredTable = tables.iterator().next();
        assertThat(restoredTable.getSnapshotId()).isEqualTo("table-1");
        assertThat(restoredTable.getName()).isEqualTo("person");
    }

    private static ParsedNode snapshotObjectsNode() throws Exception {
        ParsedNode tableNode = new ParsedNode(null, "table")
                .addChild(null, "snapshotId", "table-1")
                .addChild(null, "name", "person");
        ParsedNode tableTypeNode = new ParsedNode(null, Table.class.getName())
                .addChild(tableNode);
        return new ParsedNode(null, "objects")
                .addChild(tableTypeNode);
    }
}
