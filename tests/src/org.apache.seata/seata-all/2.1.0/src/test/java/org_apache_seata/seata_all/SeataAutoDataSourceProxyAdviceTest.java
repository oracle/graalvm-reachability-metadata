/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_seata.seata_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;
import javax.sql.DataSource;

import org.apache.seata.core.context.RootContext;
import org.apache.seata.core.model.BranchType;
import org.apache.seata.rm.datasource.SeataDataSourceProxy;
import org.apache.seata.spring.annotation.datasource.SeataAutoDataSourceProxyCreator;
import org.junit.jupiter.api.Test;

public class SeataAutoDataSourceProxyAdviceTest {
    @Test
    void routesDataSourceMethodCallsToRegisteredSeataProxyInExpectedContext() throws Exception {
        RecordingDataSource origin = new RecordingDataSource(7);
        RecordingSeataDataSourceProxy seataProxy = new RecordingSeataDataSourceProxy(origin, 13);
        SeataAutoDataSourceProxyCreator creator = new SeataAutoDataSourceProxyCreator(true, new String[0],
                BranchType.XA.name());

        Object advisedDataSource = creator.postProcessAfterInitialization(seataProxy, "dataSource");

        RootContext.bindGlobalLockFlag();
        try {
            assertThat(((DataSource) advisedDataSource).getLoginTimeout()).isEqualTo(13);
        } finally {
            RootContext.unbindGlobalLockFlag();
            RootContext.unbind();
            RootContext.unbindBranchType();
        }

        assertThat(origin.getLoginTimeoutCalls()).isZero();
        assertThat(seataProxy.getLoginTimeoutCalls()).isOne();
    }

    private static class RecordingDataSource implements DataSource {
        private int loginTimeout;
        private int getLoginTimeoutCalls;
        private PrintWriter logWriter;

        RecordingDataSource(int loginTimeout) {
            this.loginTimeout = loginTimeout;
        }

        @Override
        public Connection getConnection() throws SQLException {
            throw new SQLFeatureNotSupportedException("Connections are not used by this test");
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            throw new SQLFeatureNotSupportedException("Connections are not used by this test");
        }

        @Override
        public PrintWriter getLogWriter() throws SQLException {
            return logWriter;
        }

        @Override
        public void setLogWriter(PrintWriter out) throws SQLException {
            this.logWriter = out;
        }

        @Override
        public void setLoginTimeout(int seconds) throws SQLException {
            this.loginTimeout = seconds;
        }

        @Override
        public int getLoginTimeout() throws SQLException {
            getLoginTimeoutCalls++;
            return loginTimeout;
        }

        int getLoginTimeoutCalls() {
            return getLoginTimeoutCalls;
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            return Logger.getGlobal();
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            if (iface.isInstance(this)) {
                return iface.cast(this);
            }
            throw new SQLFeatureNotSupportedException("Unsupported unwrap type: " + iface.getName());
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) throws SQLException {
            return iface.isInstance(this);
        }
    }

    private static final class RecordingSeataDataSourceProxy extends RecordingDataSource
            implements SeataDataSourceProxy {
        private final DataSource targetDataSource;

        RecordingSeataDataSourceProxy(DataSource targetDataSource, int loginTimeout) {
            super(loginTimeout);
            this.targetDataSource = targetDataSource;
        }

        @Override
        public DataSource getTargetDataSource() {
            return targetDataSource;
        }

        @Override
        public BranchType getBranchType() {
            return BranchType.XA;
        }
    }
}
