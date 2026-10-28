/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.druid;

import com.alibaba.druid.util.H2Utils;
import org.h2.Driver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import javax.sql.XAConnection;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Timeout(120)
public class H2UtilsTest {
    @Test
    void createsXaConnectionAndCommitsTransaction() throws Exception {
        try (Connection physical = new Driver().connect("jdbc:h2:mem:xa", new Properties())) {
            XAConnection xa = H2Utils.createXAConnection(H2Utils.createJdbcDataSourceFactory(), physical);
            try {
                try (Connection connection = xa.getConnection(); Statement statement = connection.createStatement()) {
                    statement.execute("CREATE TABLE xa_records (id INT)");
                    XAResource resource = xa.getXAResource();
                    Xid xid = new TransactionId();
                    resource.start(xid, XAResource.TMNOFLAGS);
                    statement.execute("INSERT INTO xa_records VALUES (53)");
                    resource.end(xid, XAResource.TMSUCCESS);
                    resource.commit(xid, true);
                    assertEquals(53, TestDataSources.queryInt(connection, "SELECT id FROM xa_records"));
                }
            } finally {
                xa.close();
            }
        }
    }

    private static final class TransactionId implements Xid {
        @Override
        public int getFormatId() {
            return 1;
        }

        @Override
        public byte[] getGlobalTransactionId() {
            return new byte[]{1, 2, 3};
        }

        @Override
        public byte[] getBranchQualifier() {
            return new byte[]{4, 5};
        }
    }
}
