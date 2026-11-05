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
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RepeatableReadResultSetProxyLogicAnonymous2Test {
    @Test
    void convertsCachedDecimalUsingNumberConversionMethods() throws Exception {
        DataSource dataSource = ProxyDataSourceBuilder.create(TestDataSources.create("repeatableNumbers"))
                .repeatableReadResultSet().build();
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT CAST(19.50 AS DECIMAL(8, 2)) AS price")) {
            assertTrue(rows.next());
            assertEquals(new BigDecimal("19.50"), rows.getBigDecimal(1));
            assertFalse(rows.next());
            rows.beforeFirst();
            assertTrue(rows.next());
            assertEquals(19.5, rows.getDouble("price"));
            assertEquals(19.5f, rows.getFloat(1));
            assertEquals(19L, rows.getLong(1));
            assertEquals(19, rows.getInt(1));
            assertFalse(rows.next());
        }
    }
}
