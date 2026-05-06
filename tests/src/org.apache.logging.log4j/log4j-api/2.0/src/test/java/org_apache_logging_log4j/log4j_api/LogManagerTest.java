/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_logging_log4j.log4j_api;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.simple.SimpleLoggerContextFactory;
import org.apache.logging.log4j.spi.LoggerContext;
import org.apache.logging.log4j.spi.LoggerContextFactory;
import org.junit.jupiter.api.Test;

public class LogManagerTest {
    private static final String FACTORY_PROPERTY_NAME = "log4j2.loggerContextFactory";

    @Test
    void initializesFactoryFromConfiguredClassAndProviderResource() {
        String previousFactory = System.getProperty(FACTORY_PROPERTY_NAME);
        System.setProperty(FACTORY_PROPERTY_NAME, FailingLoggerContextFactory.class.getName());
        try {
            LoggerContextFactory factory = LogManager.getFactory();

            assertThat(factory).isInstanceOf(ProviderLoggerContextFactory.class);
            assertThat(LogManager.getContext()).isNotNull();
            assertThat(LogManager.getLogger("coverage.logmanager")).isNotNull();
        } finally {
            if (previousFactory == null) {
                System.clearProperty(FACTORY_PROPERTY_NAME);
            } else {
                System.setProperty(FACTORY_PROPERTY_NAME, previousFactory);
            }
        }
    }

    public static class FailingLoggerContextFactory implements LoggerContextFactory {
        public FailingLoggerContextFactory() {
            throw new IllegalStateException("configured factory constructor was exercised");
        }

        @Override
        public LoggerContext getContext(final String fqcn, final ClassLoader loader, final Object externalContext,
                                        final boolean currentContext) {
            throw new UnsupportedOperationException("This factory is only used during LogManager initialization");
        }

        @Override
        public LoggerContext getContext(final String fqcn, final ClassLoader loader, final Object externalContext,
                                        final boolean currentContext, final URI configLocation, final String name) {
            throw new UnsupportedOperationException("This factory is only used during LogManager initialization");
        }

        @Override
        public void removeContext(final LoggerContext context) {
            throw new UnsupportedOperationException("This factory is only used during LogManager initialization");
        }
    }

    public static class ProviderLoggerContextFactory implements LoggerContextFactory {
        private final SimpleLoggerContextFactory delegate = new SimpleLoggerContextFactory();

        @Override
        public LoggerContext getContext(final String fqcn, final ClassLoader loader, final Object externalContext,
                                        final boolean currentContext) {
            return delegate.getContext(fqcn, loader, externalContext, currentContext);
        }

        @Override
        public LoggerContext getContext(final String fqcn, final ClassLoader loader, final Object externalContext,
                                        final boolean currentContext, final URI configLocation, final String name) {
            return delegate.getContext(fqcn, loader, externalContext, currentContext, configLocation, name);
        }

        @Override
        public void removeContext(final LoggerContext context) {
            delegate.removeContext(context);
        }
    }
}
