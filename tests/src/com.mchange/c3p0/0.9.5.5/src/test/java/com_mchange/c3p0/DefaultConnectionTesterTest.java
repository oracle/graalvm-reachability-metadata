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
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class DefaultConnectionTesterTest {
    private static final String RUNNER_PROPERTY = "com.mchange.v2.c3p0.impl.DefaultConnectionTester.querylessTestRunner";

    @Test
    void selectsBuiltInQuerylessRunnerByName() throws Exception {
        try (AutoCloseable configuration = C3p0Support.configuration(RUNNER_PROPERTY, "IS_VALID");
                        Connection connection = C3p0Support.unpooled().getConnection()) {
            Throwable[] cause = new Throwable[1];
            assertEquals(ConnectionTester.CONNECTION_IS_OKAY,
                            new DefaultConnectionTester().activeCheckConnection(connection, null, cause));
            assertNull(cause[0]);
        }
    }

    @Test
    void selectsAnApplicationProvidedQuerylessRunner() throws Exception {
        try (AutoCloseable configuration = C3p0Support.configuration(RUNNER_PROPERTY, ApplicationRunner.class.getName());
                        Connection connection = C3p0Support.unpooled().getConnection()) {
            DefaultConnectionTester tester = new DefaultConnectionTester();
            assertEquals(ConnectionTester.CONNECTION_IS_OKAY, tester.activeCheckConnection(connection));
            connection.close();
            assertEquals(ConnectionTester.CONNECTION_IS_INVALID, tester.activeCheckConnection(connection));
        }
    }

    public static class ApplicationRunner implements DefaultConnectionTester.QuerylessTestRunner {
        @Override
        public int activeCheckConnectionNoQuery(Connection connection, Throwable[] cause) {
            try {
                return connection.isValid(10) ? ConnectionTester.CONNECTION_IS_OKAY : ConnectionTester.CONNECTION_IS_INVALID;
            } catch (SQLException exception) {
                if (cause != null) {
                    cause[0] = exception;
                }
                return ConnectionTester.CONNECTION_IS_INVALID;
            }
        }
    }
}
