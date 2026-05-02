/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_zaxxer.HikariCP;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariCredentialsProvider;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.util.Credentials;
import com.zaxxer.hikaricp.test.support.RecordingDataSource;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

public class HikariCPTest {
    @Test
    void createsPoolAndReturnsReusableConnectionsFromConfiguredDataSource() throws SQLException {
        HikariConfig config = new HikariConfig();
        config.setPoolName("basic-hikari-pool");
        config.setDataSource(new RecordingDataSource());
        config.setMaximumPoolSize(2);
        config.setMinimumIdle(0);
        config.setConnectionTimeout(1_000);
        config.setValidationTimeout(500);
        config.setInitializationFailTimeout(1_000);

        try (HikariDataSource dataSource = new HikariDataSource(config)) {
            try (Connection firstConnection = dataSource.getConnection();
                    Connection secondConnection = dataSource.getConnection()) {
                assertThat(firstConnection.isValid(1)).isTrue();
                assertThat(secondConnection.isValid(1)).isTrue();
                assertThat(dataSource.getHikariPoolMXBean().getActiveConnections()).isEqualTo(2);
            }

            assertThat(dataSource.isRunning()).isTrue();
        }
    }

    @Test
    void usesCredentialsProviderWhenCreatingPooledConnections() throws SQLException {
        RecordingDataSource recordingDataSource = new RecordingDataSource();
        StaticCredentialsProvider credentialsProvider = new StaticCredentialsProvider();
        HikariConfig config = new HikariConfig();
        config.setPoolName("credentials-provider-pool");
        config.setDataSource(recordingDataSource);
        config.setCredentialsProvider(credentialsProvider);
        config.setMaximumPoolSize(1);
        config.setMinimumIdle(0);
        config.setConnectionTimeout(1_000);
        config.setValidationTimeout(500);
        config.setInitializationFailTimeout(1_000);

        try (HikariDataSource dataSource = new HikariDataSource(config);
                Connection connection = dataSource.getConnection()) {
            assertThat(connection.isValid(1)).isTrue();
            assertThat(recordingDataSource.getLastUsername()).isEqualTo("credentialed-user");
            assertThat(recordingDataSource.getLastPassword()).isEqualTo("credentialed-password");
        }
    }

    @Test
    void appliesConfiguredConnectionStateToBorrowedConnections() throws SQLException {
        HikariConfig config = new HikariConfig();
        config.setPoolName("connection-state-pool");
        config.setDataSource(new RecordingDataSource());
        config.setMaximumPoolSize(1);
        config.setMinimumIdle(0);
        config.setConnectionTimeout(1_000);
        config.setValidationTimeout(500);
        config.setInitializationFailTimeout(1_000);
        config.setAutoCommit(false);
        config.setReadOnly(true);
        config.setCatalog("tenant_catalog");
        config.setSchema("tenant_schema");
        config.setTransactionIsolation("TRANSACTION_SERIALIZABLE");

        try (HikariDataSource dataSource = new HikariDataSource(config);
                Connection connection = dataSource.getConnection()) {
            assertThat(connection.getAutoCommit()).isFalse();
            assertThat(connection.isReadOnly()).isTrue();
            assertThat(connection.getCatalog()).isEqualTo("tenant_catalog");
            assertThat(connection.getSchema()).isEqualTo("tenant_schema");
            assertThat(connection.getTransactionIsolation()).isEqualTo(Connection.TRANSACTION_SERIALIZABLE);
        }
    }

    public static final class StaticCredentialsProvider implements HikariCredentialsProvider {
        @Override
        public Credentials getCredentials() {
            return Credentials.of("credentialed-user", "credentialed-password");
        }
    }
}
