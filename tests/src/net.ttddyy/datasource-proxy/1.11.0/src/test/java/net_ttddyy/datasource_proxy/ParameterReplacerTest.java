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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ParameterReplacerTest {
    @Test
    void replacesTypedParametersBeforeExecutingAPreparedQuery() throws Exception {
        DataSource dataSource = ProxyDataSourceBuilder.create(TestDataSources.create("parameterReplacer"))
                .parameterTransformer((replacer, info) -> {
                    String label = replacer.getValue(2);
                    replacer.clearParameters();
                    replacer.setLong(1, 41L);
                    replacer.setString(2, label + "-stored");
                    replacer.setBigDecimal(3, new BigDecimal("12.50"));
                    replacer.setBoolean(4, true);
                    replacer.setNull(5, Types.VARCHAR);
                }).build();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement query = connection.prepareStatement("""
                     SELECT CAST(? AS BIGINT), CAST(? AS VARCHAR),
                            CAST(? AS DECIMAL(12, 2)), CAST(? AS BOOLEAN), CAST(? AS VARCHAR)
                     """)) {
            query.setLong(1, 1L);
            query.setString(2, "order");
            query.setBigDecimal(3, BigDecimal.ZERO);
            query.setBoolean(4, false);
            query.setString(5, "unused");
            try (ResultSet rows = query.executeQuery()) {
                assertTrue(rows.next());
                assertEquals(41L, rows.getLong(1));
                assertEquals("order-stored", rows.getString(2));
                assertEquals(new BigDecimal("12.50"), rows.getBigDecimal(3));
                assertTrue(rows.getBoolean(4));
                assertNull(rows.getString(5));
                assertTrue(rows.wasNull());
                assertFalse(rows.next());
            }
        }
    }
}
