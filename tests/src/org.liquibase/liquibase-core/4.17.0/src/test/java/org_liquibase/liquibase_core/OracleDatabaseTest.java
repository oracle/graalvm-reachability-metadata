/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_liquibase.liquibase_core;

import liquibase.database.core.OracleDatabase;
import liquibase.database.jvm.JdbcConnection;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

public class OracleDatabaseTest {

    @Test
    void setConnectionConfiguresOracleProxySessionAndRemarksReporting() {
        OracleConnectionHandler handler = new OracleConnectionHandler();
        OracleJdbcConnection connection = handler.createConnection();

        OracleDatabase database = new OracleDatabase();
        database.setConnection(new JdbcConnection(connection));

        assertThat(handler.proxySessionUser).isEqualTo("reporting_proxy");
        assertThat(handler.proxySessionOpened).isTrue();
        assertThat(handler.proxySessionChecked).isTrue();
        assertThat(handler.remarksReportingEnabled).isTrue();
        assertThat(database.getConnection()).isInstanceOf(JdbcConnection.class);
    }

    public interface OracleJdbcConnection extends Connection {
        void openProxySession(int type, Properties properties);

        boolean isProxySession();

        void setRemarksReporting(boolean enabled);
    }

    private static final class OracleConnectionHandler implements InvocationHandler {
        private static final String URL = "jdbc:oracle:thin:reporting_proxy/@localhost:1521/service";

        private boolean proxySessionOpened;
        private boolean proxySessionChecked;
        private boolean remarksReportingEnabled;
        private String proxySessionUser;
        private boolean closed;

        private OracleJdbcConnection createConnection() {
            return (OracleJdbcConnection) Proxy.newProxyInstance(
                    OracleDatabaseTest.class.getClassLoader(),
                    new Class<?>[] {OracleJdbcConnection.class},
                    this
            );
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String methodName = method.getName();
            if ("openProxySession".equals(methodName)) {
                Properties properties = (Properties) args[1];
                proxySessionUser = properties.getProperty("PROXY_USER_NAME");
                proxySessionOpened = true;
                return null;
            }
            if ("isProxySession".equals(methodName)) {
                proxySessionChecked = true;
                return true;
            }
            if ("setRemarksReporting".equals(methodName)) {
                remarksReportingEnabled = (boolean) args[0];
                return null;
            }
            if ("getMetaData".equals(methodName)) {
                return createMetaData();
            }
            if ("prepareCall".equals(methodName)) {
                throw new SQLException("DBMS_UTILITY is not available in the test connection");
            }
            if ("prepareStatement".equals(methodName)) {
                throw new SQLException("DDL_LOCK_TIMEOUT is not applied by the test connection");
            }
            if ("getAutoCommit".equals(methodName)) {
                return true;
            }
            if ("setAutoCommit".equals(methodName)) {
                return null;
            }
            if ("isClosed".equals(methodName)) {
                return closed;
            }
            if ("close".equals(methodName)) {
                closed = true;
                return null;
            }
            if ("toString".equals(methodName)) {
                return "Oracle proxy test connection";
            }
            return defaultValue(method.getReturnType());
        }

        private DatabaseMetaData createMetaData() {
            return (DatabaseMetaData) Proxy.newProxyInstance(
                    OracleDatabaseTest.class.getClassLoader(),
                    new Class<?>[] {DatabaseMetaData.class},
                    this::invokeMetaData
            );
        }

        private Object invokeMetaData(Object proxy, Method method, Object[] args) {
            String methodName = method.getName();
            if ("getURL".equals(methodName)) {
                return URL;
            }
            if ("getUserName".equals(methodName)) {
                return "LIQUIBASE";
            }
            if ("getSQLKeywords".equals(methodName)) {
                return "SAMPLE_KEYWORD";
            }
            if ("getDatabaseProductName".equals(methodName)) {
                return "Oracle";
            }
            if ("getDatabaseProductVersion".equals(methodName)) {
                return "19c";
            }
            if ("getDatabaseMajorVersion".equals(methodName)) {
                return 19;
            }
            if ("getDatabaseMinorVersion".equals(methodName)) {
                return 0;
            }
            if ("toString".equals(methodName)) {
                return "Oracle proxy test metadata";
            }
            return defaultValue(method.getReturnType());
        }
    }

    private static Object defaultValue(Class<?> returnType) {
        if (!returnType.isPrimitive()) {
            return null;
        }
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == byte.class) {
            return (byte) 0;
        }
        if (returnType == short.class) {
            return (short) 0;
        }
        if (returnType == int.class) {
            return 0;
        }
        if (returnType == long.class) {
            return 0L;
        }
        if (returnType == float.class) {
            return 0.0F;
        }
        if (returnType == double.class) {
            return 0.0D;
        }
        if (returnType == char.class) {
            return '\0';
        }
        return null;
    }
}
