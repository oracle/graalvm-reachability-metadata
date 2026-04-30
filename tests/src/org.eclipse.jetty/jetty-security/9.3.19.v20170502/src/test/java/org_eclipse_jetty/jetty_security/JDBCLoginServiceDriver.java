/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jetty.jetty_security;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.util.Enumeration;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

public final class JDBCLoginServiceDriver implements Driver {
    private static final AtomicInteger INSTANTIATIONS = new AtomicInteger();
    private static final AtomicInteger CONNECT_ATTEMPTS = new AtomicInteger();

    public JDBCLoginServiceDriver() throws SQLException {
        INSTANTIATIONS.incrementAndGet();
        DriverManager.registerDriver(this);
    }

    static void reset() {
        INSTANTIATIONS.set(0);
        CONNECT_ATTEMPTS.set(0);
    }

    static int instantiations() {
        return INSTANTIATIONS.get();
    }

    static int connectAttempts() {
        return CONNECT_ATTEMPTS.get();
    }

    static void deregisterRegisteredDrivers() throws SQLException {
        Enumeration<Driver> drivers = DriverManager.getDrivers();
        while (drivers.hasMoreElements()) {
            Driver driver = drivers.nextElement();
            if (driver instanceof JDBCLoginServiceDriver) {
                DriverManager.deregisterDriver(driver);
            }
        }
    }

    @Override
    public Connection connect(String url, Properties info) throws SQLException {
        CONNECT_ATTEMPTS.incrementAndGet();
        throw new SQLException("Intentional test driver connection refusal");
    }

    @Override
    public boolean acceptsURL(String url) throws SQLException {
        return url.startsWith("jdbc:jetty-security-test:");
    }

    @Override
    public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
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
    public Logger getParentLogger() {
        return Logger.getGlobal();
    }
}
