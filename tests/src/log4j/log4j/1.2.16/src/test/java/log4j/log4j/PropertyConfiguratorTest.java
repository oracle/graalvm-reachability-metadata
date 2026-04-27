/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package log4j.log4j;

import java.util.Properties;

import org.apache.log4j.Hierarchy;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.apache.log4j.spi.LoggerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PropertyConfiguratorTest {

    @BeforeEach
    void setUp() {
        resetState();
    }

    @AfterEach
    void tearDown() {
        resetState();
    }

    @Test
    void configuresLoggerFactoryFromProperties() {
        String loggerName = PropertyConfiguratorTest.class.getName() + "." + System.nanoTime();
        Hierarchy repository = (Hierarchy) LogManager.getLoggerRepository();
        Properties properties = new Properties();
        properties.setProperty("log4j.rootLogger", "ERROR");
        properties.setProperty("log4j.loggerFactory", TrackingLoggerFactory.class.getName());
        properties.setProperty("log4j.factory.tag", "configured-by-property-configurator");
        properties.setProperty("log4j.logger." + loggerName, "INFO");
        properties.setProperty("log4j.additivity." + loggerName, "false");

        new PropertyConfigurator().doConfigure(properties, repository);

        assertThat(TrackingLoggerFactory.lastInstance).isNotNull();
        assertThat(TrackingLoggerFactory.lastInstance.getTag()).isEqualTo("configured-by-property-configurator");
        assertThat(TrackingLoggerFactory.makeNewLoggerInstanceCallCount).isEqualTo(1);
        assertThat(TrackingLoggerFactory.lastLoggerName).isEqualTo(loggerName);

        Logger configuredLogger = repository.getLogger(loggerName);
        assertThat(configuredLogger).isInstanceOf(TrackingLogger.class);
        assertThat(configuredLogger.getName()).isEqualTo(loggerName);
        assertThat(configuredLogger.getLevel()).isEqualTo(Level.INFO);
        assertThat(configuredLogger.getAdditivity()).isFalse();
        assertThat(((TrackingLogger) configuredLogger).getFactoryTag())
                .isEqualTo("configured-by-property-configurator");
    }

    private static void resetState() {
        LogManager.resetConfiguration();
        TrackingLoggerFactory.reset();
    }

    public static final class TrackingLoggerFactory implements LoggerFactory {
        private static TrackingLoggerFactory lastInstance;
        private static int makeNewLoggerInstanceCallCount;
        private static String lastLoggerName;

        private String tag;

        public TrackingLoggerFactory() {
            lastInstance = this;
        }

        @Override
        public Logger makeNewLoggerInstance(String name) {
            makeNewLoggerInstanceCallCount++;
            lastLoggerName = name;
            return new TrackingLogger(name, tag);
        }

        public String getTag() {
            return tag;
        }

        public void setTag(String tag) {
            this.tag = tag;
        }

        private static void reset() {
            lastInstance = null;
            makeNewLoggerInstanceCallCount = 0;
            lastLoggerName = null;
        }
    }

    public static final class TrackingLogger extends Logger {
        private final String factoryTag;

        private TrackingLogger(String name, String factoryTag) {
            super(name);
            this.factoryTag = factoryTag;
        }

        public String getFactoryTag() {
            return factoryTag;
        }
    }
}
