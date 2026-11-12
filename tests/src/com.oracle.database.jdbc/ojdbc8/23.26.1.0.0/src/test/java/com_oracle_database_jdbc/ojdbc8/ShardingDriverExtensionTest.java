/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_database_jdbc.ojdbc8;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import oracle.jdbc.driver.OracleDriver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;

public class ShardingDriverExtensionTest {
    @Test
    @Timeout(170)
    @ExtendWith(OracleDatabase.class)
    void connectsWithAnExplicitTargetShardingKey() throws Exception {
        Properties properties = OracleDatabase.properties();
        properties.setProperty("oracle.jdbc.useShardingDriverConnection", "true");
        properties.setProperty("oracle.jdbc.targetShardingKey", "test-key");
        try (Connection connection = OracleDatabase.openConnection(properties);
                Statement statement = connection.createStatement()) {
            statement.setQueryTimeout(30);
            try (ResultSet rows = statement.executeQuery("SELECT 42 FROM dual")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt(1)).isEqualTo(42);
                assertThat(rows.next()).isFalse();
            }
        }
    }

    @Test
    void createsTheConfiguredShardingConnectionImplementation() {
        Properties properties = new Properties();
        properties.setProperty("oracle.jdbc.useShardingDriverConnection", "true");
        properties.setProperty("oracle.net.CONNECT_TIMEOUT", "10000");
        properties.setProperty("oracle.jdbc.ReadTimeout", "10000");

        SQLException failure = catchThrowableOfType(
                () -> new OracleDriver()
                        .connect(
                                "jdbc:oracle:thin:@//127.0.0.1:1/"
                                        + "test-service?SHARDING_KEY=test-key",
                                properties),
                SQLException.class);

        assertThat(failure.getErrorCode()).isEqualTo(12541);
    }
}
