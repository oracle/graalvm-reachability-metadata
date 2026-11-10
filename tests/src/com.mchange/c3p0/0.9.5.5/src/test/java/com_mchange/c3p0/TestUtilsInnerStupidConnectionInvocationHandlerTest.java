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
    void delegatesQueriesWithRetriesForInjectedConnectionFailures() throws Exception {
        for (int attempt = 0; attempt < 10; attempt++) {
            ComboPooledDataSource source = C3p0Support.pool();
            try {
                try (Connection connection = TestUtils.unreliableCommitDataSource(source).getConnection()) {
                    C3p0Support.assertQuery(connection, 74);
                    return;
                } catch (SQLException failure) {
                    // Retry only the fatal connection failure this utility deliberately injects.
                    if (!"Random Fatal Exception Occurred!!!".equals(failure.getMessage()) || attempt == 9) {
                        throw failure;
                    }
                }
            } finally {
                source.close();
            }
        }
    }

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
