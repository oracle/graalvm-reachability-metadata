/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.c3p0;

import static org.assertj.core.api.Assertions.assertThat;

import com.mchange.v2.c3p0.C3P0ProxyConnection;
import com.mchange.v2.c3p0.WrapperConnectionPoolDataSource;
import java.io.PrintWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.util.Collections;
import java.util.Map;
import java.util.logging.Logger;
import javax.sql.DataSource;
import javax.sql.PooledConnection;
import org.junit.jupiter.api.Test;

public class C3P0PooledConnectionInnerProxyConnectionInvocationHandlerTest {
    @Test
    void traditionalReflectiveProxyConnectionInvokesUnderlyingJdbcOperations() throws Exception {
        TrackingConnectionHandler connectionHandler = new TrackingConnectionHandler();
        WrapperConnectionPoolDataSource dataSource = new WrapperConnectionPoolDataSource(false);
        dataSource.setNestedDataSource(new ProxyDataSource(connectionHandler));
        dataSource.setUsesTraditionalReflectiveProxies(true);

        PooledConnection pooledConnection = dataSource.getPooledConnection();
        try {
            Connection connection = pooledConnection.getConnection();
            C3P0ProxyConnection proxyConnection = (C3P0ProxyConnection) connection;

            assertThat(connection.toString()).contains("C3P0ProxyConnection");
            assertThat(proxyConnection.rawConnectionOperation(
                    Object.class.getMethod("toString"),
                    C3P0ProxyConnection.RAW_CONNECTION,
                    new Object[0]
            )).isEqualTo("tracking-connection");

            connection.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
            connection.setCatalog("analytics");
            connection.setHoldability(ResultSet.HOLD_CURSORS_OVER_COMMIT);
            Statement statement = connection.createStatement();
            PreparedStatement preparedStatement = connection.prepareStatement("select ?");
            CallableStatement callableStatement = connection.prepareCall("{ call sample() }");
            boolean autoCommit = connection.getAutoCommit();

            assertThat(autoCommit).isTrue();
            assertThat(connectionHandler.transactionIsolation).isEqualTo(Connection.TRANSACTION_SERIALIZABLE);
            assertThat(connectionHandler.catalog).isEqualTo("analytics");
            assertThat(connectionHandler.holdability).isEqualTo(ResultSet.HOLD_CURSORS_OVER_COMMIT);
            assertThat(connectionHandler.createdStatements).isEqualTo(1);
            assertThat(connectionHandler.preparedStatements).isEqualTo(1);
            assertThat(connectionHandler.callableStatements).isEqualTo(1);

            statement.close();
            preparedStatement.close();
            callableStatement.close();
            connection.close();

            assertThat(connection.isClosed()).isTrue();
        } finally {
            pooledConnection.close();
        }
    }

    private static Connection newConnectionProxy(TrackingConnectionHandler handler) {
        return (Connection) Proxy.newProxyInstance(
                C3P0PooledConnectionInnerProxyConnectionInvocationHandlerTest.class.getClassLoader(),
                new Class<?>[] {Connection.class},
                handler
        );
    }

    private static Statement newStatementProxy(Class<?> statementType, Connection connection) {
        return (Statement) Proxy.newProxyInstance(
                C3P0PooledConnectionInnerProxyConnectionInvocationHandlerTest.class.getClassLoader(),
                new Class<?>[] {statementType},
                new StatementHandler(statementType.getSimpleName(), connection)
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
        private Connection connectionProxy;
        private boolean closed;
        private boolean autoCommit = true;
        private boolean readOnly;
        private int transactionIsolation = Connection.TRANSACTION_READ_COMMITTED;
        private String catalog = "default";
        private int holdability = ResultSet.CLOSE_CURSORS_AT_COMMIT;
        private Map<String, Class<?>> typeMap = Collections.emptyMap();
        private int createdStatements;
        private int preparedStatements;
        private int callableStatements;

        private void attach(Connection connectionProxy) {
            this.connectionProxy = connectionProxy;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws SQLException {
            if (method.getDeclaringClass() == Object.class) {
                return handleObjectMethod(proxy, method, args, "tracking-connection");
            }

            String name = method.getName();
            switch (name) {
                case "createStatement":
                    createdStatements++;
                    return newStatementProxy(Statement.class, connectionProxy);
                case "prepareStatement":
                    preparedStatements++;
                    return newStatementProxy(PreparedStatement.class, connectionProxy);
                case "prepareCall":
                    callableStatements++;
                    return newStatementProxy(CallableStatement.class, connectionProxy);
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

    private static final class StatementHandler implements InvocationHandler {
        private final String description;
        private final Connection connection;
        private boolean closed;

        private StatementHandler(String description, Connection connection) {
            this.description = description;
            this.connection = connection;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws SQLException {
            if (method.getDeclaringClass() == Object.class) {
                return handleObjectMethod(proxy, method, args, description);
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

    private static final class ProxyDataSource implements DataSource {
        private final TrackingConnectionHandler handler;

        private ProxyDataSource(TrackingConnectionHandler handler) {
            this.handler = handler;
        }

        @Override
        public Connection getConnection() {
            Connection connection = newConnectionProxy(handler);
            handler.attach(connection);
            return connection;
        }

        @Override
        public Connection getConnection(String username, String password) {
            return getConnection();
        }

        @Override
        public PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(PrintWriter out) {
        }

        @Override
        public void setLoginTimeout(int seconds) {
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException();
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            throw new SQLException("No wrapped data source is available.");
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return false;
        }
    }
}
