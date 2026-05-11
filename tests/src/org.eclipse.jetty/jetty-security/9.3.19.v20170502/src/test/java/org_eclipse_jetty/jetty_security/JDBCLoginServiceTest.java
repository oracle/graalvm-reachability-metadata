/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jetty.jetty_security;

import java.nio.file.Files;
import java.nio.file.Path;
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

import org.eclipse.jetty.security.JDBCLoginService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class JDBCLoginServiceTest {
    private static final String DRIVER_CLASS_NAME = JDBCLoginServiceTest.TestDriver.class.getName();
    private static final String JDBC_URL = "jdbc:jetty-security-test:users";

    @TempDir
    Path temporaryDirectory;

    @BeforeEach
    void resetDriverState() throws SQLException {
        TestDriver.reset();
        deregisterTestDrivers();
    }

    @AfterEach
    void cleanUpDriverManager() throws SQLException {
        deregisterTestDrivers();
    }

    @Test
    void startsWithConfiguredJdbcDriverAndAttemptsConnectionWhenUserIsLoaded() throws Exception {
        Path config = writeJdbcRealmConfig();
        JDBCLoginService service = new JDBCLoginService("jdbc-realm", config.toUri().toString());

        try {
            service.start();
            assertThat(service.isStarted()).isTrue();
            assertThat(TestDriver.createdInstances()).isEqualTo(1);

            assertThat(service.loadUserInfo("alice")).isNull();

            assertThat(TestDriver.acceptedUrls()).isEqualTo(1);
            assertThat(TestDriver.connectAttempts()).isEqualTo(1);
            assertThat(TestDriver.lastUrl()).isEqualTo(JDBC_URL);
        } finally {
            if (service.isRunning()) {
                service.stop();
            }
        }
    }

    private Path writeJdbcRealmConfig() throws Exception {
        Path config = temporaryDirectory.resolve("jdbc-realm.properties");
        Files.writeString(config, """
                jdbcdriver=%s
                url=%s
                username=jetty
                password=secret
                usertable=users
                usertablekey=id
                usertableuserfield=name
                usertablepasswordfield=password
                roletable=roles
                roletablekey=id
                roletablerolefield=role
                userroletable=user_roles
                userroletableuserkey=user_id
                userroletablerolekey=role_id
                cachetime=1
                """.formatted(DRIVER_CLASS_NAME, JDBC_URL));
        return config;
    }

    private static void deregisterTestDrivers() throws SQLException {
        Enumeration<Driver> drivers = DriverManager.getDrivers();
        while (drivers.hasMoreElements()) {
            Driver driver = drivers.nextElement();
            if (driver instanceof TestDriver) {
                DriverManager.deregisterDriver(driver);
            }
        }
    }

    public static final class TestDriver implements Driver {
        private static final AtomicInteger CREATED_INSTANCES = new AtomicInteger();
        private static final AtomicInteger ACCEPTED_URLS = new AtomicInteger();
        private static final AtomicInteger CONNECT_ATTEMPTS = new AtomicInteger();
        private static volatile String lastUrl;

        public TestDriver() throws SQLException {
            CREATED_INSTANCES.incrementAndGet();
            DriverManager.registerDriver(this);
        }

        static void reset() {
            CREATED_INSTANCES.set(0);
            ACCEPTED_URLS.set(0);
            CONNECT_ATTEMPTS.set(0);
            lastUrl = null;
        }

        static int createdInstances() {
            return CREATED_INSTANCES.get();
        }

        static int acceptedUrls() {
            return ACCEPTED_URLS.get();
        }

        static int connectAttempts() {
            return CONNECT_ATTEMPTS.get();
        }

        static String lastUrl() {
            return lastUrl;
        }

        @Override
        public Connection connect(String url, Properties info) throws SQLException {
            CONNECT_ATTEMPTS.incrementAndGet();
            lastUrl = url;
            if (!acceptsURL(url)) {
                return null;
            }
            throw new SQLException("Test driver does not create database connections");
        }

        @Override
        public boolean acceptsURL(String url) {
            boolean accepted = JDBC_URL.equals(url);
            if (accepted) {
                ACCEPTED_URLS.incrementAndGet();
            }
            return accepted;
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
            throw new SQLFeatureNotSupportedException("No parent logger");
        }
    }
}
