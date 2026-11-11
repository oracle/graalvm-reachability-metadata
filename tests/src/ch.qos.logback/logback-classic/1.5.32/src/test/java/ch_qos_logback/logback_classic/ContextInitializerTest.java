/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_logback.logback_classic;

import java.nio.file.Files;
import java.nio.file.Path;

import ch.qos.logback.classic.ClassicConstants;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.util.ContextInitializer;
import ch.qos.logback.core.Appender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import static org.assertj.core.api.Assertions.assertThat;

@ResourceLock(Resources.SYSTEM_PROPERTIES)
public class ContextInitializerTest {

    @Test
    void automaticallyLoadsXmlConfigurator(@TempDir Path directory) throws Exception {
        Path configuration = directory.resolve("logging.xml");
        Files.writeString(configuration, """
                <configuration>
                    <contextName>configured-context</contextName>
                    <root level="ERROR"/>
                </configuration>
                """);
        String previous = System.getProperty(ClassicConstants.CONFIG_FILE_PROPERTY);
        LoggerContext context = new LoggerContext();
        try {
            System.setProperty(ClassicConstants.CONFIG_FILE_PROPERTY, configuration.toUri().toString());
            new ContextInitializer(context).autoConfig();

            assertThat(context.getName()).isEqualTo("configured-context");
            assertThat(context.getLogger(Logger.ROOT_LOGGER_NAME).getLevel()).isEqualTo(Level.ERROR);
            assertThat(context.getLogger("application").isInfoEnabled()).isFalse();
            assertThat(context.getLogger("application").isErrorEnabled()).isTrue();
        } finally {
            restoreConfigurationProperty(previous);
            context.stop();
        }
    }

    @Test
    void fallsBackToConsoleConfigurationWhenNoXmlIsProvided() throws Exception {
        String previous = System.getProperty(ClassicConstants.CONFIG_FILE_PROPERTY);
        LoggerContext context = new LoggerContext();
        try {
            System.clearProperty(ClassicConstants.CONFIG_FILE_PROPERTY);
            new ContextInitializer(context).autoConfig();

            Appender<ILoggingEvent> console = context.getLogger(Logger.ROOT_LOGGER_NAME).getAppender("console");
            assertThat(console).isNotNull();
            assertThat(console.isStarted()).isTrue();
        } finally {
            restoreConfigurationProperty(previous);
            context.stop();
        }
    }

    private static void restoreConfigurationProperty(String previous) {
        if (previous == null) {
            System.clearProperty(ClassicConstants.CONFIG_FILE_PROPERTY);
        } else {
            System.setProperty(ClassicConstants.CONFIG_FILE_PROPERTY, previous);
        }
    }
}
