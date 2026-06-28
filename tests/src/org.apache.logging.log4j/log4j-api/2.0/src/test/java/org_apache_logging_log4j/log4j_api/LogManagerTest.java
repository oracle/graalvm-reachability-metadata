/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_logging_log4j.log4j_api;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.simple.SimpleLoggerContextFactory;
import org.apache.logging.log4j.spi.LoggerContextFactory;
import org.junit.jupiter.api.Test;

public class LogManagerTest {
    @Test
    void loadsProviderLoggerContextFactory() {
        LoggerContextFactory factory = LogManager.getFactory();

        assertThat(factory).isInstanceOf(SimpleLoggerContextFactory.class);
        assertThat(LogManager.getLogger("example.logger").getName()).isEqualTo("example.logger");
    }
}
