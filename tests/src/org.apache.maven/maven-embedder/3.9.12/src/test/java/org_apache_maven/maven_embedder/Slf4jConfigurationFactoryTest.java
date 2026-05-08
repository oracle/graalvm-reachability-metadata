/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_embedder;

import org.apache.maven.cli.logging.Slf4jConfiguration;
import org.apache.maven.cli.logging.Slf4jConfigurationFactory;
import org.junit.jupiter.api.Test;
import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;
import org.slf4j.helpers.NOPLogger;

import static org.assertj.core.api.Assertions.assertThat;

public class Slf4jConfigurationFactoryTest {
    @Test
    void loadsConfigurationFromClasspathResource() {
        Slf4jConfiguration configuration = Slf4jConfigurationFactory.getConfiguration(new TestLoggerFactory());

        assertThat(configuration).isInstanceOf(TestSlf4jConfiguration.class);
    }

    public static final class TestLoggerFactory implements ILoggerFactory {
        @Override
        public Logger getLogger(String name) {
            return NOPLogger.NOP_LOGGER;
        }
    }

    public static final class TestSlf4jConfiguration implements Slf4jConfiguration {
        @Override
        public void setRootLoggerLevel(Level level) {
        }

        @Override
        public void activate() {
        }
    }
}
