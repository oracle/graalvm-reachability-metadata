/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_opengauss.opengauss_jdbc;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.postgresql.PGConnection;
import org.postgresql.xa.PGXADataSource;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;

import javax.sql.XAConnection;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class PGXAConnectionTest {
    private static final String USERNAME = "fred";
    private static final String PASSWORD = "Secretpassword@123";
    private static final String DATABASE = "postgres";
    private static final int PORT = 15435;
    private static Process process;

    @BeforeAll
    static void beforeAll() throws IOException {
        System.out.println("Starting OpenGauss for XA connection tests ...");
        process = new ProcessBuilder(
                "docker", "run", "--rm", "-p", PORT + ":5432", "-e", "GS_USERNAME=" + USERNAME,
                "-e", "GS_PASSWORD=" + PASSWORD, "opengauss/opengauss:5.0.0")
                .redirectOutput(new File("opengauss-xa-connection-stdout.txt"))
                .redirectError(new File("opengauss-xa-connection-stderr.txt"))
                .start();
        Awaitility.await().atMost(Duration.ofMinutes(1)).ignoreExceptions().until(() -> {
            XAConnection xaConnection = newDataSource().getXAConnection();
            xaConnection.close();
            return true;
        });
        System.out.println("OpenGauss started for XA connection tests");
    }

    @AfterAll
    static void tearDown() {
        if (process != null && process.isAlive()) {
            System.out.println("Shutting down OpenGauss for XA connection tests");
            process.destroy();
        }
    }

    @Test
    void xaConnectionReturnsTransactionAwareConnectionProxy() throws Exception {
        XAConnection xaConnection = newDataSource().getXAConnection();
        Connection connection = null;
        Xid xid = new TestXid(42);
        XAResource xaResource = xaConnection.getXAResource();
        boolean transactionStarted = false;
        boolean transactionEnded = false;
        try {
            connection = xaConnection.getConnection();

            assertThat(connection).isInstanceOf(PGConnection.class);
            assertThat(connection.toString()).contains("Pooled connection wrapping physical connection");
            assertThat(connection.getAutoCommit()).isTrue();

            xaResource.start(xid, XAResource.TMNOFLAGS);
            transactionStarted = true;
            assertThat(connection.getAutoCommit()).isFalse();
            assertThatThrownBy(connection::commit)
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("Transaction control methods");

            xaResource.end(xid, XAResource.TMSUCCESS);
            transactionEnded = true;
            xaResource.rollback(xid);
            transactionStarted = false;
            assertThat(connection.getAutoCommit()).isTrue();
        } finally {
            if (transactionStarted) {
                if (!transactionEnded) {
                    xaResource.end(xid, XAResource.TMFAIL);
                }
                xaResource.rollback(xid);
            }
            if (connection != null) {
                connection.close();
            }
            xaConnection.close();
        }
    }

    private static PGXADataSource newDataSource() {
        PGXADataSource dataSource = new PGXADataSource();
        dataSource.setServerName("localhost");
        dataSource.setPortNumber(PORT);
        dataSource.setDatabaseName(DATABASE);
        dataSource.setUser(USERNAME);
        dataSource.setPassword(PASSWORD);
        return dataSource;
    }

    private static final class TestXid implements Xid {
        private final int id;
        private final byte[] branchQualifier;
        private final byte[] globalTransactionId;

        private TestXid(int id) {
            this.id = id;
            this.branchQualifier = new byte[]{(byte) id};
            this.globalTransactionId = new byte[]{(byte) (id >>> 8), (byte) id};
        }

        @Override
        public int getFormatId() {
            return id;
        }

        @Override
        public byte[] getBranchQualifier() {
            return branchQualifier.clone();
        }

        @Override
        public byte[] getGlobalTransactionId() {
            return globalTransactionId.clone();
        }
    }
}
