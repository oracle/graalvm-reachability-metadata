/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_logmanager.jboss_logmanager;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

import org.jboss.logmanager.LogContext;
import org.jboss.logmanager.Logger;
import org.jboss.logmanager.config.HandlerConfiguration;
import org.jboss.logmanager.config.LogContextConfiguration;
import org.jboss.logmanager.config.LoggerConfiguration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AbstractPropertyConfigurationAnonymous3Test {

    @Test
    void setPostConfigurationMethodsResolvesPublicMethodsDuringCommit() {
        String loggerName = AbstractPropertyConfigurationAnonymous3Test.class.getName() + ".postConfigurationMethods";
        String handlerName = "multiStepPostConfiguredHandler";
        LogContext context = LogContext.create();
        LogContextConfiguration configuration = LogContextConfiguration.Factory.create(context);

        HandlerConfiguration handler = configuration.addHandlerConfiguration(
                null,
                MultiStepPostConfiguredHandler.class.getName(),
                handlerName
        );
        handler.setPostConfigurationMethods("prepare", "activate");

        LoggerConfiguration loggerConfiguration = configuration.addLoggerConfiguration(loggerName);
        loggerConfiguration.setUseParentHandlers(false);
        loggerConfiguration.addHandlerName(handlerName);

        configuration.commit();

        Logger logger = context.getLogger(loggerName);
        assertThat(handler.getPostConfigurationMethods()).containsExactly("prepare", "activate");
        assertThat(logger.getHandlers()).singleElement().isInstanceOfSatisfying(MultiStepPostConfiguredHandler.class,
                postConfiguredHandler -> assertThat(postConfiguredHandler.getLifecycleEvents())
                        .containsExactly("prepared", "activated"));
    }

    public static final class MultiStepPostConfiguredHandler extends Handler {
        private final List<String> lifecycleEvents = new ArrayList<>();

        public void prepare() {
            lifecycleEvents.add("prepared");
        }

        public void activate() {
            lifecycleEvents.add("activated");
        }

        public List<String> getLifecycleEvents() {
            return lifecycleEvents;
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
