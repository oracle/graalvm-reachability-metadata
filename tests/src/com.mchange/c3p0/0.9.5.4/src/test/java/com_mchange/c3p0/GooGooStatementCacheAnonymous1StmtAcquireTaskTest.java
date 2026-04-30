/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.c3p0;

import static org.assertj.core.api.Assertions.assertThat;

import com.mchange.v2.async.AsynchronousRunner;
import com.mchange.v2.c3p0.stmt.GlobalMaxOnlyStatementCache;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;

public class GooGooStatementCacheAnonymous1StmtAcquireTaskTest {
    @Test
    void checkoutStatementAcquiresPreparedStatementThroughTask() throws Exception {
        DirectAsynchronousRunner runner = new DirectAsynchronousRunner();
        GlobalMaxOnlyStatementCache cache = new GlobalMaxOnlyStatementCache(runner, null, 2);
        TrackingConnectionHandler connectionHandler = new TrackingConnectionHandler();
        Connection connection = newConnectionProxy(connectionHandler);
        Method prepareStatement = Connection.class.getMethod("prepareStatement", String.class);
        Object[] statementArguments = {"select ?"};

        try {
            PreparedStatement firstCheckout = (PreparedStatement) cache.checkoutStatement(
                    connection,
                    prepareStatement,
                    statementArguments
            );
            cache.checkinStatement(firstCheckout);

            PreparedStatement secondCheckout = (PreparedStatement) cache.checkoutStatement(
                    connection,
                    prepareStatement,
                    statementArguments
            );

            assertThat(secondCheckout).isSameAs(firstCheckout);
            assertThat(connectionHandler.prepareStatementCalls()).isEqualTo(1);
            assertThat(connectionHandler.preparedStatementHandler().clearParametersCalls()).isEqualTo(1);
            assertThat(connectionHandler.preparedStatementHandler().clearBatchCalls()).isEqualTo(1);
            assertThat(runner.postedTasks()).isEqualTo(1);
            assertThat(cache.getNumStatements()).isEqualTo(1);
            assertThat(cache.getNumStatementsCheckedOut()).isEqualTo(1);

            cache.checkinStatement(secondCheckout);
        } finally {
            cache.close();
            runner.close();
        }
    }

    private static Connection newConnectionProxy(TrackingConnectionHandler handler) {
        Connection connection = (Connection) Proxy.newProxyInstance(
                GooGooStatementCacheAnonymous1StmtAcquireTaskTest.class.getClassLoader(),
                new Class<?>[] {Connection.class},
                handler
        );
        handler.attach(connection);
        return connection;
    }

    private static PreparedStatement newPreparedStatementProxy(
            Connection connection,
            TrackingPreparedStatementHandler handler
    ) {
        handler.attach(connection);
        return (PreparedStatement) Proxy.newProxyInstance(
                GooGooStatementCacheAnonymous1StmtAcquireTaskTest.class.getClassLoader(),
                new Class<?>[] {PreparedStatement.class},
                handler
        );
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

    private static final class DirectAsynchronousRunner implements AsynchronousRunner {
        private int postedTasks;
        private boolean closed;

        @Override
        public void postRunnable(Runnable runnable) {
            if (closed) {
                throw new IllegalStateException("Runner is closed.");
            }
            postedTasks++;
            runnable.run();
        }

        @Override
        public void close(boolean skipRemainingTasks) {
            closed = true;
        }

        @Override
        public void close() {
            close(false);
        }

        private int postedTasks() {
            return postedTasks;
        }
    }

    private static final class TrackingConnectionHandler implements InvocationHandler {
        private final TrackingPreparedStatementHandler preparedStatementHandler =
                new TrackingPreparedStatementHandler();
        private Connection connection;
        private int prepareStatementCalls;
        private boolean closed;

        private void attach(Connection connection) {
            this.connection = connection;
        }

        private int prepareStatementCalls() {
            return prepareStatementCalls;
        }

        private TrackingPreparedStatementHandler preparedStatementHandler() {
            return preparedStatementHandler;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws SQLException {
            if (method.getDeclaringClass() == Object.class) {
                return handleObjectMethod(proxy, method, args, "tracking-physical-connection");
            }

            switch (method.getName()) {
                case "prepareStatement":
                    prepareStatementCalls++;
                    return newPreparedStatementProxy(connection, preparedStatementHandler);
                case "isClosed":
                    return closed;
                case "close":
                    closed = true;
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

    private static final class TrackingPreparedStatementHandler implements InvocationHandler {
        private Connection connection;
        private int clearParametersCalls;
        private int clearBatchCalls;
        private boolean closed;

        private void attach(Connection connection) {
            this.connection = connection;
        }

        private int clearParametersCalls() {
            return clearParametersCalls;
        }

        private int clearBatchCalls() {
            return clearBatchCalls;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws SQLException {
            if (method.getDeclaringClass() == Object.class) {
                return handleObjectMethod(proxy, method, args, "tracking-prepared-statement");
            }

            switch (method.getName()) {
                case "getConnection":
                    return connection;
                case "clearParameters":
                    clearParametersCalls++;
                    return null;
                case "clearBatch":
                    clearBatchCalls++;
                    return null;
                case "isClosed":
                    return closed;
                case "close":
                    closed = true;
                    return null;
                case "unwrap":
                    throw new SQLException("No wrapped prepared statement is available.");
                case "isWrapperFor":
                    return false;
                default:
                    return defaultValue(method.getReturnType());
            }
        }
    }
}
