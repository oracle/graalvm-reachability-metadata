/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_logging_log4j.log4j_core;

import org.apache.logging.log4j.core.config.LockingReliabilityStrategy;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.config.ReliabilityStrategy;
import org.apache.logging.log4j.core.config.ReliabilityStrategyFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

@ResourceLock(Resources.SYSTEM_PROPERTIES)
public class ReliabilityStrategyFactoryTest {
    @Test
    void passesTheLoggerConfigurationToACustomStrategy() {
        LoggerConfig logger = new LoggerConfig();
        ReliabilityStrategy strategy = TestSupport.withProperty("log4j.ReliabilityStrategy",
                ApplicationStrategy.class.getName(), () -> ReliabilityStrategyFactory.getReliabilityStrategy(logger));
        assertInstanceOf(ApplicationStrategy.class, strategy);
        assertSame(logger, strategy.getActiveLoggerConfig(() -> logger));
        strategy.afterLogEvent();
    }

    public static class ApplicationStrategy extends LockingReliabilityStrategy {
        public ApplicationStrategy(LoggerConfig logger) {
            super(logger);
        }
    }
}
