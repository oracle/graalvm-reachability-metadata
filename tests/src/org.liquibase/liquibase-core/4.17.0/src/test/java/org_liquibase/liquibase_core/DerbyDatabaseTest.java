/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_liquibase.liquibase_core;

import liquibase.database.core.DerbyDatabase;
import liquibase.database.jvm.JdbcConnection;
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
        EmbeddedSuccessfulShutdownDriver.connectCalls = 0;
        EmbeddedSuccessfulShutdownDriver.lastUrl = null;
    }

    @Test
    void closeLoadsEmbeddedDriverWithConnectionClassLoaderAndConnectsToShutdownUrl() throws Exception {
        Connection connection = DriverManager.getConnection("jdbc:h2:mem:derbyDatabaseTest;DB_CLOSE_DELAY=-1");
        TestableDerbyDatabase database = new TestableDerbyDatabase(EmbeddedSuccessfulShutdownDriver.class.getName());
        database.setConnection(new JdbcConnection(connection));

        database.close();

        assertThat(connection.isClosed()).isTrue();
        assertThat(EmbeddedSuccessfulShutdownDriver.connectCalls).isEqualTo(1);
        assertThat(EmbeddedSuccessfulShutdownDriver.lastUrl).isEqualTo("jdbc:h2:mem:derbyDatabaseTest;shutdown=true");
    }

    private static final class TestableDerbyDatabase extends DerbyDatabase {
        private final String driverName;

        private TestableDerbyDatabase(String driverName) {
            this.driverName = driverName;
        }

        @Override
        public String getDefaultDriver(String url) {
            return driverName;
        }
    }

    public static final class EmbeddedSuccessfulShutdownDriver implements Driver {
        private static int connectCalls;
        private static String lastUrl;

        public EmbeddedSuccessfulShutdownDriver() {
        }

        @Override
        public Connection connect(String url, Properties info) throws SQLException {
            connectCalls++;
            lastUrl = url;
            throw new SQLException("Derby shutdown succeeded", "XJ015");
        }

        @Override
        public boolean acceptsURL(String url) {
            return url != null && url.contains("shutdown=true");
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
