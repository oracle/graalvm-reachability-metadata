/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_liquibase.liquibase_core;

import liquibase.change.ColumnConfig;
import liquibase.database.PreparedStatementFactory;
import liquibase.database.core.PostgresDatabase;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import liquibase.statement.InsertExecutablePreparedStatement;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ExecutablePreparedStatementBaseTest {

    @Test
    void postgresInsertUsesPreparedStatementExecuteWithFlagsWhenAvailable() throws Exception {
        AtomicInteger executeWithFlagsCalls = new AtomicInteger();
        AtomicInteger regularExecuteCalls = new AtomicInteger();
        PreparedStatement preparedStatement = preparedStatementProxy(executeWithFlagsCalls, regularExecuteCalls);
        Connection connection = connectionProxy(preparedStatement);

        PostgresDatabase database = new PostgresDatabase();
        database.setDefaultSchemaName("public");
        ColumnConfig column = new ColumnConfig().setName("name").setValue("liquibase");
        InsertExecutablePreparedStatement statement = new InsertExecutablePreparedStatement(
                database,
                null,
                null,
                "executable_prepared_statement_base_target",
                Collections.singletonList(column),
                null,
                new ClassLoaderResourceAccessor());

        statement.execute(new PreparedStatementFactory(new JdbcConnection(connection)));

        assertEquals(1, executeWithFlagsCalls.get());
        assertEquals(0, regularExecuteCalls.get());
    }

    private static PreparedStatement preparedStatementProxy(
            AtomicInteger executeWithFlagsCalls,
            AtomicInteger regularExecuteCalls) {
        InvocationHandler handler = (Object proxy, Method method, Object[] args) -> {
            String methodName = method.getName();
            if ("executeWithFlags".equals(methodName)) {
                assertEquals(1, args[0]);
                executeWithFlagsCalls.incrementAndGet();
                return null;
            }
            if ("execute".equals(methodName)) {
                regularExecuteCalls.incrementAndGet();
                return true;
            }
            if ("isClosed".equals(methodName)) {
                return false;
            }
            if ("getConnection".equals(methodName)) {
                return connectionProxy((PreparedStatement) proxy);
            }
            return defaultValue(method.getReturnType());
        };
        return (PreparedStatement) Proxy.newProxyInstance(
                ExecutablePreparedStatementBaseTest.class.getClassLoader(),
                new Class<?>[] {PreparedStatement.class, ExecuteWithFlagsStatement.class},
                handler);
    }

    private static Connection connectionProxy(PreparedStatement preparedStatement) {
        InvocationHandler handler = (Object proxy, Method method, Object[] args) -> {
            String methodName = method.getName();
            if ("prepareStatement".equals(methodName)) {
                return preparedStatement;
            }
            if ("isClosed".equals(methodName)) {
                return false;
            }
            if ("toString".equals(methodName)) {
                return "liquibase-test-connection";
            }
            return defaultValue(method.getReturnType());
        };
        return (Connection) Proxy.newProxyInstance(
                ExecutablePreparedStatementBaseTest.class.getClassLoader(),
                new Class<?>[] {Connection.class},
                handler);
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (boolean.class.equals(type)) {
            return false;
        }
        if (char.class.equals(type)) {
            return '\0';
        }
        if (byte.class.equals(type)) {
            return (byte) 0;
        }
        if (short.class.equals(type)) {
            return (short) 0;
        }
        if (int.class.equals(type)) {
            return 0;
        }
        if (long.class.equals(type)) {
            return 0L;
        }
        if (float.class.equals(type)) {
            return 0F;
        }
        if (double.class.equals(type)) {
            return 0D;
        }
        return null;
    }

    private interface ExecuteWithFlagsStatement {
        void executeWithFlags(int flags);
    }
}
