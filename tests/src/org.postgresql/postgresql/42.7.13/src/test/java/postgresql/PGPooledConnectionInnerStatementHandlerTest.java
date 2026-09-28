/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package postgresql;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.Statement;

import org.junit.jupiter.api.Test;
import org.postgresql.PGStatement;
import org.postgresql.ds.PGPooledConnection;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the statement handle that delegates JDBC calls through
 * {@code PGPooledConnection.StatementHandler}.
 */
public class PGPooledConnectionInnerStatementHandlerTest {

    @Test
    void statementHandlerDelegatesUnhandledObjectMethodsToWrappedStatement() throws Throwable {
        Statement physicalStatement = statementProxy();
        Connection physicalConnection = connectionProxy(physicalStatement);
        PGPooledConnection pooledConnection = new PGPooledConnection(physicalConnection, true);

        try {
            try (Connection connection = pooledConnection.getConnection();
                    Statement statement = connection.createStatement()) {
                assertThat(statement).isInstanceOf(PGStatement.class);
                assertThat(statement.toString()).contains("Pooled statement wrapping physical statement");

                InvocationHandler statementHandler = Proxy.getInvocationHandler(statement);
                Method getClassMethod = Object.class.getMethod("getClass");
                Object delegatedClass = statementHandler.invoke(statement, getClassMethod, null);

                assertThat(delegatedClass).isEqualTo(physicalStatement.getClass());
            }
        } finally {
            pooledConnection.close();
        }
    }

    private static Connection connectionProxy(Statement statement) {
        InvocationHandler handler = new InvocationHandler() {
            private boolean closed;

            @Override
            public Object invoke(Object proxy, Method method, Object[] args) {
                String methodName = method.getName();
                if (method.getDeclaringClass() == Object.class) {
                    return invokeObjectMethod(proxy, methodName, args);
                }
                switch (methodName) {
                    case "setAutoCommit":
                    case "clearWarnings":
                    case "rollback":
                        return null;
                    case "getAutoCommit":
                        return true;
                    case "isClosed":
                        return closed;
                    case "close":
                        closed = true;
                        return null;
                    case "createStatement":
                        return statement;
                    default:
                        throw new UnsupportedOperationException("Unexpected Connection method: " + methodName);
                }
            }
        };
        return (Connection) Proxy.newProxyInstance(PGPooledConnectionInnerStatementHandlerTest.class.getClassLoader(),
                new Class<?>[] {Connection.class}, handler);
    }

    private static Statement statementProxy() {
        InvocationHandler handler = new InvocationHandler() {
            private boolean closed;

            @Override
            public Object invoke(Object proxy, Method method, Object[] args) {
                String methodName = method.getName();
                if (method.getDeclaringClass() == Object.class) {
                    return invokeObjectMethod(proxy, methodName, args);
                }
                switch (methodName) {
                    case "isClosed":
                        return closed;
                    case "close":
                        closed = true;
                        return null;
                    default:
                        throw new UnsupportedOperationException("Unexpected Statement method: " + methodName);
                }
            }
        };
        return (Statement) Proxy.newProxyInstance(PGPooledConnectionInnerStatementHandlerTest.class.getClassLoader(),
                new Class<?>[] {Statement.class, PGStatement.class}, handler);
    }

    private static Object invokeObjectMethod(Object proxy, String methodName, Object[] args) {
        switch (methodName) {
            case "toString":
                return proxy.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(proxy));
            case "hashCode":
                return System.identityHashCode(proxy);
            case "equals":
                return proxy == args[0];
            default:
                throw new UnsupportedOperationException("Unexpected Object method: " + methodName);
        }
    }
}
