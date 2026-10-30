/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oceanbase.oceanbase_client;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

public class ProtocolLoggingProxyTest extends DatabaseTestSupport {
    @Test
    void profilesQueriesAndBatchesWithoutChangingTheirResults() throws Exception {
        Properties properties = new Properties();
        properties.setProperty("profileSql", "true");
        try (Connection connection = database.connect("", properties);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TEMPORARY TABLE profiled_values (value INT)");
            try (PreparedStatement insert = connection.prepareStatement("INSERT INTO profiled_values VALUES (?)")) {
                insert.setInt(1, 10);
                insert.addBatch();
                insert.setInt(1, 32);
                insert.addBatch();
                assertThat(insert.executeBatch()).containsExactly(1, 1);
            }
            try (PreparedStatement select = connection.prepareStatement("SELECT SUM(value) + ? FROM profiled_values")) {
                select.setInt(1, 0);
                try (ResultSet result = select.executeQuery()) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getInt(1)).isEqualTo(42);
                    assertThat(result.next()).isFalse();
                }
            }
            assertThat(connection.getAutoCommit()).isTrue();
            assertThat(connection.isValid(10)).isTrue();
        }
    }
}
