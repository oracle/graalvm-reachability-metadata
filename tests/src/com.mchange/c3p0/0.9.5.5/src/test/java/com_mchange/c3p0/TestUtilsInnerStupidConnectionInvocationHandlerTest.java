/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.c3p0;

import com.mchange.v2.c3p0.ComboPooledDataSource;
import com.mchange.v2.c3p0.util.TestUtils;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class TestUtilsInnerStupidConnectionInvocationHandlerTest {
    @Test
    void closedConnectionRejectsFurtherOperations() throws Exception {
        ComboPooledDataSource source = C3p0Support.pool();
        try {
            Connection connection = TestUtils.unreliableCommitDataSource(source).getConnection();
            connection.close();
            assertThrows(SQLException.class, connection::createStatement);
        } finally {
            source.close();
        }
    }
}
