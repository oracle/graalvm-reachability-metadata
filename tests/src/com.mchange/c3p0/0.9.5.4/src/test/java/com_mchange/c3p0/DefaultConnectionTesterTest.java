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

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultConnectionTesterTest {
    private static final String QUERYLESS_TEST_RUNNER_PROPERTY =
        "com.mchange.v2.c3p0.impl.DefaultConnectionTester.querylessTestRunner";

    @Test
    void resolvesQuerylessRunnerFromStaticFieldName() throws Exception {
        C3p0TestSupport.withRefreshedProperty(QUERYLESS_TEST_RUNNER_PROPERTY, "THREAD_LOCAL", () -> {
            DefaultConnectionTester tester = new DefaultConnectionTester();
            try (Connection connection = C3p0TestSupport.newDriverManagerDataSource("default-tester-short").getConnection()) {
                assertThat(tester.activeCheckConnection(connection, null, new Throwable[1]))
                    .isEqualTo(ConnectionTester.CONNECTION_IS_OKAY);
            }
        });
    }

    @Test
    void resolvesQuerylessRunnerFromClassName() throws Exception {
        C3p0TestSupport.withRefreshedProperty(
            QUERYLESS_TEST_RUNNER_PROPERTY,
            "com.mchange.v2.c3p0.impl.ThreadLocalQuerylessTestRunner",
            () -> {
                DefaultConnectionTester tester = new DefaultConnectionTester();
                try (Connection connection = C3p0TestSupport.newDriverManagerDataSource("default-tester-class").getConnection()) {
                    assertThat(tester.activeCheckConnection(connection, null, new Throwable[1]))
                        .isEqualTo(ConnectionTester.CONNECTION_IS_OKAY);
                }
            }
        );
    }
}
