/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite_avatica.avatica_core;

import org.apache.calcite.avatica.Meta.DatabaseProperty;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MetaInnerDatabasePropertyTest {
    @Test
    void readsTypedDatabasePropertiesAndDefaults() {
        Map<DatabaseProperty, Object> values = Map.of(
                DatabaseProperty.GET_DATABASE_PRODUCT_NAME, "Warehouse",
                DatabaseProperty.GET_DEFAULT_TRANSACTION_ISOLATION, Connection.TRANSACTION_SERIALIZABLE);
        assertEquals("Warehouse", DatabaseProperty.GET_DATABASE_PRODUCT_NAME.getProp(values, String.class));
        assertEquals(Connection.TRANSACTION_SERIALIZABLE,
                DatabaseProperty.GET_DEFAULT_TRANSACTION_ISOLATION.getProp(values, Integer.class).intValue());
        assertEquals("", DatabaseProperty.GET_STRING_FUNCTIONS.getProp(values, String.class));
        assertTrue(DatabaseProperty.GET_DATABASE_PRODUCT_NAME.isJdbc);
        assertEquals("getDatabaseProductName", DatabaseProperty.GET_DATABASE_PRODUCT_NAME.method.getName());
        assertEquals(DatabaseProperty.GET_DATABASE_PRODUCT_NAME,
                DatabaseProperty.fromProto(DatabaseProperty.GET_DATABASE_PRODUCT_NAME.toProto()));
    }
}
