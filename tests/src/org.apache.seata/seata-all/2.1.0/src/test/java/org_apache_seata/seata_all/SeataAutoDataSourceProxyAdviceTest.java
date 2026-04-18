/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_seata.seata_all;

import java.io.PrintWriter;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;

import javax.sql.DataSource;

import org.aopalliance.intercept.MethodInvocation;
import org.apache.seata.core.context.RootContext;
import org.apache.seata.core.model.BranchType;
import org.apache.seata.rm.datasource.SeataDataSourceProxy;
import org.apache.seata.spring.annotation.datasource.SeataAutoDataSourceProxyAdvice;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SeataAutoDataSourceProxyAdviceTest {
    @AfterEach
    void clearRootContext() {
        RootContext.unbindGlobalLockFlag();
        RootContext.unbindBranchType();
        RootContext.unbind();
    }

    @Test
    void invokeUsesTheRegisteredSeataDataSourceProxyInsideExpectedContext() throws Throwable {
        SeataAutoDataSourceProxyAdvice advice = new SeataAutoDataSourceProxyAdvice(BranchType.AT.name());
        TrackingDataSource origin = new TrackingDataSource(7);
        TrackingSeataDataSourceProxy seataProxy = new TrackingSeataDataSourceProxy(origin, 42);
        MethodInvocation invocation = new StubMethodInvocation(origin, DataSource.class.getMethod("getLoginTimeout"));

        registerProxy(origin, seataProxy);

        RootContext.bindGlobalLockFlag();
        try {
            assertThat(advice.invoke(invocation)).isEqualTo(42);
        } finally {
            RootContext.unbindGlobalLockFlag();
        }

        assertThat(origin.getLoginTimeoutCallCount).isZero();
        assertThat(seataProxy.getLoginTimeoutCallCount).isEqualTo(1);
    }

    private static void registerProxy(DataSource origin, SeataDataSourceProxy seataProxy) throws ReflectiveOperationException {
        Class<?> holderClass = Class.forName("org.apache.seata.spring.annotation.datasource.DataSourceProxyHolder");
        Method putMethod = holderClass.getDeclaredMethod("put", DataSource.class, SeataDataSourceProxy.class);
        putMethod.setAccessible(true);
        putMethod.invoke(null, origin, seataProxy);
    }

    public static final class StubMethodInvocation implements MethodInvocation {
        private final DataSource target;
        private final Method method;

        public StubMethodInvocation(DataSource target, Method method) {
            this.target = target;
            this.method = method;
        }

        @Override
        public Method getMethod() {
            return method;
        }

        @Override
        public Object[] getArguments() {
            return new Object[0];
        }

        @Override
        public Object proceed() throws Throwable {
            return method.invoke(target);
        }

        @Override
        public Object getThis() {
            return target;
        }

        @Override
        public AccessibleObject getStaticPart() {
            return method;
        }
    }

    public static class TrackingDataSource implements DataSource {
        private final int loginTimeout;
        private int getLoginTimeoutCallCount;

        public TrackingDataSource(int loginTimeout) {
            this.loginTimeout = loginTimeout;
        }

        @Override
        public Connection getConnection() {
            throw new UnsupportedOperationException("Not required for this test");
        }

        @Override
        public Connection getConnection(String username, String password) {
            throw new UnsupportedOperationException("Not required for this test");
        }

        @Override
        public PrintWriter getLogWriter() {
            throw new UnsupportedOperationException("Not required for this test");
        }

        @Override
        public void setLogWriter(PrintWriter out) {
            throw new UnsupportedOperationException("Not required for this test");
        }

        @Override
        public void setLoginTimeout(int seconds) {
            throw new UnsupportedOperationException("Not required for this test");
        }

        @Override
        public int getLoginTimeout() {
            getLoginTimeoutCallCount++;
            return loginTimeout;
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException("Not required for this test");
        }

        @Override
        public <T> T unwrap(Class<T> iface) {
            throw new UnsupportedOperationException("Not required for this test");
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return false;
        }
    }

    public static final class TrackingSeataDataSourceProxy extends TrackingDataSource implements SeataDataSourceProxy {
        private final DataSource targetDataSource;
        private int getLoginTimeoutCallCount;

        public TrackingSeataDataSourceProxy(DataSource targetDataSource, int loginTimeout) {
            super(loginTimeout);
            this.targetDataSource = targetDataSource;
        }

        @Override
        public DataSource getTargetDataSource() {
            return targetDataSource;
        }

        @Override
        public BranchType getBranchType() {
            return BranchType.AT;
        }

        @Override
        public int getLoginTimeout() {
            getLoginTimeoutCallCount++;
            return super.getLoginTimeout();
        }
    }
}
