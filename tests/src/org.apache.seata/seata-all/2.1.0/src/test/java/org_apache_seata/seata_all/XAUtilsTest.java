/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_seata.seata_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;

import javax.sql.XAConnection;

import com.alibaba.druid.mock.MockConnection;

import org.apache.seata.rm.datasource.util.XAUtils;
import org.apache.seata.sqlparser.util.JdbcConstants;
import org.junit.jupiter.api.Test;

public class XAUtilsTest {
    @Test
    void createsOracleXAConnectionUsingDatabaseSpecificConstructor() throws Exception {
        Connection physicalConnection = new MockConnection();

        XAConnection xaConnection = XAUtils.createXAConnection(physicalConnection, null, JdbcConstants.ORACLE);

        assertThat(xaConnection).isInstanceOf(oracle.jdbc.xa.client.OracleXAConnection.class);
        assertThat(xaConnection.getConnection()).isSameAs(physicalConnection);
    }
}
