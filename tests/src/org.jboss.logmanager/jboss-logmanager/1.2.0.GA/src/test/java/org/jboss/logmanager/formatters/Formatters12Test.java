/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.jboss.logmanager.formatters;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import org.jboss.logmanager.ExtLogRecord;
import org.junit.jupiter.api.Test;

class Formatters12Test {

    @Test
    void extendedExceptionFormattingFallsBackToBootstrapClassLoading() throws Exception {
        final String bootstrapOnlyClassName = "java.util.UUID";
        final URL logmanagerLocation = PatternFormatter.class.getProtectionDomain().getCodeSource().getLocation();
        final BootstrapFallbackLogmanagerClassLoader isolatedLoader = new BootstrapFallbackLogmanagerClassLoader(
                logmanagerLocation,
                bootstrapOnlyClassName
        );
        final Formatter formatter = (Formatter) Class
                .forName(PatternFormatter.class.getName(), true, isolatedLoader)
                .getConstructor(String.class)
                .newInstance("%E");
        final LogRecord record = (LogRecord) Class
                .forName(ExtLogRecord.class.getName(), true, isolatedLoader)
                .getConstructor(Level.class, String.class, String.class)
                .newInstance(Level.SEVERE, "message", Formatters12Test.class.getName());
        final Throwable failure = new IllegalStateException("boom");
        final ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        final RejectingContextClassLoader contextClassLoader = new RejectingContextClassLoader(
                originalContextClassLoader,
                bootstrapOnlyClassName
        );

        failure.setStackTrace(new StackTraceElement[] {
                new StackTraceElement(
                        bootstrapOnlyClassName,
                        "loadedFromBootstrapFallback",
                        "UUID.java",
                        44
                )
        });
        record.setThrown(failure);

        Thread.currentThread().setContextClassLoader(contextClassLoader);
        try {
            final String formatted = formatter.format(record);

            assertThat(formatted)
                    .contains("java.lang.IllegalStateException: boom")
                    .contains("\tat " + bootstrapOnlyClassName + ".loadedFromBootstrapFallback");
        } finally {
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
        }
    }

    private static final class RejectingContextClassLoader extends ClassLoader {

        private final String rejectedClassName;
        private boolean rejectedBootstrapLoadAttempted;

        private RejectingContextClassLoader(final ClassLoader parent, final String rejectedClassName) {
            super(parent);
            this.rejectedClassName = rejectedClassName;
        }

        @Override
        protected Class<?> loadClass(final String name, final boolean resolve) throws ClassNotFoundException {
            if (rejectedClassName.equals(name)) {
                rejectedBootstrapLoadAttempted = true;
                throw new ClassNotFoundException(name);
            }
            return super.loadClass(name, resolve);
        }
    }

    private static final class BootstrapFallbackLogmanagerClassLoader extends URLClassLoader {

        private final String rejectedClassName;
        private boolean rejectedBootstrapLoadAttempted;

        private BootstrapFallbackLogmanagerClassLoader(final URL logmanagerLocation, final String rejectedClassName) {
            super(new URL[] {logmanagerLocation}, null);
            this.rejectedClassName = rejectedClassName;
        }

        @Override
        protected Class<?> loadClass(final String name, final boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                final Class<?> loadedClass = findLoadedClass(name);
                if (loadedClass != null) {
                    if (resolve) {
                        resolveClass(loadedClass);
                    }
                    return loadedClass;
                }
                if (rejectedClassName.equals(name)) {
                    rejectedBootstrapLoadAttempted = true;
                    throw new ClassNotFoundException(name);
                }
                if (name.startsWith("org.jboss.logmanager.")) {
                    try {
                        final Class<?> definedClass = findClass(name);
                        if (resolve) {
                            resolveClass(definedClass);
                        }
                        return definedClass;
                    } catch (ClassNotFoundException ignored) {
                    }
                }
                final Class<?> systemClass = findSystemClass(name);
                if (resolve) {
                    resolveClass(systemClass);
                }
                return systemClass;
            }
        }
    }
}
