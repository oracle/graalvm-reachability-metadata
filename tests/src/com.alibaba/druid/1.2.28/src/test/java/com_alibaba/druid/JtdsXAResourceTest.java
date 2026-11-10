/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.druid;

import com.alibaba.druid.pool.xa.JtdsXAConnection;
import net.sourceforge.jtds.jdbcx.JtdsDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;
import java.sql.Connection;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(170)
public class JtdsXAResourceTest {
    @Test
    void identifiesSharedResourceManagerAndCommitsAnEmulatedTransaction() throws Exception {
        try (DatabaseContainer database = DatabaseContainer.sqlServer()) {
            String[] address = database.address().split(":");
            JtdsDataSource source = new JtdsDataSource();
            source.setServerName(address[0]);
            source.setPortNumber(Integer.parseInt(address[1]));
            source.setDatabaseName("master");
            source.setUser("sa");
            source.setPassword(DatabaseContainer.PASSWORD);
            source.setLoginTimeout(15);
            source.setSocketTimeout(30);
            source.setXaEmulation(true);
            try (Connection first = source.getConnection(); Connection second = source.getConnection()) {
                JtdsXAConnection firstXa = new JtdsXAConnection(first);
                try {
                    JtdsXAConnection secondXa = new JtdsXAConnection(second);
                    try {
                        XAResource resource = firstXa.getXAResource();
                        assertTrue(resource.isSameRM(secondXa.getXAResource()));
                        try (Statement statement = first.createStatement()) {
                            statement.execute("CREATE TABLE #xa_records (id INT)");
                            Xid xid = new TransactionId();
                            resource.start(xid, XAResource.TMNOFLAGS);
                            statement.execute("INSERT INTO #xa_records VALUES (101)");
                            resource.end(xid, XAResource.TMSUCCESS);
                            resource.commit(xid, true);
                            assertEquals(101, TestDataSources.queryInt(first, "SELECT id FROM #xa_records"));
                        }
                    } finally {
                        secondXa.close();
                    }
                } finally {
                    firstXa.close();
                }
            }
        }
    }

    private static final class TransactionId implements Xid {
        @Override
        public int getFormatId() {
            return 7;
        }

        @Override
        public byte[] getGlobalTransactionId() {
            return new byte[]{7, 8};
        }

        @Override
        public byte[] getBranchQualifier() {
            return new byte[]{9};
        }
    }
}
