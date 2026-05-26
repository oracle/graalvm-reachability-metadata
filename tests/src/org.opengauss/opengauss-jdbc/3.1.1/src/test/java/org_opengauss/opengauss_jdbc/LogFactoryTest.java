/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_opengauss.opengauss_jdbc;

import org.junit.jupiter.api.Test;
import org.postgresql.log.Log;
import org.postgresql.log.Logger;

import static org.assertj.core.api.Assertions.assertThat;

public class LogFactoryTest {
    @Test
    void createsConfiguredJdkLoggerByFullyQualifiedName() {
        Logger.setLoggerName("org.postgresql.log.JdkLogger");
        try {
            Log log = Logger.getLogger("metadata-coverage");

            assertThat(log).isNotNull();
            assertThat(log.getClass().getName()).isEqualTo("org.postgresql.log.JdkLogger");
            assertThat(Logger.isUsingJDKLogger()).isTrue();
            log.info("log factory metadata coverage");
        } finally {
            Logger.setLoggerName(null);
        }
    }
}
