/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.druid;

import com.alibaba.druid.util.H2Utils;
import java.sql.Connection;
import java.sql.DriverManager;
import javax.sql.XAConnection;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class H2UtilsTest {
    @Test
    void createsXaConnectionAroundPhysicalH2Connection() throws Exception {
        Object factory = H2Utils.createJdbcDataSourceFactory();
        assertTrue(H2Utils.getNextId(H2Utils.XA_DATA_SOURCE) >= 0);

        try (Connection physical = DriverManager.getConnection("jdbc:h2:mem:druid_h2_xa;DB_CLOSE_DELAY=-1")) {
            XAConnection xaConnection = H2Utils.createXAConnection(factory, physical);
            try (Connection logical = xaConnection.getConnection()) {
                assertTrue(logical.isValid(10));
            } finally {
                xaConnection.close();
            }
        }
    }
}
