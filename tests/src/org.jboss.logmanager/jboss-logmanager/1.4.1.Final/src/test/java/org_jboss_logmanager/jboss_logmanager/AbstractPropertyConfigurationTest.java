/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_logmanager.jboss_logmanager;

import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

import org.jboss.logmanager.LogContext;
import org.jboss.logmanager.Logger;
import org.jboss.logmanager.config.FormatterConfiguration;
import org.jboss.logmanager.config.HandlerConfiguration;
import org.jboss.logmanager.config.LogContextConfiguration;
import org.jboss.logmanager.config.LoggerConfiguration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AbstractPropertyConfigurationTest {

    @Test
    void constructorPropertyTypeCanBeResolvedFromPublicGetter() {
        String loggerName = AbstractPropertyConfigurationTest.class.getName() + ".constructorProperty";
        String formatterName = "constructorFormatter";
        String handlerName = "capturingHandler";
        LogContext context = LogContext.create();
        LogContextConfiguration configuration = LogContextConfiguration.Factory.create(context);

        FormatterConfiguration formatter = configuration.addFormatterConfiguration(
                null,
                PrefixFormatter.class.getName(),
                formatterName,
                "prefix"
        );
        formatter.setPropertyValueString("prefix", "constructed:");

        HandlerConfiguration handler = configuration.addHandlerConfiguration(
                null,
                CapturingHandler.class.getName(),
                handlerName
        );
        handler.setFormatterName(formatterName);

        LoggerConfiguration loggerConfiguration = configuration.addLoggerConfiguration(loggerName);
        loggerConfiguration.setUseParentHandlers(false);
        loggerConfiguration.addHandlerName(handlerName);

        configuration.commit();

        Logger logger = context.getLogger(loggerName);
        logger.info("message");

        assertThat(logger.getHandlers()).singleElement().isInstanceOfSatisfying(CapturingHandler.class,
                capturingHandler -> assertThat(capturingHandler.getLastPublishedMessage())
                        .isEqualTo("constructed:message"));
    }

    public static final class PrefixFormatter extends Formatter {
        private final String prefix;

        public PrefixFormatter(final String prefix) {
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
