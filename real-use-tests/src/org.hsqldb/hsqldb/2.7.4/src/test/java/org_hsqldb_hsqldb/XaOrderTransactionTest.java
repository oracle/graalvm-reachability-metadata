/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hsqldb_hsqldb;

import org.hsqldb.jdbc.JDBCDataSource;
import org.hsqldb.jdbc.pool.JDBCXADataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import javax.sql.XAConnection;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises HSQLDB's JDBC XA transaction subsystem for committing an order import.
public class XaOrderTransactionTest {
    @Test
    @Timeout(60)
    public void preparesAndCommitsAnOrderThroughAnXaResource() throws Exception {
        JDBCDataSource database = new JDBCDataSource();
        database.setDatabase("mem:xa_order_transaction_scenario");
        database.setUser("SA");
        database.setPassword("");

        try (Connection connection = database.getConnection();
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE orders ("
                    + "id INTEGER PRIMARY KEY, description VARCHAR(128))");
        }

        JDBCXADataSource xaDatabase = new JDBCXADataSource();
        xaDatabase.setDatabase("jdbc:hsqldb:mem:xa_order_transaction_scenario");
        xaDatabase.setUser("SA");
        xaDatabase.setPassword("");
        ScenarioXid transactionId = new ScenarioXid(91);

        XAConnection xaConnection = xaDatabase.getXAConnection();
        try {
            try (Connection connection = xaConnection.getConnection()) {
                XAResource resource = xaConnection.getXAResource();
                resource.start(transactionId, XAResource.TMNOFLAGS);
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO orders VALUES (?, ?)")) {
                    insert.setInt(1, 91);
                    insert.setString(2, "Replacement filters");
                    assertEquals(1, insert.executeUpdate());
                }
                resource.end(transactionId, XAResource.TMSUCCESS);
                assertEquals(XAResource.XA_OK, resource.prepare(transactionId));
                resource.commit(transactionId, false);
            }
        } finally {
            xaConnection.close();
        }

        try (Connection connection = database.getConnection();
                PreparedStatement query = connection.prepareStatement(
                        "SELECT description FROM orders WHERE id = ?")) {
            query.setInt(1, 91);
            try (ResultSet resultSet = query.executeQuery()) {
                assertTrue(resultSet.next());
                assertEquals("Replacement filters", resultSet.getString(1));
                assertFalse(resultSet.next());
            }
        }
    }

    private static final class ScenarioXid implements Xid {
        private final byte[] transactionId;

        private ScenarioXid(int id) {
            transactionId = new byte[] {(byte) id};
        }

        @Override
        public int getFormatId() {
            return 91;
        }

        @Override
        public byte[] getGlobalTransactionId() {
            return transactionId.clone();
        }

        @Override
        public byte[] getBranchQualifier() {
            return transactionId.clone();
        }
    }
}
