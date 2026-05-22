/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgroups.jgroups;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.graalvm.internal.tck.NativeImageSupport;
import org.jgroups.Global;
import org.jgroups.logging.JDKLogImpl;
import org.jgroups.logging.Log;
import org.jgroups.logging.LogFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LogFactoryTest {
    private static final String SCENARIO_CLASS_NAME = LogFactoryTest.class.getName() + "$LogFactoryScenario";
    private static final String RECORDING_LOG_CLASS_NAME = LogFactoryTest.class.getName() + "$RecordingLog";

    @Test
    void discoversAvailableLoggingBackendDuringInitialization() throws Exception {
        String[] result = invokeScenario("defaultLoggerType");
        if (result == null) {
            return;
        }

        assertThat(result).hasSize(1);
        assertThat(result[0]).isIn("log4j2", "slf4j");
    }

    @Test
    void createsConfiguredLoggerClassThroughPublicFactory() throws Exception {
        String[] result = invokeScenario("configuredLoggerType");
        if (result == null) {
            return;
        }

        assertThat(result).containsExactly(RECORDING_LOG_CLASS_NAME, "RecordingLog");
    }

    private static String[] invokeScenario(String methodName) throws Exception {
        try (IsolatedJGroupsLoader loader = new IsolatedJGroupsLoader(isolatedClasspath())) {
            ClassLoader originalContextLoader = Thread.currentThread().getContextClassLoader();
            Thread.currentThread().setContextClassLoader(loader);
            try {
                Class<?> scenarioClass = Class.forName(SCENARIO_CLASS_NAME, true, loader);
                Method method = scenarioClass.getMethod(methodName);
                return (String[]) method.invoke(null);
            } catch (InvocationTargetException ex) {
                Throwable cause = ex.getCause();
                if (cause instanceof Error error && NativeImageSupport.isUnsupportedFeatureError(error)) {
                    return null;
                }
                throw ex;
            } finally {
                Thread.currentThread().setContextClassLoader(originalContextLoader);
            }
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
            return null;
        }
    }

    private static URL[] isolatedClasspath() throws Exception {
        List<URL> urls = new ArrayList<>();
        for (String entry : System.getProperty("java.class.path").split(File.pathSeparator)) {
            urls.add(Path.of(entry).toUri().toURL());
        }
        return urls.toArray(URL[]::new);
    }

    public static class LogFactoryScenario {
        public static String[] defaultLoggerType() {
            String previousLogClass = System.clearProperty(Global.LOG_CLASS);
            String previousJdkLogger = System.clearProperty(Global.USE_JDK_LOGGER);
            try {
                return new String[] {LogFactory.loggerType()};
            } finally {
                restoreProperty(Global.LOG_CLASS, previousLogClass);
                restoreProperty(Global.USE_JDK_LOGGER, previousJdkLogger);
            }
        }

        public static String[] configuredLoggerType() {
            String previousLogClass = System.setProperty(Global.LOG_CLASS, RecordingLog.class.getName());
            String previousJdkLogger = System.clearProperty(Global.USE_JDK_LOGGER);
            try {
                Log log = LogFactory.getLog(LogFactoryScenario.class);
                log.info("created configured JGroups logger");
                return new String[] {log.getClass().getName(), LogFactory.loggerType()};
            } finally {
                restoreProperty(Global.LOG_CLASS, previousLogClass);
                restoreProperty(Global.USE_JDK_LOGGER, previousJdkLogger);
            }
        }

        private static void restoreProperty(String name, String value) {
            if (value == null) {
                System.clearProperty(name);
            } else {
                System.setProperty(name, value);
            }
        }
    }

    public static class RecordingLog extends JDKLogImpl {
        public RecordingLog(Class<?> clazz) {
            super(clazz);
        }
    }

    private static final class IsolatedJGroupsLoader extends URLClassLoader {
        private IsolatedJGroupsLoader(URL[] urls) {
            super(urls, null);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null && isChildFirst(name)) {
                    try {
                        loaded = findClass(name);
                    } catch (ClassNotFoundException ex) {
                        loaded = null;
                    }
                }
                if (loaded == null) {
                    loaded = super.loadClass(name, false);
                }
                if (resolve) {
                    resolveClass(loaded);
                }
                return loaded;
            }
        }

        private static boolean isChildFirst(String name) {
            return name.startsWith("org.jgroups.") || SCENARIO_CLASS_NAME.equals(name)
                    || RECORDING_LOG_CLASS_NAME.equals(name);
        }
    }
}
