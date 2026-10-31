/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_ttddyy.datasource_proxy;

import net.ttddyy.dsproxy.proxy.ProxyJdbcObject;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Types;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JdkJdbcProxyFactoryTest {
    @Test
    void proxiesJdbcObjectsAndGeneratedKeysThroughTheBuilder() throws Exception {
        DataSource dataSource = ProxyDataSourceBuilder.create(TestDataSources.create("jdbcFactory"))
                .name("orders").proxyResultSet().proxyGeneratedKeys().buildProxy();
        assertInstanceOf(ProxyJdbcObject.class, dataSource);

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            assertInstanceOf(ProxyJdbcObject.class, connection);
            assertInstanceOf(ProxyJdbcObject.class, statement);
            assertSame(connection, statement.getConnection());
            statement.executeUpdate("""
                    CREATE TABLE orders (
                        id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                        customer VARCHAR(40)
                    )
                    """);
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO orders(customer) VALUES (?)", Statement.RETURN_GENERATED_KEYS)) {
                assertInstanceOf(ProxyJdbcObject.class, insert);
                insert.setString(1, "Ada");
                assertEquals(1, insert.executeUpdate());
                try (ResultSet keys = insert.getGeneratedKeys()) {
                    assertInstanceOf(ProxyJdbcObject.class, keys);
                    assertTrue(keys.next());
                    assertEquals(1L, keys.getLong(1));
                    assertFalse(keys.next());
                }
            }
            try (ResultSet rows = statement.executeQuery("SELECT customer FROM orders")) {
                assertInstanceOf(ProxyJdbcObject.class, rows);
                assertTrue(rows.next());
                assertEquals("Ada", rows.getString(1));
                assertFalse(rows.next());
            }
            try (CallableStatement call = connection.prepareCall("{? = call ABS(?)}")) {
                assertInstanceOf(ProxyJdbcObject.class, call);
                call.registerOutParameter(1, Types.INTEGER);
                call.setInt(2, -8);
                call.execute();
                assertEquals(8, call.getInt(1));
            }
        }
    }
}
