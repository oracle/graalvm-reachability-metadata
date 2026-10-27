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
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import oracle.jdbc.driver.AbstractTrueCacheConnection;
import oracle.jdbc.internal.OracleConnection;
import oracle.jdbc.internal.OraclePreparedStatement;
import oracle.jdbc.proxy.oracle$1jdbc$1driver$1AbstractTrueCachePreparedStatement$2oracle$1jdbc$1internal$1OraclePreparedStatement$$$Proxy;
import org.junit.jupiter.api.Test;

public class AbstractTrueCachePreparedStatementTest {
    @Test
    void replaysQueuedBindingsDuringBatchExecution() throws SQLException {
        PreparedStatementHandler handler = new PreparedStatementHandler();
        OraclePreparedStatement delegate = (OraclePreparedStatement) Proxy.newProxyInstance(
                OraclePreparedStatement.class.getClassLoader(),
                new Class<?>[] {OraclePreparedStatement.class},
                handler);
        PreparedStatementHarness statement =
                new PreparedStatementHarness(new DetachedTrueCacheConnection(), delegate);

        statement.setString(1, "initial");
        statement.setString(1, "replacement");
        statement.setInt(2, 42);
        statement.addBatch();

        assertThat(statement.executeBatch()).containsExactly(1);
        assertThat(handler.getStringBindings()).containsExactly("initial", "replacement");
        assertThat(handler.getIntegerBinding()).isEqualTo(42);
        assertThat(handler.isBatchExecuted()).isTrue();
    }

    private static final class PreparedStatementHarness
            extends oracle$1jdbc$1driver$1AbstractTrueCachePreparedStatement$2oracle$1jdbc$1internal$1OraclePreparedStatement$$$Proxy {
        private PreparedStatementHarness(
                AbstractTrueCacheConnection connection, OraclePreparedStatement primaryStatement) {
            super(null, connection, null, null, false);
            this.primaryStatement = primaryStatement;
        }

        @Override
        protected void createStatement(AbstractTrueCacheConnection connection) { }
    }

    private static final class DetachedTrueCacheConnection extends AbstractTrueCacheConnection {
        private OracleConnection delegate;

        @Override
        protected Object getCreator() {
            return this;
        }

        @Override
        protected OracleConnection getDelegate() {
            return delegate;
        }

        @Override
        protected void setDelegate(OracleConnection delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean isReadOnly() {
            return false;
        }
    }

    private static final class PreparedStatementHandler implements InvocationHandler {
        private final List<String> stringBindings = new ArrayList<>();
        private Integer integerBinding;
        private boolean batchExecuted;

        @Override
        public Object invoke(Object proxy, Method method, Object[] arguments) {
            switch (method.getName()) {
                case "setString":
                    stringBindings.add((String) arguments[1]);
                    return null;
                case "setInt":
                    integerBinding = (Integer) arguments[1];
                    return null;
                case "executeBatch":
                    batchExecuted = true;
                    return new int[] {1};
                default:
                    return defaultValue(method.getReturnType());
            }
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

        private List<String> getStringBindings() {
            return stringBindings;
        }

        private Integer getIntegerBinding() {
            return integerBinding;
        }

        private boolean isBatchExecuted() {
            return batchExecuted;
        }
    }
}
