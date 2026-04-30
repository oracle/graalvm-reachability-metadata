/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.c3p0;

import static org.assertj.core.api.Assertions.assertThat;

import com.mchange.v2.c3p0.ConnectionTester;
import com.mchange.v2.c3p0.cfg.C3P0Config;
import com.mchange.v2.c3p0.impl.DefaultConnectionTester;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public class DefaultConnectionTesterTest {
    private static final String QUERYLESS_TEST_RUNNER_PROPERTY =
            "com.mchange.v2.c3p0.impl.DefaultConnectionTester.querylessTestRunner";

    @AfterEach
    void clearConfiguredQuerylessTestRunner() {
        System.clearProperty(QUERYLESS_TEST_RUNNER_PROPERTY);
        C3P0Config.refreshMainConfig();
    }

    @Test
    void createsQuerylessTestRunnerFromConfiguredClassName() {
        configureQuerylessTestRunner("com.mchange.v2.c3p0.impl.ThreadLocalQuerylessTestRunner");
        IsValidConnectionHandler connectionHandler = new IsValidConnectionHandler();
        Connection connection = newConnectionProxy(connectionHandler);

        DefaultConnectionTester tester = new DefaultConnectionTester();
        Throwable[] rootCauseOutParamHolder = new Throwable[1];

        int status = tester.activeCheckConnection(connection, null, rootCauseOutParamHolder);

        assertThat(status).isEqualTo(ConnectionTester.CONNECTION_IS_OKAY);
        assertThat(rootCauseOutParamHolder[0]).isNull();
        assertThat(connectionHandler.isValidCalls).isEqualTo(1);
    }

    @Test
    void usesConfiguredStaticQuerylessTestRunnerField() {
        configureQuerylessTestRunner("METADATA_TABLESEARCH");
        MetadataTableSearchConnectionHandler connectionHandler = new MetadataTableSearchConnectionHandler();
        Connection connection = newConnectionProxy(connectionHandler);

        DefaultConnectionTester tester = new DefaultConnectionTester();
        int status = tester.activeCheckConnection(connection, null, new Throwable[1]);

        assertThat(status).isEqualTo(ConnectionTester.CONNECTION_IS_OKAY);
        assertThat(connectionHandler.getMetaDataCalls).isEqualTo(1);
        assertThat(connectionHandler.isValidCalls).isZero();
        assertThat(connectionHandler.metadataHandler.getTablesCalls).isEqualTo(1);
    }

    private static void configureQuerylessTestRunner(String querylessTestRunner) {
        System.setProperty(QUERYLESS_TEST_RUNNER_PROPERTY, querylessTestRunner);
        C3P0Config.refreshMainConfig();
    }

    private static Connection newConnectionProxy(InvocationHandler handler) {
        return (Connection) Proxy.newProxyInstance(
                DefaultConnectionTesterTest.class.getClassLoader(),
                new Class<?>[] {Connection.class},
                handler
        );
    }

    private static DatabaseMetaData newDatabaseMetaDataProxy(MetadataHandler handler) {
        return (DatabaseMetaData) Proxy.newProxyInstance(
                DefaultConnectionTesterTest.class.getClassLoader(),
                new Class<?>[] {DatabaseMetaData.class},
                handler
        );
    }

    private static Object defaultValue(Class<?> returnType) {
        if (returnType == Void.TYPE) {
            return null;
        }
        if (returnType == Boolean.TYPE) {
            return Boolean.FALSE;
        }
        if (returnType == Byte.TYPE) {
            return (byte) 0;
        }
        if (returnType == Short.TYPE) {
            return (short) 0;
        }
        if (returnType == Integer.TYPE) {
            return 0;
        }
        if (returnType == Long.TYPE) {
            return 0L;
        }
        if (returnType == Float.TYPE) {
            return 0F;
        }
        if (returnType == Double.TYPE) {
            return 0D;
        }
        if (returnType == Character.TYPE) {
            return (char) 0;
        }
        return null;
    }

    private static final class IsValidConnectionHandler implements InvocationHandler {
        private int isValidCalls;

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            if ("isValid".equals(method.getName())) {
                isValidCalls++;
                return Boolean.TRUE;
            }
            if ("toString".equals(method.getName())) {
                return "is-valid-connection";
            }
            return defaultValue(method.getReturnType());
        }
    }

    private static final class MetadataTableSearchConnectionHandler implements InvocationHandler {
        private final MetadataHandler metadataHandler = new MetadataHandler();
        private int getMetaDataCalls;
        private int isValidCalls;

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String methodName = method.getName();
            if ("getMetaData".equals(methodName)) {
                getMetaDataCalls++;
                return newDatabaseMetaDataProxy(metadataHandler);
            }
            if ("isValid".equals(methodName)) {
                isValidCalls++;
                throw new SQLException("Connection.isValid should not be used by METADATA_TABLESEARCH.");
            }
            if ("toString".equals(methodName)) {
                return "metadata-table-search-connection";
            }
            return defaultValue(method.getReturnType());
        }
    }

    private static final class MetadataHandler implements InvocationHandler {
        private int getTablesCalls;

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            if ("getTables".equals(method.getName())) {
                getTablesCalls++;
                return null;
            }
            return defaultValue(method.getReturnType());
        }
    }
}
