/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_liquibase.liquibase_core;

import liquibase.parser.core.ParsedNode;
import liquibase.structure.core.Table;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.sql.Date;

import static org.assertj.core.api.Assertions.assertThat;

public class AbstractDatabaseObjectTest {

    @Test
    void serializesDatabaseObjectAttributeAsDetachedObjectReference() {
        Table referencedTable = new Table().setName("address");
        referencedTable.setSnapshotId("table-snapshot");
        Table owner = new Table();
        owner.setAttribute("referencedTable", referencedTable);

        Object serializedValue = owner.getSerializableFieldValue("referencedTable");

        assertThat(serializedValue)
                .isInstanceOf(Table.class)
                .isNotSameAs(referencedTable);
        Table serializedTable = (Table) serializedValue;
        assertThat(serializedTable.getName()).isEqualTo("address");
        assertThat(serializedTable.getSnapshotId()).isEqualTo("table-snapshot");
    }

    @Test
    void loadsEscapedDateAttributeUsingDeclaredClassName() throws Exception {
        Table table = new Table();
        ParsedNode root = new ParsedNode(null, "table")
                .addChild(null, "createdOn", "2024-01-02!{java.sql.Date}");

        table.load(root, null);

        assertThat(table.getAttribute("createdOn", Date.class)).isEqualTo(Date.valueOf("2024-01-02"));
    }

    @Test
    void loadsEscapedStringConstructedAttributeUsingDeclaredClassName() throws Exception {
        Table table = new Table();
        ParsedNode root = new ParsedNode(null, "table")
                .addChild(null, "rowCount", "12345678901234567890!{java.math.BigInteger}");

        table.load(root, null);

        assertThat(table.getAttribute("rowCount", BigInteger.class)).isEqualTo(new BigInteger("12345678901234567890"));
    }
}
