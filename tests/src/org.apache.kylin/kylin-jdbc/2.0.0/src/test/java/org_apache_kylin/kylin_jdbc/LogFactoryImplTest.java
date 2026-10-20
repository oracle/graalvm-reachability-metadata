/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kylin.kylin_jdbc;

import org.apache.kylin.jdbc.shaded.org.apache.commons.logging.Log;
import org.apache.kylin.jdbc.shaded.org.apache.commons.logging.LogFactory;
import org.apache.kylin.jdbc.shaded.org.apache.commons.logging.impl.LogFactoryImpl;
import org.apache.kylin.jdbc.shaded.org.apache.commons.logging.impl.SimpleLog;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LogFactoryImplTest {
    @Test
    void createsTheConfiguredLoggingImplementation() {
        LogFactoryImpl factory = new LogFactoryImpl();
        factory.setAttribute(LogFactoryImpl.LOG_PROPERTY, FactoryAwareLog.class.getName());
        try {
            Log log = factory.getInstance("configured.logger");

            assertThat(log).isInstanceOf(FactoryAwareLog.class);
            assertThat(((FactoryAwareLog) log).factory).isSameAs(factory);
            assertThat(factory.getInstance("configured.logger")).isSameAs(log);
        } finally {
            factory.release();
        }
    }

    public static class FactoryAwareLog extends SimpleLog {
        private LogFactoryImpl factory;

        public FactoryAwareLog(String name) {
            super(name);
        }

        public void setLogFactory(LogFactory factory) {
            this.factory = (LogFactoryImpl) factory;
        }
    }
}
