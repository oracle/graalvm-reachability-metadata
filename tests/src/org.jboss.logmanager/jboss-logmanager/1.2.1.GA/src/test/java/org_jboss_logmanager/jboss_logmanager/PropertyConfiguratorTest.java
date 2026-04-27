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
import java.util.logging.ErrorManager;
import java.util.logging.Filter;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import org.jboss.logmanager.LogContext;
import org.jboss.logmanager.Logger;
import org.jboss.logmanager.PropertyConfigurator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PropertyConfiguratorTest {

    @Test
    void configureInstantiatesAndConfiguresNamedComponents() throws IOException {
        String loggerName = PropertyConfiguratorTest.class.getName() + ".configured";
        Logger logger = LogContext.getSystemLogContext().getLogger(loggerName);
        cleanup(logger);
        try {
            String configuration = """
                    loggers=%1$s
                    logger.%1$s.level=INFO
                    logger.%1$s.filter=coverageFilter
                    logger.%1$s.handlers=coverageHandler
                    logger.%1$s.useParentHandlers=false
                    handler.coverageHandler=%2$s
                    handler.coverageHandler.level=INFO
                    handler.coverageHandler.encoding=UTF-8
                    handler.coverageHandler.errorManager=coverageErrorManager
                    handler.coverageHandler.filter=coverageFilter
                    handler.coverageHandler.formatter=coverageFormatter
                    handler.coverageHandler.properties=label
                    handler.coverageHandler.label=primary
                    filter.coverageFilter=%3$s
                    filter.coverageFilter.properties=enabled
                    filter.coverageFilter.enabled=true
                    formatter.coverageFormatter=%4$s
                    formatter.coverageFormatter.properties=prefix
                    formatter.coverageFormatter.prefix=formatted:
                    errorManager.coverageErrorManager=%5$s
                    errorManager.coverageErrorManager.properties=name
                    errorManager.coverageErrorManager.name=once
                    """.formatted(
                    loggerName,
                    TrackingHandler.class.getName(),
                    TrackingFilter.class.getName(),
                    TrackingFormatter.class.getName(),
                    TrackingErrorManager.class.getName()
            );

            new PropertyConfigurator().configure(
                    new ByteArrayInputStream(configuration.getBytes(StandardCharsets.UTF_8))
            );

            assertThat(logger.getUseParentHandlers()).isFalse();
            assertThat(logger.getLevel()).isEqualTo(Level.INFO);
            assertThat(logger.getFilter()).isInstanceOfSatisfying(TrackingFilter.class,
                    filter -> assertThat(filter.isEnabled()).isTrue());
            assertThat(logger.getHandlers()).singleElement().isInstanceOfSatisfying(TrackingHandler.class, handler -> {
                assertThat(handler.getLevel()).isEqualTo(Level.INFO);
                assertThat(handler.getEncoding()).isEqualTo("UTF-8");
                assertThat(handler.getLabel()).isEqualTo("primary");
                assertThat(handler.getFilter()).isSameAs(logger.getFilter());
                assertThat(handler.getFormatter()).isInstanceOfSatisfying(TrackingFormatter.class,
                        formatter -> assertThat(formatter.getPrefix()).isEqualTo("formatted:"));
                assertThat(handler.getConfiguredErrorManager()).isInstanceOfSatisfying(TrackingErrorManager.class,
                        errorManager -> assertThat(errorManager.getName()).isEqualTo("once"));
            });

            TrackingHandler handler = (TrackingHandler) logger.getHandlers()[0];
            logger.info("hello");
            assertThat(handler.getLastPublishedMessage()).isEqualTo("formatted:hello");
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

    public static final class TrackingHandler extends Handler {
        private String label;
        private String lastPublishedMessage;

        public String getLabel() {
            return label;
        }

        public void setLabel(final String label) {
            this.label = label;
        }

        public String getLastPublishedMessage() {
            return lastPublishedMessage;
        }

        public ErrorManager getConfiguredErrorManager() {
            return getErrorManager();
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

    public static final class TrackingFilter implements Filter {
        private boolean enabled;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(final boolean enabled) {
            this.enabled = enabled;
        }

        @Override
        public boolean isLoggable(final LogRecord record) {
            return enabled;
        }
    }

    public static final class TrackingFormatter extends Formatter {
        private String prefix = "";

        public String getPrefix() {
            return prefix;
        }

        public void setPrefix(final String prefix) {
            this.prefix = prefix;
        }

        @Override
        public String format(final LogRecord record) {
            return prefix + record.getMessage();
        }
    }

    public static final class TrackingErrorManager extends ErrorManager {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(final String name) {
            this.name = name;
        }
    }
}
