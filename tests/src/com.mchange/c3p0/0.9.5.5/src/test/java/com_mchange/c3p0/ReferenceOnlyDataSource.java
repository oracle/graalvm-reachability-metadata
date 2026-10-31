/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.c3p0;

import org.h2.jdbcx.JdbcDataSource;

import javax.naming.Reference;
import javax.naming.Referenceable;
import javax.sql.ConnectionPoolDataSource;
import javax.sql.DataSource;
import javax.sql.PooledConnection;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Logger;

/// An application data source that supports JNDI references rather than serialization.
final class ReferenceOnlyDataSource implements DataSource, ConnectionPoolDataSource, Referenceable {
    private final JdbcDataSource delegate = new JdbcDataSource();

    ReferenceOnlyDataSource() {
        delegate.setURL(C3p0Support.databaseUrl());
        delegate.setUser("sa");
        delegate.setPassword("");
    }

    @Override
    public Reference getReference() {
        return delegate.getReference();
    }

    @Override
    public Connection getConnection() throws SQLException {
        return delegate.getConnection();
    }

    @Override
    public Connection getConnection(String user, String password) throws SQLException {
        return delegate.getConnection(user, password);
    }

    @Override
    public PooledConnection getPooledConnection() throws SQLException {
        return delegate.getPooledConnection();
    }

    @Override
    public PooledConnection getPooledConnection(String user, String password) throws SQLException {
        return delegate.getPooledConnection(user, password);
    }

    @Override
    public PrintWriter getLogWriter() {
        return delegate.getLogWriter();
    }

    @Override
    public void setLogWriter(PrintWriter writer) {
        delegate.setLogWriter(writer);
    }

    @Override
    public int getLoginTimeout() {
        return delegate.getLoginTimeout();
    }

    @Override
    public void setLoginTimeout(int seconds) {
        delegate.setLoginTimeout(seconds);
    }

    @Override
    public Logger getParentLogger() {
        return delegate.getParentLogger();
    }

    @Override
    public <T> T unwrap(Class<T> type) throws SQLException {
        return delegate.unwrap(type);
    }

    @Override
    public boolean isWrapperFor(Class<?> type) throws SQLException {
        return delegate.isWrapperFor(type);
    }
}
