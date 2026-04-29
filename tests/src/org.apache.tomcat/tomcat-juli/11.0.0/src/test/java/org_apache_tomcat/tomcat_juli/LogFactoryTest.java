/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat.tomcat_juli;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.junit.jupiter.api.Test;

public class LogFactoryTest {
    private static final String DIRECT_JDK_LOG_CLASS_NAME = "org.apache.juli.logging.DirectJDKLog";

    @Test
    void createsDefaultLogByName() {
        Log log = LogFactory.getLog("coverage.default-log");

        assertThat(log.getClass().getName()).isEqualTo(DIRECT_JDK_LOG_CLASS_NAME);
    }

    @Test
    void createsDefaultLogFromClassName() {
        Log log = LogFactory.getFactory().getInstance(LogFactoryTest.class);

        assertThat(log.getClass().getName()).isEqualTo(DIRECT_JDK_LOG_CLASS_NAME);
    }
}
