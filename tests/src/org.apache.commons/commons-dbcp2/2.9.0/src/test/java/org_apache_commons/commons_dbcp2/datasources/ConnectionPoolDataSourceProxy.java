/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package org_apache_commons.commons_dbcp2.datasources;

import org.apache.commons.dbcp2.Jdbc41Bridge;

import javax.sql.ConnectionPoolDataSource;
import javax.sql.PooledConnection;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;

public class ConnectionPoolDataSourceProxy implements ConnectionPoolDataSource {
    protected ConnectionPoolDataSource delegate;

    public ConnectionPoolDataSourceProxy(final ConnectionPoolDataSource cpds) {
        this.delegate = cpds;
    }

    public ConnectionPoolDataSource getDelegate() {
        return delegate;
    }

    @Override
    public int getLoginTimeout() throws SQLException {
        return delegate.getLoginTimeout();
    }

    @Override
    public PrintWriter getLogWriter() throws SQLException {
        return delegate.getLogWriter();
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return Jdbc41Bridge.getParentLogger(delegate);
    }

    @Override
    public PooledConnection getPooledConnection() throws SQLException {
        return wrapPooledConnection(delegate.getPooledConnection());
    }

    @Override
    public PooledConnection getPooledConnection(final String user, final String password) throws SQLException {
        return wrapPooledConnection(delegate.getPooledConnection(user, password));
    }

    @Override
    public void setLoginTimeout(final int seconds) throws SQLException {
        delegate.setLoginTimeout(seconds);
    }

    @Override
    public void setLogWriter(final PrintWriter out) throws SQLException {
        delegate.setLogWriter(out);
    }

    protected PooledConnection wrapPooledConnection(final PooledConnection pc) {
        final PooledConnectionProxy tpc = new PooledConnectionProxy(pc);
        tpc.setNotifyOnClose(true);
        return tpc;
    }
}
