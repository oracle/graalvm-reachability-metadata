/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.slf4j;

import org.junit.jupiter.api.Test;
import org.slf4j.helpers.NOPLoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

public class LoggerFactoryTest {

    @Test
    void initializesWithNopFallbackWhenNoBindingIsPresent() {
        LoggerFactory.reset();

        ILoggerFactory loggerFactory = LoggerFactory.getILoggerFactory();
        Logger logger = LoggerFactory.getLogger(LoggerFactoryTest.class);

        assertThat(loggerFactory).isInstanceOf(NOPLoggerFactory.class);
        assertThat(logger.isInfoEnabled()).isFalse();
    }
}
