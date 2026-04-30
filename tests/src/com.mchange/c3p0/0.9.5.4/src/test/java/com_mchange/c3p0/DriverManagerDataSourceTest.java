/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.c3p0;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mchange.v2.c3p0.DriverManagerDataSource;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Enumeration;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

public class DriverManagerDataSourceTest {
    @AfterAll
    static void deregisterTestDrivers() throws SQLException {
        Enumeration<Driver> drivers = DriverManager.getDrivers();
        while (drivers.hasMoreElements()) {
            Driver driver = drivers.nextElement();
            if (driver instanceof LoadingDriver) {
                DriverManager.deregisterDriver(driver);
            }
        }
    }

    @Test
    void loadsConfiguredDriverClassBeforeUsingDriverManager() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(false);
        dataSource.setDriverClass(LoadingDriver.class.getName());
        dataSource.setJdbcUrl(LoadingDriver.URL);

        assertThatThrownBy(dataSource::getConnection)
                .isInstanceOf(SQLException.class)
                .hasMessageContaining(LoadingDriver.URL);

        assertThat(LoadingDriver.connectCalls()).isEqualTo(1);
    }

    @Test
    void instantiatesConfiguredDriverWhenForcedToUseNamedDriverClass() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(false);
        dataSource.setDriverClass(ForcedDriver.class.getName());
        dataSource.setForceUseNamedDriverClass(true);
        dataSource.setJdbcUrl(ForcedDriver.URL);

        assertThatThrownBy(dataSource::getConnection)
                .isInstanceOf(SQLException.class)
                .hasMessageContaining(ForcedDriver.URL);

        assertThat(ForcedDriver.instances()).isEqualTo(1);
        assertThat(ForcedDriver.connectCalls()).isEqualTo(1);
    }

    public static final class LoadingDriver extends AbstractNullConnectionDriver {
        private static final String URL = "jdbc:c3p0-test:driver-manager";
        private static final AtomicInteger CONNECT_CALLS = new AtomicInteger();

        static {
            try {
                DriverManager.registerDriver(new LoadingDriver());
            } catch (SQLException e) {
                throw new ExceptionInInitializerError(e);
            }
        }

        public LoadingDriver() {
            super(URL, CONNECT_CALLS);
        }

        private static int connectCalls() {
            return CONNECT_CALLS.get();
        }
    }

    public static final class ForcedDriver extends AbstractNullConnectionDriver {
        private static final String URL = "jdbc:c3p0-test:forced-driver";
        private static final AtomicInteger INSTANCES = new AtomicInteger();
        private static final AtomicInteger CONNECT_CALLS = new AtomicInteger();

        public ForcedDriver() {
            super(URL, CONNECT_CALLS);
            INSTANCES.incrementAndGet();
        }

        private static int instances() {
            return INSTANCES.get();
        }

        private static int connectCalls() {
            return CONNECT_CALLS.get();
        }
    }

    public abstract static class AbstractNullConnectionDriver implements Driver {
        private final String url;
        private final AtomicInteger connectCalls;

        protected AbstractNullConnectionDriver(String url, AtomicInteger connectCalls) {
            this.url = url;
            this.connectCalls = connectCalls;
        }

        @Override
        public Connection connect(String url, Properties info) throws SQLException {
            if (acceptsURL(url)) {
                connectCalls.incrementAndGet();
            }
            return null;
        }

        @Override
        public boolean acceptsURL(String url) {
            return this.url.equals(url);
        }

        @Override
        public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) {
            return new DriverPropertyInfo[0];
        }

        @Override
        public int getMajorVersion() {
            return 1;
        }

        @Override
        public int getMinorVersion() {
            return 0;
        }

        @Override
        public boolean jdbcCompliant() {
            return false;
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException("No parent logger for test driver.");
        }
    }
}
