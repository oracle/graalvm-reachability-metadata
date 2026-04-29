/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_logmanager.jboss_logmanager;

import java.util.logging.Handler;
import java.util.logging.LogRecord;

import org.jboss.logmanager.LogContext;
import org.jboss.logmanager.Logger;
import org.jboss.logmanager.config.HandlerConfiguration;
import org.jboss.logmanager.config.LogContextConfiguration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AbstractPropertyConfigurationTest {

    @Test
    void constructorPropertyUsesPublicGetterToResolveConstructorType() {
        LogContext logContext = LogContext.create();
        LogContextConfiguration configuration = LogContextConfiguration.Factory.create(logContext);
        String loggerName = getClass().getName() + ".constructorProperty";
        String handlerName = "constructorPropertyHandler";

        HandlerConfiguration handlerConfiguration = configuration.addHandlerConfiguration(
                null,
                ConstructorPropertyHandler.class.getName(),
                handlerName,
                "label"
        );
        handlerConfiguration.setPropertyValueString("label", "configured-by-constructor");
        configuration.addLoggerConfiguration(loggerName).setHandlerNames(handlerName);

        configuration.commit();

        Logger logger = logContext.getLogger(loggerName);
        assertThat(logger.getHandlers()).singleElement().isInstanceOfSatisfying(ConstructorPropertyHandler.class,
                handler -> assertThat(handler.getLabel()).isEqualTo("configured-by-constructor"));
    }

    public static final class ConstructorPropertyHandler extends Handler {
        private final String label;

        public ConstructorPropertyHandler(final String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }

        @Override
        public void publish(final LogRecord record) {
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }
}
