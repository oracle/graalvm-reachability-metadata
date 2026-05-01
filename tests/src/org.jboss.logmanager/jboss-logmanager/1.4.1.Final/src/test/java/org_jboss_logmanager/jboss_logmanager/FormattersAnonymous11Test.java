/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_logmanager.jboss_logmanager;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import org.graalvm.internal.tck.NativeImageSupport;
import org.jboss.logmanager.ExtLogRecord;
import org.jboss.logmanager.formatters.FormatStep;
import org.jboss.logmanager.formatters.Formatters;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FormattersAnonymous11Test {
    private static final String FORMATTERS_CLASS_NAME = "org.jboss.logmanager.formatters.Formatters";
    private static final String FORMATTERS_INNER_CLASS_PREFIX = FORMATTERS_CLASS_NAME + "$";

    @Test
    void extendedExceptionFormattingFallsBackWhenThreadContextClassLoaderIsAbsent() {
        final String bootstrapClassName = String.class.getName();
        final String missingClassName = "org.jboss.logmanager.coverage.MissingFrameClass";
        final IllegalStateException failure = new IllegalStateException("boom");
        failure.setStackTrace(new StackTraceElement[] {
                new StackTraceElement(bootstrapClassName, "valueOf", "String.java", 1),
                new StackTraceElement(missingClassName, "invoke", "MissingFrameClass.java", 2)
        });

        final String formatted = formatWithTccl(
                null,
                Formatters.exceptionFormatStep(false, 0, 0, true),
                failure
        );

        assertThat(formatted)
                .contains("\tat " + bootstrapClassName + ".valueOf")
                .contains("\tat " + missingClassName + ".invoke");
    }

    @Test
    void extendedExceptionFormattingFallsBackToBootstrapClassLoaderWhenOwningLoaderCannotLoadFrameClass()
            throws Exception {
        try {
            final String bootstrapClassName = ProcessHandle.class.getName();
            final IsolatedFormattersClassLoader classLoader = new IsolatedFormattersClassLoader(
                    FormattersAnonymous11Test.class.getClassLoader(),
                    bootstrapClassName
            );
            final Class<?> formattersClass = classLoader.loadClass(FORMATTERS_CLASS_NAME);
            final Method exceptionFormatStep = formattersClass.getMethod(
                    "exceptionFormatStep",
                    boolean.class,
                    int.class,
                    int.class,
                    boolean.class
            );
            final FormatStep formatStep = (FormatStep) exceptionFormatStep.invoke(null, false, 0, 0, true);
            final IllegalStateException failure = new IllegalStateException("boom");
            failure.setStackTrace(new StackTraceElement[] {
                    new StackTraceElement(bootstrapClassName, "current", "ProcessHandle.java", 3)
            });

            classLoader.rejectFrameClassLookups();
            final String formatted = formatWithTccl(null, formatStep, failure);

            assertThat(formatted).contains("\tat " + bootstrapClassName + ".current");
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    private static String formatWithTccl(
            final ClassLoader contextClassLoader,
            final FormatStep formatStep,
            final Throwable thrown
    ) {
        final ClassLoader originalTccl = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(contextClassLoader);
            final ExtLogRecord record = new ExtLogRecord(
                    Level.SEVERE,
                    "coverage",
                    FormattersAnonymous11Test.class.getName()
            );
            record.setThrown(thrown);
            final StringBuilder builder = new StringBuilder();
            formatStep.render(builder, record);
            return builder.toString();
        } finally {
            Thread.currentThread().setContextClassLoader(originalTccl);
        }
    }

    private static final class IsolatedFormattersClassLoader extends ClassLoader {
        private final Map<String, Class<?>> definedClasses = new ConcurrentHashMap<>();
        private final String rejectedFrameClassName;
        private boolean rejectFrameClassLookups;

        private IsolatedFormattersClassLoader(final ClassLoader parent, final String rejectedFrameClassName) {
            super(parent);
            this.rejectedFrameClassName = rejectedFrameClassName;
        }

        void rejectFrameClassLookups() {
            rejectFrameClassLookups = true;
        }

        @Override
        protected Class<?> loadClass(final String name, final boolean resolve) throws ClassNotFoundException {
            if (rejectFrameClassLookups && rejectedFrameClassName.equals(name)) {
                throw new ClassNotFoundException(name);
            }
            if (FORMATTERS_CLASS_NAME.equals(name) || name.startsWith(FORMATTERS_INNER_CLASS_PREFIX)) {
                final Class<?> loadedClass = definedClasses.computeIfAbsent(name, this::defineFormatterClass);
                if (resolve) {
                    resolveClass(loadedClass);
                }
                return loadedClass;
            }
            return super.loadClass(name, resolve);
        }

        private Class<?> defineFormatterClass(final String name) {
            final byte[] classBytes = readClassBytes(name);
            return defineClass(name, classBytes, 0, classBytes.length, Formatters.class.getProtectionDomain());
        }

        private byte[] readClassBytes(final String className) {
            final String resourceName = className.replace('.', '/') + ".class";
            try (InputStream inputStream = getParent().getResourceAsStream(resourceName)) {
                if (inputStream == null) {
                    throw new IllegalStateException("Missing class resource: " + resourceName);
                }
                return inputStream.readAllBytes();
            } catch (IOException exception) {
                throw new IllegalStateException("Could not read class resource: " + resourceName, exception);
            }
        }
    }
}
