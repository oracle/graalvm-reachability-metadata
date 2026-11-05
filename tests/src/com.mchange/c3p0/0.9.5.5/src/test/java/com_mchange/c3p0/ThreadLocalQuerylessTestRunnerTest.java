/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.c3p0;

import com.mchange.v2.c3p0.ConnectionTester;
import com.mchange.v2.c3p0.impl.DefaultConnectionTester;
import org.junit.jupiter.api.Test;

import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ThreadLocalQuerylessTestRunnerTest {
    @Test
    void detectsAndReusesTheDriversIsValidMethod() throws Exception {
        try (AutoCloseable configuration = C3p0Support.configuration(
                        "com.mchange.v2.c3p0.impl.DefaultConnectionTester.querylessTestRunner", "THREAD_LOCAL");
                        Connection connection = C3p0Support.unpooled().getConnection()) {
            DefaultConnectionTester tester = new DefaultConnectionTester();
            assertEquals(ConnectionTester.CONNECTION_IS_OKAY, tester.activeCheckConnection(connection));
            assertEquals(ConnectionTester.CONNECTION_IS_OKAY, tester.activeCheckConnection(connection));
            connection.close();
            assertEquals(ConnectionTester.CONNECTION_IS_INVALID, tester.activeCheckConnection(connection));
        }
    }
}
