/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_dbcp2;

import org.apache.commons.dbcp2.BasicDataSourceMXBean;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;

public class TestBasicDataSourceMXBean {
    private final BasicDataSourceMXBean bean = new BasicDataSourceMXBean() {
        @Override
        public boolean getAbandonedUsageTracking() {
            return false;
        }

        @Override
        public boolean getCacheState() {
            return false;
        }

        @Override
        public String[] getConnectionInitSqlsAsArray() {
            return null;
        }

        @Override
        public Boolean getDefaultAutoCommit() {
            return null;
        }

        @Override
        public String getDefaultCatalog() {
            return null;
        }

        @Override
        public Boolean getDefaultReadOnly() {
            return null;
        }

        @Override
        public int getDefaultTransactionIsolation() {
            return 0;
        }

        @Override
        public String[] getDisconnectionSqlCodesAsArray() {
            return null;
        }

        @Override
        public String getDriverClassName() {
            return null;
        }

        @Override
        public boolean getFastFailValidation() {
            return false;
        }

        @Override
        public int getInitialSize() {
            return 0;
        }

        @Override
        public boolean getLifo() {
            return false;
        }

        @Override
        public boolean getLogAbandoned() {
            return false;
        }

        @Override
        public boolean getLogExpiredConnections() {
            return false;
        }

        @Override
        public long getMaxConnLifetimeMillis() {
            return 0;
        }

        @Override
        public int getMaxIdle() {
            return 0;
        }

        @Override
        public int getMaxOpenPreparedStatements() {
            return 0;
        }

        @Override
        public int getMaxTotal() {
            return 0;
        }

        @Override
        public long getMaxWaitMillis() {
            return 0;
        }

        @Override
        public long getMinEvictableIdleTimeMillis() {
            return 0;
        }

        @Override
        public int getMinIdle() {
            return 0;
        }

        @Override
        public int getNumActive() {
            return 0;
        }

        @Override
        public int getNumIdle() {
            return 0;
        }

        @Override
        public int getNumTestsPerEvictionRun() {
            return 0;
        }

        @Override
        public String getPassword() {
            return null;
        }

        @Override
        public boolean getRemoveAbandonedOnBorrow() {
            return false;
        }

        @Override
        public boolean getRemoveAbandonedOnMaintenance() {
            return false;
        }

        @Override
        public int getRemoveAbandonedTimeout() {
            return 0;
        }

        @Override
        public long getSoftMinEvictableIdleTimeMillis() {
            return 0;
        }

        @Override
        public boolean getTestOnBorrow() {
            return false;
        }

        @Override
        public boolean getTestOnCreate() {
            return false;
        }

        @Override
        public boolean getTestWhileIdle() {
            return false;
        }

        @Override
        public long getTimeBetweenEvictionRunsMillis() {
            return 0;
        }

        @Override
        public String getUrl() {
            return null;
        }

        @Override
        public String getUsername() {
            return null;
        }

        @Override
        public String getValidationQuery() {
            return null;
        }

        @Override
        public int getValidationQueryTimeout() {
            return 0;
        }

        @Override
        public boolean isAccessToUnderlyingConnectionAllowed() {
            return false;
        }

        @Override
        public boolean isClearStatementPoolOnReturn() {
            return false;
        }

        @Override
        public boolean isClosed() {
            return false;
        }

        @Override
        public boolean isPoolPreparedStatements() {
            return false;
        }
    };

    @Test
    public void testDefaultSchema() {
        assertNull(bean.getDefaultSchema());
    }
}
