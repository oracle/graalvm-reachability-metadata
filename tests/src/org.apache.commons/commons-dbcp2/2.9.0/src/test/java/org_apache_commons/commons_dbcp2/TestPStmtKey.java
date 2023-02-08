/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_dbcp2;

import org.apache.commons.dbcp2.PStmtKey;
import org.apache.commons.dbcp2.PoolingConnection.StatementType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestPStmtKey {
    @Test
    public void testCtorDifferentCatalog() {
        Assertions.assertNotEquals(new PStmtKey("sql", "catalog1", "schema1"), new PStmtKey("sql", "catalog2", "schema1"));
        Assertions.assertNotEquals(new PStmtKey("sql", "catalog1", "schema1", 0),
                new PStmtKey("sql", "catalog2", "schema1", 0));
        Assertions.assertNotEquals(new PStmtKey("sql", "catalog1", "schema1", 0, 0),
                new PStmtKey("sql", "catalog2", "schema1", 0, 0));
        Assertions.assertNotEquals(new PStmtKey("sql", "catalog1", "schema1", 0, 0, 0),
                new PStmtKey("sql", "catalog2", "schema1", 0, 0, 0));
        Assertions.assertNotEquals(new PStmtKey("sql", "catalog1", "schema1", 0, 0, 0, null),
                new PStmtKey("sql", "catalog2", "schema1", 0, 0, 0, null));
        Assertions.assertNotEquals(new PStmtKey("sql", "catalog1", "schema1", 0, 0, 0, StatementType.PREPARED_STATEMENT),
                new PStmtKey("sql", "catalog2", "schema1", 0, 0, 0, StatementType.PREPARED_STATEMENT));
        Assertions.assertNotEquals(new PStmtKey("sql", "catalog1", "schema1", 0, 0, null),
                new PStmtKey("sql", "catalog2", "schema1", 0, 0, null));
        Assertions.assertNotEquals(new PStmtKey("sql", "catalog1", "schema1", 0, 0, StatementType.PREPARED_STATEMENT),
                new PStmtKey("sql", "catalog2", "schema1", 0, 0, StatementType.PREPARED_STATEMENT));
        Assertions.assertNotEquals(new PStmtKey("sql", "catalog1", "schema1", (int[]) null),
                new PStmtKey("sql", "catalog2", "schema1", (int[]) null));
        Assertions.assertNotEquals(new PStmtKey("sql", "catalog1", "schema1", new int[1]),
                new PStmtKey("sql", "catalog2", "schema1", new int[1]));
        Assertions.assertNotEquals(new PStmtKey("sql", "catalog1", "schema1", (String[]) null),
                new PStmtKey("sql", "catalog2", "schema1", (String[]) null));
        Assertions.assertNotEquals(new PStmtKey("sql", "catalog1", "schema1", new String[]{"A"}),
                new PStmtKey("sql", "catalog2", "schema1", new String[]{"A"}));
        Assertions.assertNotEquals(new PStmtKey("sql", "catalog1", "schema1", StatementType.PREPARED_STATEMENT),
                new PStmtKey("sql", "catalog2", "schema1", StatementType.PREPARED_STATEMENT));
        Assertions.assertNotEquals(
                new PStmtKey("sql", "catalog1", "schema1", StatementType.PREPARED_STATEMENT, Integer.MAX_VALUE),
                new PStmtKey("sql", "catalog2", "schema1", StatementType.PREPARED_STATEMENT, Integer.MAX_VALUE));
    }

    @Test
    public void testCtorDifferentSchema() {
        Assertions.assertNotEquals(new PStmtKey("sql", "catalog1", "schema1"), new PStmtKey("sql", "catalog1", "schema2"));
        Assertions.assertNotEquals(new PStmtKey("sql", "catalog1", "schema1", 0),
                new PStmtKey("sql", "catalog1", "schema2", 0));
        Assertions.assertNotEquals(new PStmtKey("sql", "catalog1", "schema1", 0, 0),
                new PStmtKey("sql", "catalog1", "schema2", 0, 0));
        Assertions.assertNotEquals(new PStmtKey("sql", "catalog1", "schema1", 0, 0, 0),
                new PStmtKey("sql", "catalog1", "schema2", 0, 0, 0));
        Assertions.assertNotEquals(new PStmtKey("sql", "catalog1", "schema1", 0, 0, 0, null),
                new PStmtKey("sql", "catalog1", "schema2", 0, 0, 0, null));
        Assertions.assertNotEquals(new PStmtKey("sql", "catalog1", "schema1", 0, 0, 0, StatementType.PREPARED_STATEMENT),
                new PStmtKey("sql", "catalog1", "schema2", 0, 0, 0, StatementType.PREPARED_STATEMENT));
        Assertions.assertNotEquals(new PStmtKey("sql", "catalog1", "schema1", 0, 0, null),
                new PStmtKey("sql", "catalog1", "schema2", 0, 0, null));
        Assertions.assertNotEquals(new PStmtKey("sql", "catalog1", "schema1", 0, 0, StatementType.PREPARED_STATEMENT),
                new PStmtKey("sql", "catalog1", "schema2", 0, 0, StatementType.PREPARED_STATEMENT));
        Assertions.assertNotEquals(new PStmtKey("sql", "catalog1", "schema1", (int[]) null),
                new PStmtKey("sql", "catalog1", "schema2", (int[]) null));
        Assertions.assertNotEquals(new PStmtKey("sql", "catalog1", "schema1", new int[1]),
                new PStmtKey("sql", "catalog1", "schema2", new int[1]));
        Assertions.assertNotEquals(new PStmtKey("sql", "catalog1", "schema1", (String[]) null),
                new PStmtKey("sql", "catalog1", "schema2", (String[]) null));
        Assertions.assertNotEquals(new PStmtKey("sql", "catalog1", "schema1", new String[]{"A"}),
                new PStmtKey("sql", "catalog1", "schema2", new String[]{"A"}));
        Assertions.assertNotEquals(new PStmtKey("sql", "catalog1", "schema1", StatementType.PREPARED_STATEMENT),
                new PStmtKey("sql", "catalog1", "schema2", StatementType.PREPARED_STATEMENT));
        Assertions.assertNotEquals(
                new PStmtKey("sql", "catalog1", "schema1", StatementType.PREPARED_STATEMENT, Integer.MAX_VALUE),
                new PStmtKey("sql", "catalog1", "schema2", StatementType.PREPARED_STATEMENT, Integer.MAX_VALUE));
    }

    @Test
    public void testCtorEquals() {
        Assertions.assertEquals(new PStmtKey("sql", "catalog1", "schema1"), new PStmtKey("sql", "catalog1", "schema1"));
        Assertions.assertEquals(new PStmtKey("sql", "catalog1", "schema1", 0),
                new PStmtKey("sql", "catalog1", "schema1", 0));
        Assertions.assertEquals(new PStmtKey("sql", "catalog1", "schema1", 0, 0),
                new PStmtKey("sql", "catalog1", "schema1", 0, 0));
        Assertions.assertEquals(new PStmtKey("sql", "catalog1", "schema1", 0, 0, 0),
                new PStmtKey("sql", "catalog1", "schema1", 0, 0, 0));
        Assertions.assertEquals(new PStmtKey("sql", "catalog1", "schema1", 0, 0, 0, null),
                new PStmtKey("sql", "catalog1", "schema1", 0, 0, 0, null));
        Assertions.assertEquals(new PStmtKey("sql", "catalog1", "schema1", 0, 0, 0, StatementType.PREPARED_STATEMENT),
                new PStmtKey("sql", "catalog1", "schema1", 0, 0, 0, StatementType.PREPARED_STATEMENT));
        Assertions.assertEquals(new PStmtKey("sql", "catalog1", "schema1", 0, 0, null),
                new PStmtKey("sql", "catalog1", "schema1", 0, 0, null));
        Assertions.assertEquals(new PStmtKey("sql", "catalog1", "schema1", 0, 0, StatementType.PREPARED_STATEMENT),
                new PStmtKey("sql", "catalog1", "schema1", 0, 0, StatementType.PREPARED_STATEMENT));
        Assertions.assertEquals(new PStmtKey("sql", "catalog1", "schema1", (int[]) null),
                new PStmtKey("sql", "catalog1", "schema1", (int[]) null));
        Assertions.assertEquals(new PStmtKey("sql", "catalog1", "schema1", new int[1]),
                new PStmtKey("sql", "catalog1", "schema1", new int[1]));
        Assertions.assertEquals(new PStmtKey("sql", "catalog1", "schema1", (String[]) null),
                new PStmtKey("sql", "catalog1", "schema1", (String[]) null));
        Assertions.assertEquals(new PStmtKey("sql", "catalog1", "schema1", new String[]{"A"}),
                new PStmtKey("sql", "catalog1", "schema1", new String[]{"A"}));
        Assertions.assertEquals(new PStmtKey("sql", "catalog1", "schema1", StatementType.PREPARED_STATEMENT),
                new PStmtKey("sql", "catalog1", "schema1", StatementType.PREPARED_STATEMENT));
        Assertions.assertEquals(
                new PStmtKey("sql", "catalog1", "schema1", StatementType.PREPARED_STATEMENT, Integer.MAX_VALUE),
                new PStmtKey("sql", "catalog1", "schema1", StatementType.PREPARED_STATEMENT, Integer.MAX_VALUE));
    }

    @Test
    public void testCtorStringStringArrayOfInts() {
        final int[] input = {0, 0};
        final PStmtKey pStmtKey = new PStmtKey("", "", "", input);
        Assertions.assertArrayEquals(input, pStmtKey.getColumnIndexes());
        input[0] = 1;
        input[1] = 1;
        Assertions.assertFalse(Arrays.equals(input, pStmtKey.getColumnIndexes()));
    }

    @Test
    public void testCtorStringStringArrayOfNullInts() {
        final int[] input = null;
        final PStmtKey pStmtKey = new PStmtKey("", "", "", input);
        Assertions.assertArrayEquals(input, pStmtKey.getColumnIndexes());
    }

    @Test
    public void testCtorStringStringArrayOfNullStrings() {
        final String[] input = null;
        final PStmtKey pStmtKey = new PStmtKey("", "", "", input);
        Assertions.assertArrayEquals(input, pStmtKey.getColumnNames());
    }

    @Test
    public void testCtorStringStringArrayOfStrings() {
        final String[] input = {"A", "B"};
        final PStmtKey pStmtKey = new PStmtKey("", "", "", input);
        Assertions.assertArrayEquals(input, pStmtKey.getColumnNames());
        input[0] = "C";
        input[1] = "D";
        Assertions.assertFalse(Arrays.equals(input, pStmtKey.getColumnNames()));
    }

    @Test
    public void testEquals() {
        final PStmtKey pStmtKey = new PStmtKey("SELECT 1", "catalog", "public",
                ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY,
                StatementType.CALLABLE_STATEMENT);
        assertEquals(pStmtKey, pStmtKey);
        assertNotEquals(null, pStmtKey);
        assertNotEquals(pStmtKey, new Object());
        assertNotEquals(pStmtKey, new PStmtKey("SELECT 2", "catalog", "public",
                ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY,
                StatementType.CALLABLE_STATEMENT));
        assertNotEquals(pStmtKey, new PStmtKey("SELECT 1", "anothercatalog", "public",
                ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY,
                StatementType.CALLABLE_STATEMENT));
        assertNotEquals(pStmtKey, new PStmtKey("SELECT 1", "catalog", "private",
                ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY,
                StatementType.CALLABLE_STATEMENT));
        assertNotEquals(pStmtKey, new PStmtKey("SELECT 1", "catalog", "public",
                ResultSet.TYPE_SCROLL_SENSITIVE, ResultSet.CONCUR_READ_ONLY,
                StatementType.CALLABLE_STATEMENT));
        assertNotEquals(pStmtKey, new PStmtKey("SELECT 1", "catalog", "public",
                ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE,
                StatementType.CALLABLE_STATEMENT));
        assertNotEquals(pStmtKey, new PStmtKey("SELECT 1", "catalog", "public",
                ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY,
                StatementType.PREPARED_STATEMENT));
        assertEquals(pStmtKey, new PStmtKey("SELECT 1", "catalog", "public",
                ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY,
                StatementType.CALLABLE_STATEMENT));
        assertEquals(pStmtKey.hashCode(), new PStmtKey("SELECT 1", "catalog", "public",
                ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY,
                StatementType.CALLABLE_STATEMENT).hashCode());
    }

    @Test
    public void testGettersSetters() {
        final PStmtKey pStmtKey = new PStmtKey("SELECT 1", "catalog", "public");
        assertEquals("SELECT 1", pStmtKey.getSql());
        assertEquals("public", pStmtKey.getSchema());
        assertEquals("catalog", pStmtKey.getCatalog());
        assertNull(pStmtKey.getAutoGeneratedKeys());
        assertNull(pStmtKey.getResultSetConcurrency());
        assertNull(pStmtKey.getResultSetHoldability());
        assertNull(pStmtKey.getResultSetType());
        assertEquals(StatementType.PREPARED_STATEMENT, pStmtKey.getStmtType());
    }

    @Test
    public void testToString() {
        final PStmtKey pStmtKey = new PStmtKey("SELECT 1", "catalog", "public",
                StatementType.CALLABLE_STATEMENT, Statement.RETURN_GENERATED_KEYS);
        assertTrue(pStmtKey.toString().contains("sql=SELECT 1"));
        assertTrue(pStmtKey.toString().contains("schema=public"));
        assertTrue(pStmtKey.toString().contains("autoGeneratedKeys=1"));
        assertTrue(pStmtKey.toString().contains("statementType=CALLABLE_STATEMENT"));
    }
}
