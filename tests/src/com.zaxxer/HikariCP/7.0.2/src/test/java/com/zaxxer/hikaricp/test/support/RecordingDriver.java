/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com.zaxxer.hikaricp.test.support;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

public final class RecordingDriver implements Driver {
    private static final AtomicReference<String> LAST_URL = new AtomicReference<>();
    private static final AtomicReference<Properties> LAST_PROPERTIES = new AtomicReference<>();

    public RecordingDriver() {
    }

    public static void reset() {
        LAST_URL.set(null);
        LAST_PROPERTIES.set(null);
    }

    public static String lastUrl() {
        return LAST_URL.get();
    }

    public static Properties lastProperties() {
        return LAST_PROPERTIES.get();
    }

    @Override
    public Connection connect(String url, Properties info) throws SQLException {
        LAST_URL.set(url);
        Properties copiedProperties = new Properties();
        copiedProperties.putAll(info);
        LAST_PROPERTIES.set(copiedProperties);
        return new RecordingConnection();
    }

    @Override
    public boolean acceptsURL(String url) throws SQLException {
        return url.startsWith("jdbc:recording:");
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
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException();
    }
}
