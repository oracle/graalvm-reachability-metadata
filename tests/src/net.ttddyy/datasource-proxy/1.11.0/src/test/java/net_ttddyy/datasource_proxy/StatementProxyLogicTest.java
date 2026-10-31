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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StatementProxyLogicTest {
    @Test
    void appliesQueryAndParameterTransformersToSingleAndBatchInserts() throws Exception {
        DataSource dataSource = ProxyDataSourceBuilder.create(TestDataSources.create("statementTransforms"))
                .queryTransformer(info -> info.getQuery().replace("order_input", "order_values"))
                .parameterTransformer((replacer, info) -> {
                    String customer = replacer.getValue(1);
                    int quantity = replacer.getValue(2);
                    replacer.setString(1, customer.toUpperCase(Locale.ROOT));
                    replacer.setInt(2, quantity + 1);
                }).build();
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE order_values (customer VARCHAR(40), quantity INTEGER)");
            try (PreparedStatement insert = connection.prepareStatement("INSERT INTO order_input VALUES (?, ?)")) {
                insert.setString(1, "ada");
                insert.setInt(2, 2);
                assertEquals(1, insert.executeUpdate());
                insert.setString(1, "grace");
                insert.setInt(2, 4);
                insert.addBatch();
                insert.setString(1, "linus");
                insert.setInt(2, 6);
                insert.addBatch();
                assertArrayEquals(new int[]{1, 1}, insert.executeBatch());
            }
            try (ResultSet rows = statement.executeQuery("SELECT * FROM order_input ORDER BY quantity")) {
                assertTrue(rows.next());
                assertEquals("ADA", rows.getString(1));
                assertEquals(3, rows.getInt(2));
                assertTrue(rows.next());
                assertEquals("GRACE", rows.getString(1));
                assertEquals(5, rows.getInt(2));
                assertTrue(rows.next());
                assertEquals("LINUS", rows.getString(1));
                assertEquals(7, rows.getInt(2));
                assertFalse(rows.next());
            }
        }
    }
}
