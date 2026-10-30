/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite_avatica.avatica_core;

import org.apache.calcite.avatica.MetaImpl;
import org.apache.calcite.avatica.MetaImpl.MetaCatalog;
import org.apache.calcite.avatica.util.Cursor;
import org.apache.calcite.avatica.util.RecordIteratorCursor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RecordIteratorCursorTest {
    @Test
    void exposesCatalogFieldsAsNullableCursorColumns() throws Exception {
        List<MetaCatalog> rows = List.of(new MetaCatalog("warehouse"), new MetaCatalog(null));
        try (Cursor cursor = new RecordIteratorCursor<>(rows.iterator(), MetaCatalog.class)) {
            List<Cursor.Accessor> accessors = cursor.createAccessors(
                    List.of(MetaImpl.columnMetaData("TABLE_CAT", 0, String.class, true)), null, null);
            assertEquals(1, accessors.size());
            assertTrue(cursor.next());
            assertEquals("warehouse", accessors.get(0).getString());
            assertFalse(cursor.wasNull());
            assertTrue(cursor.next());
            assertNull(accessors.get(0).getString());
            assertTrue(accessors.get(0).wasNull());
            assertFalse(cursor.next());
        }
    }
}
