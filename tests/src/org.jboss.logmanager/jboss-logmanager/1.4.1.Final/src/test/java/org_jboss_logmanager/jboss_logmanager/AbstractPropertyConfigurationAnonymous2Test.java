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
import org.jboss.logmanager.config.LoggerConfiguration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AbstractPropertyConfigurationAnonymous2Test {

    @Test
    void addPostConfigurationMethodResolvesPublicNoArgMethodDuringCommit() {
        String loggerName = AbstractPropertyConfigurationAnonymous2Test.class.getName() + ".postConfiguration";
        String handlerName = "postConfiguredHandler";
        LogContext context = LogContext.create();
        LogContextConfiguration configuration = LogContextConfiguration.Factory.create(context);

        HandlerConfiguration handler = configuration.addHandlerConfiguration(
                null,
                PostConfiguredHandler.class.getName(),
                handlerName
        );
        assertThat(handler.addPostConfigurationMethod("activate")).isTrue();

        LoggerConfiguration loggerConfiguration = configuration.addLoggerConfiguration(loggerName);
        loggerConfiguration.setUseParentHandlers(false);
        loggerConfiguration.addHandlerName(handlerName);

        configuration.commit();

        Logger logger = context.getLogger(loggerName);
        assertThat(logger.getHandlers()).singleElement().isInstanceOfSatisfying(PostConfiguredHandler.class,
                postConfiguredHandler -> assertThat(postConfiguredHandler.isActivated()).isTrue());
    }

    public static final class PostConfiguredHandler extends Handler {
        private boolean activated;

        public void activate() {
            activated = true;
        }

        public boolean isActivated() {
            return activated;
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
