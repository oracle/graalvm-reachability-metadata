/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_ttddyy.datasource_proxy;

import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RepeatableReadResultSetProxyLogicTest {
    @Test
    void replaysRowsAndConvertsCachedNumericValues() throws Exception {
        DataSource dataSource = ProxyDataSourceBuilder.create(TestDataSources.create("repeatableRows"))
                .repeatableReadResultSet().build();
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("""
                     SELECT 12 AS amount, CAST(NULL AS VARCHAR) AS note
                     UNION ALL SELECT 27, 'ready'
                     ORDER BY amount
                     """)) {
            assertTrue(rows.next());
            assertEquals(12, rows.getInt("amount"));
            assertNull(rows.getString("note"));
            assertTrue(rows.wasNull());
            assertTrue(rows.next());
            assertEquals("ready", rows.getString(2));
            assertFalse(rows.next());

            rows.beforeFirst();
            assertTrue(rows.next());
            assertEquals(12L, rows.getLong(1));
            assertEquals((short) 12, rows.getShort("amount"));
            assertEquals((byte) 12, rows.getByte(1));
            assertEquals(12.0f, rows.getFloat(1));
            assertEquals(12.0, rows.getDouble(1));
            assertFalse(rows.wasNull());
            assertNull(rows.getString(2));
            assertTrue(rows.wasNull());
            assertTrue(rows.next());
            assertEquals(27, rows.getInt(1));
            assertEquals("ready", rows.getString("note"));
            assertFalse(rows.next());
        }
    }
}
