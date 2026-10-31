/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_logging_log4j.log4j_api;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.ParameterizedMessageFactory;
import org.apache.logging.log4j.simple.SimpleLogger;
import org.apache.logging.log4j.simple.SimpleLoggerContext;
import org.apache.logging.log4j.simple.SimpleLoggerContextFactory;
import org.apache.logging.log4j.spi.LoggerContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LogManagerTest {

    @Test
    void usesLoggerContextFactoryNamedBySystemProperty() {
        assertThat(LogManager.getFactory()).isInstanceOf(SimpleLoggerContextFactory.class);

        LoggerContext context = LogManager.getContext(false);
        assertThat(context).isInstanceOf(SimpleLoggerContext.class);
    }

    @Test
    void createsSimpleLoggersWithDefaultMessageFactory() {
        Logger logger = LogManager.getLogger(LogManagerTest.class);

        assertThat(logger).isInstanceOf(SimpleLogger.class);
        assertThat(logger.getName()).isEqualTo(LogManagerTest.class.getName());
        assertThat(logger.getMessageFactory()).isInstanceOf(ParameterizedMessageFactory.class);
        assertThat(logger.isEnabled(Level.ERROR)).isTrue();
        assertThat(logger.isEnabled(Level.DEBUG)).isFalse();
        assertThat(LogManager.getLogger(LogManagerTest.class)).isSameAs(logger);
    }
}
