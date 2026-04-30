/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.c3p0;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.mchange.v2.c3p0.util.TestUtils;
import com.mchange.v2.sql.filter.FilterConnection;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

public class TestUtilsInnerStupidConnectionInvocationHandlerTest {
    @Test
    void delegatesOpenConnectionOperationsToWrappedConnection() throws Exception {
        for (int attempt = 0; attempt < 25; attempt++) {
            RecordingConnection physicalConnection = new RecordingConnection();
            DataSource dataSource = TestUtils.unreliableCommitDataSource(new SingleConnectionDataSource(physicalConnection));
            Connection connection = dataSource.getConnection();

            try {
                boolean autoCommit = connection.getAutoCommit();

                assertThat(autoCommit).isFalse();
                assertThat(physicalConnection.getAutoCommitCalls()).isEqualTo(1);
                return;
            } catch (SQLException ignored) {
                // TestUtils deliberately injects rare random connection failures.
            } finally {
                connection.close();
            }
        }

        fail("Connection proxy did not delegate getAutoCommit before the injected failures were exhausted.");
    }

    private static final class SingleConnectionDataSource implements DataSource {
        private final Connection connection;

        private SingleConnectionDataSource(Connection connection) {
            this.connection = connection;
        }

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
    }

    private static final class RecordingConnection extends FilterConnection {
        private int getAutoCommitCalls;

        @Override
        public boolean getAutoCommit() {
            getAutoCommitCalls++;
            return false;
        }

        @Override
        public void rollback() {
        }

        @Override
        public void close() {
        }

        private int getAutoCommitCalls() {
            return getAutoCommitCalls;
        }
    }
}
