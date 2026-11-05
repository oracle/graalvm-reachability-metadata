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
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ProxyLogicSupportTest {
    @Test
    void delegatesConnectionStateAndTransactionOperations() throws Exception {
        DataSource dataSource = ProxyDataSourceBuilder.create(TestDataSources.create("delegation"))
                .proxyResultSet().buildProxy();
        Connection connection = dataSource.getConnection();
        try (connection; Statement statement = connection.createStatement()) {
            assertFalse(connection.isClosed());
            statement.executeUpdate("CREATE TABLE payments (amount INTEGER)");
            connection.setAutoCommit(false);
            assertFalse(connection.getAutoCommit());
            statement.executeUpdate("INSERT INTO payments VALUES (15)");
            connection.rollback();
            try (ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM payments")) {
                assertTrue(rows.next());
                assertEquals(0, rows.getInt(1));
            }
            statement.executeUpdate("INSERT INTO payments VALUES (9)");
            connection.commit();
            try (ResultSet rows = statement.executeQuery("SELECT SUM(amount) FROM payments")) {
                assertTrue(rows.next());
                assertEquals(9, rows.getInt(1));
            }
        }
        assertTrue(connection.isClosed());
    }
}
