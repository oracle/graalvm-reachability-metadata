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

public class MastersSlavesListenerTest extends DatabaseTestSupport {
    @Test
    void routesWritesAndReadOnlyQueriesThroughReplicationConnections() throws Exception {
        try (Connection connection = database.connect("replication", new Properties());
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE replicated_values (value INT)");
            try (PreparedStatement insert = connection.prepareStatement("INSERT INTO replicated_values VALUES (?)")) {
                insert.setInt(1, 42);
                assertThat(insert.executeUpdate()).isEqualTo(1);
            }
            connection.setReadOnly(true);
            assertThat(connection.isReadOnly()).isTrue();
            try (Statement reader = connection.createStatement();
                 ResultSet result = reader.executeQuery("SELECT value FROM replicated_values")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getInt(1)).isEqualTo(42);
                assertThat(result.next()).isFalse();
            }
            connection.setReadOnly(false);
            assertThat(connection.isReadOnly()).isFalse();
            assertThat(statement.executeUpdate("UPDATE replicated_values SET value = 43")).isEqualTo(1);
        }
    }
}
