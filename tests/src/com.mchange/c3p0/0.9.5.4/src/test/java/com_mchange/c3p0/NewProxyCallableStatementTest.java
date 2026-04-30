/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.c3p0;

import static org.assertj.core.api.Assertions.assertThat;

import com.mchange.v2.c3p0.C3P0ProxyStatement;
import com.mchange.v2.c3p0.WrapperConnectionPoolDataSource;
import java.io.PrintWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Collections;
import java.util.Map;
import java.util.logging.Logger;
import javax.sql.DataSource;
import javax.sql.PooledConnection;
import org.junit.jupiter.api.Test;

public class NewProxyCallableStatementTest {
    @Test
    void rawStatementOperationInvokesMethodOnUnderlyingCallableStatement() throws Exception {
        TrackingDataSource nestedDataSource = new TrackingDataSource();
        WrapperConnectionPoolDataSource connectionPoolDataSource = new WrapperConnectionPoolDataSource(false);
        connectionPoolDataSource.setNestedDataSource(nestedDataSource);
        connectionPoolDataSource.setUsesTraditionalReflectiveProxies(false);

        PooledConnection pooledConnection = connectionPoolDataSource.getPooledConnection();
        Connection connection = pooledConnection.getConnection();
        CallableStatement callableStatement = connection.prepareCall("{ call read_value(?) }");
        C3P0ProxyStatement proxyStatement = (C3P0ProxyStatement) callableStatement;

        Object rawValue = proxyStatement.rawStatementOperation(
                CallableStatement.class.getMethod("getString", int.class),
                C3P0ProxyStatement.RAW_STATEMENT,
                new Object[] {1}
        );

        assertThat(rawValue).isEqualTo("raw-callable-value-1");
        assertThat(nestedDataSource.connectionHandler().callableStatementHandler().rawGetStringCalls())
                .isEqualTo(1);

        callableStatement.close();
        connection.close();
        pooledConnection.close();
    }

    private static Connection newConnectionProxy(TrackingConnectionHandler handler) {
        Connection connection = (Connection) Proxy.newProxyInstance(
                NewProxyCallableStatementTest.class.getClassLoader(),
                new Class<?>[] {Connection.class},
                handler
        );
        handler.attach(connection);
        return connection;
    }

    private static CallableStatement newCallableStatementProxy(
            Connection connection,
            TrackingCallableStatementHandler handler
    ) {
        handler.attach(connection);
        return (CallableStatement) Proxy.newProxyInstance(
                NewProxyCallableStatementTest.class.getClassLoader(),
                new Class<?>[] {CallableStatement.class},
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
            return '\0';
        }
        return null;
    }

    private static Object handleObjectMethod(Object proxy, Method method, Object[] args, String description) {
        switch (method.getName()) {
            case "toString":
                return description;
            case "hashCode":
                return System.identityHashCode(proxy);
            case "equals":
                return proxy == args[0];
            default:
                return null;
        }
    }

    private static final class TrackingDataSource implements DataSource {
        private final TrackingConnectionHandler connectionHandler = new TrackingConnectionHandler();
        private final Connection connection = newConnectionProxy(connectionHandler);
        private PrintWriter logWriter;
        private int loginTimeout;

        @Override
        public Connection getConnection() {
            return connection;
        }

        @Override
        public Connection getConnection(String username, String password) {
            return connection;
        }

        @Override
        public PrintWriter getLogWriter() {
            return logWriter;
        }

        @Override
        public void setLogWriter(PrintWriter logWriter) {
            this.logWriter = logWriter;
        }

        @Override
        public void setLoginTimeout(int seconds) {
            loginTimeout = seconds;
        }

        @Override
        public int getLoginTimeout() {
            return loginTimeout;
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException("Parent logger is not available for this test data source.");
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            if (iface.isInstance(this)) {
                return iface.cast(this);
            }
            throw new SQLException("Cannot unwrap to " + iface.getName());
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return iface.isInstance(this);
        }

        private TrackingConnectionHandler connectionHandler() {
            return connectionHandler;
        }
    }

    private static final class TrackingConnectionHandler implements InvocationHandler {
        private final TrackingCallableStatementHandler callableStatementHandler =
                new TrackingCallableStatementHandler();
        private Connection connectionProxy;
        private boolean closed;
        private boolean autoCommit = true;
        private boolean readOnly;
        private int transactionIsolation = Connection.TRANSACTION_READ_COMMITTED;
        private String catalog = "default";
        private int holdability = ResultSet.CLOSE_CURSORS_AT_COMMIT;
        private Map<String, Class<?>> typeMap = Collections.emptyMap();

        private void attach(Connection connectionProxy) {
            this.connectionProxy = connectionProxy;
        }

        private TrackingCallableStatementHandler callableStatementHandler() {
            return callableStatementHandler;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws SQLException {
            if (method.getDeclaringClass() == Object.class) {
                return handleObjectMethod(proxy, method, args, "tracking-physical-connection");
            }

            String name = method.getName();
            switch (name) {
                case "prepareCall":
                    return newCallableStatementProxy(connectionProxy, callableStatementHandler);
                case "getTransactionIsolation":
                    return transactionIsolation;
                case "setTransactionIsolation":
                    transactionIsolation = (Integer) args[0];
                    return null;
                case "getCatalog":
                    return catalog;
                case "setCatalog":
                    catalog = (String) args[0];
                    return null;
                case "getHoldability":
                    return holdability;
                case "setHoldability":
                    holdability = (Integer) args[0];
                    return null;
                case "getAutoCommit":
                    return autoCommit;
                case "setAutoCommit":
                    autoCommit = (Boolean) args[0];
                    return null;
                case "isReadOnly":
                    return readOnly;
                case "setReadOnly":
                    readOnly = (Boolean) args[0];
                    return null;
                case "getTypeMap":
                    return typeMap;
                case "setTypeMap":
                    typeMap = (Map<String, Class<?>>) args[0];
                    return null;
                case "nativeSQL":
                    return args[0];
                case "isValid":
                    return !closed;
                case "isClosed":
                    return closed;
                case "close":
                    closed = true;
                    return null;
                case "commit":
                case "rollback":
                case "clearWarnings":
                    return null;
                case "unwrap":
                    throw new SQLException("No wrapped connection is available.");
                case "isWrapperFor":
                    return false;
                default:
                    return defaultValue(method.getReturnType());
            }
        }
    }

    private static final class TrackingCallableStatementHandler implements InvocationHandler {
        private Connection connection;
        private boolean closed;
        private int rawGetStringCalls;

        private void attach(Connection connection) {
            this.connection = connection;
        }

        private int rawGetStringCalls() {
            return rawGetStringCalls;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws SQLException {
            if (method.getDeclaringClass() == Object.class) {
                return handleObjectMethod(proxy, method, args, "tracking-raw-callable-statement");
            }

            String name = method.getName();
            switch (name) {
                case "getConnection":
                    return connection;
                case "getString":
                    rawGetStringCalls++;
                    return "raw-callable-value-" + args[0];
                case "isClosed":
                    return closed;
                case "close":
                    closed = true;
                    return null;
                case "unwrap":
                    throw new SQLException("No wrapped callable statement is available.");
                case "isWrapperFor":
                    return false;
                default:
                    return defaultValue(method.getReturnType());
            }
        }
    }
}
