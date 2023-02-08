/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package org_apache_commons.commons_dbcp2.datasources;

import javax.sql.ConnectionEvent;
import javax.sql.ConnectionEventListener;
import javax.sql.PooledConnection;
import javax.sql.StatementEventListener;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EventListener;
import java.util.List;

public class PooledConnectionProxy implements PooledConnection, ConnectionEventListener {
    protected PooledConnection delegate;
    private final List<EventListener> eventListeners = Collections.synchronizedList(new ArrayList<>());
    private boolean notifyOnClose;

    public PooledConnectionProxy(final PooledConnection pooledConnection) {
        this.delegate = pooledConnection;
        pooledConnection.addConnectionEventListener(this);
    }

    @Override
    public void addConnectionEventListener(final ConnectionEventListener listener) {
        if (!eventListeners.contains(listener)) {
            eventListeners.add(listener);
        }
    }

    @Override
    public void addStatementEventListener(final StatementEventListener listener) {
        if (!eventListeners.contains(listener)) {
            eventListeners.add(listener);
        }
    }

    @Override
    public void close() throws SQLException {
        delegate.close();
        if (isNotifyOnClose()) {
            notifyListeners();
        }
    }

    @Override
    public void connectionClosed(final ConnectionEvent event) {
        notifyListeners();
    }

    @Override
    public void connectionErrorOccurred(final ConnectionEvent event) {
        final Object[] listeners = eventListeners.toArray();
        Arrays.stream(listeners).forEach(listener -> ((ConnectionEventListener) listener).connectionErrorOccurred(event));
    }

    @Override
    public Connection getConnection() throws SQLException {
        return delegate.getConnection();
    }

    public Collection<EventListener> getListeners() {
        return eventListeners;
    }

    public boolean isNotifyOnClose() {
        return notifyOnClose;
    }

    void notifyListeners() {
        final ConnectionEvent event = new ConnectionEvent(this);
        final Object[] listeners = eventListeners.toArray();
        Arrays.stream(listeners).forEach(listener -> ((ConnectionEventListener) listener).connectionClosed(event));
    }

    @Override
    public void removeConnectionEventListener(final ConnectionEventListener listener) {
        eventListeners.remove(listener);
    }

    @Override
    public void removeStatementEventListener(final StatementEventListener listener) {
        eventListeners.remove(listener);
    }

    public void setNotifyOnClose(final boolean notifyOnClose) {
        this.notifyOnClose = notifyOnClose;
    }

    public void throwConnectionError() {
        final ConnectionEvent event = new ConnectionEvent(this);
        connectionErrorOccurred(event);
    }
}
