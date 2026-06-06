/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.mchange_commons_java;

import com.mchange.v1.db.sql.ConnectionBundlePoolBean;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverPropertyInfo;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;

public class ConnectionBundlePoolBeanTest {
    @Test
    void initLoadsConfiguredJdbcDriverClassBeforeCreatingThePool() throws Exception {
        InitializationTracker.reset();

        ConnectionBundlePoolBean bean = new ConnectionBundlePoolBean();
        bean.init(TrackingJdbcDriver.class.getName(), "jdbc:connection-bundle-pool:test", "user", "password", 0, 0, 0);
        bean.close();

        assertThat(InitializationTracker.loadCount()).isEqualTo(1);
    }

    public static final class TrackingJdbcDriver implements Driver {
        static {
            InitializationTracker.recordLoad();
        }

        @Override
        public Connection connect(String url, Properties info) {
            return null;
        }

        @Override
        public boolean acceptsURL(String url) {
            return false;
        }

        @Override
        public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) {
            return new DriverPropertyInfo[0];
        }

        @Override
        public int getMajorVersion() {
            return 1;
        }

        @Override
        public int getMinorVersion() {
            return 0;
        }

        @Override
        public boolean jdbcCompliant() {
            return false;
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException();
        }
    }

    private static final class InitializationTracker {
        private static final AtomicInteger LOAD_COUNT = new AtomicInteger();

        private InitializationTracker() {
        }

        private static void reset() {
            LOAD_COUNT.set(0);
        }

        private static void recordLoad() {
            LOAD_COUNT.incrementAndGet();
        }

        private static int loadCount() {
            return LOAD_COUNT.get();
        }
    }
}
