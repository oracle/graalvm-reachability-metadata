/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.apache.commons.dbcp2.managed;

import javax.sql.ConnectionEvent;
import javax.sql.ConnectionEventListener;
import javax.sql.StatementEventListener;
import javax.sql.XAConnection;
import javax.transaction.xa.XAResource;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@SuppressWarnings("unused")
public class TesterBasicXAConnection implements XAConnection {
    public static class ConnectionHandle implements InvocationHandler {
        public Connection conn;
        public final TesterBasicXAConnection xaconn;

        public ConnectionHandle(final Connection conn, final TesterBasicXAConnection xaconn) {
            this.conn = conn;
            this.xaconn = xaconn;
        }

        protected Object close() throws SQLException {
            if (conn != null) {
                conn.clearWarnings();
                conn = null;
                xaconn.handle = null;
                xaconn.notifyConnectionClosed();
            }
            return null;
        }

        public void closeHandle() {
            conn = null;
        }

        @Override
        public Object invoke(final Object proxy, final Method method, final Object[] args) throws Throwable {
            final String methodName = method.getName();
            if (methodName.equals("hashCode")) {
                return System.identityHashCode(proxy);
            }
            if (methodName.equals("equals")) {
                return proxy == args[0];
            }
            if (methodName.equals("isClosed")) {
                return conn == null;
            }
            if (methodName.equals("close")) {
                return close();
            }
            if (conn == null) {
                throw new SQLException("Connection closed");
            }
            try {
                return method.invoke(conn, args);
            } catch (final InvocationTargetException e) {
                final Throwable te = e.getTargetException();
                if (te instanceof SQLException) {
                    xaconn.notifyConnectionErrorOccurred((SQLException) te);
                }
                throw te;
            }
        }
    }

    public Connection conn;

    public ConnectionHandle handle;

    public final List<ConnectionEventListener> listeners = new LinkedList<>();

    public final AtomicInteger closeCounter;

    public TesterBasicXAConnection(final Connection conn) {
        this(conn, null);
    }

    public TesterBasicXAConnection(final Connection conn, final AtomicInteger closeCounter) {
        this.conn = conn;
        this.closeCounter = closeCounter;
    }

    @Override
    public void addConnectionEventListener(
            final ConnectionEventListener connectionEventListener) {
        listeners.add(connectionEventListener);
    }

    @Override
    public void addStatementEventListener(final StatementEventListener listener) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void close() throws SQLException {
        if (handle != null) {
            closeHandle();
        }
        try {
            conn.close();
            if (closeCounter != null) {
                closeCounter.incrementAndGet();
            }
        } finally {
            conn = null;
        }
    }

    protected void closeHandle() throws SQLException {
        handle.closeHandle();
        if (!conn.getAutoCommit()) {
            try {
                conn.rollback();
            } catch (final SQLException e) {
                e.printStackTrace();
            }
        }
        handle = null;
    }

    @Override
    public Connection getConnection() throws SQLException {
        if (conn == null) {
            final SQLException e = new SQLException("XAConnection closed");
            notifyConnectionErrorOccurred(e);
            throw e;
        }
        try {
            if (handle != null) {

                closeHandle();
                conn.clearWarnings();
            }
        } catch (final SQLException e) {
            notifyConnectionErrorOccurred(e);
            throw e;
        }
        handle = new ConnectionHandle(conn, this);
        return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{Connection.class}, handle);
    }

    @Override
    public XAResource getXAResource() {
        return new LocalXAConnectionFactory.LocalXAResource(conn);
    }

    protected void notifyConnectionClosed() {
        final ConnectionEvent event = new ConnectionEvent(this);
        final List<ConnectionEventListener> copy = new ArrayList<>(listeners);
        copy.forEach(listener -> listener.connectionClosed(event));
    }

    protected void notifyConnectionErrorOccurred(final SQLException e) {
        final ConnectionEvent event = new ConnectionEvent(this, e);
        final List<ConnectionEventListener> copy = new ArrayList<>(listeners);
        copy.forEach(listener -> listener.connectionErrorOccurred(event));
    }

    @Override
    public void removeConnectionEventListener(final ConnectionEventListener connectionEventListener) {
        listeners.remove(connectionEventListener);
    }

    @Override
    public void removeStatementEventListener(final StatementEventListener listener) {
        throw new UnsupportedOperationException();
    }
}

