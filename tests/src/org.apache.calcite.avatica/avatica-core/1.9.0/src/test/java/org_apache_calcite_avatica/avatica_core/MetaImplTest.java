/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite_avatica.avatica_core;

import org.apache.calcite.avatica.Meta.CursorFactory;
import org.apache.calcite.avatica.MetaImpl;
import org.apache.calcite.avatica.MetaImpl.MetaCatalog;
import org.apache.calcite.avatica.MetaImpl.MetaSchema;
import org.apache.calcite.avatica.remote.Driver;
import org.apache.calcite.avatica.remote.MockJsonService;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

public class MetaImplTest {
    @Test
    void derivesJdbcSchemaColumnsFromMetadataRecords() throws Exception {
        Properties properties = new Properties();
        properties.setProperty("factory", MockJsonService.Factory.class.getName());
        try (Connection connection = new Driver().connect("jdbc:avatica:remote:", properties);
             ResultSet schemas = connection.getMetaData().getSchemas()) {
            ResultSetMetaData columns = schemas.getMetaData();
            assertEquals(2, columns.getColumnCount());
            assertEquals("TABLE_SCHEM", columns.getColumnLabel(1));
            assertEquals("TABLE_CATALOG", columns.getColumnLabel(2));
            assertEquals(DatabaseMetaData.columnNoNulls, columns.isNullable(1));
            assertEquals(DatabaseMetaData.columnNullable, columns.isNullable(2));
            assertFalse(schemas.next());
        }
    }

    @Test
    void collectsCatalogRecordsIntoRows() {
        List<Object> catalogs = List.of(new MetaCatalog("warehouse"), new MetaCatalog("reporting"));
        List<List<Object>> rows = new ArrayList<>();
        assertSame(rows, MetaImpl.collect(CursorFactory.record(MetaCatalog.class), catalogs, rows));
        assertEquals(List.of(List.of("warehouse"), List.of("reporting")), rows);
    }

    @Test
    void collectsProjectedFieldsInRequestedOrderIncludingNulls() {
        CursorFactory factory = CursorFactory.record(MetaSchema.class, null, List.of("tableCatalog", "tableSchem"));
        List<Object> schemas = List.of(new MetaSchema("warehouse", "sales"), new MetaSchema(null, "public"));
        List<List<Object>> rows = MetaImpl.collect(factory, schemas.iterator(), new ArrayList<>());
        assertEquals(List.of(List.of("warehouse", "sales"), Arrays.asList(null, "public")), rows);
    }
}
