/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_logging.commons_logging_api;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.logging.impl.LogFactoryImpl;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LogFactoryImplTest {
    private static final String DIAGNOSTICS_DEST_PROPERTY = "org.apache.commons.logging.diagnostics.dest";

    @Test
    void createsAndReusesUserSpecifiedLogAdapter() {
        String previousDiagnosticsDestination = System.getProperty(DIAGNOSTICS_DEST_PROPERTY);
        ClassLoader previousContextClassLoader = Thread.currentThread().getContextClassLoader();
        System.setProperty(DIAGNOSTICS_DEST_PROPERTY, "STDOUT");
        Thread.currentThread().setContextClassLoader(new AdapterFilteringClassLoader(previousContextClassLoader));

        LogFactoryImpl factory = null;
        try {
            factory = new LogFactoryImpl();
            factory.setAttribute(LogFactoryImpl.LOG_PROPERTY, RecordingLog.class.getName());

            Log firstLog = factory.getInstance("first.logger");
            Log secondLog = factory.getInstance("second.logger");

            assertThat(firstLog).isInstanceOf(RecordingLog.class);
            assertThat(secondLog).isInstanceOf(RecordingLog.class);
            assertThat(((RecordingLog) firstLog).name).isEqualTo("first.logger");
            assertThat(((RecordingLog) secondLog).name).isEqualTo("second.logger");
            assertThat(((RecordingLog) firstLog).logFactory).isSameAs(factory);
            assertThat(((RecordingLog) secondLog).logFactory).isSameAs(factory);
            assertThat(factory.getInstance("first.logger")).isSameAs(firstLog);
        } finally {
            release(factory);
            Thread.currentThread().setContextClassLoader(previousContextClassLoader);
            restoreProperty(DIAGNOSTICS_DEST_PROPERTY, previousDiagnosticsDestination);
        }
    }

    @Test
    void createsUserSpecifiedLogAdapterFromContextClassLoader() {
        String previousDiagnosticsDestination = System.getProperty(DIAGNOSTICS_DEST_PROPERTY);
        ClassLoader previousContextClassLoader = Thread.currentThread().getContextClassLoader();
        System.setProperty(DIAGNOSTICS_DEST_PROPERTY, "STDOUT");

        LogFactoryImpl factory = null;
        try {
            factory = new LogFactoryImpl();
            factory.setAttribute(LogFactoryImpl.LOG_PROPERTY, RecordingLog.class.getName());

            Log log = factory.getInstance("context.loader.logger");

            assertThat(log).isInstanceOf(RecordingLog.class);
            assertThat(((RecordingLog) log).name).isEqualTo("context.loader.logger");
            assertThat(((RecordingLog) log).logFactory).isSameAs(factory);
        } finally {
            release(factory);
            Thread.currentThread().setContextClassLoader(previousContextClassLoader);
            restoreProperty(DIAGNOSTICS_DEST_PROPERTY, previousDiagnosticsDestination);
        }
    }

    @Test
    void discoversSimpleLogWithNullContextClassLoader() {
        String previousDiagnosticsDestination = System.getProperty(DIAGNOSTICS_DEST_PROPERTY);
        ClassLoader previousContextClassLoader = Thread.currentThread().getContextClassLoader();
        System.setProperty(DIAGNOSTICS_DEST_PROPERTY, "STDOUT");
        Thread.currentThread().setContextClassLoader(null);

        LogFactoryImpl factory = null;
        try {
            factory = new LogFactoryImpl();
            factory.setAttribute(LogFactoryImpl.LOG_PROPERTY, "org.apache.commons.logging.impl.SimpleLog");

            Log log = factory.getInstance("null.context.loader.logger");

            assertThat(log.getClass().getName()).isEqualTo("org.apache.commons.logging.impl.SimpleLog");
        } finally {
            release(factory);
            Thread.currentThread().setContextClassLoader(previousContextClassLoader);
            restoreProperty(DIAGNOSTICS_DEST_PROPERTY, previousDiagnosticsDestination);
        }
    }

    @Test
    void skipsUnavailableDiscoveryCandidatesBeforeSelectingLogger() {
        String previousDiagnosticsDestination = System.getProperty(DIAGNOSTICS_DEST_PROPERTY);
        System.setProperty(DIAGNOSTICS_DEST_PROPERTY, "STDOUT");

        LogFactoryImpl factory = null;
        try {
            factory = new LogFactoryImpl();

            Log log = factory.getInstance("default.discovery.logger");

            assertThat(log).isNotNull();
            assertThat(log.isInfoEnabled()).isTrue();
        } finally {
            release(factory);
            restoreProperty(DIAGNOSTICS_DEST_PROPERTY, previousDiagnosticsDestination);
        }
    }

    private static void release(LogFactoryImpl factory) {
        if (factory != null) {
            factory.release();
        }
    }

    private static void restoreProperty(String name, String previousValue) {
        if (previousValue == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, previousValue);
        }
    }

    private static final class AdapterFilteringClassLoader extends ClassLoader {
        private AdapterFilteringClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (RecordingLog.class.getName().equals(name)) {
                throw new ClassNotFoundException(name);
            }
            return super.loadClass(name, resolve);
        }
    }

    public static final class RecordingLog implements Log {
        private final String name;
        private LogFactory logFactory;

        public RecordingLog(String name) {
            this.name = name;
        }

        public void setLogFactory(LogFactory logFactory) {
            this.logFactory = logFactory;
        }

        @Override
        public void debug(Object message) {
        }

        @Override
        public void debug(Object message, Throwable throwable) {
        }

        @Override
        public void error(Object message) {
        }

        @Override
        public void error(Object message, Throwable throwable) {
        }

        @Override
        public void fatal(Object message) {
        }

        @Override
        public void fatal(Object message, Throwable throwable) {
        }

        @Override
        public void info(Object message) {
        }

        @Override
        public void info(Object message, Throwable throwable) {
        }

        @Override
        public boolean isDebugEnabled() {
            return true;
        }

        @Override
        public boolean isErrorEnabled() {
            return true;
        }

        @Override
        public boolean isFatalEnabled() {
            return true;
        }

        @Override
        public boolean isInfoEnabled() {
            return true;
        }

        @Override
        public boolean isTraceEnabled() {
            return true;
        }

        @Override
        public boolean isWarnEnabled() {
            return true;
        }

        @Override
        public void trace(Object message) {
        }

        @Override
        public void trace(Object message, Throwable throwable) {
        }

        @Override
        public void warn(Object message) {
        }

        @Override
        public void warn(Object message, Throwable throwable) {
        }
    }
}
