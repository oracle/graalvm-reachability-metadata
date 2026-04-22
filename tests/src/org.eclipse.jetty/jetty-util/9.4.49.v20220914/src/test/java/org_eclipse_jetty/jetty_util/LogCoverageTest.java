/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jetty.jetty_util;

import java.net.URL;
import java.net.URLClassLoader;

import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.log.StdErrLog;
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
        URL jettyUtilLocation = Log.class.getProtectionDomain().getCodeSource().getLocation();

        try (URLClassLoader parentLoader = new URLClassLoader(new URL[]{jettyUtilLocation}, ClassLoader.getPlatformClassLoader());
             ChildFirstUrlClassLoader childLoader = new ChildFirstUrlClassLoader(new URL[]{jettyUtilLocation}, parentLoader)) {
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

    private static final class ChildFirstUrlClassLoader extends URLClassLoader {
        private ChildFirstUrlClassLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        @Override
        protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!name.startsWith("org.eclipse.jetty.")) {
                return super.loadClass(name, resolve);
            }

            Class<?> loadedClass = findLoadedClass(name);
            if (loadedClass == null) {
                try {
                    loadedClass = findClass(name);
                } catch (ClassNotFoundException ignored) {
                    loadedClass = super.loadClass(name, false);
                }
            }

            if (resolve) {
                resolveClass(loadedClass);
            }
            return loadedClass;
        }
    }
}
