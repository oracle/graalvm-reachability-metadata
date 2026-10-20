/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kylin.kylin_jdbc;

import org.apache.kylin.jdbc.shaded.org.apache.commons.logging.Log;
import org.apache.kylin.jdbc.shaded.org.apache.commons.logging.impl.LogFactoryImpl;
import org.apache.kylin.jdbc.shaded.org.apache.commons.logging.impl.SimpleLog;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LogFactoryImplTest {
    @Test
    void createsTheConfiguredLoggingImplementation() {
        LogFactoryImpl factory = new LogFactoryImpl();
        factory.setAttribute(LogFactoryImpl.LOG_PROPERTY, SimpleLog.class.getName());
        try {
            Log log = factory.getInstance("configured.logger");

            assertThat(log).isInstanceOf(SimpleLog.class);
            assertThat(factory.getInstance("configured.logger")).isSameAs(log);
        } finally {
            factory.release();
        }
    }
}
