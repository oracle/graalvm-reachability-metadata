/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_liquibase.liquibase_core;

import liquibase.change.ColumnConfig;
import liquibase.database.core.PostgresDatabase;
import liquibase.resource.ClassLoaderResourceAccessor;
import liquibase.statement.ExecutablePreparedStatementBase;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ExecutablePreparedStatementBaseTest {

    @Test
    void executesPostgresPreparedStatementThroughExecuteWithFlagsMethod() throws SQLException {
        RecordingPreparedStatementHandler handler = new RecordingPreparedStatementHandler();
        PreparedStatement statement = (PreparedStatement) Proxy.newProxyInstance(
                ExecutablePreparedStatementBaseTest.class.getClassLoader(),
                new Class<?>[] {PreparedStatement.class, ExecuteWithFlagsCapable.class},
                handler
        );

        TestExecutablePreparedStatement executableStatement = new TestExecutablePreparedStatement();
        executableStatement.execute(statement);

        assertThat(handler.getExecuteWithFlagsCalls()).isEqualTo(1);
        assertThat(handler.getLastFlags()).isEqualTo(1);
        assertThat(handler.getExecuteCalls()).isZero();
    }

    public interface ExecuteWithFlagsCapable {
        void executeWithFlags(int flags);
    }

    private static final class TestExecutablePreparedStatement extends ExecutablePreparedStatementBase {
        private TestExecutablePreparedStatement() {
            super(
                    new PostgresDatabase(),
                    null,
                    null,
                    "example_table",
                    Collections.emptyList(),
                    null,
                    new ClassLoaderResourceAccessor()
            );
        }

        private void execute(PreparedStatement statement) throws SQLException {
            executePreparedStatement(statement);
        }

        @Override
        public boolean skipOnUnsupported() {
            return false;
        }

        @Override
        public boolean continueOnError() {
            return false;
        }

        @Override
        protected String generateSql(List<ColumnConfig> cols) {
            return "select 1";
        }
    }

    private static final class RecordingPreparedStatementHandler implements InvocationHandler {
        private int executeWithFlagsCalls;
        private int executeCalls;
        private int lastFlags;

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            switch (method.getName()) {
                case "executeWithFlags":
                    executeWithFlagsCalls++;
                    lastFlags = (Integer) args[0];
                    return null;
                case "execute":
                    executeCalls++;
                    return false;
                case "toString":
                    return "recording prepared statement";
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "equals":
                    return proxy == args[0];
                default:
                    throw new UnsupportedOperationException("Unexpected method call: " + method.getName());
            }
        }

        private int getExecuteWithFlagsCalls() {
            return executeWithFlagsCalls;
        }

        private int getExecuteCalls() {
            return executeCalls;
        }

        private int getLastFlags() {
            return lastFlags;
        }
    }
}
