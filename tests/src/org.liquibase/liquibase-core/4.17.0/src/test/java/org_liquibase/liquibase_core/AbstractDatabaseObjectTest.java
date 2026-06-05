/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_liquibase.liquibase_core;

import liquibase.parser.core.ParsedNode;
import liquibase.structure.DatabaseObject;
import liquibase.structure.core.Table;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;

public class AbstractDatabaseObjectTest {

    @Test
    void loadConvertsSerializedDateAndStringConstructedAttributeValues() throws Exception {
        Table table = new Table();
        ParsedNode tableNode = new ParsedNode(null, "table");
        tableNode.addChild(null, "createdAt", "2020-01-02T03:04:05!{java.util.Date}");
        tableNode.addChild(null, "externalId", "123456789!{java.math.BigInteger}");

        table.load(tableNode, null);

        assertInstanceOf(Date.class, table.getAttribute("createdAt", Object.class));
        assertEquals(new BigInteger("123456789"), table.getAttribute("externalId", Object.class));
    }

    @Test
    void serializableFieldValueCopiesDatabaseObjectAttributes() {
        Table table = new Table().setName("orders");
        Table relatedTable = new Table().setName("customers");
        relatedTable.setSnapshotId("snapshot-customers");
        table.setAttribute("relatedTable", relatedTable);

        DatabaseObject clone = (DatabaseObject) table.getSerializableFieldValue("relatedTable");

        assertInstanceOf(Table.class, clone);
        assertNotSame(relatedTable, clone);
        assertEquals("customers", clone.getName());
        assertEquals("snapshot-customers", clone.getSnapshotId());
    }
}
