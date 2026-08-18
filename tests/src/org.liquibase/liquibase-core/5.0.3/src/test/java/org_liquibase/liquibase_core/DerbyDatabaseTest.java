/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_liquibase.liquibase_core;

import liquibase.database.core.DerbyDatabase;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.DatabaseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Properties;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;

public class DerbyDatabaseTest {

    @BeforeEach
    void resetDriver() {
        SuccessfulShutdownDriver.lastUrl = null;
        SuccessfulShutdownDriver.connectCalls = 0;
    }

    @Test
    void shutdownDerbyLoadsDriverWithConnectionClassLoaderAndConnectsToShutdownUrl() throws Exception {
        String url = "jdbc:derby:memory:derbyDatabaseTest;create=true";
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:derbyDatabaseTest;DB_CLOSE_DELAY=-1")) {
            TestableDerbyDatabase database = new TestableDerbyDatabase();
            database.setConnection(new JdbcConnection(connection));

            database.shutdown(url, SuccessfulShutdownDriver.class.getName());
        }

        assertThat(SuccessfulShutdownDriver.connectCalls).isEqualTo(1);
        assertThat(SuccessfulShutdownDriver.lastUrl).isEqualTo("jdbc:derby:memory:derbyDatabaseTest;shutdown=true");
    }

    public static final class TestableDerbyDatabase extends DerbyDatabase {
        void shutdown(String url, String driverName) throws DatabaseException {
            shutdownDerby(url, driverName);
        }
    }

    public static final class SuccessfulShutdownDriver implements Driver {
        private static String lastUrl;
        private static int connectCalls;

        public SuccessfulShutdownDriver() {
        }

        @Override
        public Connection connect(String url, Properties info) throws SQLException {
            lastUrl = url;
            connectCalls++;
            throw new SQLException("Derby shutdown succeeded", "XJ015");
        }

        @Override
        public boolean acceptsURL(String url) {
            return url != null && url.startsWith("jdbc:derby:");
        }

        @Override
        public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) {
            return new DriverPropertyInfo[0];
        }

        @Override
        public int getMajorVersion() {
            return 10;
        }

        @Override
        public int getMinorVersion() {
            return 15;
        }

        @Override
        public boolean jdbcCompliant() {
            return false;
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException("No parent logger is available");
        }
    }
}
