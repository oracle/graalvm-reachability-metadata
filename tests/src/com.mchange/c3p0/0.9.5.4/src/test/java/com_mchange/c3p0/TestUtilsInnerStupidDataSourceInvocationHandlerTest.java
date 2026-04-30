/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.c3p0;

import static org.assertj.core.api.Assertions.assertThat;

import com.mchange.v2.c3p0.util.TestUtils;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

public class TestUtilsInnerStupidDataSourceInvocationHandlerTest {
    @Test
    void wrapsDataSourceConnectionsWithUnreliableCommitConnectionProxy() throws Exception {
        RecordingDataSource delegate = new RecordingDataSource();
        DataSource dataSource = TestUtils.unreliableCommitDataSource(delegate);

        Connection connection = dataSource.getConnection();

        assertThat(connection).isNotNull();
        assertThat(delegate.connectionRequests()).isEqualTo(1);

        connection.close();
    }

    @Test
    void delegatesNonConnectionDataSourceMethodsToWrappedDataSource() throws Exception {
        RecordingDataSource delegate = new RecordingDataSource();
        delegate.setLoginTimeout(11);
        DataSource dataSource = TestUtils.unreliableCommitDataSource(delegate);

        int loginTimeout = dataSource.getLoginTimeout();
        dataSource.setLoginTimeout(3);

        assertThat(loginTimeout).isEqualTo(11);
        assertThat(delegate.getLoginTimeout()).isEqualTo(3);
    }

    private static final class RecordingDataSource implements DataSource {
        private int connectionRequests;
        private int loginTimeout;
        private PrintWriter logWriter;

        @Override
        public Connection getConnection() {
            connectionRequests++;
            return null;
        }

        @Override
        public Connection getConnection(String username, String password) {
            connectionRequests++;
            return null;
        }

        @Override
        public PrintWriter getLogWriter() {
            return logWriter;
        }

        @Override
        public void setLogWriter(PrintWriter logWriter) {
            this.logWriter = logWriter;
        }

        @Override
        public void setLoginTimeout(int seconds) {
            loginTimeout = seconds;
        }

        @Override
        public int getLoginTimeout() {
            return loginTimeout;
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

        private int connectionRequests() {
            return connectionRequests;
        }
    }
}
