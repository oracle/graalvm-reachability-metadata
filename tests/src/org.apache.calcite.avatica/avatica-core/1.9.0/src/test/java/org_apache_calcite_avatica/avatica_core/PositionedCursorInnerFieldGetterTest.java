/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite_avatica.avatica_core;

import org.apache.calcite.avatica.Meta.CursorFactory;
import org.apache.calcite.avatica.MetaImpl;
import org.apache.calcite.avatica.MetaImpl.MetaSchema;
import org.apache.calcite.avatica.util.Cursor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PositionedCursorInnerFieldGetterTest {
    @Test
    void readsProjectedSchemaFieldsAndTracksNullValues() throws Exception {
        CursorFactory factory = CursorFactory.record(MetaSchema.class, null, List.of("tableCatalog", "tableSchem"));
        List<Object> rows = List.of(new MetaSchema("warehouse", "sales"), new MetaSchema(null, "public"));
        try (Cursor cursor = MetaImpl.createCursor(factory, rows)) {
            List<Cursor.Accessor> columns = cursor.createAccessors(List.of(
                    MetaImpl.columnMetaData("TABLE_CATALOG", 0, String.class, true),
                    MetaImpl.columnMetaData("TABLE_SCHEM", 1, String.class, false)), null, null);
            assertTrue(cursor.next());
            assertEquals("warehouse", columns.get(0).getString());
            assertFalse(cursor.wasNull());
            assertEquals("sales", columns.get(1).getString());
            assertTrue(cursor.next());
            assertNull(columns.get(0).getObject());
            assertTrue(cursor.wasNull());
            assertEquals("public", columns.get(1).getString());
            assertFalse(cursor.wasNull());
            assertFalse(cursor.next());
        }
    }
}
