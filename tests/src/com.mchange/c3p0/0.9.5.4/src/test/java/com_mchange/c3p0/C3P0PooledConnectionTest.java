/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.c3p0;

import static org.assertj.core.api.Assertions.assertThat;

import com.mchange.v2.c3p0.impl.C3P0PooledConnection;
import com.mchange.v2.c3p0.impl.DefaultConnectionTester;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class C3P0PooledConnectionTest {
    @Test
    void closeCleansUnclosedStatementFromExposedConnection() throws Exception {
        TrackingConnectionHandler connectionHandler = new TrackingConnectionHandler();
        Connection physicalConnection = newConnectionProxy(connectionHandler);
        connectionHandler.attach(physicalConnection);
        C3P0PooledConnection pooledConnection = new C3P0PooledConnection(
                physicalConnection,
                new DefaultConnectionTester(),
                false,
                false,
                null,
                "pooled-connection-test"
        );

        Connection connection = pooledConnection.getConnection();
        Statement statement = connection.createStatement();

        assertThat(connectionHandler.createdStatementCount()).isEqualTo(1);
        assertThat(statement.isClosed()).isFalse();

        pooledConnection.close();

        assertThat(connectionHandler.closedStatementCount()).isEqualTo(1);
        assertThat(statement.isClosed()).isTrue();
        assertThat(connection.isClosed()).isTrue();
        assertThat(connectionHandler.isClosed()).isTrue();
    }

    private static Connection newConnectionProxy(TrackingConnectionHandler handler) {
        return (Connection) Proxy.newProxyInstance(
                C3P0PooledConnectionTest.class.getClassLoader(),
                new Class<?>[] {Connection.class},
                handler
        );
    }

    private static Statement newStatementProxy(Connection connection, TrackingConnectionHandler connectionHandler) {
        TrackingStatementHandler statementHandler = new TrackingStatementHandler(connection);
        connectionHandler.addStatementHandler(statementHandler);
        return (Statement) Proxy.newProxyInstance(
                C3P0PooledConnectionTest.class.getClassLoader(),
                new Class<?>[] {Statement.class},
                statementHandler
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

    private static final class TrackingConnectionHandler implements InvocationHandler {
        private final List<TrackingStatementHandler> statementHandlers = new ArrayList<>();
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

        private void addStatementHandler(TrackingStatementHandler statementHandler) {
            statementHandlers.add(statementHandler);
        }

        private int createdStatementCount() {
            return statementHandlers.size();
        }

        private long closedStatementCount() {
            return statementHandlers.stream()
                    .filter(TrackingStatementHandler::isClosed)
                    .count();
        }

        private boolean isClosed() {
            return closed;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws SQLException {
            if (method.getDeclaringClass() == Object.class) {
                return handleObjectMethod(proxy, method, args, "tracking-physical-connection");
            }

            String name = method.getName();
            switch (name) {
                case "createStatement":
                    return newStatementProxy(connectionProxy, this);
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

    private static final class TrackingStatementHandler implements InvocationHandler {
        private final Connection connection;
        private boolean closed;

        private TrackingStatementHandler(Connection connection) {
            this.connection = connection;
        }

        private boolean isClosed() {
            return closed;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws SQLException {
            if (method.getDeclaringClass() == Object.class) {
                return handleObjectMethod(proxy, method, args, "tracking-statement");
            }

            String name = method.getName();
            switch (name) {
                case "getConnection":
                    return connection;
                case "isClosed":
                    return closed;
                case "close":
                    closed = true;
                    return null;
                case "unwrap":
                    throw new SQLException("No wrapped statement is available.");
                case "isWrapperFor":
                    return false;
                default:
                    return defaultValue(method.getReturnType());
            }
        }
    }
}
