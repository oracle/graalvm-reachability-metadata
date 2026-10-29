/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oceanbase.oceanbase_client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

public class AbstractMastersListenerTest extends DatabaseTestSupport {
    @Test
    void forwardsStatementsAndServerPreparedStatementsInLoadBalanceMode() throws Exception {
        Properties properties = new Properties();
        properties.setProperty("useServerPrepStmts", "true");
        try (Connection connection = database.connect("loadbalance", properties);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TEMPORARY TABLE master_values (value INT)");
            connection.setAutoCommit(false);
            try (PreparedStatement insert = connection.prepareStatement("INSERT INTO master_values VALUES (?)")) {
                insert.setInt(1, 37);
                assertThat(insert.executeUpdate()).isEqualTo(1);
            }
            connection.commit();
            try (PreparedStatement select = connection.prepareStatement("SELECT value + ? FROM master_values")) {
                select.setInt(1, 5);
                try (ResultSet result = select.executeQuery()) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getInt(1)).isEqualTo(42);
                    assertThat(result.next()).isFalse();
                }
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void retriesReadOnlyQueriesAfterTheServerDisconnectsAnIdleSession(boolean serverPrepared) throws Exception {
        Properties properties = new Properties();
        properties.setProperty("useServerPrepStmts", Boolean.toString(serverPrepared));
        try (Connection connection = database.connect("loadbalance", properties);
             Connection administrator = database.connect("", new Properties());
             Statement identity = connection.createStatement();
             Statement control = administrator.createStatement();
             PreparedStatement query = connection.prepareStatement("SELECT ? + 1")) {
            query.setInt(1, 41);
            long session;
            try (ResultSet result = identity.executeQuery("SELECT CONNECTION_ID()")) {
                assertThat(result.next()).isTrue();
                session = result.getLong(1);
            }
            control.execute("KILL CONNECTION " + session);
            try (ResultSet result = serverPrepared ? query.executeQuery() : identity.executeQuery("SELECT 41 + 1")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getInt(1)).isEqualTo(42);
                assertThat(result.next()).isFalse();
            }
            try (ResultSet result = identity.executeQuery("SELECT CONNECTION_ID()")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getLong(1)).isNotEqualTo(session);
            }
        }
    }
}
