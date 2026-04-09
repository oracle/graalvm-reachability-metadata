/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.jboss.logmanager.formatters;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Set;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import org.jboss.logmanager.ExtLogRecord;
import org.junit.jupiter.api.Test;

class Formatters$12Test {

    private static final String NULL_LOADER_ONLY_CLASS_NAME = ThreadDeath.class.getName();

    @Test
    void extendedExceptionFormattingUsesAllClassResolutionFallbacks() throws Exception {
        final Thread thread = Thread.currentThread();
        final ClassLoader originalContextClassLoader = thread.getContextClassLoader();
        final ClassLoader contextClassLoader = new ResourceFreeClassLoader(
                ContextLoadedFrame.class.getName(),
                readClassBytes(ContextLoadedFrame.class));
        final Throwable thrown = new IllegalStateException("boom");
        final StackTraceElement[] stackTrace = {
                new StackTraceElement(ContextLoadedFrame.class.getName(), "invoke", "ContextLoadedFrame.java", 12),
                new StackTraceElement("org.jboss.logmanager.formatters.MissingFrame", "invoke", "MissingFrame.java", 24)
        };
        final ExtLogRecord record = new ExtLogRecord(Level.SEVERE, "message", Formatters$12Test.class.getName());
        final PatternFormatter formatter = new PatternFormatter("%E");

        thrown.setStackTrace(stackTrace);
        record.setThrown(thrown);

        try {
            thread.setContextClassLoader(contextClassLoader);

            final String formatted = formatter.format(record);

            assertThat(formatted)
                    .contains("java.lang.IllegalStateException: boom")
                    .contains(ContextLoadedFrame.class.getName())
                    .contains("org.jboss.logmanager.formatters.MissingFrame");
        } finally {
            thread.setContextClassLoader(originalContextClassLoader);
        }
    }

    @Test
    void extendedExceptionFormattingFallsBackToCallerAndBootstrapClassLoaders() throws Exception {
        final Thread thread = Thread.currentThread();
        final ClassLoader originalContextClassLoader = thread.getContextClassLoader();
        final ClassLoader contextClassLoader = new BlockingClassLoader(Set.of(
                IsolatedLoaderVisibleFrame.class.getName(),
                NULL_LOADER_ONLY_CLASS_NAME));
        final Throwable thrown = new IllegalStateException("boom");
        final StackTraceElement[] stackTrace = {
                new StackTraceElement(IsolatedLoaderVisibleFrame.class.getName(), "invoke", "IsolatedLoaderVisibleFrame.java", 18),
                new StackTraceElement(NULL_LOADER_ONLY_CLASS_NAME, "invoke", "ThreadDeath.java", 27)
        };
        final LogRecord record = new LogRecord(Level.SEVERE, "message");

        thrown.setStackTrace(stackTrace);
        record.setThrown(thrown);

        try (IsolatedFormatterClassLoader formatterClassLoader = new IsolatedFormatterClassLoader(Set.of(NULL_LOADER_ONLY_CLASS_NAME))) {
            final Formatter formatter = createFormatter(formatterClassLoader);
            thread.setContextClassLoader(contextClassLoader);

            final String formatted = formatter.format(record);

            assertThat(formatted)
                    .contains(IsolatedLoaderVisibleFrame.class.getName())
                    .contains(NULL_LOADER_ONLY_CLASS_NAME);
        } finally {
            thread.setContextClassLoader(originalContextClassLoader);
        }
    }

    private static Formatter createFormatter(final ClassLoader formatterClassLoader) throws Exception {
        final Class<?> formatterClass = formatterClassLoader.loadClass(PatternFormatter.class.getName());
        return (Formatter) formatterClass.getConstructor(String.class).newInstance("%E");
    }

    private static byte[] readClassBytes(final Class<?> type) throws IOException {
        final String resourceName = type.getName().replace('.', '/') + ".class";
        final InputStream resourceStream = type.getClassLoader().getResourceAsStream(resourceName);

        assertThat(resourceStream).isNotNull();
        try (resourceStream) {
            return resourceStream.readAllBytes();
        }
    }

    private static final class ResourceFreeClassLoader extends ClassLoader {
        private final String className;
        private final byte[] classBytes;

        private ResourceFreeClassLoader(final String className, final byte[] classBytes) {
            super(null);
            this.className = className;
            this.classBytes = classBytes;
        }

        @Override
        protected Class<?> findClass(final String name) throws ClassNotFoundException {
            if (className.equals(name)) {
                return defineClass(name, classBytes, 0, classBytes.length);
            }
            throw new ClassNotFoundException(name);
        }

        @Override
        public URL getResource(final String name) {
            return null;
        }
    }

    private static final class BlockingClassLoader extends ClassLoader {
        private final Set<String> blockedClassNames;

        private BlockingClassLoader(final Set<String> blockedClassNames) {
            super(ClassLoader.getPlatformClassLoader());
            this.blockedClassNames = blockedClassNames;
        }

        @Override
        protected Class<?> loadClass(final String name, final boolean resolve) throws ClassNotFoundException {
            if (blockedClassNames.contains(name)) {
                throw new ClassNotFoundException(name);
            }
            return super.loadClass(name, resolve);
        }
    }

    private static final class IsolatedFormatterClassLoader extends URLClassLoader {
        private static final String ISOLATED_PACKAGE_PREFIX = "org.jboss.logmanager.";

        private final Set<String> blockedClassNames;

        private IsolatedFormatterClassLoader(final Set<String> blockedClassNames) {
            super(new URL[] {
                    PatternFormatter.class.getProtectionDomain().getCodeSource().getLocation(),
                    Formatters$12Test.class.getProtectionDomain().getCodeSource().getLocation()
            }, ClassLoader.getPlatformClassLoader());
            this.blockedClassNames = blockedClassNames;
        }

        @Override
        protected Class<?> loadClass(final String name, final boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                final Class<?> loadedClass = findLoadedClass(name);
                if (loadedClass != null) {
                    return loadedClass;
                }
                if (blockedClassNames.contains(name)) {
                    throw new ClassNotFoundException(name);
                }
                if (name.startsWith(ISOLATED_PACKAGE_PREFIX)) {
                    final Class<?> isolatedClass = findClass(name);
                    if (resolve) {
                        resolveClass(isolatedClass);
                    }
                    return isolatedClass;
                }
                return super.loadClass(name, resolve);
            }
        }
    }
}

final class ContextLoadedFrame {
    private ContextLoadedFrame() {
    }
}

final class IsolatedLoaderVisibleFrame {
    private IsolatedLoaderVisibleFrame() {
    }
}
