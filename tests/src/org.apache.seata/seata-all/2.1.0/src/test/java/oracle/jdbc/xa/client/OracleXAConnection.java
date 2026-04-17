/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package oracle.jdbc.xa.client;

import java.sql.Connection;

import javax.sql.ConnectionEventListener;
import javax.sql.StatementEventListener;
import javax.sql.XAConnection;
import javax.transaction.xa.XAResource;

public class OracleXAConnection implements XAConnection {
    private final Connection physicalConnection;

    public OracleXAConnection(Connection physicalConnection) {
        this.physicalConnection = physicalConnection;
    }

    public Connection getPhysicalConnection() {
        return physicalConnection;
    }

    @Override
    public XAResource getXAResource() {
        return null;
    }

    @Override
    public Connection getConnection() {
        return physicalConnection;
    }

    @Override
    public void close() {
    }

    @Override
    public void addConnectionEventListener(ConnectionEventListener listener) {
    }

    @Override
    public void removeConnectionEventListener(ConnectionEventListener listener) {
    }

    @Override
    public void addStatementEventListener(StatementEventListener listener) {
    }

    @Override
    public void removeStatementEventListener(StatementEventListener listener) {
    }
}
