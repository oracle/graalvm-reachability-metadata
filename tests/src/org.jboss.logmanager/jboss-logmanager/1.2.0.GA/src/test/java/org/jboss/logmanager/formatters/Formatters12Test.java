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
import java.net.MalformedURLException;
import java.net.URL;
import java.security.ProtectionDomain;
import java.util.logging.Level;
import org.jboss.logmanager.ExtLogRecord;
import org.junit.jupiter.api.Test;

class Formatters12Test {

    @Test
    void extendedExceptionFormattingUsesClassLoadingFallbacksAndClassResourceLookup() throws Exception {
        final PatternFormatter formatter = new PatternFormatter("%E");
        final ExtLogRecord record = new ExtLogRecord(Level.SEVERE, "message", Formatters12Test.class.getName());
        final Throwable failure = new IllegalStateException("boom");
        final ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        final ResourceReportingClassLoader contextClassLoader = new ResourceReportingClassLoader(
                Formatters12Test.class.getClassLoader(),
                Formatters12ReloadableTarget.class.getName(),
                Formatters12SystemLoadedTarget.class.getName()
        );

        failure.setStackTrace(new StackTraceElement[] {
                new StackTraceElement(
                        Formatters12ReloadableTarget.class.getName(),
                        "loadedFromContextClassLoader",
                        "Formatters12ReloadableTarget.java",
                        11
                ),
                new StackTraceElement(
                        Formatters12SystemLoadedTarget.class.getName(),
                        "loadedFromDefaultClassLoader",
                        "Formatters12SystemLoadedTarget.java",
                        22
                ),
                new StackTraceElement(
                        "org.jboss.logmanager.formatters.MissingFormatterTarget",
                        "loadedFromBootstrapFallback",
                        "MissingFormatterTarget.java",
                        33
                )
        });
        record.setThrown(failure);

        Thread.currentThread().setContextClassLoader(contextClassLoader);
        try {
            final String formatted = formatter.format(record);

            assertThat(formatted)
                    .contains("java.lang.IllegalStateException: boom")
                    .contains("\tat " + Formatters12ReloadableTarget.class.getName() + ".loadedFromContextClassLoader")
                    .contains("[generated-loader.jar:]")
                    .contains("\tat " + Formatters12SystemLoadedTarget.class.getName() + ".loadedFromDefaultClassLoader")
                    .contains("\tat org.jboss.logmanager.formatters.MissingFormatterTarget.loadedFromBootstrapFallback");
            assertThat(contextClassLoader.loadedFromBytes).isTrue();
            assertThat(contextClassLoader.rejectedSystemTargetAttempted).isTrue();
            assertThat(contextClassLoader.classResourceRequested).isTrue();
        } finally {
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
        }
    }

    private static final class ResourceReportingClassLoader extends ClassLoader {

        private final String byteDefinedClassName;
        private final String rejectedClassName;
        private boolean loadedFromBytes;
        private boolean rejectedSystemTargetAttempted;
        private boolean classResourceRequested;

        private ResourceReportingClassLoader(
                final ClassLoader parent,
                final String byteDefinedClassName,
                final String rejectedClassName
        ) {
            super(parent);
            this.byteDefinedClassName = byteDefinedClassName;
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
                    rejectedSystemTargetAttempted = true;
                    throw new ClassNotFoundException(name);
                }
                if (byteDefinedClassName.equals(name)) {
                    final byte[] classBytes = readClassBytes(name);
                    final ProtectionDomain protectionDomain = new ProtectionDomain(null, null);
                    final Class<?> definedClass = defineClass(name, classBytes, 0, classBytes.length, protectionDomain);
                    if (resolve) {
                        resolveClass(definedClass);
                    }
                    loadedFromBytes = true;
                    return definedClass;
                }
                return super.loadClass(name, resolve);
            }
        }

        @Override
        public URL getResource(final String name) {
            final String targetResourceName = byteDefinedClassName.replace('.', '/') + ".class";
            if (targetResourceName.equals(name)) {
                classResourceRequested = true;
                try {
                    return new URL("jar:file:/generated-loader.jar!/" + name);
                } catch (MalformedURLException exception) {
                    throw new IllegalStateException("Unable to create a class resource URL for " + name, exception);
                }
            }
            return super.getResource(name);
        }

        private static byte[] readClassBytes(final String className) {
            final String resourceName = className.replace('.', '/') + ".class";
            try (InputStream inputStream = Formatters12Test.class.getClassLoader().getResourceAsStream(resourceName)) {
                if (inputStream == null) {
                    throw new IllegalStateException("Missing class bytes for " + resourceName);
                }
                return inputStream.readAllBytes();
            } catch (IOException exception) {
                throw new IllegalStateException("Unable to read class bytes for " + resourceName, exception);
            }
        }
    }
}

class Formatters12ReloadableTarget {
}

class Formatters12SystemLoadedTarget {
}
