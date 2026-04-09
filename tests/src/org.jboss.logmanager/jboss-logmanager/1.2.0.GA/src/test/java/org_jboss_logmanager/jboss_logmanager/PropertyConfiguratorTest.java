/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_logmanager.jboss_logmanager;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.logging.ErrorManager;
import java.util.logging.Filter;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

import org.jboss.logmanager.LogContext;
import org.jboss.logmanager.Logger;
import org.jboss.logmanager.PropertyConfigurator;
import org.junit.jupiter.api.Test;

class PropertyConfiguratorTest {

    @Test
    void configuresReferencedLoggingComponentsAndProperties() throws Exception {
        final String loggerName = "property-configurator-" + System.nanoTime();
        final Logger logger = LogContext.getSystemLogContext().getLogger(loggerName);
        final Handler[] originalHandlers = logger.clearHandlers();
        final boolean originalUseParentHandlers = logger.getUseParentHandlers();

        try {
            final PropertyConfigurator configurator = new PropertyConfigurator();
            final InputStream inputStream = new ByteArrayInputStream(createConfiguration(loggerName)
                    .getBytes(StandardCharsets.UTF_8));

            configurator.configure(inputStream);

            assertThat(logger.getUseParentHandlers()).isFalse();
            assertThat(logger.getHandlers()).singleElement().isInstanceOf(TestHandler.class);

            final TestHandler handler = (TestHandler) logger.getHandlers()[0];
            assertThat(handler.getText()).isEqualTo("configured-handler");
            assertThat(handler.isEnabled()).isTrue();
            assertThat(handler.getFilter()).isInstanceOf(TestFilter.class);
            assertThat(handler.getFormatter()).isInstanceOf(TestFormatter.class);
            assertThat(handler.getErrorManager()).isInstanceOf(TestErrorManager.class);

            final TestFilter filter = (TestFilter) handler.getFilter();
            final TestFormatter formatter = (TestFormatter) handler.getFormatter();
            final TestErrorManager errorManager = (TestErrorManager) handler.getErrorManager();

            assertThat(filter.getPattern()).isEqualTo("allow");
            assertThat(formatter.getSuffix()).isEqualTo(" formatted");
            assertThat(errorManager.getName()).isEqualTo("configured-error-manager");

            logger.info("allow message");

            assertThat(handler.getPublishedCount()).isEqualTo(1);
            assertThat(handler.getLastFormattedMessage()).isEqualTo("allow message formatted");
        } finally {
            logger.clearHandlers();
            logger.setUseParentHandlers(originalUseParentHandlers);
            for (Handler handler : originalHandlers) {
                logger.addHandler(handler);
            }
        }
    }

    private static String createConfiguration(final String loggerName) {
        return String.join("\n",
                "loggers=" + loggerName,
                "logger." + loggerName + ".handlers=testHandler",
                "logger." + loggerName + ".useParentHandlers=false",
                "handler.testHandler=" + TestHandler.class.getName(),
                "handler.testHandler.errorManager=testErrorManager",
                "handler.testHandler.filter=testFilter",
                "handler.testHandler.formatter=testFormatter",
                "handler.testHandler.properties=text,enabled",
                "handler.testHandler.text=configured-handler",
                "handler.testHandler.enabled=true",
                "errorManager.testErrorManager=" + TestErrorManager.class.getName(),
                "errorManager.testErrorManager.properties=name",
                "errorManager.testErrorManager.name=configured-error-manager",
                "filter.testFilter=" + TestFilter.class.getName(),
                "filter.testFilter.properties=pattern",
                "filter.testFilter.pattern=allow",
                "formatter.testFormatter=" + TestFormatter.class.getName(),
                "formatter.testFormatter.properties=suffix",
                "formatter.testFormatter.suffix=\\ formatted",
                "");
    }

    public static final class TestHandler extends Handler {
        private String text;
        private boolean enabled;
        private int publishedCount;
        private String lastFormattedMessage;

        public void setText(final String text) {
            this.text = text;
        }

        public String getText() {
            return text;
        }

        public void setEnabled(final boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public int getPublishedCount() {
            return publishedCount;
        }

        public String getLastFormattedMessage() {
            return lastFormattedMessage;
        }

        @Override
        public void publish(final LogRecord record) {
            if (!enabled || !isLoggable(record)) {
                return;
            }
            publishedCount++;
            final Formatter formatter = getFormatter();
            lastFormattedMessage = formatter == null ? record.getMessage() : formatter.format(record);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }

    public static final class TestFilter implements Filter {
        private String pattern;

        public void setPattern(final String pattern) {
            this.pattern = pattern;
        }

        public String getPattern() {
            return pattern;
        }

        @Override
        public boolean isLoggable(final LogRecord record) {
            return pattern != null && record.getMessage().contains(pattern);
        }
    }

    public static final class TestFormatter extends Formatter {
        private String suffix;

        public void setSuffix(final String suffix) {
            this.suffix = suffix;
        }

        public String getSuffix() {
            return suffix;
        }

        @Override
        public String format(final LogRecord record) {
            return record.getMessage() + suffix;
        }
    }

    public static final class TestErrorManager extends ErrorManager {
        private String name;

        public void setName(final String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }
}
