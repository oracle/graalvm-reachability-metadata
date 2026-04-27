/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_logmanager.jboss_logmanager;

import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jboss.logmanager.ConfigurationLocator;
import org.jboss.logmanager.LogManager;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LogManagerTest {

    @Test
    void readConfigurationLoadsLocatorFromThreadContextClassLoader() throws Exception {
        String locatorPropertyName = "org.jboss.logmanager.configurationLocator";
        String originalLocatorProperty = System.getProperty(locatorPropertyName);
        ClassLoader originalTccl = Thread.currentThread().getContextClassLoader();
        TcclConfigurationLocator.reset();
        try {
            System.setProperty(locatorPropertyName, TcclConfigurationLocator.class.getName());
            Thread.currentThread().setContextClassLoader(TcclConfigurationLocator.class.getClassLoader());

            new LogManager().readConfiguration();

            assertThat(TcclConfigurationLocator.CONSTRUCTED).isTrue();
            assertThat(TcclConfigurationLocator.FIND_CONFIGURATION_CALLED).isTrue();
        } finally {
            Thread.currentThread().setContextClassLoader(originalTccl);
            if (originalLocatorProperty == null) {
                System.clearProperty(locatorPropertyName);
            } else {
                System.setProperty(locatorPropertyName, originalLocatorProperty);
            }
            TcclConfigurationLocator.reset();
        }
    }

    @Test
    void readConfigurationFallsBackToLogManagerClassLoaderWhenTcclCannotLoadLocator() throws Exception {
        String locatorPropertyName = "org.jboss.logmanager.configurationLocator";
        String originalLocatorProperty = System.getProperty(locatorPropertyName);
        ClassLoader originalTccl = Thread.currentThread().getContextClassLoader();
        FallbackConfigurationLocator.reset();
        try {
            System.setProperty(locatorPropertyName, FallbackConfigurationLocator.class.getName());
            Thread.currentThread().setContextClassLoader(
                    new RejectingClassLoader(originalTccl, FallbackConfigurationLocator.class.getName())
            );

            new LogManager().readConfiguration();

            assertThat(FallbackConfigurationLocator.CONSTRUCTED).isTrue();
            assertThat(FallbackConfigurationLocator.FIND_CONFIGURATION_CALLED).isTrue();
        } finally {
            Thread.currentThread().setContextClassLoader(originalTccl);
            if (originalLocatorProperty == null) {
                System.clearProperty(locatorPropertyName);
            } else {
                System.setProperty(locatorPropertyName, originalLocatorProperty);
            }
            FallbackConfigurationLocator.reset();
        }
    }

    public static final class TcclConfigurationLocator implements ConfigurationLocator {
        private static final AtomicBoolean CONSTRUCTED = new AtomicBoolean();
        private static final AtomicBoolean FIND_CONFIGURATION_CALLED = new AtomicBoolean();

        public TcclConfigurationLocator() {
            CONSTRUCTED.set(true);
        }

        @Override
        public InputStream findConfiguration() {
            FIND_CONFIGURATION_CALLED.set(true);
            return null;
        }

        private static void reset() {
            CONSTRUCTED.set(false);
            FIND_CONFIGURATION_CALLED.set(false);
        }
    }

    public static final class FallbackConfigurationLocator implements ConfigurationLocator {
        private static final AtomicBoolean CONSTRUCTED = new AtomicBoolean();
        private static final AtomicBoolean FIND_CONFIGURATION_CALLED = new AtomicBoolean();

        public FallbackConfigurationLocator() {
            CONSTRUCTED.set(true);
        }

        @Override
        public InputStream findConfiguration() {
            FIND_CONFIGURATION_CALLED.set(true);
            return null;
        }

        private static void reset() {
            CONSTRUCTED.set(false);
            FIND_CONFIGURATION_CALLED.set(false);
        }
    }

    private static final class RejectingClassLoader extends ClassLoader {
        private final String rejectedClassName;

        private RejectingClassLoader(final ClassLoader parent, final String rejectedClassName) {
            super(parent);
            this.rejectedClassName = rejectedClassName;
        }

        @Override
        protected Class<?> loadClass(final String name, final boolean resolve) throws ClassNotFoundException {
            if (rejectedClassName.equals(name)) {
                throw new ClassNotFoundException(name);
            }
            return super.loadClass(name, resolve);
        }
    }
}
