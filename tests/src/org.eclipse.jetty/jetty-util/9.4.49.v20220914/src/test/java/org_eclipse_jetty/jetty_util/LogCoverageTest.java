/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jetty.jetty_util;

import java.io.InputStream;

import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.log.StdErrLog;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LogCoverageTest {
    @Test
    void logInitializesAndCanAttemptParentDelegation() {
        Log.initialized();
        Logger original = Log.getRootLogger();
        Logger logger = Log.getLogger(LogCoverageTest.class);

        assertThat(original).isInstanceOf(StdErrLog.class);
        assertThat(logger).isNotNull();

        try {
            Log.setLogToParent("log-coverage-parent");

            assertThat(Log.getRootLogger()).isNotNull();
        } finally {
            Log.setLog(original);
        }
    }

    @Test
    void setLogToParentDelegatesToParentLogWhenAvailable() throws Exception {
        Assumptions.assumeTrue(System.getProperty("org.graalvm.nativeimage.imagecode") == null);

        try (JettyResourceClassLoader parentLoader = new JettyResourceClassLoader(
            ClassLoader.getPlatformClassLoader(),
            getClass().getClassLoader()
        ); JettyResourceClassLoader childLoader = new JettyResourceClassLoader(parentLoader, getClass().getClassLoader())) {
            Class<?> childLogClass = Class.forName("org.eclipse.jetty.util.log.Log", true, childLoader);
            Class<?> childLoggerClass = Class.forName("org.eclipse.jetty.util.log.Logger", true, childLoader);
            Object originalLogger = childLogClass.getMethod("getRootLogger").invoke(null);

            try {
                childLogClass.getMethod("setLogToParent", String.class).invoke(null, "log-coverage-parent");

                Object delegatedLogger = childLogClass.getMethod("getRootLogger").invoke(null);
                String delegatedLoggerName = (String) delegatedLogger.getClass().getMethod("getName").invoke(delegatedLogger);

                assertThat(delegatedLogger.getClass().getName()).isEqualTo("org.eclipse.jetty.util.log.LoggerLog");
                assertThat(delegatedLoggerName).isEqualTo("log-coverage-parent");
            } finally {
                childLogClass.getMethod("setLog", childLoggerClass).invoke(null, originalLogger);
            }
        }
    }

    private static final class JettyResourceClassLoader extends ClassLoader implements AutoCloseable {
        private final ClassLoader resourceLoader;

        private JettyResourceClassLoader(ClassLoader parent, ClassLoader resourceLoader) {
            super(parent);
            this.resourceLoader = resourceLoader;
        }

        @Override
        protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!name.startsWith("org.eclipse.jetty.")) {
                return super.loadClass(name, resolve);
            }

            Class<?> loadedClass = findLoadedClass(name);
            if (loadedClass == null) {
                try {
                    loadedClass = findJettyClass(name);
                } catch (ClassNotFoundException ignored) {
                    loadedClass = getParent().loadClass(name);
                }
            }

            if (resolve) {
                resolveClass(loadedClass);
            }
            return loadedClass;
        }

        private Class<?> findJettyClass(String name) throws ClassNotFoundException {
            String resourceName = name.replace('.', '/') + ".class";
            try (InputStream inputStream = resourceLoader.getResourceAsStream(resourceName)) {
                if (inputStream == null) {
                    throw new ClassNotFoundException(name);
                }
                byte[] classBytes = inputStream.readAllBytes();
                return defineClass(name, classBytes, 0, classBytes.length);
            } catch (ClassNotFoundException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new ClassNotFoundException(name, exception);
            }
        }

        @Override
        public void close() {
        }
    }
}
