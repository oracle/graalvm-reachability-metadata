/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.druid;

import com.alibaba.druid.util.MySqlUtils;
import com.mysql.cj.jdbc.Driver;

import javax.sql.XAConnection;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(120)
public class MySqlUtilsTest {
    @Test
    @Timeout(170)
    void readsConnectionIdentityAndCommitsBothXaConnectionModes() throws Exception {
        try (DatabaseContainer database = DatabaseContainer.mysql()) {
            Driver driver = new Driver();
            for (boolean pinned : new boolean[]{false, true}) {
                Properties properties = new Properties();
                properties.setProperty("user", "druid");
                properties.setProperty("password", DatabaseContainer.PASSWORD);
                properties.setProperty("connectTimeout", "10000");
                properties.setProperty("socketTimeout", "30000");
                properties.setProperty("allowPublicKeyRetrieval", "true");
                properties.setProperty("useSSL", "false");
                properties.setProperty("maintainTimeStats", "true");
                properties.setProperty("pinGlobalTxToPhysicalConnection", Boolean.toString(pinned));
                String url = "jdbc:mysql://" + database.address() + "/druid";
                try (Connection physical = driver.connect(url, properties)) {
                    Long connectionId = MySqlUtils.getId(physical);
                    assertNotNull(connectionId);
                    assertEquals(TestDataSources.queryInt(physical, "SELECT CONNECTION_ID()"), connectionId.intValue());
                    assertEquals("KILL QUERY " + connectionId, MySqlUtils.buildKillQuerySql(physical, null));
                    long received = MySqlUtils.getLastPacketReceivedTimeMs(physical);
                    assertTrue(received > 0 && received <= System.currentTimeMillis());
                    XAConnection xa = MySqlUtils.createXAConnection(driver, physical);
                    try {
                        try (Connection connection = xa.getConnection(); Statement statement = connection.createStatement()) {
                            String table = pinned ? "xa_pinned_records" : "xa_unpinned_records";
                            statement.execute("CREATE TABLE " + table + " (id INT) ENGINE=InnoDB");
                            XAResource resource = xa.getXAResource();
                            Xid xid = new TransactionId(pinned ? 1 : 2);
                            resource.start(xid, XAResource.TMNOFLAGS);
                            statement.execute("INSERT INTO " + table + " VALUES (83)");
                            resource.end(xid, XAResource.TMSUCCESS);
                            resource.commit(xid, true);
                            assertEquals(83, TestDataSources.queryInt(connection, "SELECT id FROM " + table));
                        }
                    } finally {
                        xa.close();
                    }
                }
            }
        }
    }

    private record TransactionId(int getFormatId) implements Xid {
        @Override
        public byte[] getGlobalTransactionId() {
            return new byte[]{1, 3, 5};
        }

        @Override
        public byte[] getBranchQualifier() {
            return new byte[]{2, 4, 6};
        }
    }

    @Test
    void recognizesKeywordsAndBuiltInTypesFromPackagedDictionaries() {
        assertTrue(MySqlUtils.isKeyword("SELECT"));
        assertTrue(MySqlUtils.isBuiltinDataType("VARCHAR"));
        assertFalse(MySqlUtils.isKeyword("customer_identifier"));
        assertFalse(MySqlUtils.isBuiltinDataType("customer_identifier"));
    }
}
