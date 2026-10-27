/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_database_jdbc.ojdbc11;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLRecoverableException;
import java.sql.Statement;
import oracle.jdbc.datasource.impl.OracleConnectionBuilderImpl;
import oracle.jdbc.internal.OracleConnection;
import oracle.jdbc.proxy.ProxyFactory;
import oracle.jdbc.proxy.oracle$1jdbc$1replay$1driver$1NonTxnReplayableConnection$2java$1sql$1Connection$$$Proxy;
import oracle.jdbc.replay.OracleDataSourceImpl;
import oracle.jdbc.replay.driver.NonTxnReplayableConnection;
import org.junit.jupiter.api.Test;

public class NonTxnReplayableBaseTest {
    @Test
    void replaysARecoverableConnectionCallAgainstAReplacementDelegate() throws SQLException {
        ConnectionHandler originalHandler = new ConnectionHandler(true, null);
        ConnectionHandler replacementHandler = new ConnectionHandler(false, "replayed-catalog");
        OracleConnection originalConnection = connectionFor(originalHandler);
        OracleConnection replacementConnection = connectionFor(replacementHandler);
        OfflineReplayDataSource dataSource = new OfflineReplayDataSource(replacementConnection);
        ProxyFactory factory = ProxyFactory.createProxyFactory(NonTxnReplayableConnection.class);

        try (Connection connection = replayConnectionFor(factory, originalConnection)) {
            NonTxnReplayableConnection replayableConnection = (NonTxnReplayableConnection) connection;
            replayableConnection.initialize(dataSource, null);
            replayableConnection.beginRequest();
            try {
                assertThat(connection.getCatalog()).isEqualTo("replayed-catalog");
                assertThat(originalHandler.getCatalogCalls()).isEqualTo(1);
                assertThat(replacementHandler.getCatalogCalls()).isEqualTo(1);
                assertThat(dataSource.getReconnects()).isEqualTo(1);
            } finally {
                replayableConnection.endRequest();
            }
        }

        assertThat(replacementHandler.isClosed()).isTrue();
    }

    private static Connection replayConnectionFor(ProxyFactory factory, OracleConnection delegate) {
        oracle$1jdbc$1replay$1driver$1NonTxnReplayableConnection$2java$1sql$1Connection$$$Proxy connection =
                factory.proxyForType(Connection.class);
        connection.setDelegate(delegate);
        return connection;
    }

    private static OracleConnection connectionFor(ConnectionHandler handler) {
        return (OracleConnection) Proxy.newProxyInstance(
                OracleConnection.class.getClassLoader(), new Class<?>[] {OracleConnection.class}, handler);
    }

    private static Statement statementFor() {
        InvocationHandler handler = (proxy, method, arguments) -> {
            switch (method.getName()) {
                case "execute":
                    return true;
                case "isClosed":
                    return false;
                case "toString":
                    return "offline-replay-statement";
                default:
                    return defaultValue(method.getReturnType());
            }
        };
        return (Statement) Proxy.newProxyInstance(
                Statement.class.getClassLoader(), new Class<?>[] {Statement.class}, handler);
    }

    private static Object defaultValue(Class<?> returnType) {
        if (!returnType.isPrimitive() || returnType == void.class) {
            return null;
        }
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == char.class) {
            return '\0';
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
        return 0.0D;
    }

    private static final class ConnectionHandler implements InvocationHandler {
        private final boolean failCatalog;
        private final String catalog;
        private int catalogCalls;
        private boolean closed;

        private ConnectionHandler(boolean failCatalog, String catalog) {
            this.failCatalog = failCatalog;
            this.catalog = catalog;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] arguments) throws SQLRecoverableException {
            switch (method.getName()) {
                case "createStatement":
                    return statementFor();
                case "getCatalog":
                    catalogCalls++;
                    if (failCatalog) {
                        throw new SQLRecoverableException("connection interrupted");
                    }
                    return catalog;
                case "inLocalTransaction":
                    return false;
                case "close":
                    closed = true;
                    return null;
                case "isClosed":
                    return closed;
                case "toString":
                    return failCatalog ? "original-connection" : "replacement-connection";
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "equals":
                    return proxy == arguments[0];
                default:
                    return defaultValue(method.getReturnType());
            }
        }

        private int getCatalogCalls() {
            return catalogCalls;
        }

        private boolean isClosed() {
            return closed;
        }
    }

    private static final class OfflineReplayDataSource extends OracleDataSourceImpl {
        private final OracleConnection replacementConnection;
        private int reconnects;

        private OfflineReplayDataSource(OracleConnection replacementConnection) throws SQLException {
            this.replacementConnection = replacementConnection;
        }

        @Override
        public Connection getConnectionNoProxy(OracleConnectionBuilderImpl connectionBuilder) {
            reconnects++;
            return replacementConnection;
        }

        private int getReconnects() {
            return reconnects;
        }
    }
}
