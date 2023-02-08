/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_dbcp2.managed;

import com.arjuna.ats.internal.jta.transaction.arjunacore.TransactionManagerImple;
import com.arjuna.ats.jta.common.jtaPropertyManager;
import org.apache.commons.dbcp2.managed.BasicManagedDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.transaction.RollbackException;
import javax.transaction.Status;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

@SuppressWarnings({"SqlDialectInspection", "SqlNoDataSourceInspection", "TryFinallyCanBeTryWithResources", "BusyWait", "unused"})
public class TestConnectionWithNarayana {
    private static final String CREATE_STMT = "CREATE TABLE TEST_DATA (KEY VARCHAR(100), ID BIGINT, VALUE DOUBLE PRECISION, INFO TEXT, TS TIMESTAMP)";
    private static final String INSERT_STMT = "INSERT INTO TEST_DATA   (KEY, ID, VALUE, INFO, TS) VALUES (?,?,?,?,?)";
    private static final String SELECT_STMT = "SELECT KEY, ID, VALUE, INFO, TS FROM TEST_DATA LIMIT 1";
    private static final String PAYLOAD;
    private static final String DROP_STMT = "DROP TABLE TEST_DATA";

    static {
        final StringBuilder sb = new StringBuilder();
        sb.append("Start");
        sb.append("payload");
        IntStream.range(0, 10000).forEach(i -> {
            sb.append("...");
            sb.append(i);
        });
        sb.append("End");
        sb.append("payload");

        PAYLOAD = sb.toString();
    }

    private BasicManagedDataSource mds;

    @BeforeEach
    public void setUp() throws Exception {
        jtaPropertyManager.getJTAEnvironmentBean().setLastResourceOptimisationInterfaceClassName(
                "org.apache.commons.dbcp2.managed.LocalXAConnectionFactory$LocalXAResource");
        mds = new BasicManagedDataSource();
        mds.setTransactionManager(new TransactionManagerImple());
        mds.setDriverClassName("org.h2.Driver");
        mds.setUrl("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1");
        mds.setMaxTotal(80);
        mds.setMinIdle(0);
        mds.setMaxIdle(80);
        mds.setMinEvictableIdleTimeMillis(10000);
        mds.setTimeBetweenEvictionRunsMillis(10000);
        mds.setLogAbandoned(true);
        mds.setMaxWaitMillis(2000);
        mds.setRemoveAbandonedOnMaintenance(true);
        mds.setRemoveAbandonedOnBorrow(true);
        mds.setRemoveAbandonedTimeout(10);
        mds.setLogExpiredConnections(true);
        mds.setLifo(false);
        try (Connection conn = mds.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(CREATE_STMT)) {
                ps.execute();
            }
        }
    }

    @AfterEach
    public void tearDown() throws Exception {
        try (Connection conn = mds.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(DROP_STMT)) {
                ps.execute();
            }
        }
        if (mds != null) {
            mds.close();
        }
    }

    @Test
    public void testConnectionCommitAfterTimeout() throws Exception {
        mds.getTransactionManager().setTransactionTimeout(1);
        mds.getTransactionManager().begin();
        try (Connection conn = mds.getConnection()) {
            do {
                Thread.sleep(1000);
            } while (mds.getTransactionManager().getTransaction().getStatus() != Status.STATUS_ROLLEDBACK);
            Thread.sleep(1000);
            try {
                conn.commit();
                fail("Should not work after timeout");
            } catch (final SQLException e) {
                assertEquals("Commit can not be set while enrolled in a transaction", e.getMessage());
            }
            mds.getTransactionManager().rollback();
        }
        assertEquals(0, mds.getNumActive());
    }

    @Test
    public void testConnectionInTimeout() throws Exception {
        Connection conn = null;
        PreparedStatement ps = null;
        for (int i = 0; i < 5; i++) {
            try {
                mds.getTransactionManager().setTransactionTimeout(1);
                mds.getTransactionManager().begin();
                conn = mds.getConnection();
                ps = conn.prepareStatement(INSERT_STMT);
                ps.setString(1, Thread.currentThread().getName());
                ps.setLong(2, i);
                ps.setDouble(3, new java.util.Random().nextDouble());
                ps.setString(4, PAYLOAD);
                ps.setTimestamp(5, new Timestamp(System.currentTimeMillis()));
                ps.execute();
                int n = 0;
                do {
                    if (mds.getTransactionManager().getTransaction().getStatus() != Status.STATUS_ACTIVE) {
                        n++;
                    }
                    Connection c = null;
                    PreparedStatement ps2 = null;
                    ResultSet rs = null;
                    try {
                        c = mds.getConnection();
                        ps2 = c.prepareStatement(SELECT_STMT);
                        rs = ps2.executeQuery();
                    } finally {
                        if (rs != null) {
                            rs.close();
                        }
                        if (ps2 != null) {
                            ps2.close();
                        }
                        if (c != null) {
                            c.close();
                        }
                    }
                } while (n < 2);
                ps.close();
                ps = null;
                conn.close();
                conn = null;
                try {
                    mds.getTransactionManager().commit();
                    fail("Should not have been able to commit");
                } catch (final RollbackException e) {
                    if (mds.getTransactionManager().getTransaction() != null) {
                        mds.getTransactionManager().rollback();
                    }
                }
            } catch (final Exception e) {
                if (mds.getTransactionManager().getTransaction() != null) {
                    mds.getTransactionManager().rollback();
                }
            } finally {
                if (ps != null) {
                    ps.close();
                }
                if (conn != null) {
                    conn.close();
                }
            }
            assertEquals(0, mds.getNumActive());
        }
    }

    @Test
    public void testRepeatedGetConnectionInTimeout() throws Exception {
        mds.getTransactionManager().setTransactionTimeout(1);
        mds.getTransactionManager().begin();
        try {
            do {
                Thread.sleep(1000);
            } while (mds.getTransactionManager().getTransaction().getStatus() != Status.STATUS_ROLLEDBACK);
            Thread.sleep(1000);
            try (Connection conn = mds.getConnection()) {
                fail("Should not get the connection 1");
            } catch (final SQLException e) {
                if (!e.getCause().getClass().equals(IllegalStateException.class)) {
                    throw e;
                }
                try (Connection conn = mds.getConnection()) {
                    fail("Should not get connection 2");
                } catch (final SQLException e2) {
                    if (!e2.getCause().getClass().equals(IllegalStateException.class)) {
                        throw e2;
                    }
                }
            }
        } finally {
            mds.getTransactionManager().rollback();
        }
        assertEquals(0, mds.getNumActive());
    }
}
