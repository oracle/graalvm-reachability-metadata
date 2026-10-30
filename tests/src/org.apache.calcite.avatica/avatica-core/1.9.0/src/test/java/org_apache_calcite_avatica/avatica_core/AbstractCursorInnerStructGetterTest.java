/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite_avatica.avatica_core;

import org.apache.calcite.avatica.ColumnMetaData;
import org.apache.calcite.avatica.MetaImpl;
import org.apache.calcite.avatica.MetaImpl.MetaSchema;
import org.apache.calcite.avatica.util.ArrayIteratorCursor;
import org.apache.calcite.avatica.util.Cursor;
import org.junit.jupiter.api.Test;

import java.sql.Struct;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AbstractCursorInnerStructGetterTest {
    @Test
    void convertsRecordValuedStructColumnsToJdbcAttributes() throws Exception {
        ColumnMetaData.StructType schemaType = ColumnMetaData.struct(List.of(
                MetaImpl.columnMetaData("tableSchem", 0, String.class, false),
                MetaImpl.columnMetaData("tableCatalog", 1, String.class, true)));
        List<Object[]> rows = Arrays.asList(
                new Object[]{new MetaSchema("warehouse", "sales")},
                new Object[]{new MetaSchema(null, "public")},
                new Object[]{null});
        try (Cursor cursor = new ArrayIteratorCursor(rows.iterator())) {
            Cursor.Accessor column = cursor.createAccessors(
                    List.of(ColumnMetaData.dummy(schemaType, true)), null, null).get(0);
            assertTrue(cursor.next());
            assertArrayEquals(new Object[]{"sales", "warehouse"}, ((Struct) column.getObject()).getAttributes());
            assertTrue(cursor.next());
            assertArrayEquals(new Object[]{"public", null}, ((Struct) column.getObject()).getAttributes());
            assertTrue(cursor.next());
            assertNull(column.getObject());
            assertTrue(column.wasNull());
            assertFalse(cursor.next());
        }
    }
}
