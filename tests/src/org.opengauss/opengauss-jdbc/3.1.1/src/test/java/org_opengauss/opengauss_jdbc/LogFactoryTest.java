/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_opengauss.opengauss_jdbc;

import org.junit.jupiter.api.Test;
import org.postgresql.log.JdkLogger;
import org.postgresql.log.Log;
import org.postgresql.log.LogFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises dynamic access paths owned by {@code org.postgresql.log.LogFactory}.
 */
public class LogFactoryTest {

    @Test
    void getLoggerAcceptsFullyQualifiedLoggerClassName() throws Exception {
        Log logger = LogFactory.getLogger(JdkLogger.class.getName(), "log-factory-test");

        assertThat(logger).isInstanceOf(JdkLogger.class);
    }
}
