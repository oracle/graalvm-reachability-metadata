/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_logmanager.jboss_logmanager;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import org.jboss.logmanager.LogContext;
import org.jboss.logmanager.Logger;
import org.jboss.logmanager.PropertyConfigurator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AbstractPropertyConfigurationTest {

    @Test
    void constructorPropertyTypeIsResolvedFromPublicGetter() throws IOException {
        String loggerName = AbstractPropertyConfigurationTest.class.getName() + ".constructorProperty";
        Logger logger = LogContext.getSystemLogContext().getLogger(loggerName);
        cleanup(logger);
        try {
            String configuration = """
                    loggers=%1$s
                    logger.%1$s.level=INFO
                    logger.%1$s.handlers=constructorHandler
                    logger.%1$s.useParentHandlers=false
                    handler.constructorHandler=%2$s
                    handler.constructorHandler.level=INFO
                    handler.constructorHandler.formatter=constructorFormatter
                    formatter.constructorFormatter=%3$s
                    formatter.constructorFormatter.constructorProperties=prefix
                    formatter.constructorFormatter.properties=prefix
                    formatter.constructorFormatter.prefix=constructed:
                    """.formatted(
                    loggerName,
                    CapturingHandler.class.getName(),
                    ConstructorBackedFormatter.class.getName()
            );

            new PropertyConfigurator().configure(
                    new ByteArrayInputStream(configuration.getBytes(StandardCharsets.UTF_8))
            );

            assertThat(logger.getLevel()).isEqualTo(Level.INFO);
            assertThat(logger.getUseParentHandlers()).isFalse();
            assertThat(logger.getHandlers()).singleElement().isInstanceOfSatisfying(CapturingHandler.class, handler -> {
                assertThat(handler.getFormatter()).isInstanceOfSatisfying(ConstructorBackedFormatter.class,
                        formatter -> assertThat(formatter.getPrefix()).isEqualTo("constructed:"));
            });

            CapturingHandler handler = (CapturingHandler) logger.getHandlers()[0];
            logger.info("message");
            assertThat(handler.getLastPublishedMessage()).isEqualTo("constructed:message");
        } finally {
            cleanup(logger);
        }
    }

    private static void cleanup(final Logger logger) {
        logger.setFilter(null);
        logger.setUseParentHandlers(true);
        for (Handler handler : logger.clearHandlers()) {
            handler.close();
        }
    }

    public static final class ConstructorBackedFormatter extends Formatter {
        private final String prefix;

        public ConstructorBackedFormatter(final String prefix) {
            this.prefix = prefix;
        }

        public String getPrefix() {
            return prefix;
        }

        @Override
        public String format(final LogRecord record) {
            return prefix + record.getMessage();
        }
    }

    public static final class CapturingHandler extends Handler {
        private String lastPublishedMessage;

        public String getLastPublishedMessage() {
            return lastPublishedMessage;
        }

        @Override
        public void publish(final LogRecord record) {
            if (!isLoggable(record)) {
                return;
            }
            Formatter formatter = getFormatter();
            lastPublishedMessage = formatter == null ? record.getMessage() : formatter.format(record);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }
}
