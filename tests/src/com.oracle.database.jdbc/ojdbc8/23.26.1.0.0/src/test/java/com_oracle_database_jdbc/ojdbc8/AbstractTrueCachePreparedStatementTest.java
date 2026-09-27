/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_database_jdbc.ojdbc8;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import oracle.jdbc.driver.AbstractTrueCacheConnection;
import oracle.jdbc.driver.OracleDriver;
import oracle.jdbc.internal.OraclePreparedStatement;
import oracle.jdbc.proxy.oracle$1jdbc$1driver$1AbstractTrueCachePreparedStatement$2oracle$1jdbc$1internal$1OraclePreparedStatement$$$Proxy;
import org.junit.jupiter.api.Test;

public class AbstractTrueCachePreparedStatementTest {
    @Test
    void replaysQueuedConfigurationAndBindingsDuringExecution() throws SQLException {
        PreparedStatementHandler handler = new PreparedStatementHandler();
        OraclePreparedStatement delegate = (OraclePreparedStatement) Proxy.newProxyInstance(
                OraclePreparedStatement.class.getClassLoader(),
                new Class<?>[] {OraclePreparedStatement.class},
                handler);
        try (Connection connection = newTrueCacheConnection()) {
            PreparedStatementHarness statement =
                    new PreparedStatementHarness((AbstractTrueCacheConnection) connection);

            statement.setCheckBindTypes(false);
            statement.setFormOfUse(1, (short) 2);
            statement.defineParameterType(2, Types.INTEGER, 10);
            statement.attach(delegate);
            statement.setString(1, "initial");
            statement.setString(1, "replacement");
            statement.setInt(2, 42);
            statement.setEscapeProcessing(false);
            statement.clearWarnings();

            assertThat(statement.executeUpdate()).isEqualTo(1);
            statement.addBatch();
            assertThat(statement.executeBatch()).containsExactly(1);
            assertThat(handler.getStringBindings()).containsExactly("initial", "replacement");
            assertThat(handler.getIntegerBinding()).isEqualTo(42);
            assertThat(handler.isCheckBindTypesEnabled()).isFalse();
            assertThat(handler.getFormOfUse()).isEqualTo((short) 2);
            assertThat(handler.isEscapeProcessingEnabled()).isFalse();
            assertThat(handler.isUpdateExecuted()).isTrue();
            assertThat(handler.isBatchExecuted()).isTrue();
        }
    }

    private static Connection newTrueCacheConnection() throws SQLException {
        Properties properties = new Properties();
        properties.setProperty("oracle.jdbc.useTrueCacheDriverConnection", "true");
        properties.setProperty("oracle.net.CONNECT_TIMEOUT", "10000");

        Connection connection = new OracleDriver()
                .connect("jdbc:oracle:thin:@//127.0.0.1:1/test-service", properties);
        assertThat(connection).isInstanceOf(AbstractTrueCacheConnection.class);
        return connection;
    }

    private static final class PreparedStatementHarness
            extends oracle$1jdbc$1driver$1AbstractTrueCachePreparedStatement$2oracle$1jdbc$1internal$1OraclePreparedStatement$$$Proxy {
        private PreparedStatementHarness(AbstractTrueCacheConnection connection) {
            super(null, connection, null, null, false);
        }

        private void attach(OraclePreparedStatement primaryStatement) {
            this.primaryStatement = primaryStatement;
        }

        @Override
        protected void createStatement(AbstractTrueCacheConnection connection) { }
    }

    private static final class PreparedStatementHandler implements InvocationHandler {
        private final List<String> stringBindings = new ArrayList<>();
        private Integer integerBinding;
        private boolean checkBindTypesEnabled = true;
        private Short formOfUse;
        private boolean escapeProcessingEnabled = true;
        private boolean updateExecuted;
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
                case "setCheckBindTypes":
                    checkBindTypesEnabled = (Boolean) arguments[0];
                    return null;
                case "setFormOfUse":
                    formOfUse = (Short) arguments[1];
                    return null;
                case "setEscapeProcessing":
                    escapeProcessingEnabled = (Boolean) arguments[0];
                    return null;
                case "executeUpdate":
                    updateExecuted = true;
                    return 1;
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

        private boolean isCheckBindTypesEnabled() {
            return checkBindTypesEnabled;
        }

        private Short getFormOfUse() {
            return formOfUse;
        }

        private boolean isEscapeProcessingEnabled() {
            return escapeProcessingEnabled;
        }

        private boolean isUpdateExecuted() {
            return updateExecuted;
        }

        private boolean isBatchExecuted() {
            return batchExecuted;
        }
    }
}
