/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com.zaxxer.hikaricp.test.support;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import javax.sql.DataSource;

public final class RecordingDataSource implements DataSource {
    private static final AtomicReference<RecordingDataSource> LAST_INSTANCE = new AtomicReference<>();

    private String serverName;
    private int portNumber;
    private boolean useSsl;
    private String lastUsername;
    private String lastPassword;
    private PrintWriter logWriter;
    private int loginTimeout;

    public RecordingDataSource() {
        LAST_INSTANCE.set(this);
    }

    public static void reset() {
        LAST_INSTANCE.set(null);
    }

    public static RecordingDataSource lastInstance() {
        return LAST_INSTANCE.get();
    }

    @Override
    public Connection getConnection() throws SQLException {
        return getConnection(null, null);
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        this.lastUsername = username;
        this.lastPassword = password;
        return new RecordingConnection();
    }

    @Override
    public PrintWriter getLogWriter() throws SQLException {
        return logWriter;
    }

    @Override
    public void setLogWriter(PrintWriter logWriter) throws SQLException {
        this.logWriter = logWriter;
    }

    @Override
    public void setLoginTimeout(int seconds) throws SQLException {
        this.loginTimeout = seconds;
    }

    @Override
    public int getLoginTimeout() throws SQLException {
        return loginTimeout;
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface.isInstance(this)) {
            return iface.cast(this);
        }
        throw new SQLException("Not a wrapper for " + iface.getName());
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return iface.isInstance(this);
    }

    public String getServerName() {
        return serverName;
    }

    public void setServerName(String serverName) {
        this.serverName = serverName;
    }

    public int getPortNumber() {
        return portNumber;
    }

    public void setPortNumber(int portNumber) {
        this.portNumber = portNumber;
    }

    public boolean isUseSsl() {
        return useSsl;
    }

    public void setUseSsl(boolean useSsl) {
        this.useSsl = useSsl;
    }

    public String getLastUsername() {
        return lastUsername;
    }

    public String getLastPassword() {
        return lastPassword;
    }
}
