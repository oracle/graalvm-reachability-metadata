/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kylin.kylin_jdbc;

import org.apache.kylin.jdbc.shaded.org.apache.commons.logging.Log;
import org.apache.kylin.jdbc.shaded.org.apache.commons.logging.LogConfigurationException;
import org.apache.kylin.jdbc.shaded.org.apache.commons.logging.LogFactory;
import org.apache.kylin.jdbc.shaded.org.apache.commons.logging.impl.LogFactoryImpl;
import org.apache.kylin.jdbc.shaded.org.apache.commons.logging.impl.SimpleLog;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@Order(1)
public class LogFactoryImplTest {
    @Test
    void createsTheConfiguredLoggingImplementationAfterContextLoaderFallback() {
        String originalDiagnostics = System.getProperty(LogFactory.DIAGNOSTICS_DEST_PROPERTY);
        Thread thread = Thread.currentThread();
        ClassLoader originalLoader = thread.getContextClassLoader();
        LogFactoryImpl factory = null;
        try {
            System.setProperty(LogFactory.DIAGNOSTICS_DEST_PROPERTY, "STDOUT");
            thread.setContextClassLoader(new LogFilteringClassLoader(originalLoader));
            factory = new LogFactoryImpl();
            factory.setAttribute(LogFactoryImpl.LOG_PROPERTY, FactoryAwareLog.class.getName());

            Log log = factory.getInstance("configured.logger");

            assertThat(log).isInstanceOf(FactoryAwareLog.class);
            assertThat(((FactoryAwareLog) log).name).isEqualTo("configured.logger");
            assertThat(((FactoryAwareLog) log).factory).isSameAs(factory);
            assertThat(factory.getInstance("configured.logger")).isSameAs(log);
        } finally {
            release(factory);
            thread.setContextClassLoader(originalLoader);
            restoreProperty(LogFactory.DIAGNOSTICS_DEST_PROPERTY, originalDiagnostics);
        }
    }

    @Test
    void rejectsAConfiguredClassThatDoesNotImplementLog() {
        LogFactoryImpl factory = new LogFactoryImpl();
        factory.setAttribute(LogFactoryImpl.ALLOW_FLAWED_DISCOVERY_PROPERTY, Boolean.FALSE);
        factory.setAttribute(LogFactoryImpl.LOG_PROPERTY, InvalidLogAdapter.class.getName());
        try {
            assertThatExceptionOfType(LogConfigurationException.class)
                    .isThrownBy(() -> factory.getInstance("invalid.logger"))
                    .withMessageContaining("does not implement the Log interface");
        } finally {
            factory.release();
        }
    }

    private static void release(LogFactoryImpl factory) {
        if (factory != null) {
            factory.release();
        }
    }

    private static void restoreProperty(String name, String originalValue) {
        if (originalValue == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, originalValue);
        }
    }

    private static final class LogFilteringClassLoader extends ClassLoader {
        private LogFilteringClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (FactoryAwareLog.class.getName().equals(name)) {
                throw new ClassNotFoundException(name);
            }
            return super.loadClass(name, resolve);
        }
    }

    public static final class InvalidLogAdapter {
        public InvalidLogAdapter(String name) {
        }
    }

    public static final class FactoryAwareLog extends SimpleLog {
        private final String name;
        private LogFactoryImpl factory;

        public FactoryAwareLog(String name) {
            super(name);
            this.name = name;
        }

        public void setLogFactory(LogFactory factory) {
            this.factory = (LogFactoryImpl) factory;
        }
    }
}
