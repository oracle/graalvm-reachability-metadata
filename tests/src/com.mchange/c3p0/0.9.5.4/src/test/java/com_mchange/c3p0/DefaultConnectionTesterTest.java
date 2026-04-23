/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.c3p0;

import com.mchange.v2.c3p0.ConnectionTester;
import com.mchange.v2.c3p0.cfg.C3P0Config;
import com.mchange.v2.c3p0.impl.DefaultConnectionTester;
import com.mchange.v2.cfg.MultiPropertiesConfig;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultConnectionTesterTest {
    private static final String QUERYLESS_TEST_RUNNER_PROPERTY =
        "com.mchange.v2.c3p0.impl.DefaultConnectionTester.querylessTestRunner";

    @Test
    void resolvesQuerylessRunnerFromStaticFieldName() throws Exception {
        withQuerylessRunnerOverride("THREAD_LOCAL", () -> {
            DefaultConnectionTester tester = new DefaultConnectionTester();
            try (Connection connection = C3p0TestSupport.newDriverManagerDataSource("default-tester-short").getConnection()) {
                assertThat(tester.activeCheckConnection(connection, null, new Throwable[1]))
                    .isEqualTo(ConnectionTester.CONNECTION_IS_OKAY);
            }
        });
    }

    @Test
    void resolvesQuerylessRunnerFromClassName() throws Exception {
        RecordingQuerylessTestRunner.reset();

        withQuerylessRunnerOverride(RecordingQuerylessTestRunner.class.getName(), () -> {
            DefaultConnectionTester tester = new DefaultConnectionTester();
            try (Connection connection = C3p0TestSupport.newDriverManagerDataSource("default-tester-class").getConnection()) {
                assertThat(tester.activeCheckConnection(connection, null, new Throwable[1]))
                    .isEqualTo(ConnectionTester.CONNECTION_IS_OKAY);
                assertThat(RecordingQuerylessTestRunner.invocationCount()).isEqualTo(1);
            }
        });
    }

    private static void withQuerylessRunnerOverride(String value, C3p0TestSupport.ThrowingRunnable action) throws Exception {
        Properties overrides = new Properties();
        overrides.setProperty(QUERYLESS_TEST_RUNNER_PROPERTY, value);
        MultiPropertiesConfig overrideConfig = MultiPropertiesConfig.fromProperties("DefaultConnectionTesterTest", overrides);

        try {
            C3P0Config.refreshMainConfig(new MultiPropertiesConfig[] {overrideConfig}, "DefaultConnectionTesterTest override");
            assertThat(C3P0Config.getMultiPropertiesConfig().getProperty(QUERYLESS_TEST_RUNNER_PROPERTY)).isEqualTo(value);
            action.run();
        } finally {
            C3P0Config.refreshMainConfig();
        }
    }

    public static final class RecordingQuerylessTestRunner implements DefaultConnectionTester.QuerylessTestRunner {
        private static final AtomicInteger INVOCATIONS = new AtomicInteger();

        public static int invocationCount() {
            return INVOCATIONS.get();
        }

        public static void reset() {
            INVOCATIONS.set(0);
        }

        @Override
        public int activeCheckConnectionNoQuery(Connection connection, Throwable[] rootCauseOutParamHolder) {
            INVOCATIONS.incrementAndGet();
            return ConnectionTester.CONNECTION_IS_OKAY;
        }
    }
}
